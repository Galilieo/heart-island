package com.xinyu.exception;

import com.xinyu.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
   @ExceptionHandler(Exception.class)
    public Result<String> handException(Exception e){
       e.printStackTrace();
       return Result.error("服务异常，请稍后再试");
   }
}