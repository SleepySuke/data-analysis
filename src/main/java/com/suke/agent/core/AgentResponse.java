/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent响应封装，包含输出、token用量、耗时、状态
 */

package com.suke.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.suke.agent.trace.HandoffRecord;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private String output;
    private String traceId;
    private List<HandoffRecord> handoffs;
    private int totalTokens;
    private long durationMs;
    private String status;
}
