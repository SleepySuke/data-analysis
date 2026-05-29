package com.suke.agent.skill;

import com.suke.agent.domain.dto.SkillCreateDTO;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.skill.model.SkillDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillManagerTest {

    @Mock
    private AgentSkillMapper skillMapper;

    private SkillManager skillManager;

    @BeforeEach
    void setUp() {
        skillManager = new SkillManager(skillMapper);
    }

    @Test
    void buildMetadataPromptContainsSkillNames() {
        when(skillMapper.selectList(any())).thenReturn(List.of(
                createSkill("sales_analysis", "销售趋势分析", "data_analyst", "SYSTEM"),
                createSkill("data_quality", "数据质量检查", "data_analyst", "SYSTEM")
        ));

        String prompt = skillManager.buildMetadataPrompt("data_analyst", null);

        assertTrue(prompt.contains("sales_analysis"));
        assertTrue(prompt.contains("data_quality"));
        assertTrue(prompt.contains("销售趋势分析"));
    }

    @Test
    void buildMetadataPromptContainsReadHint() {
        when(skillMapper.selectList(any())).thenReturn(List.of(
                createSkill("sales_analysis", "销售趋势分析", "data_analyst", "SYSTEM")
        ));

        String prompt = skillManager.buildMetadataPrompt("data_analyst", null);

        assertTrue(prompt.contains("read_skill"));
    }

    @Test
    void buildMetadataPromptEmptyWhenNoSkills() {
        when(skillMapper.selectList(any())).thenReturn(List.of());

        String prompt = skillManager.buildMetadataPrompt("data_analyst", null);

        assertEquals("", prompt);
    }

    @Test
    void buildMetadataPromptCombinesSystemAndUser() {
        when(skillMapper.selectList(any())).thenReturn(List.of(
                createSkill("sys_skill", "系统技能", "data_analyst", "SYSTEM"),
                createUserSkill("user_skill", "用户技能", "data_analyst", 1L)
        ));

        String prompt = skillManager.buildMetadataPrompt("data_analyst", 1L);

        assertTrue(prompt.contains("sys_skill"));
        assertTrue(prompt.contains("user_skill"));
    }

    @Test
    void readExistingSkillReturnsFullTemplate() {
        SkillDefinition skill = createSkill("sales_analysis", "销售趋势分析", "data_analyst", "SYSTEM");
        skill.setPromptTemplate("请分析以下销售数据的趋势...");
        when(skillMapper.selectOne(any())).thenReturn(skill);

        String result = skillManager.readSkill("sales_analysis", "data_analyst");

        assertTrue(result.contains("请分析以下销售数据的趋势"));
    }

    @Test
    void readNonExistentSkillReturnsError() {
        when(skillMapper.selectOne(any())).thenReturn(null);

        String result = skillManager.readSkill("nonexistent", "data_analyst");

        assertTrue(result.contains("未找到"));
    }

    @Test
    void readSkillWithAllowedToolsListsThem() {
        SkillDefinition skill = createSkill("sales_analysis", "销售趋势分析", "data_analyst", "SYSTEM");
        skill.setPromptTemplate("分析模板");
        skill.setAllowedTools("[\"csv_analysis\",\"chart_generation\"]");
        when(skillMapper.selectOne(any())).thenReturn(skill);

        String result = skillManager.readSkill("sales_analysis", "data_analyst");

        assertTrue(result.contains("csv_analysis"));
        assertTrue(result.contains("chart_generation"));
    }

    private SkillDefinition createSkill(String name, String desc, String agent, String ownerType) {
        SkillDefinition skill = new SkillDefinition();
        skill.setSkillName(name);
        skill.setDescription(desc);
        skill.setAgentName(agent);
        skill.setOwnerType(ownerType);
        return skill;
    }

    private SkillDefinition createUserSkill(String name, String desc, String agent, Long ownerId) {
        SkillDefinition skill = createSkill(name, desc, agent, "USER");
        skill.setOwnerId(ownerId);
        skill.setIsPublic(true);
        return skill;
    }

    @Test
    void createUserSkill_success() {
        when(skillMapper.selectCount(any())).thenReturn(0L);
        when(skillMapper.insert(any(SkillDefinition.class))).thenReturn(1);

        SkillCreateDTO request = new SkillCreateDTO();
        request.setSkillName("my_custom_skill");
        request.setDescription("自定义分析");
        request.setPromptTemplate("分析模板内容");
        request.setAgentName("data_analyst");

        skillManager.createUserSkill(1L, request);

        ArgumentCaptor<SkillDefinition> captor = ArgumentCaptor.forClass(SkillDefinition.class);
        verify(skillMapper).insert(captor.capture());
        assertEquals("my_custom_skill", captor.getValue().getSkillName());
        assertEquals("USER", captor.getValue().getOwnerType());
        assertEquals(1L, captor.getValue().getOwnerId());
    }

    @Test
    void createUserSkill_duplicateName_throwsException() {
        when(skillMapper.selectCount(any())).thenReturn(1L);

        SkillCreateDTO request = new SkillCreateDTO();
        request.setSkillName("existing_skill");
        request.setDescription("desc");
        request.setPromptTemplate("template");

        assertThrows(IllegalArgumentException.class,
                () -> skillManager.createUserSkill(1L, request));
    }

    @Test
    void createUserSkill_exceedsLimit_throwsException() {
        // First call: duplicate check returns 0
        when(skillMapper.selectCount(any())).thenReturn(0L, 50L);

        SkillCreateDTO request = new SkillCreateDTO();
        request.setSkillName("new_skill");
        request.setDescription("desc");
        request.setPromptTemplate("template");

        assertThrows(IllegalArgumentException.class,
                () -> skillManager.createUserSkill(1L, request));
    }

    @Test
    void createUserSkill_defaultAgentName() {
        when(skillMapper.selectCount(any())).thenReturn(0L, 0L);
        when(skillMapper.insert(any(SkillDefinition.class))).thenReturn(1);

        SkillCreateDTO request = new SkillCreateDTO();
        request.setSkillName("test");
        request.setDescription("desc");
        request.setPromptTemplate("template");
        request.setAgentName(null);

        skillManager.createUserSkill(1L, request);

        ArgumentCaptor<SkillDefinition> captor = ArgumentCaptor.forClass(SkillDefinition.class);
        verify(skillMapper).insert(captor.capture());
        assertEquals("data_analyst", captor.getValue().getAgentName());
    }

    @Test
    void readSkill_invalidAllowedToolsJson_stillReturnsTemplate() {
        SkillDefinition skill = createSkill("test", "desc", "data_analyst", "SYSTEM");
        skill.setPromptTemplate("模板内容");
        skill.setAllowedTools("not-valid-json");
        when(skillMapper.selectOne(any())).thenReturn(skill);

        String result = skillManager.readSkill("test", "data_analyst");

        assertTrue(result.contains("模板内容"));
    }
}
