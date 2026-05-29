/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill初始化器，启动时从文档加载系统Skill到数据库
 */

package com.suke.agent.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.skill.model.SkillDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SkillInitializer implements CommandLineRunner {

    private final AgentSkillMapper skillMapper;
    private final SkillDocumentLoader skillDocumentLoader;

    public SkillInitializer(AgentSkillMapper skillMapper, SkillDocumentLoader skillDocumentLoader) {
        this.skillMapper = skillMapper;
        this.skillDocumentLoader = skillDocumentLoader;
    }

    @Override
    public void run(String... args) {
        long existing = skillMapper.selectCount(
                new LambdaQueryWrapper<SkillDefinition>()
                        .eq(SkillDefinition::getOwnerType, "SYSTEM"));

        if (existing > 0) {
            log.info("System skills already initialized ({} skills), skipping", existing);
            return;
        }

        List<SkillDefinition> systemSkills = skillDocumentLoader.loadSkillDocuments();
        for (SkillDefinition skill : systemSkills) {
            try {
                skillMapper.insert(skill);
            } catch (Exception e) {
                log.warn("Skill already exists: {} - {}", skill.getSkillName(), e.getMessage());
            }
        }

        log.info("Initialized {} system skills from documents", systemSkills.size());
    }
}
