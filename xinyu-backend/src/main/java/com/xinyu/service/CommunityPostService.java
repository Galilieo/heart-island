package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.Favorite;
import com.xinyu.entity.PostLike;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.PostLikeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.vo.AdminPostDetailVO;
import com.xinyu.vo.AdminPostVO;
import com.xinyu.vo.CommunityWriteVO;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitType;

import static com.xinyu.common.AiJobValues.DISABLED;

/** 帖子的公开与作者视图、互动，以及正文和当前回复任务的持久化入口。 */
@Service
public class CommunityPostService {
    /** 正文和任务完成同一次保存后的提交者响应。 */
    public record PostSaveResult(boolean saved, CommunityWriteVO response) {

        private static PostSaveResult saved(CommunityPost post, AiJob job, boolean riskHit) {
            String supportText = riskHit ? MoodReviewSafety.SUPPORT_TEXT : null;
            Long jobId = job == null ? null : job.getId();
            String replyState = job == null ? DISABLED : job.getStatus();
            return new PostSaveResult(true, new CommunityWriteVO(supportText, post.getId(), jobId, replyState));
        }

        private static PostSaveResult failed() {
            return new PostSaveResult(false, null);
        }
    }

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private AiJobService jobs;

    @Autowired
    private AiJobAdmission admission;

    @Autowired
    private CommunityReplySource replySource;

    @Autowired(required = false)
    private ContentReportMapper contentReportMapper;

    @Autowired
    private RateLimiter rateLimiter;

    /** 分页查询公开可见的帖子，再批量填充本页的观看者状态。 */
    @Transactional(readOnly = true)
    public Page<CommunityPost> pagePosts(
            Page<CommunityPost> page, Long topicId, Long userId, Boolean mine, String sort) {
        QueryWrapper<CommunityPost> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("status", CommunityReportConstants.STATUS_VISIBLE);

        if (topicId != null) {
            queryWrapper.eq("topic_id", topicId);
        }

        if (Boolean.TRUE.equals(mine)) {
            queryWrapper.eq("user_id", userId);
        }

        // 时间相同的帖子用 ID 确定先后，避免同一批静态数据翻页时排序漂移。
        if ("hot".equals(sort)) {
            queryWrapper.orderByDesc("like_count");
        }
        queryWrapper.orderByDesc("create_time", "id");

        Page<CommunityPost> posts = communityPostMapper.selectPage(page, queryWrapper);
        populateViewerStates(posts.getRecords(), userId, false);
        replySource.attachAuthorStates(posts.getRecords(), userId);
        return posts;
    }

    /** 查询后台帖子分页，可按发布状态、归属、正文和回复类型收窄结果。 */
    public Page<AdminPostVO> pageAdminPosts(
            Integer status, Long topicId, Long postId, Long userId,
            String keyword, String resultKind, String riskStatus, Long pageNum, Long pageSize) {
        QueryWrapper<CommunityPost> queryWrapper = new QueryWrapper<>();

        if(status != null){
            queryWrapper.eq("status", status);
        }

        if(topicId != null){
            queryWrapper.eq("topic_id", topicId);
        }

        if(postId != null){
            queryWrapper.eq("id", postId);
        }

        if(userId != null){
            queryWrapper.eq("user_id", userId);
        }

        if(keyword != null && !keyword.trim().isEmpty()){
            queryWrapper.like("content", keyword.trim());
        }

        queryWrapper.eq(resultKind != null, "ai_result_kind", resultKind);
        CommunityRisk.applyListFilter(queryWrapper, riskStatus);

        Page<CommunityPost> postPage = communityPostMapper.selectPage(
                new Page<>(pageNum,pageSize),
                queryWrapper
        );

        Page<AdminPostVO> voPage = new Page<>(
                postPage.getCurrent(),
                postPage.getSize(),
                postPage.getTotal()
        );

        voPage.setRecords(postPage.getRecords().stream().map(CommunityPostService::toAdminPostRow).toList());
        return voPage;
    }

    private static AdminPostVO toAdminPostRow(CommunityPost post) {
        AdminPostVO row = new AdminPostVO(
                String.valueOf(post.getId()),
                String.valueOf(post.getUserId()),
                post.getTopicId(),
                post.getMoodType(),
                post.getContent(),
                post.getAnonymousName(),
                post.getReplyCount(),
                post.getLikeCount(),
                post.getStatus(),
                post.getHideReason(),
                post.getAiResultKind(),
                post.getCreateTime(),
                post.getUpdateTime()
        );
        row.setRiskStatus(post.getRiskStatus());
        row.setRiskHitTime(post.getRiskHitTime());
        row.setRiskReviewedTime(post.getRiskReviewedTime());
        return row;
    }

