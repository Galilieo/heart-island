package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.CommunityReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommunityReplyMapper extends BaseMapper<CommunityReply> {

    /** 对单条回复加行级写锁，用于串联后台举报裁决，避免多条举报索引状态并发修改产生间隙死锁。 */
    @Select("SELECT id, post_id, user_id, content, anonymous_name, status, hide_reason, create_time, update_time "
            + "FROM community_reply WHERE id = #{id} FOR UPDATE")
    CommunityReply selectByIdForUpdate(@Param("id") Long id);
}