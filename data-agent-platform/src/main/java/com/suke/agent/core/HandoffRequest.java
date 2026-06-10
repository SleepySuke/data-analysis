/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-02
 * @description Handoff转交请求数据结构
 */
package com.suke.agent.core;

public record HandoffRequest(
    String fromAgent,
    String toAgent,
    String reason,
    String context
) {}