    /** 仅将待关注的帖子标为已关注。 */
    public AdminStatusResult reviewRisk(Long id) {
        return CommunityRisk.markReviewed(communityPostMapper, id);
    }

    /** 管理员查看帖子详情（含小芽回复全文与待处理举报摘要）。 */
    public AdminPostDetailVO getAdminDetail(Long id) {
        CommunityPost post = communityPostMapper.selectById(id);
        if (post == null) {
            return null;
        }

        Topic topic = post.getTopicId() == null ? null : topicMapper.selectById(post.getTopicId());
        PendingReportSummary pending = PendingReportSummary.load(
                contentReportMapper, CommunityReportConstants.TARGET_POST, id);

        AdminPostDetailVO detail = new AdminPostDetailVO();
        detail.setId(String.valueOf(post.getId()));
        detail.setUserId(String.valueOf(post.getUserId()));
        detail.setTopicId(post.getTopicId());
        detail.setTopicName(topic == null ? null : topic.getName());
        detail.setMoodType(post.getMoodType());
        detail.setContent(post.getContent());
        detail.setAiReply(post.getAiReply());
        detail.setResultKind(post.getAiResultKind());
        detail.setAnonymousName(post.getAnonymousName());
        detail.setReplyCount(post.getReplyCount());
        detail.setLikeCount(post.getLikeCount());
        detail.setStatus(post.getStatus());
        detail.setHideReason(post.getHideReason());
        detail.setRiskStatus(post.getRiskStatus());
        detail.setRiskHitTime(post.getRiskHitTime());
        detail.setRiskReviewedTime(post.getRiskReviewedTime());
        detail.setPendingReportCount(pending.count());
        detail.setPendingReasons(pending.reasons());
        detail.setCreateTime(post.getCreateTime());
        detail.setUpdateTime(post.getUpdateTime());
        return detail;
    }

    /** 保留给旧集成测试调用；后台控制器统一走 {@link #updateAdminStatusWithOutcome}。 */
    @Transactional
    public CommunityPost updateAdminStatus(Long id, Integer status) {
        return updateAdminStatus(id, status, null);
    }

    @Transactional
    public CommunityPost updateAdminStatus(Long id, Integer status, String hideReason) {
        if (updateAdminStatusWithOutcome(id, status, hideReason) != AdminStatusResult.UPDATED) {
            return null;
        }
        return communityPostMapper.selectById(id);
    }

