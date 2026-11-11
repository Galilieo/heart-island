package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.AiJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 任务的数据访问入口；任务状态变更由业务层协调事务。
 */
@Mapper
public interface AiJobMapper extends BaseMapper<AiJob> {

    /**
     * 在领取事务内锁定最早一条未被锁定的日记任务，供随后CAS领取。
     * 跳过其他领取者持有的行锁，不承诺严格FIFO；调用方必须在同一事务内完成领取。
     *
     * @return 最早一条未锁定的 queued 任务；没有时返回 null
     */
    @Select("""
        SELECT * FROM ai_job
        WHERE task_type='MOOD_REPLY' AND status='queued'
        ORDER BY id LIMIT 1
        FOR UPDATE SKIP LOCKED
        """)
    AiJob selectNextQueued();

    /**
     * 当前读任务行并加锁，避免 REPEATABLE READ 快照把已变化的尝试误当成仍在执行。
     *
     * @param id 任务 ID
     * @return 加锁后的任务行；不存在时返回 null
     */
    @Select("SELECT * FROM ai_job WHERE id=#{id} FOR UPDATE")
    AiJob selectForUpdate(@Param("id") long id);

    /**
     * 原子领取任务：只有queued且累计次数仍为预期值时成功，领取才递增次数。
     *
     * @param id 任务 ID
     * @param expectedAttempt 领取前的尝试次数
     * @param requestId 本次领取生成的追踪标识
     * @return 命中并更新 1 行时返回 1，否则返回 0
     */
    @Update("""
        UPDATE ai_job SET status='running', attempt_count=attempt_count+1, request_id=#{requestId},
          started_at=CURRENT_TIMESTAMP, finished_at=NULL, last_error_code=NULL,
          result_kind=NULL, model_name=NULL, model_called=NULL, update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND task_type='MOOD_REPLY' AND status='queued'
          AND attempt_count=#{expectedAttempt}
        """)
    int claim(@Param("id") long id, @Param("expectedAttempt") int expectedAttempt,
              @Param("requestId") String requestId);

    /**
     * 状态、尝试身份与业务关联均匹配时，才允许结束这次执行。
     *
     * @param job 本次执行持有的任务快照
     * @param status 目标终态
     * @param errorCode 安全错误分类；成功时为空
     * @param resultKind 结果类型；未取得结果时为空
     * @param modelCalled 是否已发起供应商调用；无法确认时为空
     * @param modelName 模型名称；未调用模型时为空
     * @return 命中并更新 1 行时返回 1，否则返回 0
     */
    @Update("""
        UPDATE ai_job SET status=#{status}, last_error_code=#{errorCode},
          result_kind=#{resultKind}, model_called=#{modelCalled}, model_name=#{modelName},
          finished_at=CURRENT_TIMESTAMP, update_time=CURRENT_TIMESTAMP
        WHERE id=#{job.id} AND task_type='MOOD_REPLY' AND user_id=#{job.userId}
          AND target_id=#{job.targetId} AND target_version=#{job.targetVersion}
          AND status='running' AND attempt_count=#{job.attemptCount} AND request_id=#{job.requestId}
        """)
    int finishAttempt(@Param("job") AiJob job, @Param("status") String status,
                      @Param("errorCode") String errorCode, @Param("resultKind") String resultKind,
                      @Param("modelCalled") Boolean modelCalled, @Param("modelName") String modelName);

    /**
     * 用数据库时间计算当前尝试的冷却剩余秒数；正剩余向上取整，异常空结束时间放行。
     *
     * @param job 已校验归属和来源的任务快照
     * @param cooldownSeconds 根据同一快照累计次数计算的冷却时长
     * @return 剩余秒数；本次尝试已变化或任务不存在时返回null，由调用方重新核验
     */
    @Select("""
        SELECT CASE
          WHEN status != 'failed' OR #{cooldownSeconds} = 0 OR finished_at IS NULL THEN 0
          ELSE CAST(GREATEST(0, CEIL(
            TIMESTAMPDIFF(MICROSECOND, CURRENT_TIMESTAMP,
              TIMESTAMPADD(SECOND, #{cooldownSeconds}, finished_at)) / 1000000.0
          )) AS SIGNED)
        END
        FROM ai_job
        WHERE id=#{job.id} AND attempt_count=#{job.attemptCount}
        """)
    Integer selectRetryAfterSeconds(@Param("job") AiJob job, @Param("cooldownSeconds") int cooldownSeconds);

    /** 按尝试快照和数据库冷却条件重排；不重置累计次数，不反向获取来源锁。 */
    @Update("""
        UPDATE ai_job SET status='queued', request_id=NULL, started_at=NULL, finished_at=NULL,
          last_error_code=NULL, result_kind=NULL, model_called=NULL, model_name=NULL,
          update_time=CURRENT_TIMESTAMP
        WHERE id=#{job.id} AND task_type='MOOD_REPLY' AND user_id=#{job.userId}
          AND target_id=#{job.targetId} AND target_version=#{job.targetVersion}
          AND status='failed' AND attempt_count=#{job.attemptCount}
          AND (
              #{cooldownSeconds} = 0
              OR finished_at IS NULL
              OR CURRENT_TIMESTAMP >= TIMESTAMPADD(SECOND, #{cooldownSeconds}, finished_at)
          )
        """)
    int requeueAfterCooldown(@Param("job") AiJob job, @Param("cooldownSeconds") int cooldownSeconds);

    /**
     * 启动恢复：把上次进程遗留的 running 标记为 execution_interrupted。
     *
     * @return 受影响行数
     */
    @Update("""
        UPDATE ai_job SET status='failed', last_error_code='execution_interrupted',
          result_kind=NULL, model_called=NULL, model_name=NULL,
          finished_at=CURRENT_TIMESTAMP, update_time=CURRENT_TIMESTAMP
        WHERE task_type='MOOD_REPLY' AND status='running'
        """)
    int failInterrupted();

    /**
     * 与来源修改或删除处于同一事务，保留已完成任务，仅失效尚可执行或重试的任务。
     *
     * @param targetId 来源日记 ID
     * @param userId 所属用户 ID
     * @return 受影响行数
     */
    @Update("""
        UPDATE ai_job
        SET status = 'superseded', finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
        WHERE task_type = 'MOOD_REPLY' AND target_id = #{targetId} AND user_id = #{userId}
          AND status IN ('queued', 'running', 'failed')
        """)
    int supersedeUnfinished(@Param("targetId") Long targetId, @Param("userId") Long userId);
}
