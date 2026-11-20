package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.common.CommunityReportConstants;
import com.xinyu.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {

    /** 在帖子变更、举报裁决和任务完成时先锁定来源，保持来源行在任务行之前。 */
    @Select("SELECT id, user_id, topic_id, mood_type, content, ai_reply, ai_result_kind, content_version, anonymous_name, "
            + "reply_count, like_count, status, hide_reason, create_time, update_time "
            + "FROM community_post WHERE id = #{id} FOR UPDATE")
    CommunityPost selectByIdForUpdate(@Param("id") Long id);

    /** 只在帖子仍公开、归属和来源版本都匹配时写入本次回复。 */
    @Update("""
        UPDATE community_post SET ai_reply=#{reply}, ai_result_kind=#{resultKind}
        WHERE id=#{postId} AND user_id=#{userId} AND content_version=#{version}
          AND status=
        """ + CommunityReportConstants.STATUS_VISIBLE)
    int writeAiReplyAtVisibleVersion(@Param("postId") long postId, @Param("userId") long userId,
                                     @Param("version") long version, @Param("reply") String reply,
                                     @Param("resultKind") String resultKind);

    /** 只调整点赞计数，避免旧实体快照覆盖并发编辑、回复或来源版本；delta由服务传入±1。 */
    @Update("UPDATE community_post SET like_count=GREATEST(0, like_count+#{delta}) WHERE id=#{id}")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /** 只调整评论计数，不回写任何帖子内容或状态；delta由服务传入±1。 */
    @Update("UPDATE community_post SET reply_count=GREATEST(0, reply_count+#{delta}) WHERE id=#{id}")
    int adjustReplyCount(@Param("id") Long id, @Param("delta") int delta);
}
