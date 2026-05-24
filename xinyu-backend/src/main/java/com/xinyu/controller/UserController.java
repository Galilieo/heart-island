package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.dto.UserPasswordUpdateDTO;
import com.xinyu.dto.UserProfileUpdateDTO;
import com.xinyu.entity.User;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.LoginVO;
import com.xinyu.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public Result<Boolean> register(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return Result.error("密码不能为空");
        }

        if (user.getNickname() == null || user.getNickname().trim().isEmpty()) {
            return Result.error("昵称不能为空");
        }

        Boolean success = userService.register(user);

        if (!success) {
            return Result.error("用户名已存在");
        }

        return Result.success("注册成功", true);
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody User user) {
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }

        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return Result.error("密码不能为空");
        }

        User loginUser = userService.login(user);

        if (loginUser == null) {
            return Result.error("账号或密码错误");
        }

        if (Integer.valueOf(0).equals(loginUser.getStatus())) {
            return Result.error("账号已被禁用");
        }

        UserVO userVO = UserVO.fromUser(loginUser);

        String token= jwtUtil.generateToken(
                loginUser.getId(),
                loginUser.getUsername()
        );

        LoginVO loginVO =new LoginVO(userVO,token);

        return Result.success("登录成功", loginVO);
    }

    @PostMapping("/update-profile")
    public Result<UserVO> updateProfile(@RequestBody UserProfileUpdateDTO dto,
                                        @RequestHeader("token") String token) {
        if (dto.getNickname() == null || dto.getNickname().trim().isEmpty()) {
            return Result.error("昵称不能为空");
        }

        if (dto.getNickname().trim().length() > 50) {
            return Result.error("昵称不能超过50个字");
        }

        if (dto.getGender() != null && dto.getGender().length() > 20) {
            return Result.error("性别字段格式异常");
        }

        if (dto.getBio() != null && dto.getBio().length() > 300) {
            return Result.error("个性签名不能超过300个字");
        }

        if (dto.getCity() != null && dto.getCity().length() > 50) {
            return Result.error("城市不能超过50个字");
        }

        Long userId = jwtUtil.getUserId(token);
        User user = userService.updateProfile(userId, dto);

        if (user == null) {
            return Result.error("用户不存在");
        }

        return Result.success("修改成功", UserVO.fromUser(user));
    }

    @GetMapping("/profile-stats")
    public Result<com.xinyu.vo.ProfileStatsVO> profileStats(@RequestHeader("token") String token) {
        Long userId = jwtUtil.getUserId(token);
        com.xinyu.vo.ProfileStatsVO stats = userService.getProfileStats(userId);

        if (stats == null) {
            return Result.error("用户不存在");
        }

        return Result.success(stats);
    }

    @PostMapping("/update-password")
    public Result<Boolean> updatePassword(@RequestBody UserPasswordUpdateDTO dto,
                                          @RequestHeader("token") String token) {
        if (dto.getOldPassword() == null || dto.getOldPassword().trim().isEmpty()) {
            return Result.error("请输入原密码");
        }

        if (dto.getNewPassword() == null || dto.getNewPassword().trim().isEmpty()) {
            return Result.error("请输入新密码");
        }

        if (dto.getNewPassword().length() < 6) {
            return Result.error("新密码不能少于6位");
        }

        Long userId = jwtUtil.getUserId(token);
        String error = userService.updatePassword(userId, dto.getOldPassword(), dto.getNewPassword());

        if (error != null) {
            return Result.error(error);
        }

        return Result.success("密码修改成功", true);
    }

    @GetMapping("/test/{id}")
    public Result<UserVO> test(@PathVariable Long id) {
        User user = userService.getById(id);

        if (user == null) {
            return Result.error("用户不存在");
        }

        return Result.success("查询成功", UserVO.fromUser(user));
    }



}
