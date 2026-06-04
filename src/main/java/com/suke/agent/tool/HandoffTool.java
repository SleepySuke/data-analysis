/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-02
 * @description Agent间Handoff转交工具，Agent通过调用此工具触发转交请求
 */
package com.suke.agent.tool;

import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.core.HandoffContext;
import com.suke.agent.core.HandoffRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class HandoffTool {

    private final AgentRegistry agentRegistry;

    public HandoffTool(AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
    }

    @Tool(description = "将当前任务转交给其他专家Agent处理。仅在需要其他Agent协作时使用。")
    public String handoff(
            @ToolParam(description = "目标Agent名称，如 data_cleaner 或 data_analyst") String agent_name,
            @ToolParam(description = "转交原因，简要说明为什么需要转交") String reason) {
        String currentAgent = HandoffContext.getCurrentAgent();
        if (currentAgent == null) {
            return "错误: 无法确定当前Agent，无法执行转交";
        }

        if (!agentRegistry.exists(agent_name)) {
            return "错误: Agent " + agent_name + " 不存在";
        }

        AgentDescriptor descriptor = agentRegistry.getDescriptor(currentAgent);
        if (descriptor.getHandoffs() == null
                || !descriptor.getHandoffs().contains(agent_name)) {
            return "错误: 当前Agent无权转交给 " + agent_name;
        }

        HandoffRequest request = new HandoffRequest(currentAgent, agent_name, reason, null);
        HandoffContext.setPending(request);

        return "已记录转交请求: → " + agent_name + "（原因: " + reason + "）";
    }
}
