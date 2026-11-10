package com.xinyu.exception;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.xinyu.common.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<Result<?>> handleTokenExpired(TokenExpiredException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Result<>(401, "登录失效，请重新登录", null));
    }

    @ExceptionHandler(JWTVerificationException.class)
    public ResponseEntity<Result<?>> handleJwtVerification(JWTVerificationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new Result<>(401, "登录失效，请重新登录", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<?>> handleHttpStatus(ResponseStatusException e) {
        int code = e.getStatusCode().value();
        return ResponseEntity.status(e.getStatusCode())
                .headers(e.getHeaders())
                .body(new Result<>(code, e.getReason(), null));
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handException(Exception e) {
        e.printStackTrace();
        return Result.error("服务异常，请稍后再试");
    }
}
