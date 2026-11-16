package com.xinyu.community;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.dto.CommunityReportCreateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.entity.ContentReport;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.CommunityReplyMapper;
import com.xinyu.mapper.ContentReportMapper;
import com.xinyu.service.CommunityReportService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4.4 举报提交流程、每日限额与我的举报隐私过滤单测。
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

    @Mock
    private ContentReportMapper contentReportMapper;

    @Mock
    private CommunityPostMapper communityPostMapper;

    @Mock
    private CommunityReplyMapper communityReplyMapper;

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
    void submitRejectsSelfHiddenDeletedInvalidOtherAndExcessLength() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(1, "内容"));
        assertThatThrownBy(() -> service.submitReport(AUTHOR_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(2, "已隐藏"));
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(0, "已删除"));
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "OTHER", "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("补充说明");

        String over200 = "字".repeat(201);
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "OTHER", over200)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("200");
    }

    @Test
    void submitEnforcesDailyLimitAndTranslatesDuplicateKeyWithout500() {
        when(communityPostMapper.selectById(POST_ID)).thenReturn(post(1, "内容"));
        when(contentReportMapper.selectCount(any())).thenReturn(0L, 20L);
        assertThatThrownBy(() -> service.submitReport(REPORTER_ID, createDto("POST", POST_ID, "AD", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(rse.getReason()).isEqualTo("今天的举报次数已用完");
                });

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

    private static CommunityPost post(int status, String content) {
        CommunityPost post = new CommunityPost();
        post.setId(POST_ID);
        post.setUserId(AUTHOR_ID);
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
}
