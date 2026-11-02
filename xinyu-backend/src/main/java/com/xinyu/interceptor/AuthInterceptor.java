package com.xinyu.interceptor;

import com.xinyu.entity.User;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/** 统一 HTTP 登录/权限错误；Controller/Service 继续负责资源归属等业务权限。 */
public class AuthInterceptor implements HandlerInterceptor {
    private static final Set<String> PUBLIC_READS = Set.of(
            "/community/post/list", "/community/post/detail", "/community/reply/list");

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final boolean adminOnly;

    public AuthInterceptor(JwtUtil jwtUtil, UserService userService, boolean adminOnly) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.adminOnly = adminOnly;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || "OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("token");
        if (token == null || token.isBlank()) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            // 保留原有匿名社区读取；mine=true 仍然需要用户身份。
            boolean ownPosts = "/community/post/list".equals(path)
                    && Boolean.TRUE.equals(DefaultConversionService.getSharedInstance()
                            .convert(request.getParameter("mine"), Boolean.class));
            boolean publicRead = !adminOnly && "GET".equals(request.getMethod())
                    && PUBLIC_READS.contains(path) && !ownPosts;
            if (publicRead) return true;
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录失效，请重新登录");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录失效，请重新登录");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已被禁用");
        }
        if (adminOnly && !"ADMIN".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "权限不足");
        }
        return true;
    }
}
