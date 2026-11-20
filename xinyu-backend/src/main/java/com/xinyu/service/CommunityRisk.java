package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.LocalDateTime;

/** 社区危机关注的持久化规则，见举报契约「危机内容识别与关注」。 */
public final class CommunityRisk {

    public static final String PENDING = "PENDING";
    public static final String REVIEWED = "REVIEWED";
    public static final String STATUS_COLUMN = "risk_status";

    private CommunityRisk() {
    }

    static <T> void markHit(UpdateWrapper<T> edit) {
        edit.set(STATUS_COLUMN, PENDING)
                .set("risk_hit_time", LocalDateTime.now())
                .set("risk_reviewed_time", null);
    }

    static <T> void applyListFilter(QueryWrapper<T> query, String riskStatus) {
        query.eq(riskStatus != null, STATUS_COLUMN, riskStatus);
        if (PENDING.equals(riskStatus)) {
            query.orderByDesc("risk_hit_time", "id");
        } else {
            query.orderByDesc("create_time");
        }
    }

    static <T> AdminStatusResult markReviewed(BaseMapper<T> mapper, Long id) {
        // 条件与写入同一条 SQL，两个管理员只有一方能处理同一次待关注。
        // 保留内容修改时间，避免作者侧因关注操作看到变化。
        UpdateWrapper<T> review = new UpdateWrapper<>();
        review.eq("id", id).eq(STATUS_COLUMN, PENDING)
                .set(STATUS_COLUMN, REVIEWED)
                .set("risk_reviewed_time", LocalDateTime.now())
                .setSql("update_time = update_time");
        if (mapper.update(null, review) > 0) {
            return AdminStatusResult.UPDATED;
        }

        return mapper.selectById(id) == null ? AdminStatusResult.NOT_FOUND : AdminStatusResult.CONFLICT;
    }
}
