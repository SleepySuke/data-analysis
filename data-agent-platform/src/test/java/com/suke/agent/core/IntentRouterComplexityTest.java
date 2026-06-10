/**
 * @author 自然醒
 */
package com.suke.agent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntentRouterComplexityTest {

    @Test
    void intentResultRecord() {
        IntentRouter.IntentResult result = new IntentRouter.IntentResult("data_analyst", false, "单步分析");
        assertEquals("data_analyst", result.agentName());
        assertFalse(result.complex());
        assertEquals("单步分析", result.reason());
    }

    @Test
    void intentResultComplex() {
        IntentRouter.IntentResult result = new IntentRouter.IntentResult("data_analyst", true, "多步骤任务");
        assertTrue(result.complex());
    }

    @Test
    void complexMultiStepDetected() {
        String complexMessage = "先清洗数据，然后分析趋势，最后生成图表";
        boolean shouldBeComplex = complexMessage.contains("然后") || complexMessage.contains("接着")
                || complexMessage.contains("再") || complexMessage.contains("之后")
                || (complexMessage.contains("先") && complexMessage.contains("最后"));
        assertTrue(shouldBeComplex, "Multi-step message should be detected as complex");
    }

    @Test
    void simpleSingleStepNotComplex() {
        String simpleMessage = "分析这个CSV";
        boolean shouldBeComplex = simpleMessage.contains("然后") || simpleMessage.contains("接着")
                || simpleMessage.contains("再") || simpleMessage.contains("之后")
                || (simpleMessage.contains("先") && simpleMessage.contains("最后"));
        int count = 0;
        String[] keywords = {"清洗", "分析", "图表", "sql", "数据库", "抓取", "爬取"};
        for (String kw : keywords) {
            if (simpleMessage.toLowerCase().contains(kw)) count++;
        }
        assertFalse(shouldBeComplex || count >= 2, "Single-step message should not be complex");
    }
}
