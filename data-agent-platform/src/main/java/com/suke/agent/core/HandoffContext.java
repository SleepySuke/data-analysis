/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-02
 * @description Handoff请求级上下文，使用ThreadLocal实现请求隔离
 */
package com.suke.agent.core;

public class HandoffContext {

    private static final ThreadLocal<HandoffRequest> PENDING = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_AGENT = new ThreadLocal<>();

    private HandoffContext() {}

    public static void setCurrentAgent(String agentName) {
        CURRENT_AGENT.set(agentName);
    }

    public static String getCurrentAgent() {
        return CURRENT_AGENT.get();
    }

    public static void setPending(HandoffRequest request) {
        PENDING.set(request);
    }

    public static HandoffRequest getPending() {
        return PENDING.get();
    }

    public static void clear() {
        PENDING.remove();
        CURRENT_AGENT.remove();
    }
}
