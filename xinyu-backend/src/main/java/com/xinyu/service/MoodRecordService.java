package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.entity.AiJob;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.vo.MoodRecordVO;
import com.xinyu.vo.MoodSaveVO;
import com.xinyu.vo.MoodTrendVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MoodRecordService {

    /** 产品心情枚举，来源：test-contracts/mood-types.json 与前端选项。 */
    private static final Set<String> MOOD_TYPES = Set.of(
            "开心", "满足", "期待", "感动", "平静",
            "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他");

    /** mood_record.status=1 表示有效，软删除写 0。 */
    private static final int STATUS_ACTIVE = 1;

    /** 心情内容上限 500 个 UTF-16 单位，与 Python 协议及 500/501 验收边界一致。 */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 与 Python str.strip 口径一致：U+0085 NEXT LINE 在 Java 中不算空白，需单独排除。 */
    private static final int NEXT_LINE = 0x85;

    /** 未创建任务时回应关闭的状态标识，与 AiJobService.toMoodView 的 disabled 同名。 */
    private static final String STATE_DISABLED = "disabled";

    private final MoodRecordMapper moodRecordMapper;
    private final AiJobService jobs;
    private final AiJobAdmission admission;

    public MoodRecordService(MoodRecordMapper moodRecordMapper, AiJobService jobs, AiJobAdmission admission) {
        this.moodRecordMapper = moodRecordMapper;
        this.jobs = jobs;
        this.admission = admission;
    }

    /** 先保存日记与本版本任务，事务提交后返回；不在保存接口等待模型。 */
    public MoodSaveVO add(MoodRecord request) {
        validateInput(request);
        return admission.save(() -> {
            MoodRecord source = new MoodRecord();
            source.setUserId(request.getUserId());
            source.setMoodType(request.getMoodType());
            source.setContent(request.getContent());
            source.setStatus(STATUS_ACTIVE);
            source.setContentVersion(1L);
            source.setAiReplyEnabled(!Boolean.FALSE.equals(request.getNeedAiReply()));
            if (moodRecordMapper.insert(source) != 1) throw new IllegalStateException("日记保存未成功");
            return saved(source, jobs.createForVersion(source));
        });
    }

    /** UTF-16 长度与 Python 协议一致；孤立代理字符不能进入持久化与模型链路。 */
    private static void validateInput(MoodRecord request) {
        if (request == null || request.getUserId() == null || request.getUserId() <= 0) {
            throw new IllegalArgumentException("用户身份无效");
        }
        if (!MOOD_TYPES.contains(request.getMoodType() == null ? "" : request.getMoodType())) {
            throw new IllegalArgumentException("心情类型无效");
        }
        String text = request.getContent();
        // 空内容与超长共用同一条错误，长度按 UTF-16 单位计算而不是字符数。
        if (isBlankContent(text) || text.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("心情内容不能为空且不能超过500个UTF-16单位");
        }
        if (hasLoneSurrogate(text)) {
            throw new IllegalArgumentException("心情内容包含非法Unicode字符");
        }
    }

    /** 与 Python str.strip 一致：Unicode 空白、空格类字符及 NEXT LINE 都视为空白。 */
    private static boolean isBlankContent(String text) {
        return text == null || text.codePoints()
                .allMatch(unit -> Character.isWhitespace(unit) || Character.isSpaceChar(unit) || unit == NEXT_LINE);
    }

    /** 孤立的高位或低位代理字符无法组成合法码点，持久化前必须拒绝。 */
    private static boolean hasLoneSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char unit = text.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    return true;
                }
            } else if (Character.isLowSurrogate(unit)) {
                return true;
            }
        }
        return false;
    }

    /** 保存结果的公共字段：记录身份、版本与开关。 */
    private static MoodSaveVO saveView(MoodRecord source) {
        MoodSaveVO result = new MoodSaveVO();
        result.setId(source.getId());
        result.setContentVersion(source.getContentVersion());
        result.setAiReplyEnabled(source.getAiReplyEnabled());
        return result;
    }

    private static MoodSaveVO saved(MoodRecord source, AiJob job) {
        MoodSaveVO result = saveView(source);
        result.setJobId(job == null ? null : job.getId());
        result.setAiReplyState(job == null ? STATE_DISABLED : job.getStatus());
        return result;
    }

    /** 未修改生成依据时沿用当前视图的回应状态与任务，不新建版本。 */
    private static MoodSaveVO savedUnchanged(MoodRecord source, MoodRecordVO current) {
        MoodSaveVO result = saveView(source);
        result.setAiReplyState(current.aiReplyState());
        result.setJobId(current.jobId());
        return result;
    }

    /** 查询当前用户的全部有效日记，按创建时间倒序。 */
    public List<MoodRecord> listByUserId(Long userId) {
        QueryWrapper<MoodRecord> query = new QueryWrapper<MoodRecord>()
                .eq("user_id", userId)
                .eq("status", STATUS_ACTIVE)
                .orderByDesc("create_time");
        return moodRecordMapper.selectList(query);
    }

    /** 来源先锁定再条件删除，任务失效失败时来源变更也会回滚。 */
    public Boolean delete(Long id, Long userId) {
        return admission.transaction(() -> {
            MoodRecord source = moodRecordMapper.selectOwnedForUpdate(id, userId);
            if (source == null || !Integer.valueOf(STATUS_ACTIVE).equals(source.getStatus())) return false;
            if (moodRecordMapper.softDeleteAtVersion(id, userId, source.getContentVersion()) != 1) {
                throw new IllegalStateException("日记删除条件已变化");
            }
            jobs.supersedeUnfinished(id, userId);
            return true;
        });
    }

    /** 按可选条件分页查询有效日记；空条件视为未传，日期按天边界匹配。 */
    public Page<MoodRecord> listByCondition(Long userId,
                                            String moodType,
                                            String startDate,
                                            String endDate,
                                            Long pageNum,
                                            Long pageSize) {
        QueryWrapper<MoodRecord> query = new QueryWrapper<MoodRecord>()
                .eq("user_id", userId)
                .eq("status", STATUS_ACTIVE);

        if (nonBlank(moodType)) {
            query.eq("mood_type", moodType);
        }

        if (nonBlank(startDate)) {
            query.ge("create_time", startDate + " 00:00:00");
        }

        if (nonBlank(endDate)) {
            query.le("create_time", endDate + " 23:59:59");
        }

        query.orderByDesc("create_time");
        return moodRecordMapper.selectPage(new Page<>(pageNum, pageSize), query);
    }

    /** 查询条件为空或仅空白时视为未传；与 Java String.trim 判空口径一致。 */
    private static boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 来源和任务在同一快照中读取，避免组合出生成提交前后的混合视图。 */
    public Page<MoodRecordVO> listViews(Long userId, String moodType, String startDate, String endDate,
                                        Long pageNum, Long pageSize) {
        return admission.transaction(() -> {
            Page<MoodRecord> page = listByCondition(userId, moodType, startDate, endDate, pageNum, pageSize);
            Page<MoodRecordVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            result.setRecords(page.getRecords().stream().map(jobs::toMoodView).toList());
            return result;
        });
    }

    public MoodRecordVO getDetailView(Long id, Long userId) {
        return admission.transaction(() -> {
            MoodRecord source = getDetail(id, userId);
            return source == null ? null : jobs.toMoodView(source);
        });
    }

    /** 读取单条日记并校验归属；不存在、已软删除或非本人时返回 null。 */
    public MoodRecord getDetail(Long id, Long userId) {
        MoodRecord moodRecord = moodRecordMapper.selectById(id);
        if (moodRecord == null) {
            return null;
        }
        if (moodRecord.getStatus() != null && moodRecord.getStatus() == 0) {
            return null;
        }
        if (!moodRecord.getUserId().equals(userId)) {
            return null;
        }
        return moodRecord;
    }

    /** 修改输入后失效旧任务；只写指定字段，不用旧实体覆盖有效状态或删除时间。 */
    public MoodSaveVO update(Long id, Long userId, MoodRecord request) {
        // 身份始终来自 Controller 的登录上下文，不采纳请求实体携带的 userId。
        MoodRecord input = new MoodRecord();
        input.setUserId(userId);
        input.setMoodType(request == null ? null : request.getMoodType());
        input.setContent(request == null ? null : request.getContent());
        validateInput(input);
        boolean enabled = !Boolean.FALSE.equals(request.getNeedAiReply());
        return admission.save(() -> {
            MoodRecord source = moodRecordMapper.selectOwnedForUpdate(id, userId);
            if (source == null || !Integer.valueOf(STATUS_ACTIVE).equals(source.getStatus())) return null;
            // 未修改生成依据时保留原回复及任务；失败重试通过独立接口发起。
            if (Objects.equals(source.getMoodType(), input.getMoodType())
                    && Objects.equals(source.getContent(), input.getContent())
                    && Boolean.valueOf(enabled).equals(source.getAiReplyEnabled())) {
                return savedUnchanged(source, jobs.toMoodView(source));
            }
            if (moodRecordMapper.updateContentAtVersion(id, userId, source.getContentVersion(),
                    input.getMoodType(), input.getContent(), enabled) != 1) {
                throw new IllegalStateException("日记修改条件已变化");
            }
            jobs.supersedeUnfinished(id, userId);
            source.setContentVersion(Math.addExact(source.getContentVersion(), 1));
            source.setAiReplyEnabled(enabled);
            return saved(source, jobs.createForVersion(source));
        });
    }

    /** 统计最近 7 天（含今天）的有效日记数量、主要心情与最近一条记录。 */
    public MoodTrendVO getRecentSevenDaysTrend(Long userId) {
        LocalDateTime startTime = LocalDate.now().minusDays(6).atStartOfDay();

        QueryWrapper<MoodRecord> query = new QueryWrapper<MoodRecord>()
                .eq("user_id", userId)
                .eq("status", STATUS_ACTIVE)
                .ge("create_time", startTime)
                .orderByDesc("create_time");

        List<MoodRecord> records = moodRecordMapper.selectList(query);

        Map<String, Long> moodCountMap = records.stream()
                .collect(Collectors.groupingBy(MoodRecord::getMoodType, Collectors.counting()));

        List<MoodTrendVO.MoodCountVO> moodCounts = moodCountMap.entrySet()
                .stream()
                .map(entry -> new MoodTrendVO.MoodCountVO(entry.getKey(), entry.getValue().intValue()))
                .sorted((first, second) -> second.getCount().compareTo(first.getCount()))
                .collect(Collectors.toList());

        MoodTrendVO trend = new MoodTrendVO();
        trend.setTotal(records.size());
        trend.setMoodCounts(moodCounts);

        if (!records.isEmpty()) {
            MoodRecord lastRecord = records.get(0);
            trend.setLastMoodType(lastRecord.getMoodType());
            trend.setLastRecordTime(lastRecord.getCreateTime());
        }

        if (!moodCounts.isEmpty()) {
            trend.setMainMoodType(moodCounts.get(0).getMoodType());
        }

        return trend;
    }
}
