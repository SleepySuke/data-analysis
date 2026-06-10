package com.suke.handler;

import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Result exceptionHandler(BaseException baseEx){
        log.error("业务异常：{}", baseEx.getMessage());
        return Result.error(baseEx.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result validationExceptionHandler(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败：{}", message);
        return Result.error(ErrorCode.PARAMS_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public Result exceptionHandler(Exception ex) {
        log.error("未处理异常：", ex);
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }
}
