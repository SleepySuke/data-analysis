/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.config.PipelineProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelinePropertiesTest {

    @Test
    void defaultValues() {
        PipelineProperties props = new PipelineProperties();
        assertEquals(4, props.getMaxParallelAgents());
        assertEquals(60, props.getParallelTimeoutSeconds());
        assertTrue(props.getPresets().isEmpty());
    }

    @Test
    void presetWithSteps() {
        PipelineProperties.PipelinePreset preset = new PipelineProperties.PipelinePreset();
        preset.setName("多源分析");
        PipelineProperties.PipelineStep step = new PipelineProperties.PipelineStep();
        step.setAgents(List.of("web_scraper", "sql_analyst"));
        step.setMode("parallel");
        preset.setSteps(List.of(step));

        PipelineProperties props = new PipelineProperties();
        props.setPresets(Map.of("multi-source", preset));

        assertEquals(1, props.getPresets().size());
        assertEquals("多源分析", props.getPresets().get("multi-source").getName());
        assertEquals("parallel", props.getPresets().get("multi-source").getSteps().get(0).getMode());
        assertEquals(2, props.getPresets().get("multi-source").getSteps().get(0).getAgents().size());
    }

    @Test
    void presetWithSequentialSteps() {
        PipelineProperties.PipelinePreset preset = new PipelineProperties.PipelinePreset();
        preset.setName("清洗后分析");
        PipelineProperties.PipelineStep step1 = new PipelineProperties.PipelineStep();
        step1.setAgent("data_cleaner");
        step1.setMode("sequential");
        PipelineProperties.PipelineStep step2 = new PipelineProperties.PipelineStep();
        step2.setAgent("data_analyst");
        step2.setMode("sequential");
        preset.setSteps(List.of(step1, step2));

        assertEquals(2, preset.getSteps().size());
        assertEquals("data_cleaner", preset.getSteps().get(0).getAgent());
    }
}
