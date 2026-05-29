package com.suke.agent.memory;

import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.memory.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserBehaviorTrackerTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    private UserBehaviorTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new UserBehaviorTracker(userProfileMapper);
    }

    @Test
    void updateFrequentTopics_blankTopic_skipsUpdate() {
        tracker.updateFrequentTopics(1L, "");
        tracker.updateFrequentTopics(1L, "   ");
        tracker.updateFrequentTopics(1L, null);

        verifyNoInteractions(userProfileMapper);
    }

    @Test
    void updateFrequentTopics_newUser_insertsProfile() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(userProfileMapper.insert(any(UserProfile.class))).thenReturn(1);

        tracker.updateFrequentTopics(1L, "销售趋势");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).insert(captor.capture());
        assertTrue(captor.getValue().getFrequentTopics().contains("销售趋势"));
    }

    @Test
    void updateFrequentTopics_existingUser_newTopic_appendsEntry() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setId(100L);
        profile.setFrequentTopics("[{\"topic\":\"用户增长\",\"count\":5}]");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        tracker.updateFrequentTopics(1L, "收入分析");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).updateById(captor.capture());
        String topics = captor.getValue().getFrequentTopics();
        assertTrue(topics.contains("用户增长"));
        assertTrue(topics.contains("收入分析"));
    }

    @Test
    void updateFrequentTopics_existingTopic_incrementsCount() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setId(100L);
        profile.setFrequentTopics("[{\"topic\":\"销售趋势\",\"count\":3}]");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        tracker.updateFrequentTopics(1L, "销售趋势");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).updateById(captor.capture());
        String topics = captor.getValue().getFrequentTopics();
        assertTrue(topics.contains("\"count\":4"));
    }

    @Test
    void updateFrequentTopics_sortsByCountDesc() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setId(100L);
        profile.setFrequentTopics("[{\"topic\":\"用户增长\",\"count\":5},{\"topic\":\"销售趋势\",\"count\":3}]");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        tracker.updateFrequentTopics(1L, "销售趋势");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileMapper).updateById(captor.capture());
        String topics = captor.getValue().getFrequentTopics();
        // 用户增长(5) should come before 销售趋势(4)
        int idxGrowth = topics.indexOf("用户增长");
        int idxSales = topics.indexOf("销售趋势");
        assertTrue(idxGrowth < idxSales);
    }

    @Test
    void updateFrequentTopics_emptyTopicsJson_handlesGracefully() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setId(100L);
        profile.setFrequentTopics("");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        tracker.updateFrequentTopics(1L, "新主题");

        verify(userProfileMapper).updateById(any(UserProfile.class));
    }

    @Test
    void updateFrequentTopics_invalidTopicsJson_handlesGracefully() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1L);
        profile.setId(100L);
        profile.setFrequentTopics("not-json");

        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(userProfileMapper.updateById(any(UserProfile.class))).thenReturn(1);

        tracker.updateFrequentTopics(1L, "新主题");

        verify(userProfileMapper).updateById(any(UserProfile.class));
    }

    @Test
    void updateFrequentTopics_mapperException_silentlyHandles() {
        when(userProfileMapper.selectOne(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> tracker.updateFrequentTopics(1L, "销售趋势"));
    }
}
