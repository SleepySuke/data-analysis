/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent间Handoff记录，包含源/目标Agent和转交原因
 */

package com.suke.agent.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandoffRecord {
    private String fromAgent;
    private String toAgent;
    private String reason;
    private String context;
    private long timestamp;
}
