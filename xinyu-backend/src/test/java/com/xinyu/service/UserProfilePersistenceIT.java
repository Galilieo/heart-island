package com.xinyu.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.xinyu.dto.UserProfileUpdateDTO;
import com.xinyu.entity.User;
import com.xinyu.mapper.UserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 隔离 MySQL（xinyu_test_*）真实集成测试：昵称选填后，留空的昵称与清空的资料项都以 NULL 落库。
 * 重点覆盖 MyBatis-Plus updateById 默认跳过 null 字段、导致“清空”写不进去的问题。
 */
class UserProfilePersistenceIT {

    /** 与 sql/xinyu.sql 的 user 表一致，只保留本测试读写的列。 */
    private static final String USER_DDL = """
            CREATE TABLE `user` (
              `id` BIGINT NOT NULL AUTO_INCREMENT,
              `username` VARCHAR(50) NOT NULL,
              `password` VARCHAR(100) NOT NULL,
              `nickname` VARCHAR(50) DEFAULT NULL,
              `gender` VARCHAR(20) DEFAULT NULL,
              `birthday` DATE DEFAULT NULL,
              `bio` VARCHAR(300) DEFAULT NULL,
              `city` VARCHAR(50) DEFAULT NULL,
              `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
              `status` TINYINT NOT NULL DEFAULT 1,
              `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `uk_user_username` (`username`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;

    private static JdbcTemplate jdbc;
    private static UserService userService;

    @BeforeAll
    static void setUpIsolatedDatabase() throws Exception {
        String url = System.getenv("AI_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "未配置显式的 AI_TEST_JDBC_URL 隔离测试库");
        assertThat(url).startsWith("jdbc:mysql://");
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertThat(uri.getPath()).matches("/xinyu_test_[a-zA-Z0-9_]+");
        assertThat(uri.getQuery()).isNull();
        assertThat(uri.getUserInfo()).isNull();
        assertThat(uri.getFragment()).isNull();

        var source = new DriverManagerDataSource(
                url,
                System.getenv().getOrDefault("AI_TEST_DB_USER", "root"),
                System.getenv().getOrDefault("AI_TEST_DB_PASSWORD", ""));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
                Integer.class)).isZero();
        jdbc.execute(USER_DDL);

        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(UserMapper.class);
        factory.setConfiguration(configuration);
        var sessions = new SqlSessionTemplate(factory.getObject());

        userService = new UserService();
        ReflectionTestUtils.setField(userService, "userMapper", sessions.getMapper(UserMapper.class));
        ReflectionTestUtils.setField(userService, "passwordEncoder", new BCryptPasswordEncoder());
    }

    @BeforeEach
    void clearUsers() {
        jdbc.update("DELETE FROM `user`");
    }

    @Test
    void registerWithoutNicknameStoresNull() {
        assertThat(userService.register(registration("no-nick", null))).isTrue();

        assertThat(storedNickname("no-nick")).isNull();
    }

    @Test
    void registerWithBlankNicknameStoresNull() {
        assertThat(userService.register(registration("blank-nick", "   "))).isTrue();

        assertThat(storedNickname("blank-nick")).isNull();
    }

    @Test
    void registerTrimsFilledNickname() {
        assertThat(userService.register(registration("filled-nick", "  小岛  "))).isTrue();

        assertThat(storedNickname("filled-nick")).isEqualTo("小岛");
    }

    @Test
    void clearingProfileFieldsWritesNullToDatabase() {
        long id = insertFilledProfile("cleared");
        UserProfileUpdateDTO cleared = new UserProfileUpdateDTO();
        cleared.setNickname("  ");
        cleared.setGender("");
        cleared.setBirthday(null);
        cleared.setBio(" ");
        cleared.setCity("");

        userService.updateProfile(id, cleared);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT nickname, gender, birthday, bio, city FROM `user` WHERE id = ?", id);
        assertThat(row).containsEntry("nickname", null)
                .containsEntry("gender", null)
                .containsEntry("birthday", null)
                .containsEntry("bio", null)
                .containsEntry("city", null);
    }

    @Test
    void updatingProfileKeepsTrimmedValues() {
        long id = insertFilledProfile("updated");
        UserProfileUpdateDTO changed = new UserProfileUpdateDTO();
        changed.setNickname("  新昵称 ");
        changed.setGender("FEMALE");
        changed.setBirthday(LocalDate.of(2000, 2, 29));
        changed.setBio(" 新签名 ");
        changed.setCity(" 武汉 ");

        userService.updateProfile(id, changed);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT nickname, gender, bio, city FROM `user` WHERE id = ?", id);
        assertThat(row).containsEntry("nickname", "新昵称")
                .containsEntry("gender", "FEMALE")
                .containsEntry("bio", "新签名")
                .containsEntry("city", "武汉");
    }

    private static User registration(String username, String nickname) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("secret123");
        user.setNickname(nickname);
        return user;
    }

    private static String storedNickname(String username) {
        return jdbc.queryForObject("SELECT nickname FROM `user` WHERE username = ?", String.class, username);
    }

    private static long insertFilledProfile(String username) {
        jdbc.update("INSERT INTO `user` (username, password, nickname, gender, birthday, bio, city) "
                + "VALUES (?, 'x', '旧昵称', 'MALE', '1999-01-01', '旧签名', '上海')", username);
        return jdbc.queryForObject("SELECT id FROM `user` WHERE username = ?", Long.class, username);
    }
}
