/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 预定义流水线配置加载器
 */
package com.suke.agent.core;

import com.suke.agent.config.PipelineProperties;
import com.suke.agent.core.models.StepMode;
import com.suke.agent.domain.entity.PlanStep;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PipelineConfig {

    private final PipelineProperties properties;

    public PipelineConfig(PipelineProperties properties) {
        this.properties = properties;
    }

    public List<PlanStep> loadPreset(String presetName) {
        PipelineProperties.PipelinePreset preset = properties.getPresets().get(presetName);
        if (preset == null || preset.getSteps() == null) {
            return Collections.emptyList();
        }

        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < preset.getSteps().size(); i++) {
            PipelineProperties.PipelineStep cfg = preset.getSteps().get(i);
            StepMode mode = "parallel".equalsIgnoreCase(cfg.getMode())
                    ? StepMode.PARALLEL : StepMode.SEQUENTIAL;

            steps.add(PlanStep.builder()
                    .stepIndex(i)
                    .mode(mode)
                    .agentName(cfg.getAgent())
                    .agentNames(cfg.getAgents())
                    .build());
        }
        return steps;
    }

    public Set<String> availablePresets() {
        return properties.getPresets().keySet();
    }
}
