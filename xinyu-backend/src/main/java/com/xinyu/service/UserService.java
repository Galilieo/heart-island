package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Boolean register(User user) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());

        User existUser = userMapper.selectOne(queryWrapper);

        if (existUser != null) {
            return false;
        }

        // 注册时，把明文密码加密后再保存
        String encodePassword =passwordEncoder.encode(user.getPassword());
        user.setPassword(encodePassword);

        int rows = userMapper.insert(user);
        return rows > 0;
    }

    public User login(User user) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());

        User dbUser = userMapper.selectOne(queryWrapper);

        if (dbUser == null) {
            return null;
        }

        // 登录时，用明文密码和数据库中的加密密码进行匹配
        boolean matches = passwordEncoder.matches(user.getPassword(), dbUser.getPassword());

        if (!matches) {
            return null;
        }

        return dbUser;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);
    }
}