    /** 隐藏时失效当前版本，恢复只更新可见状态。 */
    @Transactional
    public AdminStatusResult updateAdminStatusWithOutcome(Long id, Integer status, String hideReason) {
        if (status == null || (status != CommunityReportConstants.STATUS_ADMIN_HIDDEN
                && status != CommunityReportConstants.STATUS_VISIBLE)) {
            return AdminStatusResult.CONFLICT;
        }

        boolean updated;
        if (status == CommunityReportConstants.STATUS_ADMIN_HIDDEN) {
            updated = replySource.hideVisible(id, hideReason);
        } else {
            UpdateWrapper<CommunityPost> restore = new UpdateWrapper<>();
            restore.eq("id", id).eq("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN)
                    .set("status", CommunityReportConstants.STATUS_VISIBLE).set("hide_reason", null);
            updated = communityPostMapper.update(null, restore) == 1;
        }

        if (updated) {
            return AdminStatusResult.UPDATED;
        }
        return communityPostMapper.selectById(id) == null
                ? AdminStatusResult.NOT_FOUND
                : AdminStatusResult.CONFLICT;
    }

    public List<CommunityPost> listFavorites(Long userId) {
        QueryWrapper<Favorite> favQuery = new QueryWrapper<>();
        favQuery.eq("user_id", userId);
        favQuery.orderByDesc("create_time");

        List<Favorite> favorites = favoriteMapper.selectList(favQuery);
        if (favorites.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = favorites.stream().map(Favorite::getPostId).toList();
        QueryWrapper<CommunityPost> postQuery = new QueryWrapper<CommunityPost>()
                .in("id", postIds)
                .eq("status", CommunityReportConstants.STATUS_VISIBLE);
        Map<Long, CommunityPost> visiblePosts = new HashMap<>();
        for (CommunityPost post : communityPostMapper.selectList(postQuery)) {
            if (post.isVisible()) {
                visiblePosts.put(post.getId(), post);
            }
        }

        List<CommunityPost> result = new ArrayList<>();
        for (Long postId : postIds) {
            CommunityPost post = visiblePosts.get(postId);
            if (post != null) {
                result.add(post);
            }
        }

        populateViewerStates(result, userId, true);
        return result;
    }

    public Boolean isTopicAvailable(Long topicId) {
        if (topicId == null) {
            return false;
        }

        Topic topic = topicMapper.selectById(topicId);
        if (topic == null) {
            return false;
        }

        return topic.getStatus() != null && topic.getStatus() == 1;
    }

    /** 入队额度在事务外判断，正文、风险状态和新任务在准入事务内提交。 */
    public PostSaveResult add(CommunityPostAddDTO dto, Long userId) {
        if (!isTopicAvailable(dto.getTopicId())) {
            return PostSaveResult.failed();
        }

        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());
        post.setAnonymousName("匿名岛民");
        post.setReplyCount(0);
        post.setLikeCount(0);
        post.setStatus(CommunityReportConstants.STATUS_VISIBLE);
        post.setContentVersion(1L);

        boolean riskHit = MoodReviewSafety.matches(post.getContent());
        if (riskHit) {
            post.setRiskStatus(CommunityRisk.PENDING);
            post.setRiskHitTime(LocalDateTime.now());
        }
        boolean needAiReply = !Boolean.FALSE.equals(dto.getNeedAiReply());
        RateLimiter.Decision quota = replyQuota(needAiReply, riskHit, userId);

        return admission.save(() -> {
            if (communityPostMapper.insert(post) != 1) {
                return PostSaveResult.failed();
            }
            return createSaveResult(post, needAiReply, quota, riskHit);
        });
    }

    /** 帖子与旧任务按来源行、任务行的顺序更新，并在同一事务内提交。 */
    @Transactional
    public Boolean delete(Long id, Long userId) {
        UpdateWrapper<CommunityPost> removal = new UpdateWrapper<>();
        removal.eq("id", id).eq("user_id", userId)
                .in("status", CommunityReportConstants.STATUS_VISIBLE, CommunityReportConstants.STATUS_ADMIN_HIDDEN)
                .set("status", CommunityReportConstants.STATUS_AUTHOR_DELETED);
        CommunityReplySource.invalidateReply(removal);
        if (communityPostMapper.update(null, removal) != 1) {
            return false;
        }

        jobs.supersedeCommunityJobs(id, userId);
        return true;
    }

    /** 每次成功编辑都递增来源版本，包括正文相同及关闭 AI 的保存。 */
    public PostSaveResult update(CommunityPostUpdateDTO dto, Long userId) {
        if (!isTopicAvailable(dto.getTopicId())) {
            return PostSaveResult.failed();
        }

        CommunityPost observed = communityPostMapper.selectById(dto.getId());
        if (!canEdit(observed, userId)) {
            return PostSaveResult.failed();
        }

        String content = dto.getContent().trim();
        boolean riskHit = MoodReviewSafety.matches(content);
        boolean needAiReply = !Boolean.FALSE.equals(dto.getNeedAiReply());
        RateLimiter.Decision quota = replyQuota(needAiReply, riskHit, userId);

        return admission.save(() -> {
            CommunityPost post = communityPostMapper.selectByIdForUpdate(dto.getId());
            if (!canEdit(post, userId)) {
                return PostSaveResult.failed();
            }

            UpdateWrapper<CommunityPost> edit = postEdit(post, userId, dto, content, riskHit);
            if (communityPostMapper.update(null, edit) != 1) {
                return PostSaveResult.failed();
            }

            jobs.supersedeCommunityJobs(post.getId(), userId);
            post.setContentVersion(Math.addExact(post.getContentVersion(), 1L));
            return createSaveResult(post, needAiReply, quota, riskHit);
        });
    }

    /** 正文、版本、旧回复与命中的风险字段必须同条 UPDATE，失败时一起保留原值。 */
    private static UpdateWrapper<CommunityPost> postEdit(
            CommunityPost post, Long userId, CommunityPostUpdateDTO dto, String content, boolean riskHit) {
        UpdateWrapper<CommunityPost> edit = new UpdateWrapper<>();
        edit.eq("id", post.getId()).eq("user_id", userId)
                .eq("status", CommunityReportConstants.STATUS_VISIBLE)
                .eq("content_version", post.getContentVersion())
                .set("topic_id", dto.getTopicId()).set("mood_type", dto.getMoodType().trim())
                .set("content", content);
        CommunityReplySource.invalidateReply(edit);
        if (riskHit) {
            CommunityRisk.markHit(edit);
        }
        return edit;
    }

    private static boolean canEdit(CommunityPost post, Long userId) {
        return post != null && post.isVisible() && userId.equals(post.getUserId());
    }

    private RateLimiter.Decision replyQuota(boolean enabled, boolean riskHit, Long userId) {
        if (!enabled || riskHit) {
            return RateLimiter.Decision.allow();
        }
        return rateLimiter.check(RateLimitType.COMMUNITY_POST, userId);
    }

    private PostSaveResult createSaveResult(
            CommunityPost post, boolean enabled, RateLimiter.Decision quota, boolean riskHit) {
        AiJob job = enabled
                ? jobs.createCommunityJob(post.getId(), post.getUserId(), post.getContentVersion(), quota)
                : null;
        return PostSaveResult.saved(post, job, riskHit);
    }

    @Transactional
    public Boolean like(Long postId, Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);

        if (post == null || !post.isVisible()) {
            return false;
        }

        if (isLiked(postId, userId)) {
            return true;
        }

        PostLike postLike = new PostLike();
        postLike.setPostId(postId);
        postLike.setUserId(userId);

        int rows = postLikeMapper.insert(postLike);

        if (rows > 0) {
            communityPostMapper.adjustLikeCount(postId, 1);
        }

        return rows > 0;
    }

