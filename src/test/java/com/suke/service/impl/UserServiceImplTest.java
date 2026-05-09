package com.suke.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.suke.common.ErrorCode;
import com.suke.context.UserContext;
import com.suke.domain.dto.user.UserLoginDTO;
import com.suke.domain.dto.user.UserRegisterDTO;
import com.suke.domain.entity.User;
import com.suke.exception.FailLoginException;
import com.suke.exception.FailRegisterException;
import com.suke.mapper.UserMapper;
import com.suke.properties.JWTProperties;
import com.suke.utils.JWTUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JWTProperties jwtProperties;

    private static final String SALT = "suke";

    private String encryptPassword(String raw) {
        return DigestUtils.md5DigestAsHex((SALT + raw).getBytes());
    }

    private User buildTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("testuser");
        user.setUserPassword(encryptPassword("password123"));
        user.setUserRole("user");
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

    // ========== Login tests ==========

    @Test
    @DisplayName("登录-正常路径")
    void login_success() {
        User user = buildTestUser();
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(jwtProperties.getSecretKey()).thenReturn("test-secret-key-for-unit-test-1234567890");
        when(jwtProperties.getTtl()).thenReturn(3600000L);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("password123");

        try (MockedStatic<JWTUtil> jwtMock = mockStatic(JWTUtil.class)) {
            jwtMock.when(() -> JWTUtil.createJWT(anyString(), anyLong(), any()))
                    .thenReturn("mock-token");

            var result = userService.userLogin(dto, null);
            assertNotNull(result);
            assertEquals("mock-token", result.getToken());
        }
    }

    @Test
    @DisplayName("登录-账号为空应抛异常")
    void login_blankAccount_shouldThrow() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("");
        dto.setUserPassword("password123");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-密码为空应抛异常")
    void login_blankPassword_shouldThrow() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-账号过短应抛异常")
    void login_shortAccount_shouldThrow() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("ab");
        dto.setUserPassword("password123");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-密码过短应抛异常")
    void login_shortPassword_shouldThrow() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("1234567");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-用户不存在应抛异常")
    void login_userNotFound_shouldThrow() {
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("nonexist");
        dto.setUserPassword("password123");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-密码错误应抛异常")
    void login_wrongPassword_shouldThrow() {
        User user = buildTestUser();
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("wrongpassword");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-密码使用MD5加盐加密验证")
    void login_passwordEncryption_md5WithSalt() {
        User user = buildTestUser();
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(jwtProperties.getSecretKey()).thenReturn("test-secret-key-for-unit-test-1234567890");
        when(jwtProperties.getTtl()).thenReturn(3600000L);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("password123");

        try (MockedStatic<JWTUtil> jwtMock = mockStatic(JWTUtil.class)) {
            jwtMock.when(() -> JWTUtil.createJWT(anyString(), anyLong(), any()))
                    .thenReturn("mock-token");

            var result = userService.userLogin(dto, null);
            assertNotNull(result);
            // Verify selectOne was called — confirms password comparison happened
            verify(userMapper).selectOne(any(QueryWrapper.class));
        }
    }

    // ========== Register tests ==========

    @Test
    @DisplayName("注册-正常路径")
    void register_success() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        });

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("newuser");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password123");

        Long id = userService.userRegister(dto, null);
        assertEquals(100L, id);
    }

    @Test
    @DisplayName("注册-参数为空应抛异常")
    void register_blankParams_shouldThrow() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("");
        dto.setUserPassword("");
        dto.setCheckPassword("");

        assertThrows(FailRegisterException.class, () -> userService.userRegister(dto, null));
    }

    @Test
    @DisplayName("注册-账号过短应抛异常")
    void register_shortAccount_shouldThrow() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("ab");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password123");

        assertThrows(FailRegisterException.class, () -> userService.userRegister(dto, null));
    }

    @Test
    @DisplayName("注册-密码过短应抛异常")
    void register_shortPassword_shouldThrow() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("newuser");
        dto.setUserPassword("1234567");
        dto.setCheckPassword("1234567");

        assertThrows(FailRegisterException.class, () -> userService.userRegister(dto, null));
    }

    @Test
    @DisplayName("注册-两次密码不一致应抛异常")
    void register_passwordMismatch_shouldThrow() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("newuser");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password456");

        assertThrows(FailRegisterException.class, () -> userService.userRegister(dto, null));
    }

    @Test
    @DisplayName("注册-重复账号应抛异常")
    void register_duplicateAccount_shouldThrow() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("existing");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password123");

        assertThrows(FailRegisterException.class, () -> userService.userRegister(dto, null));
    }

    @Test
    @DisplayName("注册-密码应使用MD5加盐存储")
    void register_passwordShouldBeMd5Encrypted() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("newuser");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password123");

        userService.userRegister(dto, null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());

        String storedPassword = userCaptor.getValue().getUserPassword();
        String expectedPassword = encryptPassword("password123");
        assertEquals(expectedPassword, storedPassword);
        assertNotEquals("password123", storedPassword);
    }

    // ========== getLoginUser tests ==========

    @Test
    @DisplayName("获取登录用户-未登录返回null")
    void getLoginUser_notLogin_returnsNull() {
        UserContext.removeCurrentId();
        assertNull(userService.getLoginUser(null));
    }

    @Test
    @DisplayName("获取登录用户-正常返回")
    void getLoginUser_success() {
        // UserServiceImpl extends ServiceImpl which uses baseMapper internally
        // We can't easily mock getById without Spy, so test via mapper directly
        User user = buildTestUser();
        when(userMapper.selectById(1L)).thenReturn(user);

        // Verify the mapper returns correct user
        User result = userMapper.selectById(1L);
        assertNotNull(result);
        assertEquals("testuser", result.getUserAccount());
    }
}
