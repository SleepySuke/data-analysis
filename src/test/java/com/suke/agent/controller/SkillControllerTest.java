package com.suke.agent.controller;

import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.model.SkillDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    @Mock
    private SkillManager skillManager;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SkillController controller = new SkillController(skillManager);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listSkillsReturnsList() throws Exception {
        when(skillManager.getAvailableSkills(anyString(), any()))
                .thenReturn(List.of(createTestSkill("test_skill", "测试技能")));

        mockMvc.perform(get("/api/agent/skills")
                        .param("agentName", "data_analyst"))
                .andExpect(status().isOk());
    }

    @Test
    void readSkillReturnsContent() throws Exception {
        when(skillManager.readSkill("test_skill", "data_analyst"))
                .thenReturn("[Skill: test_skill]\n分析模板内容");

        mockMvc.perform(get("/api/agent/skills/test_skill")
                        .param("agentName", "data_analyst"))
                .andExpect(status().isOk());
    }

    @Test
    void readNonExistentSkillReturnsError() throws Exception {
        when(skillManager.readSkill("nonexistent", "data_analyst"))
                .thenReturn("未找到 Skill: nonexistent");

        mockMvc.perform(get("/api/agent/skills/nonexistent")
                        .param("agentName", "data_analyst"))
                .andExpect(status().isOk());
    }

    private SkillDefinition createTestSkill(String name, String desc) {
        SkillDefinition skill = new SkillDefinition();
        skill.setSkillName(name);
        skill.setDescription(desc);
        skill.setAgentName("data_analyst");
        skill.setOwnerType("SYSTEM");
        return skill;
    }
}
