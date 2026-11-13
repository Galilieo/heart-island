package com.xinyu.mapper;

import com.xinyu.dto.ReviewAiResponse;
import com.xinyu.entity.MoodRecord;
import com.xinyu.entity.MoodReview;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** 回顾专用数据访问；调用方负责短事务，所有锁均持有至该事务结束。 */
@Repository
public class MoodReviewMapper {
    private final JdbcTemplate jdbc;
    private static final RowMapper<MoodReview> ROW = (row, index) -> new MoodReview(
            row.getLong("id"), row.getLong("user_id"), row.getString("review_type"),
            row.getDate("range_start").toLocalDate(), row.getDate("range_end").toLocalDate(),
            row.getInt("revision"), row.getString("source_fingerprint"), row.getString("status"),
            row.getString("stats_json"), row.getString("body"), row.getString("ps"),
            row.getString("result_kind"), row.getString("finished_at"));

    public MoodReviewMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean lockUser(long userId) {
        return !jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE", userId).isEmpty();
    }

    public int queuedCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE status='queued'", Integer.class);
    }

    public int activeCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE user_id=? AND status IN ('queued','running')", Integer.class, userId);
    }

    public int nextRevision(long userId, String type, LocalDate start) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM mood_review WHERE user_id=? AND review_type=? AND range_start=?",
                Integer.class, userId, type, start);
    }

    /** 调用方持有用户行锁；LAST_INSERT_ID 与 INSERT 使用同一事务连接。 */
    public long insert(long userId, String type, LocalDate start, LocalDate end, int revision, String fingerprint, String statsJson) {
        jdbc.update("INSERT INTO mood_review(user_id,review_type,range_start,range_end,revision,source_fingerprint,status,stats_json) VALUES (?,?,?,?,?,?,'queued',?)",
                userId, type, start, end, revision, fingerprint, statsJson);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void insertSource(long reviewId, long recordId, long version) {
        jdbc.update("INSERT INTO mood_review_source(review_id,record_id,content_version,sent_to_model) VALUES (?,?,?,0)", reviewId, recordId, version);
    }

    public void insertJob(long userId, long reviewId, int revision, String template) {
        jdbc.update("INSERT INTO ai_job(user_id,target_id,task_type,target_version,status,attempt_count,template_version) VALUES (?,?,'MOOD_REVIEW',?,'queued',0,?)",
                userId, reviewId, revision, template);
    }

    public List<MoodReview> history(long userId, int limit, int offset) {
        return jdbc.query("SELECT * FROM mood_review WHERE user_id=? AND review_type='WEEK' ORDER BY range_start DESC,revision DESC LIMIT ? OFFSET ?", ROW, userId, limit, offset);
    }

    /** 状态条件由固定布尔参数选择，不接受任意 SQL 输入。 */
    public MoodReview latest(long userId, String type, LocalDate start, boolean active) {
        String condition = active ? "status IN ('queued','running')" : "status='succeeded'";
        List<MoodReview> rows = start == null
                ? jdbc.query("SELECT * FROM mood_review WHERE user_id=? AND review_type=? AND " + condition + " ORDER BY range_start DESC,revision DESC LIMIT 1", ROW, userId, type)
                : jdbc.query("SELECT * FROM mood_review WHERE user_id=? AND review_type=? AND range_start=? AND " + condition + " ORDER BY revision DESC LIMIT 1", ROW, userId, type, start);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public MoodReview load(long id) {
        List<MoodReview> rows = jdbc.query("SELECT * FROM mood_review WHERE id=?", ROW, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按 user/status/time 索引查询范围；当前读锁保护来源，禁止把锁带到 HTTP 等待阶段。 */
    public List<MoodRecord> sources(long userId, LocalDate start, LocalDate end, boolean lock) {
        String sql = "SELECT * FROM mood_record WHERE user_id=? AND status=1 AND create_time>=? AND create_time<? ORDER BY create_time,id";
        return jdbc.query(sql + (lock ? " FOR UPDATE" : ""), (row, index) -> {
            MoodRecord record = new MoodRecord();
            record.setId(row.getLong("id"));
            record.setUserId(row.getLong("user_id"));
            record.setContentVersion(row.getLong("content_version"));
            record.setAiReplyEnabled(row.getBoolean("ai_reply_enabled"));
            record.setContent(row.getString("content"));
            record.setMoodType(row.getString("mood_type"));
            record.setCreateTime(row.getTimestamp("create_time").toLocalDateTime());
            record.setStatus(1);
            return record;
        }, userId, start.atStartOfDay(), end.atStartOfDay());
    }

    public void resetSentSources(long id) {
        jdbc.update("UPDATE mood_review_source SET sent_to_model=0 WHERE review_id=?", id);
    }

    public void markSentSource(long reviewId, long recordId) {
        jdbc.update("UPDATE mood_review_source SET sent_to_model=1 WHERE review_id=? AND record_id=?", reviewId, recordId);
    }

    public int deletedSourceCount(long userId, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mood_review_source s LEFT JOIN mood_record m ON m.id=s.record_id AND m.user_id=? WHERE s.review_id=? AND (m.id IS NULL OR m.status<>1)", Integer.class, userId, id);
    }

    public int sentSourceCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mood_review_source WHERE review_id=? AND sent_to_model=1", Integer.class, id);
    }

    public long jobId(long id, int revision) {
        return jdbc.queryForObject("SELECT id FROM ai_job WHERE task_type='MOOD_REVIEW' AND target_id=? AND target_version=?", Long.class, id, revision);
    }

    public void markActive(long id, String status) {
        jdbc.update("UPDATE mood_review SET status=?,last_error_code=NULL,finished_at=NULL WHERE id=?", status, id);
    }

    public void finish(long id, String status, String error) {
        jdbc.update("UPDATE mood_review SET status=?,last_error_code=?,finished_at=CURRENT_TIMESTAMP WHERE id=?", status, error, id);
    }

    public void saveResult(long id, ReviewAiResponse response) {
        jdbc.update("UPDATE mood_review SET body=?,ps=?,result_kind=?,template_version=?,model_name=? WHERE id=?",
                response.body(), response.ps(), response.resultKind(), response.templateVersion(), response.modelName(), id);
    }

    public void recoverInterrupted() {
        jdbc.update("UPDATE mood_review r JOIN ai_job j ON j.target_id=r.id AND j.target_version=r.revision AND j.task_type='MOOD_REVIEW' SET r.status='failed',r.last_error_code='execution_interrupted',r.finished_at=j.finished_at WHERE r.status='running' AND j.status='failed' AND j.last_error_code='execution_interrupted'");
    }
}
