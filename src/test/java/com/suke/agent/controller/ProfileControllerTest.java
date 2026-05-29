package com.suke.agent.controller;

import com.suke.agent.memory.LongTermMemoryStore;
import com.suke.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private LongTermMemoryStore memoryStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProfileController controller = new ProfileController(memoryStore);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        UserContext.removeCurrentId();
    }

    @Test
    void getProfileRequiresLogin() throws Exception {
        UserContext.removeCurrentId();

        mockMvc.perform(get("/api/agent/profile"))
                .andExpect(status().isOk());
    }
}
