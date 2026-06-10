/**
 * @author 自然醒
 */
package com.suke.mcp.common.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionExceptionTest {
    @Test
    void preservesToolNameAndMessage() {
        ToolExecutionException e = new ToolExecutionException("my_tool", "failed");
        assertEquals("my_tool", e.getToolName());
        assertEquals("failed", e.getMessage());
    }

    @Test
    void preservesCause() {
        RuntimeException cause = new RuntimeException("root cause");
        ToolExecutionException e = new ToolExecutionException("tool", "msg", cause);
        assertEquals(cause, e.getCause());
    }
}
