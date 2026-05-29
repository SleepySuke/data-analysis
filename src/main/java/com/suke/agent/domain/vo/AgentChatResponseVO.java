/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent聊天响应VO
 */

package com.suke.agent.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AgentChatResponseVO {
    private String messageId;
    private String content;
    private List<ArtifactVO> artifacts;
    private String traceId;
}
