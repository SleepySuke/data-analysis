/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent响应VO
 */

package com.suke.agent.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AgentResponseVO {
    private String output;
    private String traceId;
    private List<String> handoffAgents;
    private int totalTokens;
    private long durationMs;
    private String status;
}
