package com.xinyu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.dto.UserProfileUpdateDTO;
import com.xinyu.entity.User;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.FavoriteMapper;
import com.xinyu.mapper.MoodRecordMapper;
import com.xinyu.mapper.UserMapper;
import com.xinyu.vo.AdminUserVO;
import com.xinyu.vo.ProfileStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class UserService {

    private static final String ROLE_ADMIN = "ADMIN";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MoodRecordMapper moodRecordMapper;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

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
        user.setRole("USER");
        user.setStatus(1);

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

    public boolean isAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null
                && ROLE_ADMIN.equals(user.getRole())
                && Integer.valueOf(1).equals(user.getStatus());
    }

    public Page<AdminUserVO> pageUsers(String username, Integer status, Long pageNum, Long pageSize) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        if (username != null && !username.trim().isEmpty()) {
            queryWrapper.like("username", username.trim());
        }

        if (status != null) {
            queryWrapper.eq("status", status);
        }

        queryWrapper.orderByDesc("create_time");

        Page<User> userPage = userMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
        Page<AdminUserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(userPage.getRecords().stream()
                .map(user -> new AdminUserVO(
                        String.valueOf(user.getId()),
                        user.getUsername(),
                        user.getNickname(),
                        user.getRole(),
                        user.getStatus(),
                        user.getCreateTime(),
                        user.getUpdateTime()
                ))
                .toList());

        return voPage;
    }

    public User updateStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);

        if (user == null) {
            return null;
        }

        user.setStatus(status);
        userMapper.updateById(user);

        return user;
    }

    /**
     * 修改个人资料：昵称 + 性别 + 生日 + 签名 + 城市。
     * nickname 由 Controller 保证非空，其余字段为空字符串视为清空（写 NULL），
     * 字段为 null 也视为清空（前端固定提交全部字段）。
     */
    public User updateProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = userMapper.selectById(userId);

        if (user == null) {
            return null;
        }

        user.setNickname(dto.getNickname().trim());
        user.setGender(normalize(dto.getGender()));
        user.setBirthday(dto.getBirthday());
        user.setBio(normalize(dto.getBio()));
        user.setCity(normalize(dto.getCity()));

        userMapper.updateById(user);

        return user;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 查询当前用户的资料统计：心情数、帖子数、收藏数、加入天数。
     * 心情和帖子均按 status=1 过滤，排除软删除；收藏无状态字段，全量计数。
     */
    public ProfileStatsVO getProfileStats(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        long moodCount = moodRecordMapper.selectCount(
                new QueryWrapper<com.xinyu.entity.MoodRecord>().eq("user_id", userId).eq("status", 1));

        long postCount = communityPostMapper.selectCount(
                new QueryWrapper<com.xinyu.entity.CommunityPost>().eq("user_id", userId).eq("status", 1));

        long favoriteCount = favoriteMapper.selectCount(
                new QueryWrapper<com.xinyu.entity.Favorite>().eq("user_id", userId));

        long joinDays = 0;
        if (user.getCreateTime() != null) {
            joinDays = ChronoUnit.DAYS.between(user.getCreateTime().toLocalDate(), LocalDate.now()) + 1;
        }

        return new ProfileStatsVO(moodCount, postCount, favoriteCount, joinDays);
    }

    /**
     * 修改密码。先校验原密码，成功返回 null，失败返回错误提示文案。
     */
    public String updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);

        if (user == null) {
            return "用户不存在";
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return "原密码不正确";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        return null;
    }
}
