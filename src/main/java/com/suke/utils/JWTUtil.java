package com.suke.utils;

import com.suke.properties.JWTProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * @author 自然醒
 * @version 1.0
 */
//JWT令牌工具类
public class JWTUtil {

    @Autowired
    private static JWTProperties jwtProperties;

    /**
     * 创建JWT令牌
     *
     * @param secretKey 密钥
     * @param ttlMillis 过期时间
     * @param claims    存放的数据
     * @return
     */
    public static String createJWT(String secretKey, Long ttlMillis, Map<String, Object> claims) {
        //指定签名算法 header头部分 HS256
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        //生成JWT时间
        long nowMillis = System.currentTimeMillis();
        if(ttlMillis == null){
            ttlMillis = jwtProperties.getTtl();
        }
        long expMillis = nowMillis + ttlMillis;
        Date exp = new Date(expMillis);
        //设置JWT的body部分
        JwtBuilder builder = Jwts.builder()
                // 如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
                .setClaims(claims)
                // 设置签名使用的签名算法和签名使用的秘钥
                .signWith(signatureAlgorithm, secretKey.getBytes(StandardCharsets.UTF_8))
                // 设置过期时间
                .setExpiration(exp);
        return builder.compact();
    }

    /**
     * 解析JWT令牌
     *
     * @param secretKey 密钥
     * @param token     令牌
     * @return
     */
    public static Claims parseJWT(String secretKey, String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
        return claims;
    }
}
