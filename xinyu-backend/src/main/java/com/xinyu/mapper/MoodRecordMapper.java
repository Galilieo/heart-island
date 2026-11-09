package com.xinyu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xinyu.entity.MoodRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MoodRecordMapper extends BaseMapper<MoodRecord> {

    /** 只提交本版本的回应，不递增内容版本，也不改删除时间。 */
    @Update("""
        UPDATE mood_record SET ai_reply=#{reply}, update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND user_id=#{userId} AND status=1
          AND content_version=#{expectedVersion} AND ai_reply_enabled=1
        """)
    int writeReplyAtVersion(@Param("id") Long id, @Param("userId") Long userId,
                            @Param("expectedVersion") Long expectedVersion, @Param("reply") String reply);

    /**
     * 按记录及所属用户锁定来源行，调用方须在同一事务中完成后续校验和写入。
     *
     * @return 来源记录；不存在或不属于该用户时返回 null
     */
    @Select("""
        SELECT * FROM mood_record
        WHERE id = #{id} AND user_id = #{userId}
        FOR UPDATE
        """)
    MoodRecord selectOwnedForUpdate(@Param("id") Long id,
                                   @Param("userId") Long userId);

    /**
     * 仅在来源有效且版本匹配时修改生成依据，并递增版本、清空旧回复。
     * 不更新有效状态和删除时间，避免旧数据覆盖并发删除。
     *
     * @return 符合条件并完成更新的行数
     */
    @Update("""
        UPDATE mood_record
        SET mood_type = #{moodType}, content = #{content}, ai_reply_enabled = #{enabled},
            ai_reply = NULL, content_version = content_version + 1, update_time = CURRENT_TIMESTAMP
        WHERE id = #{id} AND user_id = #{userId} AND status = 1
          AND content_version = #{expectedVersion}
        """)
    int updateContentAtVersion(@Param("id") Long id,
                               @Param("userId") Long userId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("moodType") String moodType,
                               @Param("content") String content,
                               @Param("enabled") boolean enabled);

    /**
     * 仅在来源有效且版本匹配时软删除，递增版本并关闭回应、清空回复、写入删除时间。
     * 重复删除不更新版本或删除时间。
     *
     * @return 符合条件并完成删除的行数
     */
    @Update("""
        UPDATE mood_record
        SET status = 0, content_version = content_version + 1, ai_reply_enabled = 0,
            ai_reply = NULL, deleted_at = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
        WHERE id = #{id} AND user_id = #{userId} AND status = 1
          AND content_version = #{expectedVersion}
        """)
    int softDeleteAtVersion(@Param("id") Long id,
                            @Param("userId") Long userId,
                            @Param("expectedVersion") Long expectedVersion);
}
