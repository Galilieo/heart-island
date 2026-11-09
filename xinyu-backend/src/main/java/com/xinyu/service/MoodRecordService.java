package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.entity.AiJob;
import com.xinyu.vo.MoodSaveVO;
import com.xinyu.vo.MoodRecordVO;
import com.xinyu.entity.MoodRecord;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.vo.MoodTrendVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MoodRecordService {
    private final MoodRecordMapper moodRecordMapper;
    private final AiJobService jobs;
    private final AiJobAdmission admission;
    private static final Set<String> MOOD_TYPES = Set.of("开心", "满足", "期待", "感动", "平静", "焦虑", "难过", "生气", "委屈", "孤独", "迷茫", "疲惫", "其他");

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
            source.setStatus(1);
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
        // Python str.strip 同时视 Unicode 空格及 NEXT LINE 为白字符，不能只用 Java isBlank。
        if (text == null || text.codePoints().allMatch(unit -> Character.isWhitespace(unit)
                || Character.isSpaceChar(unit) || unit == 0x85) || text.length() > 500) {
            throw new IllegalArgumentException("心情内容不能为空且不能超过500个UTF-16单位");
        }
        for (int i = 0; i < text.length(); i++) {
            char unit = text.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    throw new IllegalArgumentException("心情内容包含非法Unicode字符");
                }
            } else if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException("心情内容包含非法Unicode字符");
            }
        }
    }

    private static MoodSaveVO saved(MoodRecord source, AiJob job) {
        MoodSaveVO result = new MoodSaveVO();
        result.setId(source.getId());
        result.setContentVersion(source.getContentVersion());
        result.setAiReplyEnabled(source.getAiReplyEnabled());
        result.setJobId(job == null ? null : job.getId());
        result.setAiReplyState(job == null ? "disabled" : job.getStatus());
        return result;
    }

    public List<MoodRecord> listByUserId(Long userId) {
        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("status", 1);
        queryWrapper.orderByDesc("create_time");

        return moodRecordMapper.selectList(queryWrapper);
    }

    /** 来源先锁定再条件删除，任务失效失败时来源变更也会回滚。 */
    public Boolean delete(Long id, Long userId) {
        return admission.transaction(() -> {
            MoodRecord source = moodRecordMapper.selectOwnedForUpdate(id, userId);
            if (source == null || !Integer.valueOf(1).equals(source.getStatus())) return false;
            if (moodRecordMapper.softDeleteAtVersion(id, userId, source.getContentVersion()) != 1) {
                throw new IllegalStateException("日记删除条件已变化");
            }
            jobs.supersedeUnfinished(id, userId);
            return true;
        });
    }

    public Page<MoodRecord> listByCondition(Long userId,
                                            String moodType,
                                            String startDate,
                                            String endDate,
                                            Long pageNum,
                                            Long pageSize) {
        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("status", 1);

        if(moodType != null && !moodType.trim().isEmpty()) {
            queryWrapper.eq("mood_type", moodType);
        }

        if(startDate != null && !startDate.trim().isEmpty()) {
            queryWrapper.ge("create_time", startDate+" 00:00:00");
        }

        if(endDate != null && !endDate.trim().isEmpty()) {
            queryWrapper.le("create_time", endDate+" 23:59:59");
        }

        queryWrapper.orderByDesc("create_time");

        Page<MoodRecord> page = new Page<>(pageNum, pageSize);

        return moodRecordMapper.selectPage(page,queryWrapper);
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

    public MoodRecord getDetail(Long id,Long userId){
        MoodRecord moodRecord =moodRecordMapper.selectById(id);

        if(moodRecord==null){
            return null;
        }

        if (moodRecord.getStatus() != null && moodRecord.getStatus() == 0) {
            return null;
        }

        if(!moodRecord.getUserId().equals(userId)){
            return  null;
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
            if (source == null || !Integer.valueOf(1).equals(source.getStatus())) return null;
            // 未修改生成依据时保留原回复及任务；失败重试通过独立接口发起。
            if (java.util.Objects.equals(source.getMoodType(), input.getMoodType())
                    && java.util.Objects.equals(source.getContent(), input.getContent())
                    && Boolean.valueOf(enabled).equals(source.getAiReplyEnabled())) {
                MoodRecordVO current = jobs.toMoodView(source);
                MoodSaveVO unchanged = new MoodSaveVO();
                unchanged.setId(source.getId());
                unchanged.setContentVersion(source.getContentVersion());
                unchanged.setAiReplyEnabled(source.getAiReplyEnabled());
                unchanged.setAiReplyState(current.aiReplyState());
                unchanged.setJobId(current.jobId());
                return unchanged;
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

    public MoodTrendVO getRecentSevenDaysTrend(Long userId) {
        LocalDateTime startTime = LocalDate.now().minusDays(6).atStartOfDay();

        QueryWrapper<MoodRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("status", 1);
        queryWrapper.ge("create_time", startTime);
        queryWrapper.orderByDesc("create_time");

        List<MoodRecord> records = moodRecordMapper.selectList(queryWrapper);

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
