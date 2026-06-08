/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 并行步骤结果事件
 */
package com.suke.agent.core.sse;

import java.util.Map;

public record ParallelStepResultEvent(int stepIndex, String status, long durationMs,
                                       Map<String, String> outputs, String merged) implements SseEvent {
    @Override
    public String type() { return "step_result"; }
}
