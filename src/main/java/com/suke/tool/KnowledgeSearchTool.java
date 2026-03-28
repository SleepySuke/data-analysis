package com.suke.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-02-18 21:54
 * @description 增强检索工具 - 用于Agent调用知识库进行检索增强
 */
@Component
@Slf4j
public class KnowledgeSearchTool {

    @Autowired
    private VectorStore vectorStore;

    // 默认返回的文档数量
    private static final int DEFAULT_TOP_K = 5;

    // 查询改写的关键词提取数量
    private static final int KEYWORD_COUNT = 3;

    /**
     * 知识库搜索工具 - Agent可调用的主要方法
     *
     * @param goal 用户的分析目标/查询意图
     * @return 搜索结果，包含相关的知识内容
     */
    public String searchKnowledge(
            @JsonProperty(value = "goal", required = true)
            @JsonPropertyDescription("用户的分析目标或查询意图，例如：分析销售额趋势、分析用户增长情况")
            String goal) {

        log.info("知识库搜索工具被调用，查询目标: {}", goal);

        if (!StringUtils.hasText(goal)) {
            return "查询目标为空，无法进行搜索";
        }

        try {
            // 1. 查询改写 - 生成多个查询变体提高召回率
            List<String> queries = rewriteQuery(goal);
            log.info("查询改写结果: {}", queries);

            // 2. 执行多查询搜索
            List<Document> allResults = executeMultiQuerySearch(queries);

            // 3. 去重并排序
            List<Document> uniqueResults = deduplicateResults(allResults);

            // 4. 格式化返回结果
            String result = formatSearchResults(goal, uniqueResults);

            log.info("知识库搜索完成，找到 {} 条相关知识", uniqueResults.size());
            return result;

        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return "知识库搜索失败: " + e.getMessage();
        }
    }

