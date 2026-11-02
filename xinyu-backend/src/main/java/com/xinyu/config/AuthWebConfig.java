package com.xinyu.config;

import com.xinyu.interceptor.AuthInterceptor;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthWebConfig(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(jwtUtil, userService, false))
                .addPathPatterns("/mood/**", "/community/**", "/user/**")
                .excludePathPatterns("/user/login", "/user/register");
        registry.addInterceptor(new AuthInterceptor(jwtUtil, userService, true))
                .addPathPatterns("/admin/**");
    }
}
