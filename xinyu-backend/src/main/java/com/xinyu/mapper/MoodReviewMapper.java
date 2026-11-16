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
            row.getLong("id"),
            row.getLong("user_id"),
            row.getString("review_type"),
            row.getDate("range_start").toLocalDate(),
            row.getDate("range_end").toLocalDate(),
            row.getInt("revision"),
            row.getString("source_fingerprint"),
            row.getString("status"),
            row.getString("stats_json"),
            row.getString("body"),
            row.getString("ps"),
            row.getString("result_kind"),
            row.getString("finished_at"));

    private static final RowMapper<MoodRecord> RECORD_ROW = (row, index) -> {
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
    };

    public MoodReviewMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 锁定用户行以串行化同一用户跨实例的回顾创建请求；用户不存在时返回 false。 */
    public boolean lockUser(long userId) {
        return !jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE", userId).isEmpty();
    }

    /** 统计全站排队中的任务数，供容量门禁判断。 */
    public int queuedCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE status='queued'", Integer.class);
    }

    /** 统计指定用户进行中的任务数（queued 或 running）。 */
    public int activeCount(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_job WHERE user_id=? AND status IN ('queued','running')",
                Integer.class, userId);
    }

    /** 获取当前用户在指定类型与起始日期下的下一个版本号（从 1 递增）。 */
    public int nextRevision(long userId, String type, LocalDate start) {
        String sql = "SELECT COALESCE(MAX(revision),0)+1 FROM mood_review "
                + "WHERE user_id=? AND review_type=? AND range_start=?";
        return jdbc.queryForObject(sql, Integer.class, userId, type, start);
    }

    /** 插入排队状态的回顾主表；调用方持有用户行锁，LAST_INSERT_ID 与 INSERT 使用同一事务连接。 */
    public long insert(long userId, String type, LocalDate start, LocalDate end,
                       int revision, String fingerprint, String statsJson) {
        String sql = """
                INSERT INTO mood_review(
                    user_id, review_type, range_start, range_end,
                    revision, source_fingerprint, status, stats_json
                ) VALUES (?, ?, ?, ?, ?, ?, 'queued', ?)
                """;
        jdbc.update(sql, userId, type, start, end, revision, fingerprint, statsJson);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 记录本次回顾包含的心情来源及其内容版本快照，默认未发送给模型。 */
    public void insertSource(long reviewId, long recordId, long version) {
        jdbc.update(
                "INSERT INTO mood_review_source(review_id,record_id,content_version,sent_to_model) VALUES (?,?,?,0)",
                reviewId, recordId, version);
    }

    /** 创建关联的 MOOD_REVIEW 异步任务。 */
    public void insertJob(long userId, long reviewId, int revision, String template) {
        String sql = """
                INSERT INTO ai_job(
                    user_id, target_id, task_type, target_version,
                    status, attempt_count, template_version
                ) VALUES (?, ?, 'MOOD_REVIEW', ?, 'queued', 0, ?)
                """;
        jdbc.update(sql, userId, reviewId, revision, template);
    }

    /** 分页查询每周历史信封，每周仅取最新版本号的一条记录以避免重生成产生重复信封。 */
    public List<MoodReview> history(long userId, int limit, int offset) {
        String sql = """
                SELECT r.* FROM mood_review r
                JOIN (
                    SELECT range_start, MAX(revision) AS max_revision
                    FROM mood_review
                    WHERE user_id=? AND review_type='WEEK'
                    GROUP BY range_start
                ) latest
                  ON r.user_id=? AND r.review_type='WEEK'
                 AND r.range_start=latest.range_start
                 AND r.revision=latest.max_revision
                ORDER BY r.range_start DESC
                LIMIT ? OFFSET ?
                """;
        return jdbc.query(sql, ROW, userId, userId, limit, offset);
    }

    /** 查询最近一条进行中或已成功回顾；状态条件由固定布尔参数选择，不接受任意 SQL 输入。 */
    public MoodReview latest(long userId, String type, LocalDate start, boolean active) {
        String condition = active ? "status IN ('queued','running')" : "status='succeeded'";
        if (start == null) {
            String sql = "SELECT * FROM mood_review WHERE user_id=? AND review_type=? AND "
                    + condition + " ORDER BY range_start DESC,revision DESC LIMIT 1";
            List<MoodReview> rows = jdbc.query(sql, ROW, userId, type);
            return rows.isEmpty() ? null : rows.get(0);
        }
        String sql = "SELECT * FROM mood_review WHERE user_id=? AND review_type=? AND range_start=? AND "
                + condition + " ORDER BY revision DESC LIMIT 1";
        List<MoodReview> rows = jdbc.query(sql, ROW, userId, type, start);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按主键加载回顾快照，不存在返回 null。 */
    public MoodReview load(long id) {
        List<MoodReview> rows = jdbc.query("SELECT * FROM mood_review WHERE id=?", ROW, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按 user/status/time 索引查询范围；当前读锁保护来源，禁止把锁带到 HTTP 等待阶段。 */
    public List<MoodRecord> sources(long userId, LocalDate start, LocalDate end, boolean lock) {
        String sql = "SELECT * FROM mood_record "
                + "WHERE user_id=? AND status=1 AND create_time>=? AND create_time<? "
                + "ORDER BY create_time,id";
        return jdbc.query(sql + (lock ? " FOR UPDATE" : ""), RECORD_ROW, userId, start.atStartOfDay(), end.atStartOfDay());
    }

    /** 按生成时发往模型的同一顺序返回当前仍有效的记录全文，供读取报告时重建 r1..rN 对照。 */
    public List<MoodRecord> sentSources(long userId, long reviewId) {
        String sql = """
                SELECT m.* FROM mood_review_source s
                JOIN mood_record m ON m.id=s.record_id AND m.user_id=?
                WHERE s.review_id=? AND s.sent_to_model=1 AND m.status=1
                ORDER BY m.create_time,m.id
                """;
        return jdbc.query(sql, RECORD_ROW, userId, reviewId);
    }

    /** 返回当前范围修订号最高的一次尝试，用于判断最新任务是否处于 failed。 */
    public MoodReview latestAttempt(long userId, String type, LocalDate start) {
        String sql = "SELECT * FROM mood_review "
                + "WHERE user_id=? AND review_type=? AND range_start=? "
                + "ORDER BY revision DESC LIMIT 1";
        List<MoodReview> rows = jdbc.query(sql, ROW, userId, type, start);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查询指定回顾版本关联的 ai_job 状态，不存在时返回 null。 */
    public String jobStatus(long reviewId, int revision) {
        String sql = "SELECT status FROM ai_job "
                + "WHERE task_type='MOOD_REVIEW' AND target_id=? AND target_version=? LIMIT 1";
        List<String> rows = jdbc.queryForList(sql, String.class, reviewId, revision);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 重置指定回顾所有来源的发送标记，在准备新一次尝试前调用。 */
    public void resetSentSources(long id) {
        jdbc.update("UPDATE mood_review_source SET sent_to_model=0 WHERE review_id=?", id);
    }

    /** 标记某条来源记录已纳入本次发送给模型的抽样集合。 */
    public void markSentSource(long reviewId, long recordId) {
        jdbc.update("UPDATE mood_review_source SET sent_to_model=1 WHERE review_id=? AND record_id=?", reviewId, recordId);
    }

    /** 统计当前回顾关联的来源中已被软删除或硬删除的条数。 */
    public int deletedSourceCount(long userId, long id) {
        String sql = """
                SELECT COUNT(*) FROM mood_review_source s
                LEFT JOIN mood_record m ON m.id=s.record_id AND m.user_id=?
                WHERE s.review_id=? AND (m.id IS NULL OR m.status<>1)
                """;
        return jdbc.queryForObject(sql, Integer.class, userId, id);
    }

    /** 统计实际标记为已发送给模型的来源条数。 */
    public int sentSourceCount(long id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM mood_review_source WHERE review_id=? AND sent_to_model=1",
                Integer.class, id);
    }

    /** 查询指定回顾及版本号对应的任务主键。 */
    public long jobId(long id, int revision) {
        return jdbc.queryForObject(
                "SELECT id FROM ai_job WHERE task_type='MOOD_REVIEW' AND target_id=? AND target_version=?",
                Long.class, id, revision);
    }

    /** 将回顾状态置为活跃（queued 或 running）并清空结束信息。 */
    public void markActive(long id, String status) {
        jdbc.update(
                "UPDATE mood_review SET status=?,last_error_code=NULL,finished_at=NULL WHERE id=?",
                status, id);
    }

    /** 将回顾置为终态并写入安全错误码与完成时间。 */
    public void finish(long id, String status, String error) {
        jdbc.update(
                "UPDATE mood_review SET status=?,last_error_code=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",
                status, error, id);
    }

    /** 保存模型或支持性结果正文、P.S. 与模型元信息。 */
    public void saveResult(long id, ReviewAiResponse response) {
        String sql = """
                UPDATE mood_review
                SET body=?, ps=?, result_kind=?, template_version=?, model_name=?
                WHERE id=?
                """;
        jdbc.update(sql, response.body(), response.ps(), response.resultKind(),
                response.templateVersion(), response.modelName(), id);
    }

    /** 将因进程中断而遗留为 running 的回顾同步标记为 execution_interrupted 失败态。 */
    public void recoverInterrupted() {
        String sql = """
                UPDATE mood_review r
                JOIN ai_job j
                  ON j.target_id=r.id AND j.target_version=r.revision AND j.task_type='MOOD_REVIEW'
                SET r.status='failed',
                    r.last_error_code='execution_interrupted',
                    r.finished_at=j.finished_at
                WHERE r.status='running'
                  AND j.status='failed'
                  AND j.last_error_code='execution_interrupted'
                """;
        jdbc.update(sql);
    }
}
