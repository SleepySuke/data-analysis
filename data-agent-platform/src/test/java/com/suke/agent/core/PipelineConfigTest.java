/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.config.PipelineProperties;
import com.suke.agent.core.models.StepMode;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelineConfigTest {

    @Test
    void presetLoadedFromProperties() {
        PipelineProperties props = new PipelineProperties();
        PipelineProperties.PipelinePreset preset = new PipelineProperties.PipelinePreset();
        preset.setName("清洗后分析");
        PipelineProperties.PipelineStep step1 = new PipelineProperties.PipelineStep();
        step1.setAgent("data_cleaner");
        step1.setMode("sequential");
        PipelineProperties.PipelineStep step2 = new PipelineProperties.PipelineStep();
        step2.setAgent("data_analyst");
        step2.setMode("sequential");
        preset.setSteps(List.of(step1, step2));
        props.setPresets(Map.of("clean-then-analyze", preset));

        PipelineConfig config = new PipelineConfig(props);
        List<PlanStep> steps = config.loadPreset("clean-then-analyze");

        assertEquals(2, steps.size());
        assertEquals("data_cleaner", steps.get(0).getAgentName());
        assertEquals("data_analyst", steps.get(1).getAgentName());
        assertEquals(StepMode.SEQUENTIAL, steps.get(0).getMode());
    }

    @Test
    void parallelPresetLoadedCorrectly() {
        PipelineProperties props = new PipelineProperties();
        PipelineProperties.PipelinePreset preset = new PipelineProperties.PipelinePreset();
        preset.setName("多源分析");
        PipelineProperties.PipelineStep step1 = new PipelineProperties.PipelineStep();
        step1.setAgents(List.of("web_scraper", "sql_analyst"));
        step1.setMode("parallel");
        PipelineProperties.PipelineStep step2 = new PipelineProperties.PipelineStep();
        step2.setAgent("data_analyst");
        step2.setMode("sequential");
        preset.setSteps(List.of(step1, step2));
        props.setPresets(Map.of("multi-source", preset));

        PipelineConfig config = new PipelineConfig(props);
        List<PlanStep> steps = config.loadPreset("multi-source");

        assertEquals(2, steps.size());
        assertTrue(steps.get(0).isParallel());
        assertEquals(List.of("web_scraper", "sql_analyst"), steps.get(0).getAgentNames());
        assertFalse(steps.get(1).isParallel());
    }

    @Test
    void invalidPresetReturnsEmptyList() {
        PipelineProperties props = new PipelineProperties();
        PipelineConfig config = new PipelineConfig(props);
        List<PlanStep> steps = config.loadPreset("nonexistent");
        assertTrue(steps.isEmpty());
    }

    @Test
    void availablePresetsReturnsKeys() {
        PipelineProperties props = new PipelineProperties();
        PipelineProperties.PipelinePreset p1 = new PipelineProperties.PipelinePreset();
        p1.setName("A");
        p1.setSteps(List.of());
        PipelineProperties.PipelinePreset p2 = new PipelineProperties.PipelinePreset();
        p2.setName("B");
        p2.setSteps(List.of());
        props.setPresets(Map.of("preset-a", p1, "preset-b", p2));

        PipelineConfig config = new PipelineConfig(props);
        assertEquals(2, config.availablePresets().size());
        assertTrue(config.availablePresets().contains("preset-a"));
    }
}
