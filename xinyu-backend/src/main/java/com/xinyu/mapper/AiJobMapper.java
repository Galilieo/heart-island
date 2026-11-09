package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.AiJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

/**
 * AI 任务的数据访问入口；任务状态变更由业务层协调事务。
 */
@Mapper
public interface AiJobMapper extends BaseMapper<AiJob> {

    /** 当前 worker 只实现日记回应，不能误领未来其他类型的任务。 */
    @Select("SELECT * FROM ai_job WHERE task_type='MOOD_REPLY' AND status='queued' AND attempt_count<3 ORDER BY id LIMIT 1")
    AiJob selectNextQueued();

    /** 当前读避免 REPEATABLE READ 快照把已变化的尝试误当成仍在执行。 */
    @Select("SELECT * FROM ai_job WHERE id=#{id} FOR UPDATE")
    AiJob selectForUpdate(@Param("id") long id);

    @Update("""
        UPDATE ai_job SET status='running', attempt_count=attempt_count+1, request_id=#{requestId},
          started_at=CURRENT_TIMESTAMP, finished_at=NULL, last_error_code=NULL,
          result_kind=NULL, model_name=NULL, model_called=NULL, update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND task_type='MOOD_REPLY' AND status='queued'
          AND attempt_count=#{expectedAttempt} AND attempt_count<3
        """)
    int claim(@Param("id") long id, @Param("expectedAttempt") int expectedAttempt,
              @Param("requestId") String requestId);

    /** 状态、尝试身份与业务关联均匹配时，才允许结束这次执行。 */
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

    /** 重试只重排同一任务，不重置次数，也不持有任务锁后再锁来源。 */
    @Update("""
        UPDATE ai_job SET status='queued', request_id=NULL, started_at=NULL, finished_at=NULL,
          last_error_code=NULL, result_kind=NULL, model_called=NULL, model_name=NULL,
          update_time=CURRENT_TIMESTAMP
        WHERE id=#{job.id} AND task_type='MOOD_REPLY' AND user_id=#{job.userId}
          AND target_id=#{job.targetId} AND target_version=#{job.targetVersion}
          AND status='failed' AND attempt_count=#{job.attemptCount} AND attempt_count<3
        """)
    int requeue(@Param("job") AiJob job);

    @Update("""
        UPDATE ai_job SET status='failed', last_error_code='execution_interrupted',
          result_kind=NULL, model_called=NULL, model_name=NULL,
          finished_at=CURRENT_TIMESTAMP, update_time=CURRENT_TIMESTAMP
        WHERE task_type='MOOD_REPLY' AND status='running'
        """)
    int failInterrupted();

    /** 与来源修改或删除处于同一事务，保留已完成任务，仅失效尚可执行或重试的任务。 */
    @Update("""
        UPDATE ai_job
        SET status = 'superseded', finished_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
        WHERE task_type = 'MOOD_REPLY' AND target_id = #{targetId} AND user_id = #{userId}
          AND status IN ('queued', 'running', 'failed')
        """)
    int supersedeUnfinished(@Param("targetId") Long targetId, @Param("userId") Long userId);
}
