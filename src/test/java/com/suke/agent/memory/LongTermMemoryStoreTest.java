package com.suke.agent.memory;

import com.suke.agent.memory.mapper.InteractionLogMapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.memory.model.InteractionLog;
import com.suke.agent.memory.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LongTermMemoryStoreTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private InteractionLogMapper interactionLogMapper;

    private UserProfileInjector profileInjector;

    private LongTermMemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        profileInjector = new UserProfileInjector();
        memoryStore = new LongTermMemoryStore(userProfileMapper, interactionLogMapper, profileInjector);
    }

    @Test
    void buildMemoryContextWithProfile() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("finance");
        profile.setPreferredCharts("bar,line");
        profile.setDetailLevel("detailed");
        profile.setReportStyle("business");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(interactionLogMapper.selectRecentTopics(1L, 5))
                .thenReturn(List.of("销售趋势"));

        String context = memoryStore.buildMemoryContext(1L);

        assertTrue(context.contains("finance"));
        assertTrue(context.contains("bar,line"));
        assertTrue(context.contains("detailed"));
        assertTrue(context.contains("销售趋势"));
    }

    @Test
    void buildMemoryContextWithoutProfile() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        String context = memoryStore.buildMemoryContext(999L);

        assertEquals("", context);
    }

    @Test
    void buildMemoryContextWithRecentTopics() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("tech");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(interactionLogMapper.selectRecentTopics(1L, 5))
                .thenReturn(List.of("用户增长", "收入分析"));

        String context = memoryStore.buildMemoryContext(1L);

        assertTrue(context.contains("用户增长"));
        assertTrue(context.contains("收入分析"));
    }

    @Test
    void getProfileReturnsProfile() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("retail");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);

        UserProfile result = memoryStore.getProfile(1L);

        assertNotNull(result);
        assertEquals("retail", result.getIndustry());
    }

    @Test
    void getProfileReturnsNullWhenNotFound() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        UserProfile result = memoryStore.getProfile(999L);

        assertNull(result);
    }

    @Test
    void updateProfileInsertsWhenNotExists() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("medical");

        when(userProfileMapper.insertOrUpdate(any(UserProfile.class))).thenReturn(true);

        memoryStore.updateProfile(1L, profile);

        verify(userProfileMapper).insertOrUpdate(any(UserProfile.class));
    }

    @Test
    void updateProfileUpdatesWhenExists() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setId(100L);
        profile.setIndustry("medical");

        when(userProfileMapper.insertOrUpdate(any(UserProfile.class))).thenReturn(true);

        memoryStore.updateProfile(1L, profile);

        verify(userProfileMapper).insertOrUpdate(any(UserProfile.class));
    }

    @Test
    void trackInteractionInsertsLog() {
        when(interactionLogMapper.insert(any(InteractionLog.class))).thenReturn(1);

        assertDoesNotThrow(() ->
            memoryStore.trackInteraction(1L, "sess-123", "data_analyst",
                    "analyze", "销售趋势", 500, 1200)
        );

        verify(interactionLogMapper).insert(any(InteractionLog.class));
    }

    @Test
    void getRecentTopics_returnsTopicsFromLog() {
        when(interactionLogMapper.selectRecentTopics(1L, 5))
                .thenReturn(List.of("销售趋势", "用户增长"));

        List<String> topics = memoryStore.getRecentTopics(1L, 5);

        assertEquals(2, topics.size());
        assertTrue(topics.contains("销售趋势"));
        assertTrue(topics.contains("用户增长"));
    }

    @Test
    void getRecentTopics_returnsEmptyOnException() {
        when(interactionLogMapper.selectRecentTopics(999L, 5))
                .thenThrow(new RuntimeException("DB error"));

        List<String> topics = memoryStore.getRecentTopics(999L, 5);

        assertTrue(topics.isEmpty());
    }

    @Test
    void getRecentTopics_returnsEmptyWhenNull() {
        when(interactionLogMapper.selectRecentTopics(1L, 5))
                .thenReturn(null);

        List<String> topics = memoryStore.getRecentTopics(1L, 5);

        assertTrue(topics.isEmpty());
    }

    @Test
    void buildMemoryContext_withPartialFields() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setIndustry("tech");
        // preferredCharts, detailLevel, reportStyle are null

        when(userProfileMapper.selectOne(any())).thenReturn(profile);

        String context = memoryStore.buildMemoryContext(1L);

        assertTrue(context.contains("tech"));
        assertFalse(context.contains("偏好图表"));
    }
}
