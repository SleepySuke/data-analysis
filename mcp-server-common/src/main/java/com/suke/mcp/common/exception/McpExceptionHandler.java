/**
 * @author 自然醒
 */
package com.suke.mcp.common.exception;

import com.suke.mcp.common.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class McpExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(McpExceptionHandler.class);

    @ExceptionHandler(ToolExecutionException.class)
    public ToolResult<Void> handleToolExecution(ToolExecutionException e) {
        log.warn("Tool '{}' execution failed: {}", e.getToolName(), e.getMessage());
        return ToolResult.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ToolResult<Void> handleGeneral(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        return ToolResult.fail("Internal server error");
    }
}
