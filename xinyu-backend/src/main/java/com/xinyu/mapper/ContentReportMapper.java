package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.ContentReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区举报记录数据访问层，包含按内容聚合分页与并发安全的状态条件关闭语句。
 */
@Mapper
public interface ContentReportMapper extends BaseMapper<ContentReport> {

    /** 统计待处理举报按 (target_type, target_id) 去重聚合后的目标总数。 */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT target_type, target_id
                FROM content_report
                WHERE status = 'PENDING'
                GROUP BY target_type, target_id
            ) t
            """)
    long countPendingTargetGroups();

    /** 分页查询待处理目标聚合行：含 SAFETY 举报置顶，其余按首次举报时间升序。 */
    @Select("""
            SELECT target_type AS targetType,
                   target_id AS targetId,
                   MIN(post_id) AS postId,
                   MIN(target_user_id) AS targetUserId,
                   COUNT(*) AS reporterCount,
                   MAX(CASE WHEN reason = 'SAFETY' THEN 1 ELSE 0 END) AS hasSafety,
                   MIN(create_time) AS firstReportTime
            FROM content_report
            WHERE status = 'PENDING'
            GROUP BY target_type, target_id
            ORDER BY hasSafety DESC, firstReportTime ASC, target_type ASC, target_id ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<ReportTargetGroup> selectPendingTargetGroups(@Param("offset") long offset, @Param("size") long size);

    /** 统计已处理举报（HIDDEN / DISMISSED / CLOSED）按目标聚合后的总数。 */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT target_type, target_id
                FROM content_report
                WHERE status IN ('HIDDEN', 'DISMISSED', 'CLOSED')
                GROUP BY target_type, target_id
            ) t
            """)
    long countDoneTargetGroups();

    /** 分页查询已处理目标聚合行，按最近处理时间倒序排列。 */
    @Select("""
            SELECT target_type AS targetType,
                   target_id AS targetId,
                   MIN(post_id) AS postId,
                   MIN(target_user_id) AS targetUserId,
                   COUNT(*) AS reporterCount,
                   MAX(CASE WHEN reason = 'SAFETY' THEN 1 ELSE 0 END) AS hasSafety,
                   MIN(create_time) AS firstReportTime,
                   MAX(handle_time) AS latestHandleTime
            FROM content_report
            WHERE status IN ('HIDDEN', 'DISMISSED', 'CLOSED')
            GROUP BY target_type, target_id
            ORDER BY latestHandleTime DESC, target_type ASC, target_id ASC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<ReportTargetGroup> selectDoneTargetGroups(@Param("offset") long offset, @Param("size") long size);

    /** 条件关闭指定内容下所有待处理举报，返回影响行数用于并发防重。 */
    @Update("""
            UPDATE content_report
            SET status = #{status},
                hide_reason = #{hideReason},
                handler_id = #{handlerId},
                handle_note = #{handleNote},
                handle_time = #{handleTime}
            WHERE target_type = #{targetType}
              AND target_id = #{targetId}
              AND status = 'PENDING'
            """)
    int handlePendingByTarget(@Param("targetType") String targetType,
                              @Param("targetId") Long targetId,
                              @Param("status") String status,
                              @Param("hideReason") String hideReason,
                              @Param("handlerId") Long handlerId,
                              @Param("handleNote") String handleNote,
                              @Param("handleTime") LocalDateTime handleTime);

    /** 若内容在处理瞬间被作者删除，在同一事务内将刚处理的举报改记为 CLOSED 并清空隐藏原因。 */
    @Update("""
            UPDATE content_report
            SET status = 'CLOSED',
                hide_reason = NULL
            WHERE target_type = #{targetType}
              AND target_id = #{targetId}
              AND handler_id = #{handlerId}
              AND handle_time = #{handleTime}
              AND status = 'HIDDEN'
            """)
    int correctClosedOnDeletedTarget(@Param("targetType") String targetType,
                                     @Param("targetId") Long targetId,
                                     @Param("handlerId") Long handlerId,
                                     @Param("handleTime") LocalDateTime handleTime);
}
