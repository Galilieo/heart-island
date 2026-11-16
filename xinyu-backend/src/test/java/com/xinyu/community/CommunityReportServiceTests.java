package com.xinyu.community;

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
import com.xinyu.service.CommunityReportService;
import com.xinyu.vo.AdminReportDetailVO;
import com.xinyu.vo.AdminReportQueueRowVO;
import com.xinyu.vo.MyReportVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.4 / 4.5 举报提交流程、每日限额、我的举报隐私过滤与后台聚合/裁决单测。
 */
@ExtendWith(MockitoExtension.class)
class CommunityReportServiceTests {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant FIXED_INSTANT = LocalDateTime.of(2026, 11, 14, 15, 30)
            .atZone(SHANGHAI).toInstant();
    private static final long AUTHOR_ID = 101L;
    private static final long REPORTER_ID = 202L;
    private static final long ADMIN_ID = 303L;
    private static final long POST_ID = 11L;
    private static final long REPLY_ID = 21L;

    @Mock
    private ContentReportMapper contentReportMapper;

    @Mock
    private CommunityPostMapper communityPostMapper;

    @Mock
    private CommunityReplyMapper communityReplyMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private CommunityReportService service;

    @BeforeEach
    void setUpClock() {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(FIXED_INSTANT, SHANGHAI));
        lenient().when(contentReportMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void submitPostReportSavesSnapshotPostIdAndTargetAuthor() {
        CommunityPost post = post(1, "含广告引流全文");
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post);
        when(contentReportMapper.insert(any(ContentReport.class))).thenReturn(1);

        CommunityReportCreateDTO dto = createDto("POST", POST_ID, "AD", "  微信号引流  ");
        service.submitReport(REPORTER_ID, dto);

        ArgumentCaptor<ContentReport> captor = ArgumentCaptor.forClass(ContentReport.class);
        verify(contentReportMapper).insert(captor.capture());
        ContentReport saved = captor.getValue();
        assertThat(saved.getReporterId()).isEqualTo(REPORTER_ID);
        assertThat(saved.getTargetType()).isEqualTo("POST");
        assertThat(saved.getTargetId()).isEqualTo(POST_ID);
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        assertThat(saved.getTargetUserId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getContentSnapshot()).isEqualTo("含广告引流全文");
        assertThat(saved.getDetail()).isEqualTo("微信号引流");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void submitRejectsSelfReport() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(1, "内容"));
        assertThatThrownBy(() -> service.submitReport(AUTHOR_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void submitRejectsAdminHiddenPost() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2, "已隐藏"));
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void submitRejectsAuthorDeletedPost() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(0, "已删除"));
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void submitRejectsOtherReasonWithoutDetail() {
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "OTHER", "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("补充说明");
    }

    @Test
    void submitRejectsDetailExceedingMaxLength() {
        String over200 = "字".repeat(201);
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "OTHER", over200)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("200");
    }

    @Test
    void submitEnforcesDailyReportLimit() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(1, "内容"));
        // 首次查重返回 0，随后当日累计返回 20
        when(contentReportMapper.selectCount(any())).thenReturn(0L, 20L);
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(rse.getReason()).isEqualTo("今天的举报次数已用完");
                });
    }

    @Test
    void submitTranslatesDuplicateKeyToConflictWithout500() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(1, "内容"));
        when(contentReportMapper.selectCount(any())).thenReturn(0L, 19L);
        when(contentReportMapper.insert(any(ContentReport.class)))
                .thenThrow(new DuplicateKeyException("uk_report_reporter_target"));
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("已经举报过");
                });
    }

    @Test
    void listMineTruncatesExcerptAndExcludesSensitiveFields() {
        ContentReport report = new ContentReport();
        report.setId(99L);
        report.setReporterId(REPORTER_ID);
        report.setTargetType("POST");
        report.setTargetId(POST_ID);
        report.setPostId(POST_ID);
        report.setTargetUserId(AUTHOR_ID);
        report.setReason("PRIVACY");
        report.setContentSnapshot("甲".repeat(70));
        report.setStatus("HIDDEN");
        report.setHideReason("PRIVACY");
        report.setHandlerId(ADMIN_ID);
        report.setHandleNote("内部备注不能返回");
        report.setCreateTime(LocalDateTime.of(2026, 11, 14, 10, 0));

        Page<ContentReport> dbPage = new Page<>(1, 10, 1);
        dbPage.setRecords(List.of(report));
        when(contentReportMapper.selectPage(any(), any())).thenReturn(dbPage);

        Page<MyReportVO> result = service.listMine(REPORTER_ID, 1L, 10L);
        assertThat(result.getRecords()).hasSize(1);
        MyReportVO vo = result.getRecords().get(0);
        assertThat(vo.getExcerpt()).isEqualTo("甲".repeat(60) + "…");
        assertThat(vo.getStatus()).isEqualTo("HIDDEN");
        assertThat(vo.getHideReason()).isEqualTo("PRIVACY");
    }

    @Test
    void adminDetailSuggestsIllegalWhenSafetyOrOtherIsTopAndDetectsEdits() {
        ContentReport r1 = reportRecord(1L, 201L, "SAFETY", "改前原文", "PENDING", LocalDateTime.of(2026, 11, 14, 9, 0));
        ContentReport r2 = reportRecord(2L, 202L, "SAFETY", "改前原文", "PENDING", LocalDateTime.of(2026, 11, 14, 9, 5));
        when(contentReportMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(1, "改后正文"));
        when(communityPostMapper.selectCount(any())).thenReturn(1L);
        when(communityReplyMapper.selectCount(any())).thenReturn(2L);

        AdminReportDetailVO detail = service.getAdminReportDetail("POST", POST_ID);
        assertThat(detail).isNotNull();
        assertThat(detail.getSuggestedHideReason()).isEqualTo(CommunityReportConstants.REASON_ILLEGAL);
        assertThat(detail.getContentChanged()).isTrue();
        assertThat(detail.getAuthorHiddenCount()).isEqualTo(3);
        assertThat(detail.getHasSafety()).isTrue();
        assertThat(detail.getRecords()).hasSize(2);
    }

    @Test
    void handleReportThrowsConflictWhenAlreadyHandled() {
        when(communityPostMapper.selectByIdForUpdate(POST_ID)).thenReturn(post(1, "正文"));
        when(contentReportMapper.handlePendingByTarget(any(), anyLong(), any(), any(), anyLong(), any(), any()))
                .thenReturn(0);
        assertThatThrownBy(() -> service.handleReport(ADMIN_ID, handleDto("POST", POST_ID, "HIDE", "AD")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo("已被其他管理员处理");
                });
    }

    @Test
    void handleReportClosesWhenTargetAuthorDeleted() {
        when(communityPostMapper.selectByIdForUpdate(POST_ID)).thenReturn(post(0, "正文"));
        when(contentReportMapper.handlePendingByTarget(
                eq("POST"), eq(POST_ID), eq("CLOSED"), eq(null), eq(ADMIN_ID), any(), any()))
                .thenReturn(1);
        assertThat(service.handleReport(ADMIN_ID, handleDto("POST", POST_ID, "HIDE", "AD")))
                .contains("作者删除");
    }

    @Test
    void handleReportOnAlreadyHiddenReplyDoesNotDecrementReplyCount() {
        CommunityReply hiddenReply = new CommunityReply();
        hiddenReply.setId(REPLY_ID);
        hiddenReply.setPostId(POST_ID);
        hiddenReply.setUserId(AUTHOR_ID);
        hiddenReply.setContent("回复");
        hiddenReply.setStatus(2);
        when(communityReplyMapper.selectByIdForUpdate(REPLY_ID)).thenReturn(hiddenReply);
        when(contentReportMapper.handlePendingByTarget(
                eq("REPLY"), eq(REPLY_ID), eq("HIDDEN"), eq("AD"), eq(ADMIN_ID), any(), any()))
                .thenReturn(2);
        service.handleReport(ADMIN_ID, handleDto("REPLY", REPLY_ID, "HIDE", "AD"));
        verify(communityPostMapper, never()).adjustReplyCount(anyLong(), anyInt());
    }

    @Test
    void handleReportDismissLeavesContentStatusUnchanged() {
        when(communityPostMapper.selectByIdForUpdate(POST_ID)).thenReturn(post(2, "已隐藏帖子"));
        when(contentReportMapper.handlePendingByTarget(
                eq("POST"), eq(POST_ID), eq("DISMISSED"), eq(null), eq(ADMIN_ID), any(), any()))
                .thenReturn(1);
        service.handleReport(ADMIN_ID, handleDto("POST", POST_ID, "DISMISS", null));
        verify(communityPostMapper, never()).update(any(), any());
    }

    @Test
    void pageAdminReportsAssemblesAggregatedQueueAndHandlerName() {
        ReportTargetGroup g = new ReportTargetGroup();
        g.setTargetType("POST");
        g.setTargetId(POST_ID);
        g.setPostId(POST_ID);
        g.setTargetUserId(AUTHOR_ID);
        g.setReporterCount(2);
        g.setHasSafety(1);
        g.setFirstReportTime(LocalDateTime.of(2026, 11, 14, 10, 0));

        when(contentReportMapper.countDoneTargetGroups()).thenReturn(1L);
        when(contentReportMapper.selectDoneTargetGroups(0L, 10L)).thenReturn(List.of(g));

        ContentReport r = reportRecord(5L, REPORTER_ID, "SAFETY", "快照文本", "HIDDEN", LocalDateTime.of(2026, 11, 14, 10, 0));
        r.setHideReason("PRIVACY");
        r.setHandlerId(ADMIN_ID);
        r.setHandleNote("确认违规");
        r.setHandleTime(LocalDateTime.of(2026, 11, 14, 12, 0));
        when(contentReportMapper.selectList(any())).thenReturn(List.of(r));

        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setUsername("admin");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(admin));

        Page<AdminReportQueueRowVO> page = service.pageAdminReports("DONE", 1L, 10L);
        assertThat(page.getTotal()).isEqualTo(1);
        AdminReportQueueRowVO row = page.getRecords().get(0);
        assertThat(row.getHasSafety()).isTrue();
        assertThat(row.getResultStatus()).isEqualTo("HIDDEN");
        assertThat(row.getHandlerName()).isEqualTo("admin");
        assertThat(row.getReasonCounts()).containsEntry("SAFETY", 1);
    }

    private static CommunityPost post(int status, String content) {
        CommunityPost post = new CommunityPost();
        post.setId(POST_ID);
        post.setUserId(AUTHOR_ID);
        post.setTopicId(1L);
        post.setContent(content);
        post.setStatus(status);
        return post;
    }

    private static CommunityReportCreateDTO createDto(String type, Long id, String reason, String detail) {
        CommunityReportCreateDTO dto = new CommunityReportCreateDTO();
        dto.setTargetType(type);
        dto.setTargetId(id);
        dto.setReason(reason);
        dto.setDetail(detail);
        return dto;
    }

    private static AdminReportHandleDTO handleDto(String type, Long id, String action, String hideReason) {
        AdminReportHandleDTO dto = new AdminReportHandleDTO();
        dto.setTargetType(type);
        dto.setTargetId(id);
        dto.setAction(action);
        dto.setHideReason(hideReason);
        return dto;
    }

    private static ContentReport reportRecord(Long id, Long reporterId, String reason, String snapshot,
                                              String status, LocalDateTime createTime) {
        ContentReport report = new ContentReport();
        report.setId(id);
        report.setReporterId(reporterId);
        report.setTargetType("POST");
        report.setTargetId(POST_ID);
        report.setPostId(POST_ID);
        report.setTargetUserId(AUTHOR_ID);
        report.setReason(reason);
        report.setContentSnapshot(snapshot);
        report.setStatus(status);
        report.setCreateTime(createTime);
        return report;
    }
}
