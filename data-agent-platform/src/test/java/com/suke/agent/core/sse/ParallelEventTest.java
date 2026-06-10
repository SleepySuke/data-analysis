/**
 * @author 自然醒
 */
package com.suke.agent.core.sse;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParallelEventTest {

    @Test
    void parallelStepStartEvent() {
        ParallelStepStartEvent event = new ParallelStepStartEvent(
                1, "parallel", List.of("web_scraper", "sql_analyst"), "获取多源数据");
        assertEquals("step_start", event.type());
        assertEquals(1, event.stepIndex());
        assertEquals("parallel", event.mode());
        assertEquals(2, event.agents().size());
        assertEquals("获取多源数据", event.input());
    }

    @Test
    void parallelStepResultEvent() {
        Map<String, String> outputs = Map.of("web_scraper", "数据抓取", "sql_analyst", "查询结果");
        ParallelStepResultEvent event = new ParallelStepResultEvent(
                1, "pass", 1200L, outputs, "合并后的分析报告");
        assertEquals("step_result", event.type());
        assertEquals(1, event.stepIndex());
        assertEquals("pass", event.status());
        assertEquals(2, event.outputs().size());
        assertEquals("合并后的分析报告", event.merged());
    }
}
