package com.xinyu.vo;

public class LoginVO {

    private UserVO user;
    private String token;

    public LoginVO() {
    }

    public LoginVO(UserVO user, String token) {
        this.user = user;
        this.token = token;
    }

    public UserVO getUser() {
        return user;
    }

    public void setUser(UserVO user) {
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}