/**
 * @author 自然醒
 * @version 1.1
 * @date 2026-05-31
 * @description DataCleaner约束Hook，确保Agent先生成数据画像再执行清洗操作。
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

public class ProfileFirstHook extends AgentHook {

    private static final String PROFILE_TOOL = "profileData";
    private static final Set<String> CLEAN_TOOLS = Set.of(
            "handleMissingValues", "missing_value",
            "detectOutliers", "outlier_detection",
            "transformData", "data_transform",
            "deduplicate", "deduplication"
    );
    private static final String BLOCKED_MESSAGE = "请先调用 profileData 生成数据画像，再执行清洗操作";

    final ThreadLocal<Set<String>> calledTools = ThreadLocal.withInitial(HashSet::new);

    @Override
    public String getName() {
        return "profile_first_hook";
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
        return List.of(new ProfileFirstInterceptor(calledTools));
    }

    static class ProfileFirstInterceptor extends ToolInterceptor {

        private final ThreadLocal<Set<String>> calledTools;

        ProfileFirstInterceptor(ThreadLocal<Set<String>> calledTools) {
            this.calledTools = calledTools;
        }

        @Override
        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
            String toolName = request.getToolName();
            Set<String> tools = calledTools.get();

            if (toolName.contains("profile") || toolName.contains("profiling")) {
                tools.add(PROFILE_TOOL);
                return handler.call(request);
            }

            if (isCleanTool(toolName) && !tools.contains(PROFILE_TOOL)) {
                return ToolCallResponse.error(toolName, request.getToolCallId(), BLOCKED_MESSAGE);
            }

            return handler.call(request);
        }

        private boolean isCleanTool(String toolName) {
            return CLEAN_TOOLS.stream().anyMatch(toolName::contains);
        }

        @Override
        public String getName() {
            return "profile_first_interceptor";
        }
    }
}
