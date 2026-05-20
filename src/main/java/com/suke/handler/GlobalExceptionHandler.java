package com.suke.handler;

import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Result exceptionHandler(BaseException baseEx){
        log.error("业务异常：{}", baseEx.getMessage());
        return Result.error(baseEx.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception ex) {
        log.error("未处理异常：", ex);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
