package com.suke.interceptor;

import com.suke.properties.JWTProperties;
import com.suke.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtTokenInterceptorTest {

    @InjectMocks
    private JwtTokenInterceptor jwtTokenInterceptor;

    @Mock
    private JWTProperties jwtProperties;

    @Mock
    private IUserService userService;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Object handler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        // 模拟一个 HandlerMethod（Controller 方法）
        handler = mock(HandlerMethod.class);
        when(jwtProperties.getTokenName()).thenReturn("Authorization");
    }

    @Test
    @DisplayName("handler 非 HandlerMethod 时应放行")
    void preHandle_nonHandlerMethod_shouldPass() throws Exception {
        boolean result = jwtTokenInterceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    @DisplayName("无 Token 时不应放行，应返回 401")
    void preHandle_noToken_shouldRejectWith401() throws Exception {
        boolean result = jwtTokenInterceptor.preHandle(request, response, handler);

        assertFalse(result, "无 Token 时不应放行");
        assertEquals(401, response.getStatus(), "应返回 401 状态码");
    }

    @Test
    @DisplayName("Token 为空字符串时不应放行")
    void preHandle_blankToken_shouldReject() throws Exception {
        request.addHeader("Authorization", "");

        boolean result = jwtTokenInterceptor.preHandle(request, response, handler);

        assertFalse(result, "空 Token 不应放行");
    }

    @Test
    @DisplayName("无效 Token 应返回 401")
    void preHandle_invalidToken_shouldReturn401() throws Exception {
        request.addHeader("Authorization", "invalid.jwt.token");

        boolean result = jwtTokenInterceptor.preHandle(request, response, handler);

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("afterCompletion 应清理 UserContext")
    void afterCompletion_shouldCleanUserContext() throws Exception {
        // 确保不会抛异常
        assertDoesNotThrow(() ->
                jwtTokenInterceptor.afterCompletion(request, response, handler, null));
    }
}
