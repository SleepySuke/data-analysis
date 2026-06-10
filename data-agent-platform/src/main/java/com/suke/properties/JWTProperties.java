package com.suke.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author 自然醒
 * @version 1.0
 */
//JWT令牌配置属性
@Component
@ConfigurationProperties(prefix = "suke.jwt")
@Data
public class JWTProperties {

    /**
     * 密钥
     */
    private String secretKey;

    /**
     * 过期时间
     */
    private Long ttl;

    /**
     * 令牌名称
     */
    private String tokenName;
}
