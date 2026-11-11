package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.dto.AiReplyResult;
import com.xinyu.entity.AiReply;
import com.xinyu.mapper.AiReplyMapper;
import com.xinyu.vo.AdminAiReplyVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 写入调用记录；全站管理员可查询已存原文和错误详情，公开接口不复用此视图。 */
@Service
public class AiReplyLogService {
    public static final String TARGET_TYPE_MOOD_RECORD = "MOOD_RECORD";
    public static final String TARGET_TYPE_POST = "POST";

    @Autowired
    private AiReplyMapper aiReplyMapper;

    public void saveMoodReply(Long userId, Long moodRecordId, AiReplyResult result) {
        save(userId, TARGET_TYPE_MOOD_RECORD, moodRecordId, result);
    }

    public void savePostReply(Long userId, Long postId, AiReplyResult result) {
        save(userId, TARGET_TYPE_POST, postId, result);
    }

    private void save(Long userId, String targetType, Long targetId, AiReplyResult result) {
        boolean success = Boolean.TRUE.equals(result.getSuccess());
        AiReply log = new AiReply();
        log.setUserId(userId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setModelName(result.getModelName());
        log.setStatus(success ? 1 : 0);
        log.setReplyContent(success ? result.getReplyContent() : null);
        log.setErrorMessage(success ? null : result.getErrorMessage());
        // 不新增prompt副本；失败无兜底正文，错误详情由调用端遮蔽凭据后传入。
        aiReplyMapper.insert(log);
    }

    /** 全站管理员可跨用户查询；可选筛选参数只收窄结果，不绑定当前管理员的用户ID。 */
    public Page<AdminAiReplyVO> pageAdminAiReplies(
            Long userId, String targetType, Integer status, String keyword, Long pageNum, Long pageSize) {
        QueryWrapper<AiReply> query = new QueryWrapper<>();
        query.select("id", "user_id", "target_type", "target_id", "prompt", "reply_content",
                "model_name", "status", "error_message", "create_time");
        query.eq(userId != null, "user_id", userId);
        if (targetType != null && !targetType.isBlank()) {
            query.eq("target_type", targetType.trim());
        }
        query.eq(status != null, "status", status);

        if (keyword != null && !keyword.isBlank()) {
            String search = keyword.trim();
            // 关键词仅作为绑定参数；管理员可搜索其有权查看的完整字段。
            query.and(fields -> fields.like("id", search)
                    .or().like("user_id", search)
                    .or().like("target_type", search)
                    .or().like("target_id", search)
                    .or().like("model_name", search)
                    .or().like("status", search)
                    .or().like("create_time", search)
                    .or().like("prompt", search)
                    .or().like("reply_content", search)
                    .or().like("error_message", search));
        }
        query.orderByDesc("create_time").orderByDesc("id");

        Page<AiReply> rows = aiReplyMapper.selectPage(new Page<>(pageNum, pageSize), query);
        Page<AdminAiReplyVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(rows.getRecords().stream().map(row -> new AdminAiReplyVO(
                row.getId(), row.getUserId(), row.getTargetType(), row.getTargetId(),
                row.getPrompt(), row.getReplyContent(), row.getModelName(),
                row.getStatus(), row.getErrorMessage(), row.getCreateTime())).toList());
        return result;
    }
}
