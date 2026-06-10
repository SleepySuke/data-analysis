/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 步骤评估结果
 */
package com.suke.agent.core.models;

import lombok.Getter;

@Getter
public enum EvalResult {
    PASS("步骤输出符合预期"),
    RETRY("步骤输出不符合预期，需要重试"),
    REPLAN("步骤执行失败或需要调整计划"),
    FAIL("无法继续执行");

    private final String reason;

    EvalResult(String reason) {
        this.reason = reason;
    }
}
