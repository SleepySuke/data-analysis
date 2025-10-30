package com.suke.config;

import com.suke.utils.AIDocking;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 自然醒
 * @version 1.0
 */
@Configuration
public class AIDockingConfig {
    @Bean
    public AIDocking aiDocking(){
        return new AIDocking();
    }
}
