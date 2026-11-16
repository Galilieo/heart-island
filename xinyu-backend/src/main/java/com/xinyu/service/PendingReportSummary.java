package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.entity.ContentReport;
import com.xinyu.mapper.ContentReportMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 后台帖子/回复详情页查询关联待处理举报的共用助手，避免在多个服务中重复拼装查询。
 */
final class PendingReportSummary {

    private final long count;
    private final List<String> reasons;

    private PendingReportSummary(long count, List<String> reasons) {
        this.count = count;
        this.reasons = reasons;
    }

    static PendingReportSummary load(ContentReportMapper mapper, String targetType, Long targetId) {
        if (mapper == null || targetId == null) {
            return new PendingReportSummary(0L, List.of());
        }
        QueryWrapper<ContentReport> wrapper = new QueryWrapper<>();
        wrapper.eq("target_type", targetType)
                .eq("target_id", targetId)
                .eq("status", CommunityReportConstants.REPORT_PENDING)
                .orderByAsc("create_time");
        List<ContentReport> reports = mapper.selectList(wrapper);
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        for (ContentReport report : reports) {
            if (report.getReason() != null) {
                reasons.add(report.getReason());
            }
        }
        return new PendingReportSummary(reports.size(), new ArrayList<>(reasons));
    }

    long count() {
        return count;
    }

    List<String> reasons() {
        return reasons;
    }
}
