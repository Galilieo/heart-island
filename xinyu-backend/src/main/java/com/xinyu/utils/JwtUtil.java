package com.xinyu.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    // 密钥：实际项目中不要写死在代码里，后面可以放到配置文件
    private static final String SECRET = "xinyu-secret-key";

    // 过期时间：24小时
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;

    /**
     * 生成 token
     */
    public String generateToken(Long userId, String username) {
        Date expireDate = new Date(System.currentTimeMillis() + EXPIRE_TIME);

        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(expireDate)
                .sign(Algorithm.HMAC256(SECRET));
    }

    /**
     * 解析 token
     */
    public DecodedJWT parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(SECRET))
                .build()
                .verify(token);
    }

    /**
     * 从 token 中获取 userId
     */
    public Long getUserId(String token) {
        DecodedJWT decodedJWT = parseToken(token);
        return decodedJWT.getClaim("userId").asLong();
    }

    /**
     * 从 token 中获取 username
     */
    public String getUsername(String token) {
        DecodedJWT decodedJWT = parseToken(token);
        return decodedJWT.getClaim("username").asString();
    }
}