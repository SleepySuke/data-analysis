/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description WebScraper自定义Agent，基于StateGraph编排流水线：fetch → extract → LLM分析 → ingest/output
 */

package com.suke.agent.specialized;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.BaseAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.internal.node.Node;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.prompt.AgentPrompts;
import com.suke.agent.tool.scraping.ContentExtractorTool;
import com.suke.agent.tool.scraping.KnowledgeIngestTool;
import com.suke.agent.tool.scraping.UrlFetchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class WebScraperAgent extends BaseAgent {

    private static final String STATE_FETCH_RESULT = "fetchResult";
    private static final String STATE_EXTRACT_RESULT = "extractResult";
    private static final String STATE_ANALYZE_RESULT = "analyzeResult";
    private static final String STATE_FINAL_OUTPUT = "finalOutput";
    private static final String STATE_USER_MESSAGE = "messages";
    private static final String STATE_ERROR = "error";

    private static final String NODE_FETCH = "fetch";
    private static final String NODE_EXTRACT = "extract";
    private static final String NODE_ANALYZE = "analyze";
    private static final String NODE_INGEST = "ingest";
    private static final String NODE_OUTPUT = "output";

    private static final String ROUTE_INGEST = "ingest";
    private static final String ROUTE_OUTPUT = "output";

    private final ChatModel chatModel;
    private final UrlFetchTool fetchTool;
    private final ContentExtractorTool extractTool;
    private final KnowledgeIngestTool ingestTool;

    public WebScraperAgent(String name, String description,
                          ChatModel chatModel,
                          UrlFetchTool fetchTool,
                          ContentExtractorTool extractTool,
                          @Nullable KnowledgeIngestTool ingestTool,
                          @Nullable BaseCheckpointSaver saver) {
        super(name, description, true, false, "output", KeyStrategy.REPLACE);
        this.chatModel = chatModel;
        this.fetchTool = fetchTool;
        this.extractTool = extractTool;
        this.ingestTool = ingestTool;
        this.compileConfig = saver != null
                ? CompileConfig.builder().saverConfig(
                        com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig.builder()
                                .register(saver).build()).build()
                : null;
    }

    @Override
    protected StateGraph initGraph() throws com.alibaba.cloud.ai.graph.exception.GraphStateException {
        StateGraph g = new StateGraph();

        g.addNode(NODE_FETCH, fetchAction());
        g.addNode(NODE_EXTRACT, extractAction());
        g.addNode(NODE_ANALYZE, analyzeAction());
        g.addNode(NODE_INGEST, ingestAction());
        g.addNode(NODE_OUTPUT, outputAction());

        g.addEdge(StateGraph.START, NODE_FETCH);
        g.addEdge(NODE_FETCH, NODE_EXTRACT);
        g.addEdge(NODE_EXTRACT, NODE_ANALYZE);
        g.addConditionalEdges(NODE_ANALYZE, routeAfterAnalyze(),
                Map.of(ROUTE_INGEST, NODE_INGEST, ROUTE_OUTPUT, NODE_OUTPUT));
        g.addEdge(NODE_INGEST, NODE_OUTPUT);
        g.addEdge(NODE_OUTPUT, StateGraph.END);

        return g;
    }

    @Override
    public Node asNode(boolean allowSubGraph, boolean mergeState) {
        return new Node(name());
    }

    // --- Node Actions ---

    private AsyncNodeAction fetchAction() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                String userMessage = extractUserMessage(state);
                String url = extractUrl(userMessage);

                if (url == null || url.isBlank()) {
                    result.put(STATE_ERROR, "无法从消息中识别URL，请提供有效的HTTP/HTTPS链接");
                    result.put(STATE_FETCH_RESULT, "");
                    return result;
                }

                log.info("[WebScraper] Fetching URL: {}", url);
                String fetchResult = fetchTool.fetchUrl(url, 30);
                JSONObject fetchJson = JSON.parseObject(fetchResult);

                if (fetchJson.getBooleanValue("success")) {
                    result.put(STATE_FETCH_RESULT, fetchResult);
                } else {
                    result.put(STATE_ERROR, "抓取失败: " + fetchJson.getString("error"));
                    result.put(STATE_FETCH_RESULT, "");
                }
            } catch (Exception e) {
                log.error("[WebScraper] Fetch failed", e);
                result.put(STATE_ERROR, "抓取异常: " + e.getMessage());
                result.put(STATE_FETCH_RESULT, "");
            }
            return result;
        });
    }

    private AsyncNodeAction extractAction() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                String fetchResult = state.value(STATE_FETCH_RESULT, "");
                if (fetchResult.isBlank()) {
                    result.put(STATE_EXTRACT_RESULT, "{}");
                    return result;
                }

                JSONObject fetchJson = JSON.parseObject(fetchResult);
                String html = fetchJson.getString("html");

                if (html == null || html.isBlank()) {
                    result.put(STATE_EXTRACT_RESULT, "{}");
                    return result;
                }

                log.info("[WebScraper] Extracting content from HTML ({} chars)", html.length());
                String extractResult = extractTool.extractContent(html, "all");
                result.put(STATE_EXTRACT_RESULT, extractResult);
            } catch (Exception e) {
                log.error("[WebScraper] Extract failed", e);
                result.put(STATE_EXTRACT_RESULT, "{}");
            }
            return result;
        });
    }

    private AsyncNodeAction analyzeAction() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                String extractResult = state.value(STATE_EXTRACT_RESULT, "{}");
                String userMessage = extractUserMessage(state);
                String error = state.value(STATE_ERROR, "");

                if (!error.isBlank()) {
                    result.put(STATE_ANALYZE_RESULT, JSON.toJSONString(Map.of(
                            "shouldIngest", false,
                            "analysis", "抓取阶段失败: " + error
                    )));
                    return result;
                }

                String analyzePrompt = AgentPrompts.WEB_SCRAPER_ANALYZE
                        + "\n\n提取结果：\n" + extractResult
                        + "\n\n用户原始请求：" + userMessage;

                String llmResponse = chatModel.call(analyzePrompt);
                result.put(STATE_ANALYZE_RESULT, llmResponse);
            } catch (Exception e) {
                log.error("[WebScraper] Analyze failed", e);
                result.put(STATE_ANALYZE_RESULT, "{\"shouldIngest\":false,\"analysis\":\"LLM分析失败\"}");
            }
            return result;
        });
    }

    private AsyncNodeAction ingestAction() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            if (ingestTool == null) {
                log.info("[WebScraper] KnowledgeIngestTool not available, skipping ingest");
                result.put("ingestResult", "{\"skipped\":true,\"reason\":\"VectorStore不可用\"}");
                return result;
            }

            try {
                String extractResult = state.value(STATE_EXTRACT_RESULT, "{}");
                JSONObject extractJson = JSON.parseObject(extractResult);

                String content = extractJson.getString("content");
                if (content == null || content.isBlank()) {
                    content = extractResult;
                }

                log.info("[WebScraper] Ingesting content ({} chars)", content.length());
                String ingestResult = ingestTool.ingestContent(content,
                        "{\"source\":\"web_scraper\",\"agent\":\"web_scraper\"}");
                result.put("ingestResult", ingestResult);
            } catch (Exception e) {
                log.error("[WebScraper] Ingest failed", e);
                result.put("ingestResult", JSON.toJSONString(Map.of("success", false, "error", e.getMessage())));
            }
            return result;
        });
    }

    private AsyncNodeAction outputAction() {
        return state -> CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new HashMap<>();
            try {
                String fetchResult = state.value(STATE_FETCH_RESULT, "");
                String extractResult = state.value(STATE_EXTRACT_RESULT, "");
                String analyzeResult = state.value(STATE_ANALYZE_RESULT, "");
                String ingestResult = state.value("ingestResult", "");
                String error = state.value(STATE_ERROR, "");

                JSONObject output = new JSONObject();
                output.put("agent", "web_scraper");
                output.put("success", error.isBlank());
                if (!error.isBlank()) {
                    output.put("error", error);
                }
                if (!fetchResult.isBlank()) {
                    JSONObject fetchJson = JSON.parseObject(fetchResult);
                    output.put("url", fetchJson.getString("url"));
                    output.put("statusCode", fetchJson.getIntValue("statusCode"));
                }
                if (!extractResult.isBlank() && !"{}".equals(extractResult)) {
                    JSONObject extractJson = JSON.parseObject(extractResult);
                    output.put("title", extractJson.getString("title"));
                    output.put("tables", extractJson.getJSONArray("tables"));
                    output.put("contentPreview", truncate(extractJson.getString("content"), 500));
                }
                if (!analyzeResult.isBlank()) {
                    output.put("analysis", analyzeResult);
                }
                if (!ingestResult.isBlank()) {
                    output.put("ingestResult", JSON.parseObject(ingestResult));
                }

                String finalOutput = output.toJSONString();
                result.put(STATE_FINAL_OUTPUT, finalOutput);
                result.put("output", finalOutput);
                log.info("[WebScraper] Pipeline complete, output size: {} chars", finalOutput.length());
            } catch (Exception e) {
                log.error("[WebScraper] Output formatting failed", e);
                result.put("output", JSON.toJSONString(Map.of("success", false, "error", e.getMessage())));
            }
            return result;
        });
    }

    // --- Edge Routing ---

    private AsyncEdgeAction routeAfterAnalyze() {
        return state -> {
            String analyzeResult = state.value(STATE_ANALYZE_RESULT, "");
            boolean shouldIngest = parseShouldIngest(analyzeResult);
            return CompletableFuture.completedFuture(shouldIngest ? ROUTE_INGEST : ROUTE_OUTPUT);
        };
    }

    private boolean parseShouldIngest(String analyzeResult) {
        try {
            JSONObject json = JSON.parseObject(analyzeResult);
            if (json != null && json.containsKey("shouldIngest")) {
                return json.getBooleanValue("shouldIngest");
            }
        } catch (Exception ignored) {
        }
        return analyzeResult.toLowerCase().contains("shouldingest")
                && analyzeResult.contains("true");
    }

    // --- Helpers ---

    private String extractUserMessage(OverAllState state) {
        Object messages = state.data().get("messages");
        if (messages instanceof List<?> list && !list.isEmpty()) {
            Object last = list.get(list.size() - 1);
            return last.toString();
        }
        return state.value("input", "");
    }

    private String extractUrl(String text) {
        if (text == null) return null;
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.startsWith("http://") || word.startsWith("https://")) {
                return word;
            }
        }
        return null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
