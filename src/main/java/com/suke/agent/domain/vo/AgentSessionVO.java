/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 会话VO
 */

package com.suke.agent.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionVO {
    private String sessionId;
    private String title;
    private String createdAt;
}
