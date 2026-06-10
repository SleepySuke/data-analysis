/**
 * @author 自然醒
 * @version 1.1
 * @date 2026-05-31
 * @description SqlAnalyst约束Hook，确保Agent先查询表结构再执行SQL。
 *              使用ThreadLocal + beforeAgent/afterAgent生命周期实现请求级状态隔离。
 */

package com.suke.agent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SchemaFirstHook extends AgentHook {

    private static final String SCHEMA_TOOL = "introspectSchema";
    private static final String BLOCKED_MESSAGE = "请先调用 introspectSchema 查询表结构，再执行 SQL 查询";

    final ThreadLocal<Set<String>> calledTools = ThreadLocal.withInitial(HashSet::new);

    @Override
    public String getName() {
        return "schema_first_hook";
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        calledTools.get().clear();
        return super.beforeAgent(state, config);
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        calledTools.remove();
        return super.afterAgent(state, config);
    }

    @Override
    public List<ToolInterceptor> getToolInterceptors() {
        return List.of(new SchemaFirstInterceptor(calledTools));
    }

    static class SchemaFirstInterceptor extends ToolInterceptor {

        private final ThreadLocal<Set<String>> calledTools;

        SchemaFirstInterceptor(ThreadLocal<Set<String>> calledTools) {
            this.calledTools = calledTools;
        }

        @Override
        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
            String toolName = request.getToolName();
            Set<String> tools = calledTools.get();

            if (toolName.contains("schema") || toolName.contains("introspect")) {
                tools.add(SCHEMA_TOOL);
                return handler.call(request);
            }

            if (toolName.contains("sql_execution") || toolName.contains("executeSql")) {
                if (!tools.contains(SCHEMA_TOOL)) {
                    return ToolCallResponse.error(toolName, request.getToolCallId(), BLOCKED_MESSAGE);
                }
                return handler.call(request);
            }

            return handler.call(request);
        }

        @Override
        public String getName() {
            return "schema_first_interceptor";
        }
    }
}
