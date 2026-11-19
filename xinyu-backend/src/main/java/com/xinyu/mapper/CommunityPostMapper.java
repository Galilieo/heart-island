package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {

    /** 对单篇帖子加行级写锁，用于串联后台举报裁决，避免多条举报索引状态并发修改产生间隙死锁。 */
    @Select("SELECT id, user_id, topic_id, mood_type, content, ai_reply, ai_result_kind, ai_generation_id, anonymous_name, "
            + "reply_count, like_count, status, hide_reason, create_time, update_time "
            + "FROM community_post WHERE id = #{id} FOR UPDATE")
    CommunityPost selectByIdForUpdate(@Param("id") Long id);

    /** 只调整点赞计数，避免旧实体快照覆盖并发编辑、回复或生成标识；delta由服务传入±1。 */
    @Update("UPDATE community_post SET like_count=GREATEST(0, like_count+#{delta}) WHERE id=#{id}")
    int adjustLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /** 只调整评论计数，不回写任何帖子内容或状态；delta由服务传入±1。 */
    @Update("UPDATE community_post SET reply_count=GREATEST(0, reply_count+#{delta}) WHERE id=#{id}")
    int adjustReplyCount(@Param("id") Long id, @Param("delta") int delta);
}
