/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent对话请求DTO
 */

package com.suke.agent.domain.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgentChatDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String message;
    private String sessionId;
}
