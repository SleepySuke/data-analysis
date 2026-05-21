package com.suke.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.suke.utils.PasswordEncoder;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @Mock
    private PasswordEncoder passwordEncoder;

    private static final String MOCK_BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuvwxABCDEFGHIJ1234567890abcdefghijklm";

    private User buildTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("testuser");
        user.setUserPassword(MOCK_BCRYPT_HASH);
        user.setUserRole("user");
        return user;
    }

    private User buildTestUserWithMd5() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("testuser");
        user.setUserPassword("5d5c5b5a5e5f50515253545556575859");
        user.setUserRole("user");
        return user;
    }

    @BeforeEach
    void setUp() throws Exception {
        UserContext.setCurrentId(1L);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        // Inject baseMapper into ServiceImpl for getById() support
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(userService, userMapper);
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
        when(passwordEncoder.matches("password123", MOCK_BCRYPT_HASH)).thenReturn(true);
        when(passwordEncoder.needsUpgrade(MOCK_BCRYPT_HASH)).thenReturn(false);
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
        when(passwordEncoder.matches("wrongpassword", MOCK_BCRYPT_HASH)).thenReturn(false);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("wrongpassword");

        assertThrows(FailLoginException.class, () -> userService.userLogin(dto, null));
    }

    @Test
    @DisplayName("登录-MD5密码自动升级为BCrypt")
    void login_md5Password_autoUpgradeToBcrypt() {
        User user = buildTestUserWithMd5();
        String md5Hash = user.getUserPassword();
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password123", md5Hash)).thenReturn(true);
        when(passwordEncoder.needsUpgrade(md5Hash)).thenReturn(true);
        when(passwordEncoder.encode("password123")).thenReturn(MOCK_BCRYPT_HASH);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
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
            verify(passwordEncoder).encode("password123");
            verify(userMapper).updateById(any(User.class));
        }
    }

    @Test
    @DisplayName("登录-BCrypt密码不触发升级")
    void login_bcryptPassword_noUpgrade() {
        User user = buildTestUser();
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password123", MOCK_BCRYPT_HASH)).thenReturn(true);
        when(passwordEncoder.needsUpgrade(MOCK_BCRYPT_HASH)).thenReturn(false);
        when(jwtProperties.getSecretKey()).thenReturn("test-secret-key-for-unit-test-1234567890");
        when(jwtProperties.getTtl()).thenReturn(3600000L);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("password123");

        try (MockedStatic<JWTUtil> jwtMock = mockStatic(JWTUtil.class)) {
            jwtMock.when(() -> JWTUtil.createJWT(anyString(), anyLong(), any()))
                    .thenReturn("mock-token");

            userService.userLogin(dto, null);
            verify(passwordEncoder, never()).encode(anyString());
            verify(userMapper, never()).updateById(any(User.class));
        }
    }

    // ========== Register tests ==========

    @Test
    @DisplayName("注册-正常路径")
    void register_success() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn(MOCK_BCRYPT_HASH);
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
    @DisplayName("注册-密码应使用BCrypt编码")
    void register_passwordShouldBeBcryptEncoded() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn(MOCK_BCRYPT_HASH);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("newuser");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password123");

        userService.userRegister(dto, null);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());

        String storedPassword = userCaptor.getValue().getUserPassword();
        assertEquals(MOCK_BCRYPT_HASH, storedPassword);
        verify(passwordEncoder).encode("password123");
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
        User user = buildTestUser();
        when(userMapper.selectById(1L)).thenReturn(user);

        User result = userService.getLoginUser(null);
        assertNotNull(result);
        assertEquals("testuser", result.getUserAccount());
    }

    // ========== Boundary tests ==========

    @Test
    @DisplayName("登录-账号最小长度4通过验证")
    void login_accountMinLength_success() {
        User user = buildTestUser();
        user.setUserAccount("abcd");
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password123", MOCK_BCRYPT_HASH)).thenReturn(true);
        when(passwordEncoder.needsUpgrade(MOCK_BCRYPT_HASH)).thenReturn(false);
        when(jwtProperties.getSecretKey()).thenReturn("test-secret-key-for-unit-test-1234567890");
        when(jwtProperties.getTtl()).thenReturn(3600000L);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("abcd");
        dto.setUserPassword("password123");

        try (MockedStatic<JWTUtil> jwtMock = mockStatic(JWTUtil.class)) {
            jwtMock.when(() -> JWTUtil.createJWT(anyString(), anyLong(), any()))
                    .thenReturn("mock-token");
            assertNotNull(userService.userLogin(dto, null));
        }
    }

    @Test
    @DisplayName("登录-密码最小长度8通过验证")
    void login_passwordMinLength_success() {
        User user = buildTestUser();
        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("12345678", MOCK_BCRYPT_HASH)).thenReturn(true);
        when(passwordEncoder.needsUpgrade(MOCK_BCRYPT_HASH)).thenReturn(false);
        when(jwtProperties.getSecretKey()).thenReturn("test-secret-key-for-unit-test-1234567890");
        when(jwtProperties.getTtl()).thenReturn(3600000L);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUserAccount("testuser");
        dto.setUserPassword("12345678");

        try (MockedStatic<JWTUtil> jwtMock = mockStatic(JWTUtil.class)) {
            jwtMock.when(() -> JWTUtil.createJWT(anyString(), anyLong(), any()))
                    .thenReturn("mock-token");
            assertNotNull(userService.userLogin(dto, null));
        }
    }

    @Test
    @DisplayName("注册-账号最小长度4通过验证")
    void register_accountMinLength_success() {
        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn(MOCK_BCRYPT_HASH);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return 1;
        });

        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUserAccount("abcd");
        dto.setUserPassword("password123");
        dto.setCheckPassword("password123");

        Long id = userService.userRegister(dto, null);
        assertEquals(100L, id);
    }
}
