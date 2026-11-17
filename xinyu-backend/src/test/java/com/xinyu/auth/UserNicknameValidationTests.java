package com.xinyu.auth;

import com.xinyu.common.Result;
import com.xinyu.controller.UserController;
import com.xinyu.dto.UserProfileUpdateDTO;
import com.xinyu.entity.User;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import com.xinyu.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 昵称选填：注册与修改资料都允许留空，填写时不超过 user.nickname 列宽 50。
 * 控制器直接实例化，UserService 与 JwtUtil 用替身，只验证入口校验。
 */
class UserNicknameValidationTests {

    private static final String TOKEN = "member-token";
    private static final long USER_ID = 11L;
    private static final String NICKNAME_51 = "昵".repeat(51);

    private UserController controller;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        JwtUtil jwtUtil = mock(JwtUtil.class);
        when(jwtUtil.getUserId(TOKEN)).thenReturn(USER_ID);

        controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "jwtUtil", jwtUtil);
    }

    @Test
    void registerWithoutNicknameIsAccepted() {
        User request = registration(null);
        when(userService.register(request)).thenReturn(true);

        Result<Boolean> result = controller.register(request);

        assertThat(result.getCode()).isEqualTo(200);
        verify(userService).register(request);
    }

    @Test
    void registerWithBlankNicknameIsAccepted() {
        User request = registration("   ");
        when(userService.register(request)).thenReturn(true);

        Result<Boolean> result = controller.register(request);

        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    void registerWithNicknameOver50IsRejectedBeforeService() {
        Result<Boolean> result = controller.register(registration(NICKNAME_51));

        assertThat(result.getMessage()).isEqualTo("昵称不能超过50个字");
        verifyNoInteractions(userService);
    }

    @Test
    void updateProfileWithBlankNicknameIsAccepted() {
        UserProfileUpdateDTO request = profile("  ");
        when(userService.updateProfile(USER_ID, request)).thenReturn(storedUser());

        Result<UserVO> result = controller.updateProfile(request, TOKEN);

        assertThat(result.getCode()).isEqualTo(200);
        verify(userService).updateProfile(USER_ID, request);
    }

    @Test
    void updateProfileWithoutNicknameFieldIsAccepted() {
        UserProfileUpdateDTO request = profile(null);
        when(userService.updateProfile(any(), any())).thenReturn(storedUser());

        Result<UserVO> result = controller.updateProfile(request, TOKEN);

        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    void updateProfileWithNicknameOver50IsRejectedBeforeService() {
        Result<UserVO> result = controller.updateProfile(profile(NICKNAME_51), TOKEN);

        assertThat(result.getMessage()).isEqualTo("昵称不能超过50个字");
        verifyNoInteractions(userService);
    }

    private static User registration(String nickname) {
        User user = new User();
        user.setUsername("island");
        user.setPassword("secret123");
        user.setNickname(nickname);
        return user;
    }

    private static UserProfileUpdateDTO profile(String nickname) {
        UserProfileUpdateDTO dto = new UserProfileUpdateDTO();
        dto.setNickname(nickname);
        return dto;
    }

    private static User storedUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("island");
        return user;
    }
}
