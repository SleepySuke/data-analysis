package com.suke.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

@Component
public class PasswordEncoder {

    private static final String LEGACY_SALT = "suke";
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    public boolean matches(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        if (storedPassword.startsWith("$2")) {
            return bcrypt.matches(rawPassword, storedPassword);
        }
        String md5 = DigestUtils.md5DigestAsHex((LEGACY_SALT + rawPassword).getBytes());
        return md5.equals(storedPassword);
    }

    public String encode(String rawPassword) {
        return bcrypt.encode(rawPassword);
    }

    public boolean needsUpgrade(String storedPassword) {
        if (storedPassword == null || storedPassword.isEmpty()) {
            return false;
        }
        return !storedPassword.startsWith("$2");
    }
}
