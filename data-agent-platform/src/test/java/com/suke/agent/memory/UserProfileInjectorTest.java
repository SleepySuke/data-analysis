package com.suke.agent.memory;

import com.suke.agent.memory.model.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileInjectorTest {

    private final UserProfileInjector injector = new UserProfileInjector();

    @Nested
    @DisplayName("Agent-aware 注入")
    class AgentAware {

        @Test
        @DisplayName("data_analyst 注入所有字段")
        void dataAnalyst_injectsAllFields() {
            UserProfile profile = new UserProfile();
            profile.setUserId(1L);
            profile.setIndustry("finance");
            profile.setPreferredCharts("bar,pie");
            profile.setDetailLevel("detailed");
            profile.setReportStyle("business");

            String result = injector.inject(profile, "data_analyst", null);

            assertTrue(result.contains("[用户记忆]"));
            assertTrue(result.contains("finance"));
            assertTrue(result.contains("bar,pie"));
            assertTrue(result.contains("detailed"));
            assertTrue(result.contains("business"));
        }

        @Test
        @DisplayName("sql_analyst 只注入 industry + expertise")
        void sqlAnalyst_onlyIndustryAndExpertise() {
            UserProfile profile = new UserProfile();
            profile.setUserId(1L);
            profile.setIndustry("finance");
            profile.setPreferredCharts("bar");
            profile.setExpertise("SQL优化");

            String result = injector.inject(profile, "sql_analyst", null);

            assertTrue(result.contains("finance"));
            assertTrue(result.contains("SQL优化"));
            assertFalse(result.contains("bar"), "sql_analyst 不应注入 preferredCharts");
        }

        @Test
        @DisplayName("web_scraper 不注入任何 profile")
        void webScraper_noInjection() {
            UserProfile profile = new UserProfile();
            profile.setUserId(1L);
            profile.setIndustry("finance");

            String result = injector.inject(profile, "web_scraper", null);

            assertEquals("", result);
        }
    }

    @Test
    void injectEmptyProfile() {
        String result = injector.inject(null, null, null);
        assertEquals("", result);
    }

    @Test
    void injectWithRecentTopics() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("tech");

        String result = injector.inject(profile, "data_analyst", List.of("销售分析", "趋势预测"));

        assertTrue(result.contains("近期关注"));
        assertTrue(result.contains("销售分析"));
        assertTrue(result.contains("趋势预测"));
    }

    @Test
    void injectBackwardCompatible() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("finance");

        String result = injector.inject(profile);

        assertTrue(result.contains("finance"));
    }
}
