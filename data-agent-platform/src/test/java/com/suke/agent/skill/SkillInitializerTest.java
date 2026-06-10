package com.suke.agent.skill;

import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.skill.model.SkillDefinition;
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
class SkillInitializerTest {

    @Mock
    private AgentSkillMapper skillMapper;

    @Mock
    private SkillDocumentLoader skillDocumentLoader;

    @Test
    void skipsInitializationWhenSkillsExist() throws Exception {
        when(skillMapper.selectCount(any())).thenReturn(13L);

        SkillInitializer initializer = new SkillInitializer(skillMapper, skillDocumentLoader);
        initializer.run();

        verify(skillDocumentLoader, never()).loadSkillDocuments();
        verify(skillMapper, never()).insert(any(SkillDefinition.class));
    }

    @Test
    void initializesSystemSkillsWhenEmpty() throws Exception {
        when(skillMapper.selectCount(any())).thenReturn(0L);
        when(skillMapper.insert(any(SkillDefinition.class))).thenReturn(1);

        SkillDefinition testSkill = new SkillDefinition();
        testSkill.setSkillName("test_skill");
        testSkill.setOwnerType("SYSTEM");
        when(skillDocumentLoader.loadSkillDocuments()).thenReturn(List.of(testSkill));

        SkillInitializer initializer = new SkillInitializer(skillMapper, skillDocumentLoader);
        initializer.run();

        verify(skillDocumentLoader).loadSkillDocuments();
        ArgumentCaptor<SkillDefinition> captor = ArgumentCaptor.forClass(SkillDefinition.class);
        verify(skillMapper, times(1)).insert(captor.capture());

        SkillDefinition inserted = captor.getValue();
        assertEquals("test_skill", inserted.getSkillName());
        assertEquals("SYSTEM", inserted.getOwnerType());
    }

    @Test
    void continuesOnDuplicateInsert() throws Exception {
        when(skillMapper.selectCount(any())).thenReturn(0L);
        when(skillMapper.insert(any(SkillDefinition.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("Duplicate key"))
                .thenReturn(1);

        SkillDefinition s1 = new SkillDefinition();
        s1.setSkillName("skill1");
        SkillDefinition s2 = new SkillDefinition();
        s2.setSkillName("skill2");
        SkillDefinition s3 = new SkillDefinition();
        s3.setSkillName("skill3");
        when(skillDocumentLoader.loadSkillDocuments()).thenReturn(List.of(s1, s2, s3));

        SkillInitializer initializer = new SkillInitializer(skillMapper, skillDocumentLoader);
        assertDoesNotThrow(() -> initializer.run());
        verify(skillMapper, times(3)).insert(any(SkillDefinition.class));
    }
}
