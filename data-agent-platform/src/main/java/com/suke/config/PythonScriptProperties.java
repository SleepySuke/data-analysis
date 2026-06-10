/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Python脚本执行配置属性
 */

package com.suke.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "python.script")
public class PythonScriptProperties {

    private String executable = "python3";
    private int timeout = 60;
    private boolean enabled = true;
}
