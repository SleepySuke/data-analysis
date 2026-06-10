package com.suke.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new PasswordEncoder();

    @Nested
    @DisplayName("matches - BCrypt")
    class MatchesBcryptTest {
        @Test
        @DisplayName("BCrypt密码正确验证")
        void success() {
            String raw = "password123";
            String encoded = new BCryptPasswordEncoder().encode(raw);
            assertTrue(passwordEncoder.matches(raw, encoded));
        }

        @Test
        @DisplayName("BCrypt密码错误拒绝")
        void wrongPassword() {
            String encoded = new BCryptPasswordEncoder().encode("password123");
            assertFalse(passwordEncoder.matches("wrongpassword", encoded));
        }
    }

    @Nested
    @DisplayName("matches - MD5 legacy")
    class MatchesMd5Test {
        @Test
        @DisplayName("MD5旧密码兼容验证")
        void success() {
            String raw = "password123";
            String md5Hash = org.springframework.util.DigestUtils
                    .md5DigestAsHex(("suke" + raw).getBytes());
            assertTrue(passwordEncoder.matches(raw, md5Hash));
        }

        @Test
        @DisplayName("MD5旧密码错误拒绝")
        void wrongPassword() {
            String md5Hash = org.springframework.util.DigestUtils
                    .md5DigestAsHex(("suke" + "password123").getBytes());
            assertFalse(passwordEncoder.matches("wrongpassword", md5Hash));
        }
    }

    @Nested
    @DisplayName("matches - null/empty")
    class MatchesNullEmptyTest {
        @Test
        @DisplayName("storedPassword为null返回false")
        void storedNull() {
            assertFalse(passwordEncoder.matches("password123", null));
        }

        @Test
        @DisplayName("storedPassword为空返回false")
        void storedEmpty() {
            assertFalse(passwordEncoder.matches("password123", ""));
        }
    }

    @Nested
    @DisplayName("encode")
    class EncodeTest {
        @Test
        @DisplayName("输出BCrypt格式以$2a$开头且长度60")
        void producesBcryptFormat() {
            String encoded = passwordEncoder.encode("password123");
            assertTrue(encoded.startsWith("$2a$"));
            assertEquals(60, encoded.length());
        }

        @Test
        @DisplayName("同一密码两次编码产生不同哈希")
        void differentSaltsEachTime() {
            String encoded1 = passwordEncoder.encode("password123");
            String encoded2 = passwordEncoder.encode("password123");

            assertNotEquals(encoded1, encoded2);
            assertTrue(passwordEncoder.matches("password123", encoded1));
            assertTrue(passwordEncoder.matches("password123", encoded2));
        }

        @Test
        @DisplayName("特殊字符密码编码")
        void specialCharacters() {
            String raw = "p@$$w0rd!#中文密码🔑";
            String encoded = passwordEncoder.encode(raw);
            assertTrue(passwordEncoder.matches(raw, encoded));
        }
    }

    @Nested
    @DisplayName("needsUpgrade")
    class NeedsUpgradeTest {
        @Test
        @DisplayName("MD5格式需要升级")
        void md5_returnsTrue() {
            String md5Hash = org.springframework.util.DigestUtils
                    .md5DigestAsHex(("suke" + "password123").getBytes());
            assertTrue(passwordEncoder.needsUpgrade(md5Hash));
        }

        @Test
        @DisplayName("BCrypt格式无需升级")
        void bcrypt_returnsFalse() {
            String bcryptHash = new BCryptPasswordEncoder().encode("password123");
            assertFalse(passwordEncoder.needsUpgrade(bcryptHash));
        }

        @Test
        @DisplayName("null返回false")
        void null_returnsFalse() {
            assertFalse(passwordEncoder.needsUpgrade(null));
        }

        @Test
        @DisplayName("空字符串返回false")
        void empty_returnsFalse() {
            assertFalse(passwordEncoder.needsUpgrade(""));
        }
    }
}
