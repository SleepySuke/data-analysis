package com.suke.handler;

import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("#40 BaseException应返回FORBIDDEN_ERROR")
    void handleBaseException_shouldReturnForbiddenError() {
        BaseException ex = new AIDockingException("AI调用失败");
        Result<?> result = handler.exceptionHandler(ex);
        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), result.getCode());
        assertEquals("AI调用失败", result.getMessage());
    }

    @Test
    @DisplayName("#40 FailLoginException应返回对应错误码")
    void handleLoginException_shouldReturnForbiddenError() {
        FailLoginException ex = new FailLoginException("密码错误");
        Result<?> result = handler.exceptionHandler(ex);
        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), result.getCode());
        assertEquals("密码错误", result.getMessage());
    }

    @Test
    @DisplayName("#40 未处理异常（如NPE）应返回SYSTEM_ERROR兜底")
    void handleUncaughtException_shouldReturnSystemError() {
        NullPointerException ex = new NullPointerException("unexpected null");
        Result<?> result = handler.exceptionHandler(ex);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
        assertEquals("系统内部异常", result.getMessage());
    }

    @Test
    @DisplayName("#40 RuntimeException应返回SYSTEM_ERROR兜底")
    void handleRuntimeException_shouldReturnSystemError() {
        RuntimeException ex = new RuntimeException("database connection failed");
        Result<?> result = handler.exceptionHandler(ex);
        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), result.getCode());
        assertEquals("系统内部异常", result.getMessage());
    }

    @Test
    @DisplayName("#41 Result.error(ErrorCode)应返回对应错误码")
    void resultError_withErrorCode_shouldReturnCorrectCode() {
        Result<?> result = Result.error(ErrorCode.PARAMS_ERROR);
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), result.getCode());
        assertEquals(ErrorCode.PARAMS_ERROR.getMessage(), result.getMessage());
    }

    @Test
    @DisplayName("#41 Result.error(ErrorCode, message)应返回自定义消息")
    void resultError_withErrorCodeAndMessage_shouldReturnCustomMessage() {
        Result<?> result = Result.error(ErrorCode.PARAMS_ERROR, "文件大小超过限制");
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), result.getCode());
        assertEquals("文件大小超过限制", result.getMessage());
    }

    @Test
    @DisplayName("#41 Result.error(String)应保持向后兼容返回FORBIDDEN_ERROR")
    void resultError_withMessageOnly_shouldReturnForbiddenError() {
        Result<?> result = Result.error("操作失败");
        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), result.getCode());
        assertEquals("操作失败", result.getMessage());
    }
}
