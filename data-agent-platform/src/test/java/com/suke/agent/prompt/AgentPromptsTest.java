package com.suke.agent.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentPromptsTest {

    @Test
    void allPromptsNotEmpty() {
        assertFalse(AgentPrompts.DATA_ANALYST.isBlank());
        assertTrue(AgentPrompts.DATA_ANALYST.length() > 50);

        assertFalse(AgentPrompts.WEB_SCRAPER.isBlank());
        assertTrue(AgentPrompts.WEB_SCRAPER.length() > 50);

        assertFalse(AgentPrompts.SQL_ANALYST.isBlank());
        assertTrue(AgentPrompts.SQL_ANALYST.length() > 50);

        assertFalse(AgentPrompts.DATA_CLEANER.isBlank());
        assertTrue(AgentPrompts.DATA_CLEANER.length() > 50);

        assertFalse(AgentPrompts.SUPERVISOR.isBlank());
        assertTrue(AgentPrompts.SUPERVISOR.length() > 50);
    }

    @Test
    void eachPromptContainsRoleDescription() {
        assertTrue(AgentPrompts.DATA_ANALYST.contains("数据分析师"));
        assertTrue(AgentPrompts.WEB_SCRAPER.contains("网页"));
        assertTrue(AgentPrompts.SQL_ANALYST.contains("SQL"));
        assertTrue(AgentPrompts.DATA_CLEANER.contains("数据清洗"));
        assertTrue(AgentPrompts.SUPERVISOR.contains("调度"));
    }

    @Test
    void promptsContainToolDescriptions() {
        assertTrue(AgentPrompts.DATA_ANALYST.contains("csv_analysis"));
        assertTrue(AgentPrompts.DATA_ANALYST.contains("chart_generation"));
        assertTrue(AgentPrompts.WEB_SCRAPER.contains("url_fetch"));
        assertTrue(AgentPrompts.SQL_ANALYST.contains("sql_execution"));
        assertTrue(AgentPrompts.DATA_CLEANER.contains("data_profiling"));
    }
}
