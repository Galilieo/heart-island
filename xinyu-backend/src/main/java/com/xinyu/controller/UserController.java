package com.xinyu.controller;

import com.xinyu.common.Result;
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

        UserVO userVO = new UserVO(
                loginUser.getId(),
                loginUser.getUsername(),
                loginUser.getNickname()
        );

        String token= jwtUtil.generateToken(
                loginUser.getId(),
                loginUser.getUsername()
        );

        LoginVO loginVO =new LoginVO(userVO,token);

        return Result.success("登录成功", loginVO);
    }

    @GetMapping("/test/{id}")
    public Result<UserVO> test(@PathVariable Long id) {
        User user = userService.getById(id);

        if (user == null) {
            return Result.error("用户不存在");
        }

        UserVO userVO = new UserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname()
        );

        return Result.success("查询成功", userVO);
    }



}