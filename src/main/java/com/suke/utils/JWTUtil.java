package com.suke.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JWTUtil {

    public static String createJWT(String secretKey, Long ttlMillis, Map<String, Object> claims) {
        if (ttlMillis == null) {
            throw new IllegalArgumentException("ttlMillis must not be null");
        }
        long nowMillis = System.currentTimeMillis();
        Date exp = new Date(nowMillis + ttlMillis);
        SecretKey key = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.builder()
                .claims(claims)
                .signWith(key)
                .expiration(exp)
                .compact();
    }

    public static Claims parseJWT(String secretKey, String token) {
        SecretKey key = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
