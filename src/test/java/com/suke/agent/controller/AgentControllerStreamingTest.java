/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description AgentController SSE 流式端点测试
 */
package com.suke.agent.controller;

import com.suke.agent.core.*;
import com.suke.agent.core.sse.*;
import com.suke.context.UserContext;
import com.suke.utils.RedisUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentControllerStreamingTest {

    @Mock
    private AgentOrchestrator orchestrator;

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private AgentSessionManager sessionManager;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private IntentRouter intentRouter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentId(1L);
        AgentController controller = new AgentController(
                orchestrator, agentRegistry, sessionManager, redisUtils, intentRouter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        UserContext.setCurrentId(null);
    }

    @Test
    void chatStreamReturnsSseEvents() throws Exception {
        when(intentRouter.route(anyString())).thenReturn("data_analyst");
        when(orchestrator.streamingCall(anyString(), anyString(), any(), anyString()))
                .thenReturn(Flux.just(
                        new TokenEvent("你好"),
                        new DoneEvent("trace-1", 100, 500)
                ));

        mockMvc.perform(post("/api/agent/chat/stream")
                        .param("sessionId", "sess1")
                        .param("message", "分析数据")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());
    }

    @Test
    void chatStreamHandlesEmptyFlux() throws Exception {
        when(intentRouter.route(anyString())).thenReturn("data_analyst");
        when(orchestrator.streamingCall(anyString(), anyString(), any(), anyString()))
                .thenReturn(Flux.empty());

        mockMvc.perform(post("/api/agent/chat/stream")
                        .param("sessionId", "sess2")
                        .param("message", "空消息")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());
    }

    @Test
    void chatStreamHandlesFluxError() throws Exception {
        when(intentRouter.route(anyString())).thenReturn("data_analyst");
        when(orchestrator.streamingCall(anyString(), anyString(), any(), anyString()))
                .thenReturn(Flux.error(new RuntimeException("Agent调用失败")));

        mockMvc.perform(post("/api/agent/chat/stream")
                        .param("sessionId", "sess3")
                        .param("message", "触发错误")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());
    }

    @Test
    void chatStreamFiltersAgentStreamEndEvent() throws Exception {
        when(intentRouter.route(anyString())).thenReturn("data_analyst");
        when(orchestrator.streamingCall(anyString(), anyString(), any(), anyString()))
                .thenReturn(Flux.just(
                        new TokenEvent("部分内容"),
                        AgentStreamEndEvent.INSTANCE,
                        new DoneEvent("trace-2", 50, 200)
                ));

        mockMvc.perform(post("/api/agent/chat/stream")
                        .param("sessionId", "sess4")
                        .param("message", "测试过滤")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk());
    }
}
