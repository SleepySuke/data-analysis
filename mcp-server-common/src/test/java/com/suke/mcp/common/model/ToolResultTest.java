/**
 * @author 自然醒
 */
package com.suke.mcp.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolResultTest {
    @Test
    void ok_returnsSuccessWithData() {
        ToolResult<String> result = ToolResult.ok("hello");
        assertTrue(result.success());
        assertEquals("hello", result.data());
        assertNull(result.error());
    }

    @Test
    void fail_returnsFailureWithError() {
        ToolResult<Void> result = ToolResult.fail("something went wrong");
        assertFalse(result.success());
        assertNull(result.data());
        assertEquals("something went wrong", result.error());
    }

    @Test
    void ok_withNullData() {
        ToolResult<Object> result = ToolResult.ok(null);
        assertTrue(result.success());
        assertNull(result.data());
    }
}
