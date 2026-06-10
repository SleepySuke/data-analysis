/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 流水线配置属性（对应 application.yml 中 agent.pipeline 前缀）
 */
package com.suke.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "agent.pipeline")
public class PipelineProperties {

    private int maxParallelAgents = 4;
    private int parallelTimeoutSeconds = 60;
    private Map<String, PipelinePreset> presets = new HashMap<>();

    @Data
    public static class PipelinePreset {
        private String name;
        private List<PipelineStep> steps;
    }

    @Data
    public static class PipelineStep {
        private String agent;
        private List<String> agents;
        private String mode = "sequential";
    }
}