    @Transactional
    public Boolean unlike(Long postId, Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null || !post.isVisible()) {
            return false;
        }

        QueryWrapper<PostLike> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        int rows = postLikeMapper.delete(queryWrapper);

        if (rows > 0) {
            communityPostMapper.adjustLikeCount(postId, -1);
        }

        return rows > 0;
    }

    private Boolean isLiked(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }

        QueryWrapper<PostLike> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        return postLikeMapper.selectCount(queryWrapper) > 0;
    }

    public Boolean favorite(Long postId, Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);

        if (post == null || !post.isVisible()) {
            return false;
        }

        if (isFavorited(postId, userId)) {
            return true;
        }

        Favorite favorite = new Favorite();
        favorite.setPostId(postId);
        favorite.setUserId(userId);

        return favoriteMapper.insert(favorite) > 0;
    }

    public Boolean unfavorite(Long postId, Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null || !post.isVisible()) {
            return false;
        }

        QueryWrapper<Favorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        return favoriteMapper.delete(queryWrapper) > 0;
    }

    private Boolean isFavorited(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }

        QueryWrapper<Favorite> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);
        queryWrapper.eq("user_id", userId);

        return favoriteMapper.selectCount(queryWrapper) > 0;
    }

    @Transactional(readOnly = true)
    public CommunityPost detail(Long id, Long userId) {
        CommunityPost post = communityPostMapper.selectById(id);
        if (post == null || post.getStatus() == null
                || post.getStatus() == CommunityReportConstants.STATUS_AUTHOR_DELETED) {
            return null;
        }

        if (!post.isVisible()) {
            boolean isAuthor = userId != null && userId.equals(post.getUserId());
            if (post.getStatus() != CommunityReportConstants.STATUS_ADMIN_HIDDEN || !isAuthor) {
                return null;
            }
            post.setHidden(true);
        } else {
            post.setHidden(false);
            post.setHideReason(null);
        }

        populateViewerState(post, userId);
        replySource.attachAuthorStates(List.of(post), userId);

        return post;
    }

    private void populateViewerState(CommunityPost post, Long userId) {
        post.setLiked(isLiked(post.getId(), userId));
        post.setFavorited(isFavorited(post.getId(), userId));
        populateOwnershipAndVisibility(post, userId);
    }

    private void populateViewerStates(List<CommunityPost> posts, Long userId, boolean alreadyFavorited) {
        Set<Long> likedIds = Set.of();
        Set<Long> favoritedIds = Set.of();
        if (userId != null && !posts.isEmpty()) {
            List<Long> postIds = posts.stream().map(CommunityPost::getId).toList();
            QueryWrapper<PostLike> likes = new QueryWrapper<PostLike>()
                    .select("post_id").eq("user_id", userId).in("post_id", postIds);
            likedIds = postLikeMapper.selectList(likes).stream()
                    .map(PostLike::getPostId)
                    .collect(Collectors.toSet());

            if (!alreadyFavorited) {
                QueryWrapper<Favorite> favorites = new QueryWrapper<Favorite>()
                        .select("post_id").eq("user_id", userId).in("post_id", postIds);
                favoritedIds = favoriteMapper.selectList(favorites).stream()
                        .map(Favorite::getPostId)
                        .collect(Collectors.toSet());
            }
        }

        for (CommunityPost post : posts) {
            post.setLiked(likedIds.contains(post.getId()));
            post.setFavorited(alreadyFavorited || favoritedIds.contains(post.getId()));
            populateOwnershipAndVisibility(post, userId);
        }
    }

    private void populateOwnershipAndVisibility(CommunityPost post, Long userId) {
        post.setMine(userId != null && userId.equals(post.getUserId()));
        if (post.getHidden() == null) {
            post.setHidden(Integer.valueOf(CommunityReportConstants.STATUS_ADMIN_HIDDEN).equals(post.getStatus()));
        }
    }
}