    /**
     * 查询改写 - 生成多个查询变体以提高召回率
     *
     * @param originalQuery 原始查询
     * @return 查询变体列表
     */
    private List<String> rewriteQuery(String originalQuery) {
        List<String> queries = new ArrayList<>();

        // 1. 添加原始查询
        queries.add(originalQuery);

        // 2. 提取关键词
        List<String> keywords = extractKeywords(originalQuery);
        if (!keywords.isEmpty()) {
            // 添加关键词组合查询
            queries.addAll(keywords);
        }

        // 3. 生成简化查询（去掉修饰词）
        String simplifiedQuery = simplifyQuery(originalQuery);
        if (!simplifiedQuery.equals(originalQuery)) {
            queries.add(simplifiedQuery);
        }

        // 4. 行业领域扩展
        List<String> domainQueries = expandDomainQuery(originalQuery);
        queries.addAll(domainQueries);

        return queries.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 从查询中提取关键词
     */
    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();

        // 常见的分析相关关键词
        String[] analysisKeywords = {
                "趋势", "增长", "下降", "对比", "占比", "分布",
                "金融", "医疗", "股票", "基金", "投资",
                "用户", "销售", "收入", "利润", "成本",
                "数据", "分析", "统计", "指标", "比率"
        };

        for (String keyword : analysisKeywords) {
            if (query.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        return keywords;
    }

    /**
     * 简化查询 - 去掉修饰词
     */
    private String simplifyQuery(String query) {
        // 去掉常见的修饰词
        return query
                .replace("请帮我", "")
                .replace("帮我", "")
                .replace("分析一下", "分析")
                .replace("看一下", "查看")
                .replace("我想了解", "")
                .replace("我想知道", "")
                .trim();
    }

    /**
     * 领域扩展查询
     */
    private List<String> expandDomainQuery(String query) {
        List<String> expandedQueries = new ArrayList<>();

        // 领域映射
        Map<String, String[]> domainMap = new HashMap<>();
        domainMap.put("销售", new String[]{"收入", "营业额", "业绩"});
        domainMap.put("用户", new String[]{"客户", "会员", "活跃用户"});
        domainMap.put("金融", new String[]{"投资", "理财", "资金", "股票"});
        domainMap.put("医疗", new String[]{"健康", "医院", "诊断"});
        domainMap.put("趋势", new String[]{"变化", "走势", "发展"});

        for (Map.Entry<String, String[]> entry : domainMap.entrySet()) {
            if (query.contains(entry.getKey())) {
                for (String synonym : entry.getValue()) {
                    expandedQueries.add(query.replace(entry.getKey(), synonym));
                }
            }
        }

        return expandedQueries;
    }

    /**
     * 执行多查询搜索
     */
    private List<Document> executeMultiQuerySearch(List<String> queries) {
        List<Document> allResults = new ArrayList<>();

        for (String query : queries) {
            try {
                List<Document> results = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(DEFAULT_TOP_K)
                                .build()
                );

                if (results != null && !results.isEmpty()) {
                    allResults.addAll(results);
                    log.debug("查询 '{}' 找到 {} 条结果", query, results.size());
                }
            } catch (Exception e) {
                log.warn("查询 '{}' 失败: {}", query, e.getMessage());
            }
        }

        return allResults;
    }

    /**
     * 去重结果
     */
    private List<Document> deduplicateResults(List<Document> results) {
        Map<String, Document> uniqueDocs = new HashMap<>();

        for (Document doc : results) {
            String id = doc.getId();
            if (!uniqueDocs.containsKey(id)) {
                uniqueDocs.put(id, doc);
            }
        }

        return new ArrayList<>(uniqueDocs.values());
    }

    /**
     * 格式化搜索结果
     */
    private String formatSearchResults(String goal, List<Document> results) {
        if (results.isEmpty()) {
            return String.format("未找到与 '%s' 相关的知识内容。建议尝试更具体的查询关键词。", goal);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("根据您的分析目标 '%s'，从知识库中找到以下相关内容：\n\n", goal));

        int index = 1;
        for (Document doc : results) {
            sb.append(String.format("【知识 %d】\n", index++));
            sb.append("内容: ").append(doc.getText()).append("\n");

            // 添加元数据信息
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata != null && !metadata.isEmpty()) {
                if (metadata.containsKey("knowledge_type")) {
                    sb.append("类型: ").append(metadata.get("knowledge_type")).append("\n");
                }
                if (metadata.containsKey("industry")) {
                    sb.append("行业: ").append(metadata.get("industry")).append("\n");
                }
                if (metadata.containsKey("chart_type")) {
                    sb.append("推荐图表: ").append(metadata.get("chart_type")).append("\n");
                }
            }
            sb.append("\n");

            // 最多返回5条结果
            if (index > 5) {
                break;
            }
        }

        sb.append("---\n");
        sb.append(String.format("共找到 %d 条相关知识，以上展示前 %d 条。", results.size(), Math.min(5, results.size())));

        return sb.toString();
    }

    /**
     * 根据行业类型进行精确搜索
     *
     * @param query   查询内容
     * @param industry 行业类型（如：金融、医疗等）
     * @return 搜索结果
     */
    public String searchByIndustry(
            @JsonProperty(value = "query", required = true)
            @JsonPropertyDescription("查询内容")
            String query,
            @JsonProperty(value = "industry", required = true)
            @JsonPropertyDescription("行业类型，如：金融、医疗、科技等")
            String industry) {

        log.info("按行业搜索知识库，查询: {}, 行业: {}", query, industry);

        try {
            // 使用元数据过滤进行搜索
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(DEFAULT_TOP_K)
                            // 注意：元数据过滤需要根据 Spring AI 版本调整
                            .build()
            );

            // 手动过滤行业
            List<Document> filteredResults = results.stream()
                    .filter(doc -> {
                        Object docIndustry = doc.getMetadata().get("industry");
                        return docIndustry != null && docIndustry.toString().contains(industry);
                    })
                    .collect(Collectors.toList());

            return formatSearchResults(query + " (行业: " + industry + ")", filteredResults);

        } catch (Exception e) {
            log.error("按行业搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}
