package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.dto.CommunityAiResponse;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
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
import java.util.UUID;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.vo.AdminPostDetailVO;
import com.xinyu.vo.AdminPostVO;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.dto.AiReplyResult;
import com.xinyu.ratelimit.RateLimiter;
import com.xinyu.ratelimit.RateLimitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

@Service
public class CommunityPostService {
    private static final Logger logger = LoggerFactory.getLogger(CommunityPostService.class);

    /** 发布 / 编辑结果：帖子一律照常保存；aiReplyLimited 表示本次因额度不足未发起生成。 */
    public record PostSaveResult(boolean saved, boolean aiReplyLimited, String supportText) {

        public static PostSaveResult saved(boolean aiReplyLimited, boolean riskHit) {
            String supportText = riskHit ? MoodReviewSafety.SUPPORT_TEXT : null;
            return new PostSaveResult(true, aiReplyLimited, supportText);
        }

        public static PostSaveResult failed() {
            return new PostSaveResult(false, false, null);
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
    private CommunityAiClient communityAiClient;

    @Autowired
    private AiReplyLogService aiReplyLogService;

    @Autowired(required = false)
    private ContentReportMapper contentReportMapper;

    @Autowired
    private RateLimiter rateLimiter;

    /** 分页查询公开可见的帖子，再批量填充本页的观看者状态。 */
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

        voPage.setRecords(postPage.getRecords().stream()
                .map(post -> {
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
                })
                .toList());

        return voPage;
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
    public CommunityPost updateAdminStatus(Long id, Integer status) {
        return updateAdminStatus(id, status, null);
    }

    public CommunityPost updateAdminStatus(Long id, Integer status, String hideReason) {
        if (updateAdminStatusWithOutcome(id, status, hideReason) != AdminStatusResult.UPDATED) {
            return null;
        }
        return communityPostMapper.selectById(id);
    }

    /** 带前置状态条件更新帖子（1->2 隐藏并清生成标识；2->1 恢复并清隐藏原因）。 */
    public AdminStatusResult updateAdminStatusWithOutcome(Long id, Integer status, String hideReason) {
        if (status == null || (status != CommunityReportConstants.STATUS_ADMIN_HIDDEN
                && status != CommunityReportConstants.STATUS_VISIBLE)) {
            return AdminStatusResult.CONFLICT;
        }

        int expectedStatus = status == CommunityReportConstants.STATUS_ADMIN_HIDDEN
                ? CommunityReportConstants.STATUS_VISIBLE
                : CommunityReportConstants.STATUS_ADMIN_HIDDEN;

        UpdateWrapper<CommunityPost> change = new UpdateWrapper<>();
        change.eq("id", id).eq("status", expectedStatus).set("status", status);
        if (status == CommunityReportConstants.STATUS_ADMIN_HIDDEN) {
            // 隐藏即使随后恢复，也不允许此前进行中的生成重新回写。
            change.set("hide_reason", hideReason).set("ai_generation_id", null);
        } else {
            change.set("hide_reason", null);
        }

        if (communityPostMapper.update(null, change) > 0) {
            return AdminStatusResult.UPDATED;
        }

        return communityPostMapper.selectById(id) == null
                ? AdminStatusResult.NOT_FOUND
                : AdminStatusResult.CONFLICT;
    }

    /**
     * 查询当前用户收藏的帖子，按收藏时间倒序。
     * 已被删除/隐藏的帖子会自动跳过。
     */
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

        boolean riskHit = MoodReviewSafety.matches(post.getContent());
        if (riskHit) {
            post.setRiskStatus(CommunityRisk.PENDING);
            post.setRiskHitTime(LocalDateTime.now());
        }

        boolean needAiReply = !Boolean.FALSE.equals(dto.getNeedAiReply());
        post.setAiGenerationId(needAiReply ? UUID.randomUUID().toString() : null);
        int rows = communityPostMapper.insert(post);
        if (rows <= 0) {
            return PostSaveResult.failed();
        }

        // 先 insert 拿到 post.id，再调 AI 回写。AI 失败不阻断发帖。
        // 用户选择不要 AI 回复时跳过生成（也不写调用日志）。
        boolean quotaAllowed = needAiReply && reserveAiReplyQuota(riskHit, userId);

        // 额度不足时帖子照常保存、不发起生成（契约 2.4）：ai_reply 保持 NULL，
        // 不写失败兜底文案，也不记成生成失败。
        if (quotaAllowed) {
            attachAiReply(post);
        }

        return PostSaveResult.saved(needAiReply && !quotaAllowed, riskHit);
    }

    @Transactional
    public Boolean delete(Long id, Long userId) {
        CommunityPost post = communityPostMapper.selectById(id);

        if (post == null) {
            return false;
        }

        if (post.getStatus() == null || post.getStatus() == CommunityReportConstants.STATUS_AUTHOR_DELETED) {
            return false;
        }

        if (!post.getUserId().equals(userId)) {
            return false;
        }

        // 仅更新删除状态与生成标识，避免整实体回写覆盖并发编辑或互动计数；允许作者删除正常或已隐藏帖子。
        UpdateWrapper<CommunityPost> removal = new UpdateWrapper<>();
        removal.eq("id", id)
                .eq("user_id", userId)
                .in("status", CommunityReportConstants.STATUS_VISIBLE, CommunityReportConstants.STATUS_ADMIN_HIDDEN);
        removal.set("status", CommunityReportConstants.STATUS_AUTHOR_DELETED).set("ai_generation_id", null);
        return communityPostMapper.update(null, removal) > 0;
    }

