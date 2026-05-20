package com.suke.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.suke.config.RAGSearchProperties;
import com.suke.rag.DashScopeRerankClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class KnowledgeSearchTool {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RAGSearchProperties searchProperties;

    @Autowired
    private DashScopeRerankClient rerankClient;

    @Autowired
    private JedisPooled jedisPooled;

    @Value("${rag.redis.index-name:idx:data_analysis_knowledge_v1}")
    private String indexName;

    @Value("${rag.search.keywords:趋势,增长,下降,对比,占比,分布,金融,医疗,股票,基金,投资,用户,销售,收入,利润,成本,数据,分析,统计,指标,比率}")
    private String keywordsConfig;

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

            // 2. 执行检索
            List<Document> allResults;
            if (searchProperties.isHybridEnabled()) {
                // 双路检索：向量 + BM25
                allResults = executeHybridSearch(queries, goal);
            } else {
                // 原始行为：纯向量检索
                allResults = executeMultiQuerySearch(queries);
            }

            // 3. 去重
            List<Document> uniqueResults = deduplicateResults(allResults);

            // 4. 相似度阈值过滤（仅混合检索模式下启用）
            if (searchProperties.isHybridEnabled()) {
                uniqueResults = applySimilarityThreshold(
                        uniqueResults, searchProperties.getSimilarityThreshold());
            }

            // 5. 重排（可开关）
            if (searchProperties.isRerankEnabled() && !uniqueResults.isEmpty()) {
                try {
                    uniqueResults = rerankClient.rerank(goal, uniqueResults);
                    log.info("DashScope rerank 完成，结果数: {}", uniqueResults.size());
                } catch (Exception e) {
                    log.warn("Rerank 调用失败，使用原始排序: {}", e.getMessage());
                }
            }

            // 6. Top-K 截断
            int topK = searchProperties.getTopK();
            if (uniqueResults.size() > topK) {
                uniqueResults = uniqueResults.subList(0, topK);
            }

            // 7. 格式化返回结果
            String result = formatSearchResults(goal, uniqueResults);

            log.info("知识库搜索完成，找到 {} 条相关知识", uniqueResults.size());
            return result;

        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return "知识库搜索失败: " + e.getMessage();
        }
    }

    /**
     * 混合检索：对每个查询变体执行向量 + BM25 双路检索，RRF 分数融合
     */
    private List<Document> executeHybridSearch(List<String> queries, String originalGoal) {
        List<List<RankedHit>> allRankedLists = new ArrayList<>();

        for (String query : queries) {
            try {
                // 向量检索
                List<Document> vectorResults = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(searchProperties.getVectorTopK())
                                .build()
                );
                if (vectorResults != null && !vectorResults.isEmpty()) {
                    List<RankedHit> vectorRanked = new ArrayList<>();
                    for (int i = 0; i < vectorResults.size(); i++) {
                        Document doc = vectorResults.get(i);
                        vectorRanked.add(new RankedHit(
                                doc.getId(), doc.getText(), doc.getMetadata(), doc.getScore()));
                    }
                    allRankedLists.add(vectorRanked);
                }

                // BM25 检索
                List<RankedHit> bm25Ranked = executeBM25Search(query);
                if (!bm25Ranked.isEmpty()) {
                    allRankedLists.add(bm25Ranked);
                }
            } catch (Exception e) {
                log.warn("混合检索查询 '{}' 失败: {}", query, e.getMessage());
            }
        }

        // RRF 分数融合
        List<RankedHit> fused = reciprocalRankFusion(allRankedLists, searchProperties.getRrfK());

        // 转回 Document 列表
        List<Document> result = new ArrayList<>();
        for (RankedHit hit : fused) {
            result.add(Document.builder()
                    .id(hit.id)
                    .text(hit.text)
                    .metadata(hit.metadata)
                    .score(hit.originalScore)
                    .build());
        }
        return result;
    }

    /**
     * BM25 全文检索（通过 Jedis FT.SEARCH）
     */
    private List<RankedHit> executeBM25Search(String query) {
        List<RankedHit> results = new ArrayList<>();
        try {
            String searchQuery = "@knowledge_content:" + query;
            FTSearchParams params = FTSearchParams.searchParams()
                    .limit(0, searchProperties.getBm25TopK())
                    .withScores()
                    .dialect(2);

            SearchResult searchResult = jedisPooled.ftSearch(indexName, searchQuery, params);

            for (redis.clients.jedis.search.Document doc : searchResult.getDocuments()) {
                String text = doc.getString("knowledge_content");
                if (text != null && !text.isEmpty()) {
                    Map<String, Object> metadata = new java.util.HashMap<>();
                    for (Map.Entry<String, Object> prop : doc.getProperties()) {
                        String key = prop.getKey();
                        if (!"knowledge_content".equals(key) && prop.getValue() != null) {
                            metadata.put(key, prop.getValue());
                        }
                    }
                    results.add(new RankedHit(doc.getId(), text, metadata, null));
                }
            }
        } catch (Exception e) {
            log.warn("BM25 检索失败，查询 '{}': {}", query, e.getMessage());
        }
        return results;
    }

    /**
     * Reciprocal Rank Fusion（RRF）分数融合
     * score(d) = Σ 1/(k + rank(d))
     */
    List<RankedHit> reciprocalRankFusion(List<List<RankedHit>> rankedLists, int k) {
        Map<String, RankedHit> docMap = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new LinkedHashMap<>();

        for (List<RankedHit> rankedList : rankedLists) {
            for (int rank = 0; rank < rankedList.size(); rank++) {
                RankedHit hit = rankedList.get(rank);
                rrfScores.merge(hit.id, 1.0 / (k + rank + 1), Double::sum);
                docMap.putIfAbsent(hit.id, hit);
            }
        }

        List<RankedHit> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : rrfScores.entrySet()) {
            RankedHit hit = docMap.get(entry.getKey());
            fused.add(new RankedHit(hit.id, hit.text, hit.metadata, hit.originalScore, entry.getValue()));
        }

        fused.sort(Comparator.comparingDouble((RankedHit h) -> h.rrfScore).reversed());
        return fused;
    }

    /**
     * 相似度阈值过滤
     * score == null（BM25-only）保留，score < threshold 过滤
     */
    List<Document> applySimilarityThreshold(List<Document> docs, double threshold) {
        return docs.stream()
                .filter(doc -> doc.getScore() == null || doc.getScore() >= threshold)
                .collect(Collectors.toList());
    }

    /**
     * 内部类：RRF 排名命中
     */
    public static class RankedHit {
        public final String id;
        public final String text;
        public final Map<String, Object> metadata;
        public final Double originalScore;
        public final double rrfScore;

        public RankedHit(String id, String text, Map<String, Object> metadata, Double originalScore) {
            this(id, text, metadata, originalScore, 0.0);
        }

        public RankedHit(String id, String text, Map<String, Object> metadata, Double originalScore, double rrfScore) {
            this.id = id;
            this.text = text;
            this.metadata = metadata;
            this.originalScore = originalScore;
            this.rrfScore = rrfScore;
        }
    }

    private List<String> rewriteQuery(String originalQuery) {
        List<String> queries = new ArrayList<>();

        queries.add(originalQuery);

        List<String> keywords = extractKeywords(originalQuery);
        if (!keywords.isEmpty()) {
            queries.addAll(keywords);
        }

        String simplifiedQuery = simplifyQuery(originalQuery);
        if (!simplifiedQuery.equals(originalQuery)) {
            queries.add(simplifiedQuery);
        }

        List<String> domainQueries = expandDomainQuery(originalQuery);
        queries.addAll(domainQueries);

        return queries.stream().distinct().collect(Collectors.toList());
    }

    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : keywordsConfig.split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty() && query.contains(trimmed)) {
                keywords.add(trimmed);
            }
        }
        return keywords;
    }

    private String simplifyQuery(String query) {
        return query
                .replace("请帮我", "")
                .replace("帮我", "")
                .replace("分析一下", "分析")
                .replace("看一下", "查看")
                .replace("我想了解", "")
                .replace("我想知道", "")
                .trim();
    }

    private List<String> expandDomainQuery(String query) {
        List<String> expandedQueries = new ArrayList<>();

        Map<String, String[]> domainMap = new java.util.HashMap<>();
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

    private List<Document> executeMultiQuerySearch(List<String> queries) {
        List<Document> allResults = new ArrayList<>();

        int topK = searchProperties.getVectorTopK();

        for (String query : queries) {
            try {
                List<Document> results = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(topK)
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

    private List<Document> deduplicateResults(List<Document> results) {
        Map<String, Document> uniqueDocs = new LinkedHashMap<>();
        for (Document doc : results) {
            uniqueDocs.putIfAbsent(doc.getId(), doc);
        }
        return new ArrayList<>(uniqueDocs.values());
    }

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

            int topK = searchProperties.getTopK();
            if (index > topK) {
                break;
            }
        }

        sb.append("---\n");
        int topK = searchProperties.getTopK();
        sb.append(String.format("共找到 %d 条相关知识，以上展示前 %d 条。",
                results.size(), Math.min(topK, results.size())));

        return sb.toString();
    }

    public String searchByIndustry(
            @JsonProperty(value = "query", required = true)
            @JsonPropertyDescription("查询内容")
            String query,
            @JsonProperty(value = "industry", required = true)
            @JsonPropertyDescription("行业类型，如：金融、医疗、科技等")
            String industry) {

        log.info("按行业搜索知识库，查询: {}, 行业: {}", query, industry);

        try {
            FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(searchProperties.getTopK())
                            .filterExpression(filterBuilder.eq("industry", industry).build())
                            .build()
            );

            return formatSearchResults(query + " (行业: " + industry + ")", results);

        } catch (Exception e) {
            log.error("按行业搜索失败", e);
            return "搜索失败: " + e.getMessage();
        }
    }
}
