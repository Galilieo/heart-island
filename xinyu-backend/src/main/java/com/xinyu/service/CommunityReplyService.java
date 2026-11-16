package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.dto.CommunityReplyAddDTO;
import com.xinyu.dto.CommunityReplyUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.vo.AdminReplyDetailVO;
import com.xinyu.vo.AdminReplyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommunityReplyService {

    @Autowired
    private CommunityReplyMapper communityReplyMapper;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired(required = false)
    private ContentReportMapper contentReportMapper;

    public List<CommunityReply> listByPostId(Long postId) {
        return listByPostId(postId, null);
    }

    /** 按帖子ID查询回复：所有人可见正常回复，已登录作者额外可见自己在该帖子下被隐藏的回复。 */
    public List<CommunityReply> listByPostId(Long postId, Long userId) {
        if (postId == null) {
            return List.of();
        }
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null || !isPostReadableForReplies(post, userId)) {
            return List.of();
        }

        QueryWrapper<CommunityReply> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId);

        if (userId != null) {
            queryWrapper.and(w -> w.eq("status", CommunityReportConstants.STATUS_VISIBLE)
                    .or(ow -> ow.eq("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN)
                            .eq("user_id", userId)));
        } else {
            queryWrapper.eq("status", CommunityReportConstants.STATUS_VISIBLE);
        }

        queryWrapper.orderByAsc("create_time");

        List<CommunityReply> replies = communityReplyMapper.selectList(queryWrapper);
        for (CommunityReply reply : replies) {
            reply.setMine(userId != null && userId.equals(reply.getUserId()));
            boolean isHidden = Integer.valueOf(CommunityReportConstants.STATUS_ADMIN_HIDDEN)
                    .equals(reply.getStatus());
            reply.setHidden(isHidden);
            if (!isHidden) {
                reply.setHideReason(null);
            }
        }
        return replies;
    }

    private boolean isPostReadableForReplies(CommunityPost post, Long userId) {
        if (post.isVisible()) {
            return true;
        }
        return Integer.valueOf(CommunityReportConstants.STATUS_ADMIN_HIDDEN).equals(post.getStatus())
                && userId != null
                && userId.equals(post.getUserId());
    }

    public Page<AdminReplyVO> pageAdminReplies(Integer status, Long postId, Long userId, String keyword, Long pageNum, Long pageSize) {
        QueryWrapper<CommunityReply> queryWrapper = new QueryWrapper<>();

        if (status != null) {
            queryWrapper.eq("status", status);
        }

        if (postId != null) {
            queryWrapper.eq("post_id", postId);
        }

        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.like("content", keyword.trim());
        }

        queryWrapper.orderByDesc("create_time");

        Page<CommunityReply> replyPage = communityReplyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                queryWrapper
        );

        Page<AdminReplyVO> voPage = new Page<>(
                replyPage.getCurrent(),
                replyPage.getSize(),
                replyPage.getTotal()
        );

        voPage.setRecords(replyPage.getRecords().stream()
                .map(reply -> new AdminReplyVO(
                        String.valueOf(reply.getId()),
                        String.valueOf(reply.getPostId()),
                        String.valueOf(reply.getUserId()),
                        reply.getContent(),
                        reply.getAnonymousName(),
                        reply.getStatus(),
                        reply.getHideReason(),
                        reply.getCreateTime(),
                        reply.getUpdateTime()
                ))
                .toList());

        return voPage;
    }

    /** 管理员查看回复详情（含所属帖子摘录及待处理举报摘要）。 */
    public AdminReplyDetailVO getAdminDetail(Long id) {
        CommunityReply reply = communityReplyMapper.selectById(id);
        if (reply == null) {
            return null;
        }

        CommunityPost post = reply.getPostId() == null ? null : communityPostMapper.selectById(reply.getPostId());
        PendingReportSummary pending = PendingReportSummary.load(
                contentReportMapper, CommunityReportConstants.TARGET_REPLY, id);

        AdminReplyDetailVO detail = new AdminReplyDetailVO();
        detail.setId(String.valueOf(reply.getId()));
        detail.setPostId(String.valueOf(reply.getPostId()));
        detail.setUserId(String.valueOf(reply.getUserId()));
        detail.setPostContent(post == null ? null : post.getContent());
        detail.setContent(reply.getContent());
        detail.setAnonymousName(reply.getAnonymousName());
        detail.setStatus(reply.getStatus());
        detail.setHideReason(reply.getHideReason());
        detail.setPendingReportCount(pending.count());
        detail.setPendingReasons(pending.reasons());
        detail.setCreateTime(reply.getCreateTime());
        detail.setUpdateTime(reply.getUpdateTime());
        return detail;
    }

    @Transactional
    public CommunityReply updateAdminStatus(Long id, Integer status) {
        return updateAdminStatus(id, status, null);
    }

    @Transactional
    public CommunityReply updateAdminStatus(Long id, Integer status, String hideReason) {
        if (updateAdminStatusWithOutcome(id, status, hideReason) != AdminStatusResult.UPDATED) {
            return null;
        }
        return communityReplyMapper.selectById(id);
    }

    /** 同事务内带前置状态更新回复并在 1<->2 切换时同步增减所在帖子回复计数。 */
    @Transactional
    public AdminStatusResult updateAdminStatusWithOutcome(Long id, Integer status, String hideReason) {
        if (status == null || (status != CommunityReportConstants.STATUS_ADMIN_HIDDEN
                && status != CommunityReportConstants.STATUS_VISIBLE)) {
            return AdminStatusResult.CONFLICT;
        }

        int expectedStatus = status == CommunityReportConstants.STATUS_ADMIN_HIDDEN
                ? CommunityReportConstants.STATUS_VISIBLE
                : CommunityReportConstants.STATUS_ADMIN_HIDDEN;
        int countDelta = status == CommunityReportConstants.STATUS_ADMIN_HIDDEN ? -1 : 1;

        UpdateWrapper<CommunityReply> change = new UpdateWrapper<>();
        change.eq("id", id).eq("status", expectedStatus)
                .set("status", status)
                .set("hide_reason", status == CommunityReportConstants.STATUS_ADMIN_HIDDEN ? hideReason : null);

        if (communityReplyMapper.update(null, change) > 0) {
            CommunityReply current = communityReplyMapper.selectById(id);
            if (current != null && current.getPostId() != null) {
                communityPostMapper.adjustReplyCount(current.getPostId(), countDelta);
            }
            return AdminStatusResult.UPDATED;
        }

        return communityReplyMapper.selectById(id) == null
                ? AdminStatusResult.NOT_FOUND
                : AdminStatusResult.CONFLICT;
    }

    @Transactional
    public Boolean add(CommunityReplyAddDTO dto, Long userId) {
        CommunityPost post = communityPostMapper.selectById(dto.getPostId());
        if (post == null || !post.isVisible()) {
            return false;
        }

        CommunityReply reply = new CommunityReply();

        reply.setPostId(dto.getPostId());
        reply.setUserId(userId);
        reply.setContent(dto.getContent().trim());
        reply.setAnonymousName("匿名岛民");
        reply.setStatus(CommunityReportConstants.STATUS_VISIBLE);

        int rows = communityReplyMapper.insert(reply);

        if (rows > 0) {
            communityPostMapper.adjustReplyCount(dto.getPostId(), 1);
        }

        return rows > 0;
    }

    @Transactional
    public Boolean delete(Long id, Long userId) {
        CommunityReply reply = communityReplyMapper.selectById(id);
        if (reply == null || !reply.getUserId().equals(userId)
                || reply.getStatus() == null
                || reply.getStatus() == CommunityReportConstants.STATUS_AUTHOR_DELETED) {
            return false;
        }

        if (reply.isVisible()) {
            if (deleteReplyWithExpectedStatus(id, userId, CommunityReportConstants.STATUS_VISIBLE)) {
                communityPostMapper.adjustReplyCount(reply.getPostId(), -1);
                return true;
            }
            // 并发场景下若刚刚被管理员隐藏，回退按隐藏态条件删除且不再重复扣减回复数。
            return deleteReplyWithExpectedStatus(id, userId, CommunityReportConstants.STATUS_ADMIN_HIDDEN);
        }

        return deleteReplyWithExpectedStatus(id, userId, CommunityReportConstants.STATUS_ADMIN_HIDDEN);
    }

    private boolean deleteReplyWithExpectedStatus(Long id, Long userId, int expectedStatus) {
        UpdateWrapper<CommunityReply> removal = new UpdateWrapper<>();
        removal.eq("id", id)
                .eq("user_id", userId)
                .eq("status", expectedStatus)
                .set("status", CommunityReportConstants.STATUS_AUTHOR_DELETED);
        return communityReplyMapper.update(null, removal) > 0;
    }

    @Transactional
    public Boolean update(CommunityReplyUpdateDTO dto, Long userId) {
        CommunityReply existing = communityReplyMapper.selectById(dto.getId());
        if (existing == null || !Integer.valueOf(CommunityReportConstants.STATUS_VISIBLE).equals(existing.getStatus())) {
            return false;
        }
        CommunityPost post = communityPostMapper.selectById(existing.getPostId());
        if (post == null || !post.isVisible()) {
            return false;
        }

        UpdateWrapper<CommunityReply> edit = new UpdateWrapper<>();
        edit.eq("id", dto.getId())
                .eq("user_id", userId)
                .eq("status", CommunityReportConstants.STATUS_VISIBLE)
                .set("content", dto.getContent().trim());
        return communityReplyMapper.update(null, edit) > 0;
    }
}
