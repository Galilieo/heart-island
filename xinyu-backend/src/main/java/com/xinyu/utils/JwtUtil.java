package com.xinyu.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final Algorithm algorithm;
    // HS256 部署密钥至少 32 字节；旧公开固定密钥短于此下限，同样被拒绝。
    private static final int MIN_SECRET_BYTES = 32;

    // 过期时间：24小时
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;

    /** 从部署环境读取签名密钥；错误信息不包含配置内容。 */
    public JwtUtil(@Value("${JWT_SECRET:}") String secret) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("invalid_jwt_secret");
        }
        this.algorithm = Algorithm.HMAC256(secret);
    }

    /**
     * 生成 token
     */
    public String generateToken(Long userId, String username) {
        Date expireDate = new Date(System.currentTimeMillis() + EXPIRE_TIME);

        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withExpiresAt(expireDate)
                .sign(algorithm);
    }

    /**
     * 解析 token
     */
    public DecodedJWT parseToken(String token) {
        return JWT.require(algorithm)
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
