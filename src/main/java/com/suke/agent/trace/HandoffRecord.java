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
