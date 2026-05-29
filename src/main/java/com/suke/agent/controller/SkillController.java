/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill控制器，提供Skill的CRUD API
 */

package com.suke.agent.controller;

import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.model.SkillDefinition;
import com.suke.agent.domain.dto.SkillCreateDTO;
import com.suke.agent.domain.vo.SkillVO;
import com.suke.common.Result;
import com.suke.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/agent/skills")
public class SkillController {

    private final SkillManager skillManager;

    public SkillController(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @GetMapping
    public Result<List<SkillVO>> listSkills(
            @RequestParam(defaultValue = "data_analyst") String agentName) {
        Long userId = UserContext.getCurrentId();
        List<SkillDefinition> skills = skillManager.getAvailableSkills(agentName, userId);
        List<SkillVO> voList = skills.stream().map(this::toVO).collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/{skillName}")
    public Result<String> readSkill(
            @PathVariable String skillName,
            @RequestParam(defaultValue = "data_analyst") String agentName) {
        String content = skillManager.readSkill(skillName, agentName);
        return Result.success(content);
    }

    @PostMapping
    public Result<String> createSkill(@RequestBody SkillCreateDTO request) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        try {
            skillManager.createUserSkill(userId, request);
            return Result.success("创建成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    private SkillVO toVO(SkillDefinition skill) {
        return new SkillVO()
                .setId(skill.getId())
                .setSkillName(skill.getSkillName())
                .setDescription(skill.getDescription())
                .setAgentName(skill.getAgentName())
                .setOwnerType(skill.getOwnerType())
                .setUsageCount(skill.getUsageCount())
                .setIsPublic(skill.getIsPublic())
                .setExtension(skill.getExtension());
    }
}
