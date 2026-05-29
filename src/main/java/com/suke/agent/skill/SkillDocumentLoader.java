/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill文档加载器，从Markdown文件解析frontmatter和模板
 */

package com.suke.agent.skill;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.skill.model.SkillDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class SkillDocumentLoader {

    private static final String SKILL_PATTERN = "classpath:skills/**/*.md";
    private static final String FRONTMATTER_DELIMITER = "---";

    private static final Set<String> REQUIRED_FIELDS = Set.of("skillName", "description", "agentName");
    private static final Set<String> KNOWN_FIELDS = Set.of("skillName", "description", "agentName", "allowedTools");

    public List<SkillDefinition> loadSkillDocuments() {
        List<SkillDefinition> skills = new ArrayList<>();
        Resource[] resources;

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            resources = resolver.getResources(SKILL_PATTERN);
        } catch (IOException e) {
            log.error("Failed to scan skill documents: {}", e.getMessage());
            return skills;
        }

        for (Resource resource : resources) {
            try {
                SkillDefinition skill = parseSkillDocument(resource);
                if (skill != null) {
                    skills.add(skill);
                }
            } catch (Exception e) {
                log.warn("Failed to parse skill document {}: {}", getResourceName(resource), e.getMessage());
            }
        }

        log.info("Loaded {} skill documents from resources", skills.size());
        return skills;
    }

    SkillDefinition parseSkillDocument(Resource resource) throws IOException {
        String content;
        try (InputStream is = resource.getInputStream()) {
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        if (content.isBlank()) {
            log.warn("Empty skill document: {}", getResourceName(resource));
            return null;
        }

        ParsedDocument doc = parseFrontmatter(content);
        if (doc.frontmatter == null) {
            log.warn("No frontmatter found in skill document: {}", getResourceName(resource));
            return null;
        }

        String skillName = (String) doc.frontmatter.get("skillName");
        String description = (String) doc.frontmatter.get("description");
        String agentName = (String) doc.frontmatter.get("agentName");

        if (skillName == null || description == null || agentName == null) {
            log.warn("Missing required frontmatter fields (skillName/description/agentName) in: {}",
                    getResourceName(resource));
            return null;
        }

        SkillDefinition skill = new SkillDefinition();
        skill.setSkillName(skillName);
        skill.setDescription(description);
        skill.setAgentName(agentName);
        skill.setPromptTemplate(doc.body.trim());
        skill.setAllowedTools(JSON.toJSONString(doc.frontmatter.get("allowedTools")));
        skill.setExtension(buildExtension(doc.frontmatter));
        skill.setOwnerType("SYSTEM");
        skill.setUsageCount(0);
        skill.setIsPublic(false);

        return skill;
    }

    /**
     * Collect all non-core frontmatter fields into the extension JSON.
     * Known fields like scripts, tags, inputFormat, outputFormat go directly.
     * Any other unknown fields are also included for forward compatibility.
     */
    @SuppressWarnings("unchecked")
    private String buildExtension(Map<String, Object> frontmatter) {
        Map<String, Object> ext = new LinkedHashMap<>();

        // Explicitly handled extension fields
        collectIfPresent(ext, frontmatter, "scripts");
        collectIfPresent(ext, frontmatter, "tags");
        collectIfPresent(ext, frontmatter, "inputFormat");
        collectIfPresent(ext, frontmatter, "outputFormat");
        collectIfPresent(ext, frontmatter, "version");
        collectIfPresent(ext, frontmatter, "author");

        // Any other unknown fields (future-proofing)
        for (Map.Entry<String, Object> entry : frontmatter.entrySet()) {
            String key = entry.getKey();
            if (!KNOWN_FIELDS.contains(key) && !ext.containsKey(key)) {
                ext.put(key, entry.getValue());
            }
        }

        return ext.isEmpty() ? null : JSON.toJSONString(ext);
    }

    private void collectIfPresent(Map<String, Object> ext, Map<String, Object> frontmatter, String key) {
        Object value = frontmatter.get(key);
        if (value != null) {
            ext.put(key, value);
        }
    }

    ParsedDocument parseFrontmatter(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return new ParsedDocument(null, content);
        }

        int end = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (end < 0) {
            return new ParsedDocument(null, content);
        }

        String yamlContent = trimmed.substring(FRONTMATTER_DELIMITER.length(), end).trim();
        String body = trimmed.substring(end + FRONTMATTER_DELIMITER.length()).trim();

        Yaml yaml = new Yaml();
        Map<String, Object> frontmatter = yaml.load(yamlContent);
        return new ParsedDocument(frontmatter, body);
    }

    private String getResourceName(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (IOException e) {
            return resource.toString();
        }
    }

    public record ParsedDocument(Map<String, Object> frontmatter, String body) {}
}
