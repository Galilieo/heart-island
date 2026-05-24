package com.xinyu.vo;

import com.xinyu.entity.User;

import java.time.LocalDate;

public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String gender;
    private LocalDate birthday;
    private String bio;
    private String city;
    private String role;
    private Integer status;

    public UserVO() {
    }

    public UserVO(Long id, String username, String nickname) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
    }

    public UserVO(Long id, String username, String nickname, String role, Integer status) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.status = status;
    }

    /**
     * 从 User 实体构造完整 UserVO（用于登录、修改资料后返回展示字段）。
     * 不包含 password。
     */
    public static UserVO fromUser(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setGender(user.getGender());
        vo.setBirthday(user.getBirthday());
        vo.setBio(user.getBio());
        vo.setCity(user.getCity());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
