package com.suke.agent.skill;

import com.suke.agent.skill.model.SkillDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SkillDocumentLoaderTest {

    private final SkillDocumentLoader loader = new SkillDocumentLoader();

    @Test
    void parseSkillDocument_withValidFrontmatter() throws Exception {
        String content = """
                ---
                skillName: sales_analysis
                description: 销售趋势分析
                agentName: data_analyst
                allowedTools:
                  - analyzeCsv
                  - calculateStatistics
                ---

                # 销售趋势分析

                ## 目标
                分析销售数据的趋势变化。

                ## 步骤
                使用 `analyzeCsv` 工具加载数据。
                """;

        Resource resource = new ByteArrayResource(content.getBytes());
        SkillDefinition skill = loader.parseSkillDocument(resource);

        assertNotNull(skill);
        assertEquals("sales_analysis", skill.getSkillName());
        assertEquals("销售趋势分析", skill.getDescription());
        assertEquals("data_analyst", skill.getAgentName());
        assertEquals("SYSTEM", skill.getOwnerType());
        assertEquals(0, skill.getUsageCount());
        assertFalse(skill.getIsPublic());
        assertTrue(skill.getPromptTemplate().startsWith("# 销售趋势分析"));
        assertTrue(skill.getPromptTemplate().contains("analyzeCsv"));
        assertTrue(skill.getAllowedTools().contains("analyzeCsv"));
        assertTrue(skill.getAllowedTools().contains("calculateStatistics"));
        assertNull(skill.getExtension(), "No extension fields → null");
    }

    @Test
    void parseSkillDocument_withoutFrontmatter() throws Exception {
        String content = "# Just a regular markdown\nNo frontmatter here.";
        Resource resource = new ByteArrayResource(content.getBytes());

        SkillDefinition skill = loader.parseSkillDocument(resource);
        assertNull(skill);
    }

    @Test
    void parseSkillDocument_emptyFile() throws Exception {
        Resource resource = new ByteArrayResource("".getBytes());
        SkillDefinition skill = loader.parseSkillDocument(resource);
        assertNull(skill);
    }

    @Test
    void parseSkillDocument_missingRequiredFields() throws Exception {
        String content = """
                ---
                skillName: test_skill
                description: Test
                ---

                # Test
                """;

        Resource resource = new ByteArrayResource(content.getBytes());
        SkillDefinition skill = loader.parseSkillDocument(resource);
        assertNull(skill);
    }

    @Test
    void parseSkillDocument_frontmatter_noCloseDelimiter() throws Exception {
        String content = "---\nskillName: test\nNo closing delimiter.";
        Resource resource = new ByteArrayResource(content.getBytes());

        SkillDefinition skill = loader.parseSkillDocument(resource);
        assertNull(skill);
    }

    @Test
    void loadSkillDocuments_loadsAll13FromClasspath() {
        List<SkillDefinition> skills = loader.loadSkillDocuments();

        assertFalse(skills.isEmpty(), "Should load skill documents from classpath");
        assertEquals(13, skills.size(), "Should load all 13 skill documents");

        assertTrue(skills.stream().anyMatch(s -> "data_analyst".equals(s.getAgentName())));
        assertTrue(skills.stream().anyMatch(s -> "web_scraper".equals(s.getAgentName())));
        assertTrue(skills.stream().anyMatch(s -> "sql_analyst".equals(s.getAgentName())));
        assertTrue(skills.stream().anyMatch(s -> "data_cleaner".equals(s.getAgentName())));
    }

    @Test
    void loadSkillDocuments_allSkillsHaveRichTemplates() {
        List<SkillDefinition> skills = loader.loadSkillDocuments();

        for (SkillDefinition skill : skills) {
            assertTrue(skill.getPromptTemplate().length() > 200,
                    "Skill " + skill.getSkillName() + " template should be > 200 chars, got: "
                            + skill.getPromptTemplate().length());
            assertTrue(skill.getPromptTemplate().contains("步骤") || skill.getPromptTemplate().contains("执行步骤"),
                    "Skill " + skill.getSkillName() + " should contain execution steps");
        }
    }

    @Test
    void loadSkillDocuments_dataAnalystSkillsUseActualToolNames() {
        List<SkillDefinition> skills = loader.loadSkillDocuments().stream()
                .filter(s -> "data_analyst".equals(s.getAgentName()))
                .toList();

        for (SkillDefinition skill : skills) {
            String tools = skill.getAllowedTools();
            assertTrue(tools.contains("analyzeCsv") || tools.contains("calculateStatistics")
                            || tools.contains("generateChart") || tools.contains("searchKnowledge"),
                    "Skill " + skill.getSkillName() + " should reference actual tool names");
        }
    }

    @Test
    void loadSkillDocuments_allSkillsHaveRequiredFields() {
        List<SkillDefinition> skills = loader.loadSkillDocuments();

        for (SkillDefinition skill : skills) {
            assertNotNull(skill.getSkillName(), "skillName should not be null");
            assertNotNull(skill.getDescription(), "description should not be null");
            assertNotNull(skill.getAgentName(), "agentName should not be null");
            assertNotNull(skill.getPromptTemplate(), "promptTemplate should not be null");
            assertNotNull(skill.getAllowedTools(), "allowedTools should not be null");
            assertEquals("SYSTEM", skill.getOwnerType());
            assertEquals(0, skill.getUsageCount());
        }
    }

    @Test
    void parseSkillDocument_extensionFieldsCollected() throws Exception {
        String content = """
                ---
                skillName: web_extract
                description: 网页提取
                agentName: web_scraper
                allowedTools:
                  - executeScript
                scripts:
                  - web_scraper/scripts/web_scraper.py
                tags:
                  - scraping
                  - web
                inputFormat: url
                version: "1.0"
                ---

                # Web Extract
                """;

        Resource resource = new ByteArrayResource(content.getBytes());
        SkillDefinition skill = loader.parseSkillDocument(resource);

        assertNotNull(skill);
        assertNotNull(skill.getExtension());
        assertTrue(skill.getExtension().contains("scripts"));
        assertTrue(skill.getExtension().contains("web_scraper.py"));
        assertTrue(skill.getExtension().contains("tags"));
        assertTrue(skill.getExtension().contains("scraping"));
        assertTrue(skill.getExtension().contains("inputFormat"));
        assertTrue(skill.getExtension().contains("url"));
        assertTrue(skill.getExtension().contains("version"));
    }

    @Test
    void loadSkillDocuments_skillsWithScriptsHaveExtension() {
        List<SkillDefinition> skills = loader.loadSkillDocuments();

        // web_scraper and data_cleaner skills should have scripts in extension
        long withScripts = skills.stream()
                .filter(s -> s.getExtension() != null && s.getExtension().contains("scripts"))
                .count();
        assertTrue(withScripts > 0, "Some skills should have scripts in extension");
    }
}
