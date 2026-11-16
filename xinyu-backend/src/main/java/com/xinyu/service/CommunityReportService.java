package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.dto.AdminReportHandleDTO;
import com.xinyu.dto.CommunityReportCreateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.CommunityReply;
import com.xinyu.entity.ContentReport;
import com.xinyu.entity.User;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.mapper.ReportTargetGroup;
import com.xinyu.mapper.UserMapper;
import com.xinyu.vo.AdminReportDetailVO;
import com.xinyu.vo.AdminReportQueueRowVO;
import com.xinyu.vo.AdminReportRecordVO;
import com.xinyu.vo.MyReportVO;
import com.xinyu.vo.ReportedTargetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 社区举报提交、我的举报查询及后台聚合审核服务。
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

    @Autowired
    private UserMapper userMapper;

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

    /** 分页查询后台举报队列（待处理：SAFETY 在前并按首次举报正序；已处理：按处理时间倒序）。 */
    public Page<AdminReportQueueRowVO> pageAdminReports(String statusParam, long pageNum, long pageSize) {
        boolean pending = !"DONE".equalsIgnoreCase(statusParam);
        long total = pending
                ? contentReportMapper.countPendingTargetGroups()
                : contentReportMapper.countDoneTargetGroups();
        Page<AdminReportQueueRowVO> page = new Page<>(pageNum, pageSize, total);
        if (total == 0) {
            page.setRecords(List.of());
            return page;
        }

        long offset = (pageNum - 1) * pageSize;
        List<ReportTargetGroup> groups = pending
                ? contentReportMapper.selectPendingTargetGroups(offset, pageSize)
                : contentReportMapper.selectDoneTargetGroups(offset, pageSize);
        if (groups == null || groups.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }

        Map<TargetKey, List<ContentReport>> reportsByTarget = loadReportsForGroups(groups, pending);
        Map<Long, String> handlerNames = pending ? Map.of() : loadHandlerNames(reportsByTarget);

        List<AdminReportQueueRowVO> rows = new ArrayList<>();
        for (ReportTargetGroup group : groups) {
            List<ContentReport> targetReports = reportsByTarget.getOrDefault(
                    new TargetKey(group.getTargetType(), group.getTargetId()), List.of());
            rows.add(buildQueueRow(group, targetReports, pending, handlerNames));
        }
        page.setRecords(rows);
        return page;
    }

    private Map<TargetKey, List<ContentReport>> loadReportsForGroups(List<ReportTargetGroup> groups, boolean pending) {
        QueryWrapper<ContentReport> wrapper = new QueryWrapper<>();
        if (pending) {
            wrapper.eq("status", CommunityReportConstants.REPORT_PENDING);
        } else {
            wrapper.in("status", List.of(
                    CommunityReportConstants.REPORT_HIDDEN,
                    CommunityReportConstants.REPORT_DISMISSED,
                    CommunityReportConstants.REPORT_CLOSED));
        }
        wrapper.and(root -> {
            for (int i = 0; i < groups.size(); i++) {
                ReportTargetGroup group = groups.get(i);
                if (i > 0) {
                    root.or();
                }
                root.nested(pair -> pair.eq("target_type", group.getTargetType())
                        .eq("target_id", group.getTargetId()));
            }
        });
        wrapper.orderByAsc("create_time").orderByAsc("id");

        Map<TargetKey, List<ContentReport>> grouped = new HashMap<>();
        for (ContentReport report : contentReportMapper.selectList(wrapper)) {
            TargetKey key = new TargetKey(report.getTargetType(), report.getTargetId());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(report);
        }
        return grouped;
    }

    private AdminReportQueueRowVO buildQueueRow(ReportTargetGroup group,
                                                List<ContentReport> reports,
                                                boolean pending,
                                                Map<Long, String> handlerNames) {
        AdminReportQueueRowVO row = new AdminReportQueueRowVO();
        row.setTargetType(group.getTargetType());
        row.setTargetId(String.valueOf(group.getTargetId()));
        row.setPostId(String.valueOf(group.getPostId()));
        row.setTargetUserId(String.valueOf(group.getTargetUserId()));
        row.setReporterCount(group.getReporterCount());
        row.setHasSafety(group.getHasSafety() != null && group.getHasSafety() > 0);
        row.setFirstReportTime(group.getFirstReportTime());
        row.setReasonCounts(countReasons(reports));

        String earliestSnapshot = reports.isEmpty() ? "" : reports.get(0).getContentSnapshot();
        row.setExcerpt(CommunityReportConstants.buildExcerpt(earliestSnapshot));

        if (!pending && !reports.isEmpty()) {
            ContentReport latestHandled = findLatestHandledRecord(reports);
            if (latestHandled != null) {
                row.setResultStatus(latestHandled.getStatus());
                row.setHideReason(latestHandled.getHideReason());
                row.setHandlerId(latestHandled.getHandlerId() == null ? null : String.valueOf(latestHandled.getHandlerId()));
                row.setHandlerName(resolveHandlerDisplayName(latestHandled.getHandlerId(), handlerNames));
                row.setHandleTime(latestHandled.getHandleTime());
                row.setHandleNote(latestHandled.getHandleNote());
            }
        }
        return row;
    }

    /** 查询指定内容对象的全部举报详情、历史快照对比与作者被隐藏次数。 */
    public AdminReportDetailVO getAdminReportDetail(String targetType, Long targetId) {
        if (!CommunityReportConstants.isValidTargetType(targetType) || targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "举报对象参数无效");
        }

        QueryWrapper<ContentReport> wrapper = new QueryWrapper<>();
        wrapper.eq("target_type", targetType)
                .eq("target_id", targetId)
                .orderByAsc("create_time")
                .orderByAsc("id");
        List<ContentReport> reports = contentReportMapper.selectList(wrapper);
        if (reports.isEmpty()) {
            return null;
        }

        ContentReport earliest = reports.get(0);
        TargetSnapshot currentTarget = loadTargetSnapshotIgnoringStatus(targetType, targetId);
        Long targetUserId = currentTarget != null ? currentTarget.targetUserId() : earliest.getTargetUserId();

        AdminReportDetailVO detail = new AdminReportDetailVO();
        detail.setTargetType(targetType);
        detail.setTargetId(String.valueOf(targetId));
        detail.setPostId(String.valueOf(earliest.getPostId()));
        detail.setTargetUserId(String.valueOf(targetUserId));
        detail.setAuthorHiddenCount(countAuthorHiddenContents(targetUserId));
        detail.setReporterCount(reports.size());
        detail.setFirstReportTime(earliest.getCreateTime());
        detail.setHasSafety(reports.stream().anyMatch(r -> CommunityReportConstants.REASON_SAFETY.equals(r.getReason())));
        detail.setSuggestedHideReason(pickSuggestedHideReason(reports));
        detail.setSnapshotContent(earliest.getContentSnapshot());
        detail.setCurrentContent(currentTarget == null ? null : currentTarget.content());
        detail.setCurrentStatus(currentTarget == null ? CommunityReportConstants.STATUS_AUTHOR_DELETED : currentTarget.status());
        detail.setCurrentHideReason(currentTarget == null ? null : currentTarget.hideReason());
        detail.setContentChanged(currentTarget != null
                && !Objects.equals(earliest.getContentSnapshot(), currentTarget.content()));

        ContentReport latestHandled = findLatestHandledRecord(reports);
        if (latestHandled != null) {
            detail.setResultStatus(latestHandled.getStatus());
            detail.setHideReason(latestHandled.getHideReason());
            if (latestHandled.getHandlerId() != null) {
                detail.setHandlerId(String.valueOf(latestHandled.getHandlerId()));
                User handler = userMapper.selectById(latestHandled.getHandlerId());
                detail.setHandlerName(handler != null && handler.getUsername() != null
                        ? handler.getUsername()
                        : "#" + latestHandled.getHandlerId());
            }
            detail.setHandleTime(latestHandled.getHandleTime());
            detail.setHandleNote(latestHandled.getHandleNote());
        }

        detail.setRecords(reports.stream().map(this::toRecordVO).toList());
        return detail;
    }

    private AdminReportRecordVO toRecordVO(ContentReport report) {
        AdminReportRecordVO vo = new AdminReportRecordVO();
        vo.setId(String.valueOf(report.getId()));
        vo.setReporterId(String.valueOf(report.getReporterId()));
        vo.setReason(report.getReason());
        vo.setDetail(report.getDetail());
        vo.setCreateTime(report.getCreateTime());
        vo.setStatus(report.getStatus());
        return vo;
    }

    /** 在单一事务内关闭目标全部待处理举报，并在 HIDE 且原内容正常时隐藏内容并同步计数。 */
    @Transactional
    public String handleReport(Long adminId, AdminReportHandleDTO dto) {
        String targetType = dto == null || dto.getTargetType() == null ? "" : dto.getTargetType().trim();
        Long targetId = dto == null ? null : dto.getTargetId();
        String action = dto == null || dto.getAction() == null ? "" : dto.getAction().trim();
        String hideReason = dto == null || dto.getHideReason() == null ? null : dto.getHideReason().trim();
        String note = dto == null || dto.getNote() == null ? null : dto.getNote().trim();
        if (note != null && note.isEmpty()) {
            note = null;
        }

        validateHandleRequest(targetType, targetId, action, hideReason, note);

        LocalDateTime now = LocalDateTime.now(activeClock());
        // 先锁定目标内容主键行，串行化针对同一内容的多管理员并发裁决，消除二级索引间隙死锁。
        TargetSnapshot target = lockTargetSnapshotForUpdate(targetType, targetId);
        if (target == null || target.status() == null
                || target.status() == CommunityReportConstants.STATUS_AUTHOR_DELETED) {
            closePendingReportsOrThrow(targetType, targetId, CommunityReportConstants.REPORT_CLOSED, null, adminId, note, now);
            return "内容已被作者删除，相关举报已关闭";
        }

        if (CommunityReportConstants.ACTION_DISMISS.equals(action)) {
            closePendingReportsOrThrow(targetType, targetId, CommunityReportConstants.REPORT_DISMISSED, null, adminId, note, now);
            return "已标记不违规，相关举报已关闭";
        }

        closePendingReportsOrThrow(targetType, targetId, CommunityReportConstants.REPORT_HIDDEN, hideReason, adminId, note, now);
        if (target.status() == CommunityReportConstants.STATUS_VISIBLE) {
            boolean hid = hideContentFromVisible(targetType, targetId, target.postId(), hideReason);
            if (!hid) {
                TargetSnapshot latest = loadTargetSnapshotIgnoringStatus(targetType, targetId);
                if (latest == null || latest.status() == null
                        || latest.status() == CommunityReportConstants.STATUS_AUTHOR_DELETED) {
                    contentReportMapper.correctClosedOnDeletedTarget(targetType, targetId, adminId, now);
                }
            }
        }
        return "已隐藏，相关举报都已关闭";
    }

    private TargetSnapshot lockTargetSnapshotForUpdate(String targetType, Long targetId) {
        if (CommunityReportConstants.TARGET_POST.equals(targetType)) {
            CommunityPost post = communityPostMapper.selectByIdForUpdate(targetId);
            if (post == null) {
                return null;
            }
            return new TargetSnapshot(post.getId(), post.getUserId(), post.getContent(), post.getStatus(), post.getHideReason());
        }
        CommunityReply reply = communityReplyMapper.selectByIdForUpdate(targetId);
        if (reply == null) {
            return null;
        }
        return new TargetSnapshot(reply.getPostId(), reply.getUserId(), reply.getContent(), reply.getStatus(), reply.getHideReason());
    }

    private void validateHandleRequest(String targetType, Long targetId, String action, String hideReason, String note) {
        if (!CommunityReportConstants.isValidTargetType(targetType) || targetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "举报对象参数无效");
        }
        if (!CommunityReportConstants.ACTION_HIDE.equals(action)
                && !CommunityReportConstants.ACTION_DISMISS.equals(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的处理动作");
        }
        if (CommunityReportConstants.ACTION_HIDE.equals(action)
                && !CommunityReportConstants.isValidHideReason(hideReason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "隐藏内容必须选择合法的隐藏原因");
        }
        if (note != null && note.length() > CommunityReportConstants.MAX_HANDLE_NOTE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内部备注不能超过200个字");
        }
    }

    private void closePendingReportsOrThrow(String targetType, Long targetId, String status, String hideReason,
                                            Long adminId, String note, LocalDateTime now) {
        int updated = contentReportMapper.handlePendingByTarget(
                targetType, targetId, status, hideReason, adminId, note, now);
        if (updated <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已被其他管理员处理");
        }
    }

    private boolean hideContentFromVisible(String targetType, Long targetId, Long postId, String hideReason) {
        if (CommunityReportConstants.TARGET_POST.equals(targetType)) {
            UpdateWrapper<CommunityPost> update = new UpdateWrapper<>();
            update.eq("id", targetId)
                    .eq("status", CommunityReportConstants.STATUS_VISIBLE)
                    .set("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN)
                    .set("hide_reason", hideReason)
                    .set("ai_generation_id", null);
            return communityPostMapper.update(null, update) > 0;
        }

        UpdateWrapper<CommunityReply> update = new UpdateWrapper<>();
        update.eq("id", targetId)
                .eq("status", CommunityReportConstants.STATUS_VISIBLE)
                .set("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN)
                .set("hide_reason", hideReason);
        boolean hid = communityReplyMapper.update(null, update) > 0;
        if (hid && postId != null) {
            communityPostMapper.adjustReplyCount(postId, -1);
        }
        return hid;
    }

    /** 获取待处理内容条数（按内容对象去重聚合）。 */
    public long getPendingTargetCount() {
        return contentReportMapper.countPendingTargetGroups();
    }

    private TargetSnapshot loadTargetSnapshotIgnoringStatus(String targetType, Long targetId) {
        if (CommunityReportConstants.TARGET_POST.equals(targetType)) {
            CommunityPost post = communityPostMapper.selectById(targetId);
            if (post == null) {
                return null;
            }
            return new TargetSnapshot(post.getId(), post.getUserId(), post.getContent(), post.getStatus(), post.getHideReason());
        }
        CommunityReply reply = communityReplyMapper.selectById(targetId);
        if (reply == null) {
            return null;
        }
        return new TargetSnapshot(reply.getPostId(), reply.getUserId(), reply.getContent(), reply.getStatus(), reply.getHideReason());
    }

    private int countAuthorHiddenContents(Long targetUserId) {
        if (targetUserId == null) {
            return 0;
        }
        long hiddenPosts = communityPostMapper.selectCount(
                new QueryWrapper<CommunityPost>()
                        .eq("user_id", targetUserId)
                        .eq("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN));
        long hiddenReplies = communityReplyMapper.selectCount(
                new QueryWrapper<CommunityReply>()
                        .eq("user_id", targetUserId)
                        .eq("status", CommunityReportConstants.STATUS_ADMIN_HIDDEN));
        return Math.toIntExact(hiddenPosts + hiddenReplies);
    }

    private Map<String, Integer> countReasons(List<ContentReport> reports) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ContentReport report : reports) {
            counts.merge(report.getReason(), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparingInt(entry -> CommunityReportConstants.ALL_REASONS.indexOf(entry.getKey())));
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    /** 默认隐藏原因取被举报最多的原因；若最多为 SAFETY 或 OTHER，则回退为 ILLEGAL。 */
    private String pickSuggestedHideReason(List<ContentReport> reports) {
        List<ContentReport> pendingOnly = reports.stream()
                .filter(r -> CommunityReportConstants.REPORT_PENDING.equals(r.getStatus()))
                .toList();
        List<ContentReport> source = pendingOnly.isEmpty() ? reports : pendingOnly;
        Map<String, Integer> counts = countReasons(source);
        if (counts.isEmpty()) {
            return CommunityReportConstants.REASON_ILLEGAL;
        }
        String topReason = counts.keySet().iterator().next();
        return CommunityReportConstants.isValidHideReason(topReason)
                ? topReason
                : CommunityReportConstants.REASON_ILLEGAL;
    }

    private ContentReport findLatestHandledRecord(List<ContentReport> reports) {
        return reports.stream()
                .filter(r -> !CommunityReportConstants.REPORT_PENDING.equals(r.getStatus()))
                .max(Comparator.comparing(
                        ContentReport::getHandleTime,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ContentReport::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private Map<Long, String> loadHandlerNames(Map<TargetKey, List<ContentReport>> reportsByTarget) {
        Set<Long> handlerIds = new LinkedHashSet<>();
        for (List<ContentReport> list : reportsByTarget.values()) {
            for (ContentReport report : list) {
                if (report.getHandlerId() != null) {
                    handlerIds.add(report.getHandlerId());
                }
            }
        }
        if (handlerIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(handlerIds);
        Map<Long, String> names = new HashMap<>();
        if (users != null) {
            for (User user : users) {
                String label = user.getUsername() != null && !user.getUsername().isBlank()
                        ? user.getUsername()
                        : user.getNickname();
                names.put(user.getId(), label);
            }
        }
        return names;
    }

    private String resolveHandlerDisplayName(Long handlerId, Map<Long, String> names) {
        if (handlerId == null) {
            return null;
        }
        String name = names.get(handlerId);
        return name != null && !name.isBlank() ? name : "#" + handlerId;
    }

    private record TargetSnapshot(Long postId, Long targetUserId, String content, Integer status, String hideReason) {
    }

    private record TargetKey(String targetType, Long targetId) {
    }
}
