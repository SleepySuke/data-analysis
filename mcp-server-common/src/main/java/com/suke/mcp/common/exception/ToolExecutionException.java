/**
 * @author 自然醒
 */
package com.suke.mcp.common.exception;

public class ToolExecutionException extends RuntimeException {
    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
