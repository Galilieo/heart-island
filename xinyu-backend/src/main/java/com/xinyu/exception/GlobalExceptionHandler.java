package com.xinyu.exception;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.xinyu.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TokenExpiredException.class)
    public Result<?> handleTokenExpired(TokenExpiredException e) {
        return Result.error("登录失效，请重新登录");
    }

    @ExceptionHandler(JWTVerificationException.class)
    public Result<?> handleJwtVerification(JWTVerificationException e) {
        return Result.error("登录失效，请重新登录");
    }
   @ExceptionHandler(Exception.class)
    public Result<String> handException(Exception e){
       e.printStackTrace();
       return Result.error("服务异常，请稍后再试");
   }
}