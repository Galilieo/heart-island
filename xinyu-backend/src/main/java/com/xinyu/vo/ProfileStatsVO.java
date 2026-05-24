package com.xinyu.vo;

/**
 * 个人资料页顶部数据小条的返回结构。
 * 4 个数字：心情记录数、发帖数、收藏数、加入天数。
 */
public class ProfileStatsVO {

    private long moodCount;
    private long postCount;
    private long favoriteCount;
    private long joinDays;

    public ProfileStatsVO() {
    }

    public ProfileStatsVO(long moodCount, long postCount, long favoriteCount, long joinDays) {
        this.moodCount = moodCount;
        this.postCount = postCount;
        this.favoriteCount = favoriteCount;
        this.joinDays = joinDays;
    }

    public long getMoodCount() {
        return moodCount;
    }

    public void setMoodCount(long moodCount) {
        this.moodCount = moodCount;
    }

    public long getPostCount() {
        return postCount;
    }

    public void setPostCount(long postCount) {
        this.postCount = postCount;
    }

    public long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public long getJoinDays() {
        return joinDays;
    }

    public void setJoinDays(long joinDays) {
        this.joinDays = joinDays;
    }
}
