package com.suke.handler;

import com.suke.common.Result;
import com.suke.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author 自然醒
 * @version 1.0
 */
//全局异常处理
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常处理
     * @param baseEx
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException baseEx){
        log.error("异常信息：{}",baseEx.getMessage());
        return Result.error(baseEx.getMessage());
    }
}
