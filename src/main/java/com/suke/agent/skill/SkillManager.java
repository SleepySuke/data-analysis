/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill管理器，负责Skill的元数据构建、读取、创建和校验
 */

package com.suke.agent.skill;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.skill.model.SkillDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class SkillManager {

    private static final Set<String> BLOCKED_PATTERNS = Set.of(
            "ignore previous", "ignore all previous", "disregard",
            "forget your instructions", "new instructions",
            "system prompt:", "you are now", "act as",
            "jailbreak", "dan mode", "developer mode"
    );

    private final AgentSkillMapper skillMapper;

    public SkillManager(AgentSkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    public String buildMetadataPrompt(String agentName, Long userId) {
        List<SkillDefinition> skills = getAvailableSkills(agentName, userId);
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[可用 Skills]\n");
        sb.append("你可以使用以下 Skill 来增强分析能力。使用 read_skill(skill_name) 查看完整指令。\n\n");

        for (SkillDefinition skill : skills) {
            sb.append("- ").append(skill.getSkillName())
                    .append(": ").append(skill.getDescription());

            // Show script info if available
            String extInfo = getScriptInfo(skill);
            if (!extInfo.isEmpty()) {
                sb.append(" (").append(extInfo).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String readSkill(String skillName, String agentName) {
        SkillDefinition skill = skillMapper.selectOne(
                new LambdaQueryWrapper<SkillDefinition>()
                        .eq(SkillDefinition::getSkillName, skillName)
                        .eq(SkillDefinition::getAgentName, agentName));

        if (skill == null) {
            return "未找到 Skill: " + skillName;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Skill: ").append(skillName).append("]\n");
        sb.append(skill.getPromptTemplate()).append("\n");

        if (skill.getAllowedTools() != null && !skill.getAllowedTools().isBlank()) {
            try {
                JSONArray tools = JSON.parseArray(skill.getAllowedTools());
                String toolList = tools.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                sb.append("\n可用工具: ").append(toolList).append("\n");
            } catch (Exception e) {
                log.debug("Failed to parse allowed tools JSON: {}", e.getMessage());
            }
        }

        if (skill.getExtension() != null && !skill.getExtension().isBlank()) {
            try {
                var ext = JSON.parseObject(skill.getExtension());
                if (ext != null && !ext.isEmpty()) {
                    sb.append("\n[扩展信息]\n");
                    for (var entry : ext.entrySet()) {
                        sb.append("- ").append(entry.getKey()).append(": ")
                                .append(JSON.toJSONString(entry.getValue())).append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse extension JSON: {}", e.getMessage());
            }
        }

        return sb.toString();
    }

    public List<SkillDefinition> getAvailableSkills(String agentName, Long userId) {
        LambdaQueryWrapper<SkillDefinition> wrapper = new LambdaQueryWrapper<SkillDefinition>()
                .eq(SkillDefinition::getAgentName, agentName)
                .and(w -> w
                        .eq(SkillDefinition::getOwnerType, "SYSTEM")
                        .or(ow -> {
                            ow.eq(SkillDefinition::getOwnerType, "USER");
                            if (userId != null) {
                                ow.and(iw -> iw.eq(SkillDefinition::getOwnerId, userId)
                                        .or().eq(SkillDefinition::getIsPublic, 1));
                            } else {
                                ow.eq(SkillDefinition::getIsPublic, 1);
                            }
                        })
                );

        return skillMapper.selectList(wrapper);
    }

    @Transactional
    public void createUserSkill(Long userId, com.suke.agent.domain.dto.SkillCreateDTO request) {
        validatePromptSafety(request.getPromptTemplate());

        Long count = skillMapper.selectCount(
                new LambdaQueryWrapper<SkillDefinition>()
                        .eq(SkillDefinition::getSkillName, request.getSkillName())
                        .eq(SkillDefinition::getOwnerId, userId));
        if (count > 0) {
            throw new IllegalArgumentException("Skill名称已存在: " + request.getSkillName());
        }

        long userSkillCount = skillMapper.selectCount(
                new LambdaQueryWrapper<SkillDefinition>()
                        .eq(SkillDefinition::getOwnerType, "USER")
                        .eq(SkillDefinition::getOwnerId, userId));
        if (userSkillCount >= 50) {
            throw new IllegalArgumentException("用户自定义Skill数量已达上限(50个)");
        }

        SkillDefinition skill = new SkillDefinition();
        skill.setSkillName(request.getSkillName());
        skill.setDescription(request.getDescription());
        skill.setAgentName(request.getAgentName() != null ? request.getAgentName() : "data_analyst");
        skill.setPromptTemplate(request.getPromptTemplate());
        skill.setAllowedTools(request.getAllowedTools());
        skill.setExtension(request.getExtension());
        skill.setOwnerType("USER");
        skill.setOwnerId(userId);
        skill.setUsageCount(0);
        skill.setIsPublic(false);

        skillMapper.insert(skill);
    }

    private void validatePromptSafety(String prompt) {
        if (prompt == null) return;
        String lower = prompt.toLowerCase();
        for (String pattern : BLOCKED_PATTERNS) {
            if (lower.contains(pattern)) {
                throw new IllegalArgumentException("Skill 指令包含不安全内容，被拒绝");
            }
        }
    }

    private String getScriptInfo(SkillDefinition skill) {
        if (skill.getExtension() == null || skill.getExtension().isBlank()) {
            return "";
        }
        try {
            var ext = JSON.parseObject(skill.getExtension());
            if (ext != null && ext.containsKey("scripts")) {
                var scripts = ext.getJSONArray("scripts");
                if (scripts != null && !scripts.isEmpty()) {
                    return scripts.size() + " 个脚本";
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}
