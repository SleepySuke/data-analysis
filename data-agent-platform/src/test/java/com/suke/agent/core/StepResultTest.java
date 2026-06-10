/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.core.models.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StepResultTest {

    @Test
    void sequentialStepResult() {
        StepResult result = StepResult.sequential("分析完成", "success", 1500L);
        assertEquals("分析完成", result.getOutput());
        assertEquals("success", result.getStatus());
        assertEquals(1500L, result.getDurationMs());
        assertNull(result.getOutputs());
        assertFalse(result.isPartialFailure());
    }

    @Test
    void parallelStepResultWithAllSuccess() {
        Map<String, StepResult.AgentOutput> outputs = Map.of(
                "web_scraper", new StepResult.AgentOutput("抓取数据", "success", 800L, 200),
                "sql_analyst", new StepResult.AgentOutput("查询结果", "success", 600L, 150)
        );
        StepResult result = StepResult.parallel("合并后的分析报告", "success", 850L, outputs);
        assertEquals("合并后的分析报告", result.getOutput());
        assertTrue(result.isParallel());
        assertEquals(2, result.getOutputs().size());
        assertFalse(result.isPartialFailure());
        assertEquals(350, result.getTotalTokens());
    }

    @Test
    void parallelStepResultWithPartialFailure() {
        Map<String, StepResult.AgentOutput> outputs = Map.of(
                "web_scraper", new StepResult.AgentOutput("抓取数据", "success", 800L, 200),
                "sql_analyst", new StepResult.AgentOutput("查询失败", "failed", 600L, 0)
        );
        StepResult result = StepResult.parallel("部分合并报告", "partial", 850L, outputs);
        assertTrue(result.isPartialFailure());
        assertEquals("partial", result.getStatus());
    }

    @Test
    void failedStepResult() {
        StepResult result = StepResult.failed("执行异常");
        assertEquals("执行异常", result.getOutput());
        assertEquals("failed", result.getStatus());
        assertFalse(result.isPartialFailure());
        assertEquals(0, result.getTotalTokens());
    }

    @Test
    void isPartialFailureReturnsFalseWhenParallelWithNullOutputs() {
        StepResult result = StepResult.builder()
                .output("test").status("success").parallel(true)
                .outputs(null).build();
        assertFalse(result.isPartialFailure());
    }

    @Test
    void getTotalTokensReturnsZeroForSequential() {
        StepResult result = StepResult.sequential("done", "success", 100L);
        assertEquals(0, result.getTotalTokens());
    }

    @Test
    void getTotalTokensReturnsZeroForEmptyOutputs() {
        StepResult result = StepResult.builder()
                .output("test").status("success").parallel(true)
                .outputs(Map.of()).build();
        assertEquals(0, result.getTotalTokens());
    }
}
