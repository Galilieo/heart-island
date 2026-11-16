package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.dto.CommunityReportCreateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.ContentReport;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.vo.MyReportVO;
import com.xinyu.vo.ReportedTargetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 社区举报提交与我的举报查询服务。
 */
@Service
public class CommunityReportService {

    private static final Clock DEFAULT_CLOCK = Clock.system(ZoneId.of("Asia/Shanghai"));

    @Autowired
    private ContentReportMapper contentReportMapper;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private CommunityReplyMapper communityReplyMapper;

    @Autowired(required = false)
    private Clock clock;

    private Clock activeClock() {
        return clock != null ? clock : DEFAULT_CLOCK;
    }

    /** 提交一条社区举报，在应用层校验目标可见性、限额与防自举，并兜底处理唯一索引并发冲突。 */
    public void submitReport(Long reporterId, CommunityReportCreateDTO dto) {
        String targetType = dto == null || dto.getTargetType() == null ? "" : dto.getTargetType().trim();
        Long targetId = dto == null ? null : dto.getTargetId();
        String reason = dto == null || dto.getReason() == null ? "" : dto.getReason().trim();
        String detail = validateSubmitParams(targetType, targetId, reason, dto == null ? null : dto.getDetail());

        TargetSnapshot target = resolveVisibleTarget(targetType, targetId);
        if (reporterId.equals(target.targetUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能举报自己的内容");
        }

        ensureNotReportedBefore(reporterId, targetType, targetId);
        ensureWithinDailyLimit(reporterId);

        LocalDateTime now = LocalDateTime.now(activeClock());
        ContentReport report = new ContentReport();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setPostId(target.postId());
        report.setTargetUserId(target.targetUserId());
        report.setReason(reason);
        report.setDetail(detail);
        report.setContentSnapshot(target.content());
        report.setStatus(CommunityReportConstants.REPORT_PENDING);
        report.setCreateTime(now);

        try {
            contentReportMapper.insert(report);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已经举报过这条内容了");
        }
    }

    private String validateSubmitParams(String targetType, Long targetId, String reason, String rawDetail) {
        if (!CommunityReportConstants.isValidTargetType(targetType) || targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "举报目标无效");
        }
        if (!CommunityReportConstants.isValidReason(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择合法的举报原因");
        }
        String normalizedDetail = rawDetail == null ? null : rawDetail.trim();
        if (normalizedDetail != null && normalizedDetail.isEmpty()) {
            normalizedDetail = null;
        }
        if (CommunityReportConstants.REASON_OTHER.equals(reason) && normalizedDetail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "选「其他」时请填写补充说明");
        }
        if (normalizedDetail != null && normalizedDetail.length() > CommunityReportConstants.MAX_DETAIL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "补充说明不能超过200个字");
        }
        return normalizedDetail;
    }

    private TargetSnapshot resolveVisibleTarget(String targetType, Long targetId) {
        if (CommunityReportConstants.TARGET_POST.equals(targetType)) {
            CommunityPost post = communityPostMapper.selectById(targetId);
            if (post == null || !post.isVisible()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该内容不存在或暂不可举报");
            }
            return new TargetSnapshot(post.getId(), post.getUserId(), post.getContent(), post.getStatus(), post.getHideReason());
        }

        CommunityReply reply = communityReplyMapper.selectById(targetId);
        if (reply == null || !reply.isVisible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该内容不存在或暂不可举报");
        }
        CommunityPost parent = reply.getPostId() == null ? null : communityPostMapper.selectById(reply.getPostId());
        if (parent == null || !parent.isVisible()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该内容不存在或暂不可举报");
        }
        return new TargetSnapshot(reply.getPostId(), reply.getUserId(), reply.getContent(), reply.getStatus(), reply.getHideReason());
    }

    private void ensureNotReportedBefore(Long reporterId, String targetType, Long targetId) {
        QueryWrapper<ContentReport> existing = new QueryWrapper<>();
        existing.eq("reporter_id", reporterId)
                .eq("target_type", targetType)
                .eq("target_id", targetId);
        if (contentReportMapper.selectCount(existing) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "你已经举报过这条内容了");
        }
    }

    private void ensureWithinDailyLimit(Long reporterId) {
        LocalDate today = LocalDate.now(activeClock());
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime nextDayStart = today.plusDays(1).atStartOfDay();
        QueryWrapper<ContentReport> dailyQuery = new QueryWrapper<>();
        dailyQuery.eq("reporter_id", reporterId)
                .ge("create_time", dayStart)
                .lt("create_time", nextDayStart);
        if (contentReportMapper.selectCount(dailyQuery) >= CommunityReportConstants.DAILY_REPORT_LIMIT) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "今天的举报次数已用完");
        }
    }

    /** 查询当前用户最近 90 天提交的举报，按时间倒序，过滤内部备注与被举报人信息。 */
    public Page<MyReportVO> listMine(Long reporterId, long pageNum, long pageSize) {
        LocalDateTime cutoff = LocalDateTime.now(activeClock())
                .minusDays(CommunityReportConstants.MY_REPORTS_WINDOW_DAYS);
        QueryWrapper<ContentReport> wrapper = new QueryWrapper<>();
        wrapper.eq("reporter_id", reporterId)
                .ge("create_time", cutoff)
                .orderByDesc("create_time")
                .orderByDesc("id");

        Page<ContentReport> reportPage = contentReportMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Page<MyReportVO> voPage = new Page<>(reportPage.getCurrent(), reportPage.getSize(), reportPage.getTotal());
        voPage.setRecords(reportPage.getRecords().stream().map(this::toMyReportVO).toList());
        return voPage;
    }

    private MyReportVO toMyReportVO(ContentReport report) {
        MyReportVO vo = new MyReportVO();
        vo.setId(String.valueOf(report.getId()));
        vo.setTargetType(report.getTargetType());
        vo.setTargetId(String.valueOf(report.getTargetId()));
        vo.setPostId(String.valueOf(report.getPostId()));
        vo.setExcerpt(CommunityReportConstants.buildExcerpt(report.getContentSnapshot()));
        vo.setReason(report.getReason());
        vo.setStatus(report.getStatus());
        vo.setHideReason(report.getHideReason());
        vo.setCreateTime(report.getCreateTime());
        vo.setHandleTime(report.getHandleTime());
        return vo;
    }

    /** 查询当前用户在指定帖子及其回复下已举报的对象集合。 */
    public List<ReportedTargetVO> listReportedTargets(Long reporterId, Long postId) {
        QueryWrapper<ContentReport> wrapper = new QueryWrapper<>();
        wrapper.eq("reporter_id", reporterId)
                .eq("post_id", postId)
                .orderByDesc("create_time");
        return contentReportMapper.selectList(wrapper).stream()
                .map(report -> new ReportedTargetVO(
                        report.getTargetType(),
                        String.valueOf(report.getTargetId()),
                        report.getStatus()))
                .toList();
    }

    private record TargetSnapshot(Long postId, Long targetUserId, String content, Integer status, String hideReason) {
    }
}