    public PostSaveResult update(CommunityPostUpdateDTO dto, Long userId) {
        if(!isTopicAvailable(dto.getTopicId())){
            return PostSaveResult.failed();
        }

        CommunityPost post = communityPostMapper.selectById(dto.getId());

        if (post == null || !post.isVisible()) {
            return PostSaveResult.failed();
        }

        if (!post.getUserId().equals(userId)) {
            return PostSaveResult.failed();
        }

        post.setTopicId(dto.getTopicId());
        post.setMoodType(dto.getMoodType().trim());
        post.setContent(dto.getContent().trim());
        post.setAiReply(null);
        post.setAiResultKind(null);
        boolean needAiReply = !Boolean.FALSE.equals(dto.getNeedAiReply());
        post.setAiGenerationId(needAiReply ? UUID.randomUUID().toString() : null);

        // 编辑与清旧回复是同一条UPDATE，必须在事务外的网络请求之前落库。
        // 标识也在此处更换；相同正文、关闭AI及改回原文都会使旧生成失效。
        // 只更新编辑字段，不回写读取时的点赞数、状态等快照；NULL不能交给updateById忽略。
        UpdateWrapper<CommunityPost> edit = new UpdateWrapper<>();
        edit.eq("id", post.getId()).eq("user_id", userId).eq("status", CommunityReportConstants.STATUS_VISIBLE);
        edit.set("topic_id", post.getTopicId());
        edit.set("mood_type", post.getMoodType());
        edit.set("content", post.getContent());
        edit.set("ai_reply", null);
        edit.set("ai_result_kind", null);
        edit.set("ai_generation_id", post.getAiGenerationId());

        boolean riskHit = MoodReviewSafety.matches(post.getContent());
        if (riskHit) {
            CommunityRisk.markHit(edit);
        }

        int rows = communityPostMapper.update(null, edit);
        if (rows <= 0) {
            return PostSaveResult.failed();
        }

        if (!needAiReply) {
            // 旧回复已经清除；不生成，也不写调用日志。
            return PostSaveResult.saved(false, riskHit);
        }

        // 内容已落库，AI 回复同步重新生成；额度不足时不发起、旧回复已被编辑清掉。
        boolean quotaAllowed = reserveAiReplyQuota(riskHit, userId);
        if (quotaAllowed) {
            attachAiReply(post);
        }

        return PostSaveResult.saved(!quotaAllowed, riskHit);
    }

    /** Java 只预检危机额度豁免，最终生成分流由 Python 判定。 */
    private boolean reserveAiReplyQuota(boolean riskHit, Long userId) {
        return riskHit || rateLimiter.check(RateLimitType.COMMUNITY_POST, userId).allowed();
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

    /**
     * 已保存帖子在请求线程中同步生成，网络期间不持有数据库事务。
     * 只发布仍匹配本次生成标识的成功正文；失败或失效不改当前回复。
     * 网络期间的编辑／关闭／删除／隐藏会使旧标识失效，回写检查与更新在同一条SQL内完成。
     * 回复回写／日志失败不改变已保存帖子的结果；调用日志记录生成结果，不代表回复已发布。
     */
    private void attachAiReply(CommunityPost post) {
        Topic topic = topicMapper.selectById(post.getTopicId());
        String topicName = topic == null ? "" : topic.getName();

        CommunityAiResponse result;
        try {
            result = communityAiClient.generateReplyForPost(topicName, post.getMoodType(), post.getContent());
        } catch (AiServiceException failure) {
            saveAiReplyLog(post, AiReplyResult.failure(null, null, null, failure.code()));
            return;
        }

        publishAiReply(post, result);
        if (Boolean.FALSE.equals(result.modelCalled())) {
            logger.info("社区AI回复 postId={} kind=SUPPORT", post.getId());
            return;
        }
        saveAiReplyLog(post, AiReplyResult.success(result.replyContent(), null, result.modelName()));
    }

    private void publishAiReply(CommunityPost post, CommunityAiResponse result) {
        UpdateWrapper<CommunityPost> reply = new UpdateWrapper<>();
        reply.eq("id", post.getId())
                .eq("user_id", post.getUserId())
                .eq("status", CommunityReportConstants.STATUS_VISIBLE)
                .eq("ai_generation_id", post.getAiGenerationId());
        reply.set("ai_reply", result.replyContent());
        reply.set("ai_result_kind", result.resultKind());

        try {
            if (communityPostMapper.update(null, reply) > 0) {
                post.setAiReply(result.replyContent());
                post.setAiResultKind(result.resultKind());
            }
        } catch (DataAccessException problem) {
            logger.warn("社区AI回复回写失败 postId={} code=internal_error", post.getId());
        }
    }

    private void saveAiReplyLog(CommunityPost post, AiReplyResult result) {
        try {
            aiReplyLogService.savePostReply(post.getUserId(), post.getId(), result);
        } catch (DataAccessException problem) {
            logger.warn("社区AI调用日志写入失败 postId={} code=internal_error", post.getId());
        }
    }
}
