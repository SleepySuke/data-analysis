package com.suke.aop;

import com.suke.annotation.AuthCheck;
import com.suke.common.ErrorCode;
import com.suke.context.UserContext;
import com.suke.domain.entity.User;
import com.suke.service.IUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @Mock
    private IUserService userService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private AuthCheck authCheck;

    private User buildUser(String role) {
        User user = new User();
        user.setId(1L);
        user.setUserRole(role);
        return user;
    }

    @BeforeEach
    void setUp() {
        UserContext.setCurrentId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeCurrentId();
    }

    // ========== mustRole empty/null — only need login ==========

    @Test
    @DisplayName("mustRole为空-已登录用户应放行")
    void mustRoleEmpty_loggedIn_shouldProceed() throws Throwable {
        when(authCheck.mustRole()).thenReturn("");
        when(userService.getById(1L)).thenReturn(buildUser("user"));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = authInterceptor.doInterceptor(joinPoint, authCheck);
        assertEquals("ok", result);
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("mustRole为空-未登录用户应拒绝")
    void mustRoleEmpty_notLoggedIn_shouldReject() throws Throwable {
        UserContext.removeCurrentId();
        when(authCheck.mustRole()).thenReturn("");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authInterceptor.doInterceptor(joinPoint, authCheck));
        assertTrue(ex.getMessage().contains(ErrorCode.NOT_LOGIN_ERROR.getMessage())
                || ex.getMessage().contains("登录"));
    }

    // ========== mustRole set — need role check ==========

    @Test
    @DisplayName("mustRole为admin-管理员应放行")
    void mustRoleAdmin_admin_shouldProceed() throws Throwable {
        when(authCheck.mustRole()).thenReturn("ADMIN");
        when(userService.getById(1L)).thenReturn(buildUser("admin"));
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = authInterceptor.doInterceptor(joinPoint, authCheck);
        assertEquals("ok", result);
    }

    @Test
    @DisplayName("mustRole为admin-普通用户应拒绝")
    void mustRoleAdmin_normalUser_shouldReject() throws Throwable {
        when(authCheck.mustRole()).thenReturn("ADMIN");
        when(userService.getById(1L)).thenReturn(buildUser("user"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authInterceptor.doInterceptor(joinPoint, authCheck));
        assertTrue(ex.getMessage().contains(ErrorCode.NO_AUTH_ERROR.getMessage())
                || ex.getMessage().contains("权限"));
    }

    @Test
    @DisplayName("mustRole为admin-被封号用户应拒绝")
    void mustRoleAdmin_bannedUser_shouldReject() throws Throwable {
        when(authCheck.mustRole()).thenReturn("ADMIN");
        when(userService.getById(1L)).thenReturn(buildUser("ban"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authInterceptor.doInterceptor(joinPoint, authCheck));
        assertTrue(ex.getMessage().contains(ErrorCode.NO_AUTH_ERROR.getMessage())
                || ex.getMessage().contains("权限"));
    }

    @Test
    @DisplayName("mustRole为admin-未登录用户应拒绝")
    void mustRoleAdmin_notLoggedIn_shouldReject() throws Throwable {
        UserContext.removeCurrentId();
        when(authCheck.mustRole()).thenReturn("ADMIN");

        assertThrows(RuntimeException.class,
                () -> authInterceptor.doInterceptor(joinPoint, authCheck));
    }

    // ========== userId null but not removed from context edge case ==========

    @Test
    @DisplayName("userId存在但用户被删除-shouldReject")
    void userExistsInContext_butUserDeleted_shouldReject() throws Throwable {
        when(authCheck.mustRole()).thenReturn("ADMIN");
        when(userService.getById(1L)).thenReturn(null);

        // loginUser will be null since getById returns null
        // UserRoleEnum.getEnumByname(null.getUserRole()) will throw NPE or role mismatch
        assertThrows(Exception.class,
                () -> authInterceptor.doInterceptor(joinPoint, authCheck));
    }
}
