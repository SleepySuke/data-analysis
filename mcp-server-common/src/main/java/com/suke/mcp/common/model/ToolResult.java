/**
 * @author 自然醒
 */
package com.suke.mcp.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult<T>(boolean success, T data, String error) {
    public static <T> ToolResult<T> ok(T data) {
        return new ToolResult<>(true, data, null);
    }

    public static <T> ToolResult<T> fail(String error) {
        return new ToolResult<>(false, null, error);
    }
}
