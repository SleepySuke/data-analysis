package com.suke.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkingMemoryTest {

    private WorkingMemory workingMemory;
    private RMapCache<String, String> mapCache;

    @BeforeEach
    void setUp() {
        RedissonClient redisson = mock(RedissonClient.class);
        mapCache = mock(RMapCache.class);
        when(redisson.<String, String>getMapCache(anyString())).thenReturn(mapCache);
        workingMemory = new WorkingMemory(redisson);
    }

    @Nested
    @DisplayName("put / get")
    class PutGet {

        @Test
        @DisplayName("put 存储键值对")
        void put_storesKeyValue() {
            when(mapCache.put(anyString(), anyString(), anyLong(), any())).thenReturn(null);

            workingMemory.put("sess-1", "uploaded_file", "sales_2024.xlsx");

            verify(mapCache).put(eq("uploaded_file"), eq("sales_2024.xlsx"), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("get 返回存储的值")
        void get_returnsValue() {
            when(mapCache.get("uploaded_file")).thenReturn("sales_2024.xlsx");

            String result = workingMemory.get("sess-1", "uploaded_file");

            assertEquals("sales_2024.xlsx", result);
        }

        @Test
        @DisplayName("get 不存在的 key 返回 null")
        void get_missingKey_returnsNull() {
            when(mapCache.get("nonexistent")).thenReturn(null);

            assertNull(workingMemory.get("sess-1", "nonexistent"));
        }
    }

    @Nested
    @DisplayName("getAll / clear")
    class GetAllClear {

        @Test
        @DisplayName("getAll 返回所有键值对")
        void getAll_returnsAll() {
            Map<String, String> data = Map.of("file", "data.csv", "goal", "趋势分析");
            when(mapCache.readAllMap()).thenReturn(data);

            Map<String, String> result = workingMemory.getAll("sess-1");

            assertEquals(2, result.size());
            assertEquals("data.csv", result.get("file"));
        }

        @Test
        @DisplayName("clear 清除所有")
        void clear_removesAll() {
            workingMemory.clear("sess-1");
            verify(mapCache).clear();
        }
    }

    @Nested
    @DisplayName("buildContext")
    class BuildContext {

        @Test
        @DisplayName("空工作记忆返回空字符串")
        void buildContext_empty_returnsEmpty() {
            when(mapCache.readAllMap()).thenReturn(Map.of());

            String result = workingMemory.buildContext("sess-1");

            assertEquals("", result);
        }

        @Test
        @DisplayName("有数据时格式化输出")
        void buildContext_withData_formatsCorrectly() {
            when(mapCache.readAllMap()).thenReturn(Map.of(
                    "uploaded_file", "sales.xlsx",
                    "analysis_goal", "月度趋势分析"
            ));

            String result = workingMemory.buildContext("sess-1");

            assertTrue(result.contains("[工作记忆]"));
            assertTrue(result.contains("uploaded_file: sales.xlsx"));
            assertTrue(result.contains("analysis_goal: 月度趋势分析"));
        }
    }

    @Nested
    @DisplayName("null safety")
    class NullSafety {

        @Test
        void put_nullSession_noop() {
            assertDoesNotThrow(() -> workingMemory.put(null, "key", "value"));
        }

        @Test
        void get_nullSession_returnsNull() {
            assertNull(workingMemory.get(null, "key"));
        }

        @Test
        void getAll_nullSession_returnsEmpty() {
            assertTrue(workingMemory.getAll(null).isEmpty());
        }

        @Test
        void clear_nullSession_noop() {
            assertDoesNotThrow(() -> workingMemory.clear(null));
        }
    }
}
