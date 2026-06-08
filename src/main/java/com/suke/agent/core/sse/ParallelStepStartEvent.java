/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 并行步骤开始事件
 */
package com.suke.agent.core.sse;

import java.util.List;

public record ParallelStepStartEvent(int stepIndex, String mode,
                                      List<String> agents, String input) implements SseEvent {
    @Override
    public String type() { return "step_start"; }
}
