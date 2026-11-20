package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.CommunityPost;
import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.CommunityPostMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xinyu.common.AiJobValues.COMMUNITY_REPLY;
import static com.xinyu.common.AiJobValues.DISABLED;
import static com.xinyu.common.AiJobValues.LEGACY;

/** 社区任务的帖子校验、作者视图与来源失效规则。 */
@Service
public class CommunityReplySource {
    private final CommunityPostMapper posts;
    private final AiJobMapper jobs;
    private final AiJobService jobService;

    public CommunityReplySource(CommunityPostMapper posts, AiJobMapper jobs, AiJobService jobService) {
        this.posts = posts;
        this.jobs = jobs;
        this.jobService = jobService;
    }

    static boolean matches(CommunityPost post, AiJob job) {
        return post != null && post.isVisible()
                && Objects.equals(post.getId(), job.getTargetId())
                && Objects.equals(post.getUserId(), job.getUserId())
                && Objects.equals(post.getContentVersion(), job.getTargetVersion());
    }

    String currentReply(AiJob job) {
        CommunityPost post = posts.selectById(job.getTargetId());
        if (post == null || !post.isVisible() || !Objects.equals(post.getUserId(), job.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        if (!matches(post, job)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "回应任务已失效，请刷新帖子");
        }
        return post.getAiReply();
    }

    String retryContent(AiJob job) {
        CommunityPost post = posts.selectById(job.getTargetId());
        return matches(post, job) ? post.getContent() : null;
    }

    /** 调用方在帖子写事务内执行。UPDATE 先取得帖子行锁，再处理任务，和完成路径的锁顺序一致。 */
    public boolean hideVisible(long postId, String hideReason) {
        UpdateWrapper<CommunityPost> hide = new UpdateWrapper<>();
        hide.eq("id", postId).eq("status", CommunityReportConstants.STATUS_VISIBLE)
                .set("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN).set("hide_reason", hideReason);
        invalidateReply(hide);
        if (posts.update(null, hide) != 1) {
            return false;
        }

        CommunityPost post = posts.selectById(postId);
        jobService.supersedeCommunityJobs(postId, post.getUserId());
        return true;
    }

    static void invalidateReply(UpdateWrapper<CommunityPost> update) {
        update.setSql("content_version = content_version + 1")
                .set("ai_reply", null).set("ai_result_kind", null);
    }

    /** 仅对作者的当前版本查任务，每页最多一条查询。 */
    public void attachAuthorStates(List<CommunityPost> page, Long viewerId) {
        List<CommunityPost> owned = page.stream()
                .filter(post -> viewerId != null && viewerId.equals(post.getUserId()))
                .toList();
        if (owned.isEmpty()) {
            return;
        }

        Map<Long, AiJob> current = currentJobsByPost(owned, viewerId);
        for (CommunityPost post : owned) {
            AiJob job = current.get(post.getId());
            if (job == null) {
                post.setAiReplyState(post.getAiReply() == null || post.getAiReply().isBlank() ? DISABLED : LEGACY);
                post.setJobId(null);
            } else {
                post.setAiReplyState(job.getStatus());
                post.setJobId(job.getId());
            }
        }
    }

    private Map<Long, AiJob> currentJobsByPost(List<CommunityPost> posts, Long viewerId) {
        QueryWrapper<AiJob> query = new QueryWrapper<>();
        query.eq("user_id", viewerId).eq("task_type", COMMUNITY_REPLY);
        query.and(versions -> {
            for (CommunityPost post : posts) {
                versions.or(version -> version.eq("target_id", post.getId())
                        .eq("target_version", post.getContentVersion()));
            }
        });
        return jobs.selectList(query).stream()
                .collect(Collectors.toMap(AiJob::getTargetId, Function.identity()));
    }
}
