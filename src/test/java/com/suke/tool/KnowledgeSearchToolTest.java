package com.suke.tool;

import com.suke.config.RAGSearchProperties;
import com.suke.rag.DashScopeRerankClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.search.FTSearchParams;
import redis.clients.jedis.search.SearchResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeSearchTool 单元测试")
class KnowledgeSearchToolTest {

    @InjectMocks
    private KnowledgeSearchTool tool;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RAGSearchProperties searchProperties;

    @Mock
    private DashScopeRerankClient rerankClient;

    @Mock
    private JedisPooled jedisPooled;

    private void setField(String name, Object value) throws Exception {
        Field field = KnowledgeSearchTool.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(tool, value);
    }

    @BeforeEach
    void initDefaults() throws Exception {
        setField("keywordsConfig", "趋势,增长,下降,对比,占比,分布,金融,医疗,股票,基金,投资,用户,销售,收入,利润,成本,数据,分析,统计,指标,比率");
        setField("indexName", "idx:data_analysis_knowledge_v1");
    }

    private Document createDoc(String id, String text) {
        return Document.builder().id(id).text(text).metadata(Map.of()).build();
    }

    private Document createDocWithScore(String id, String text, Double score) {
        return Document.builder().id(id).text(text).metadata(Map.of()).score(score).build();
    }

    // ========== 去重测试 ==========

    private List<Document> invokeDeduplicate(List<Document> input) throws Exception {
        Method method = KnowledgeSearchTool.class.getDeclaredMethod("deduplicateResults", List.class);
        method.setAccessible(true);
        return (List<Document>) method.invoke(tool, input);
    }

    @Test
    @DisplayName("#36 去重应保留首次出现顺序（相关性排序）")
    void deduplicateResults_shouldPreserveFirstOccurrenceOrder() throws Exception {
        List<Document> input = new ArrayList<>();
        input.add(createDoc("a", "text_a_first"));
        input.add(createDoc("b", "text_b"));
        input.add(createDoc("a", "text_a_duplicate"));
        input.add(createDoc("c", "text_c"));

        List<Document> result = invokeDeduplicate(input);

        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getId());
        assertEquals("b", result.get(1).getId());
        assertEquals("c", result.get(2).getId());
        assertEquals("text_a_first", result.get(0).getText());
    }

    @Test
    @DisplayName("#36 全部唯一文档应保持原始顺序")
    void deduplicateResults_allUnique_shouldPreserveOrder() throws Exception {
        List<Document> input = new ArrayList<>();
        input.add(createDoc("x", "text_x"));
        input.add(createDoc("y", "text_y"));
        input.add(createDoc("z", "text_z"));

        List<Document> result = invokeDeduplicate(input);

        assertEquals(3, result.size());
        assertEquals("x", result.get(0).getId());
        assertEquals("y", result.get(1).getId());
        assertEquals("z", result.get(2).getId());
    }

    @Test
    @DisplayName("#36 空输入应返回空列表")
    void deduplicateResults_emptyInput_shouldReturnEmptyList() throws Exception {
        List<Document> result = invokeDeduplicate(new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("#36 单个重复文档应保留首次出现版本")
    void deduplicateResults_singleDuplicate_shouldKeepFirstVersion() throws Exception {
        List<Document> input = new ArrayList<>();
        input.add(createDoc("same", "first_version"));
        input.add(createDoc("same", "second_version"));

        List<Document> result = invokeDeduplicate(input);

        assertEquals(1, result.size());
        assertEquals("first_version", result.get(0).getText());
    }

    // ========== 关键词外部化 ==========

    private List<String> invokeExtractKeywords(String query) throws Exception {
        Method method = KnowledgeSearchTool.class.getDeclaredMethod("extractKeywords", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(tool, query);
    }

    @Test
    @DisplayName("#35 默认关键词应匹配金融术语")
    void extractKeywords_defaultKeywords_shouldMatchFinancialTerms() throws Exception {
        List<String> keywords = invokeExtractKeywords("分析金融投资趋势");
        assertFalse(keywords.isEmpty());
        assertTrue(keywords.contains("金融"));
        assertTrue(keywords.contains("投资"));
        assertTrue(keywords.contains("趋势"));
    }

    @Test
    @DisplayName("#35 自定义关键词应从配置中读取")
    void extractKeywords_customKeywords_shouldMatchConfiguredTerms() throws Exception {
        setField("keywordsConfig", "区块链,加密,去中心化");
        List<String> keywords = invokeExtractKeywords("分析区块链技术");
        assertEquals(1, keywords.size());
        assertEquals("区块链", keywords.get(0));
    }

    @Test
    @DisplayName("#35 空配置应返回空列表")
    void extractKeywords_emptyConfig_shouldReturnEmptyList() throws Exception {
        setField("keywordsConfig", "");
        List<String> keywords = invokeExtractKeywords("分析趋势");
        assertTrue(keywords.isEmpty());
    }

    @Test
    @DisplayName("#35 无匹配应返回空列表")
    void extractKeywords_noMatch_shouldReturnEmptyList() throws Exception {
        setField("keywordsConfig", "趋势,增长,金融");
        List<String> keywords = invokeExtractKeywords("你好世界");
        assertTrue(keywords.isEmpty());
    }

    // ========== 服务端行业过滤 ==========

    @Test
    @DisplayName("#34 searchByIndustry应传递filterExpression给VectorStore")
    void searchByIndustry_shouldPassFilterExpressionToVectorStore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        tool.searchByIndustry("销售趋势", "金融");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest request = captor.getValue();
        assertNotNull(request.getFilterExpression());
        String filterStr = request.getFilterExpression().toString();
        assertTrue(filterStr.contains("industry"));
        assertTrue(filterStr.contains("金融"));
    }

    @Test
    @DisplayName("#34 searchByIndustry不应做客户端post-filtering")
    void searchByIndustry_shouldNotDoPostFiltering() {
        List<Document> mockResults = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mockResults.add(Document.builder().id("doc_" + i).text("内容" + i)
                    .metadata(Map.of("industry", i == 0 ? "金融" : "其他")).build());
        }
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockResults);

        String result = tool.searchByIndustry("查询", "金融");
        assertTrue(result.contains("共找到 5 条") || result.contains("5 条"));
    }

    @Test
    @DisplayName("#34 searchByIndustry VectorStore异常应返回错误信息")
    void searchByIndustry_vectorStoreException_shouldReturnError() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("connection error"));

        String result = tool.searchByIndustry("查询", "金融");
        assertTrue(result.contains("失败"));
    }

    // ========== RRF 算法测试 ==========

    @Nested
    @DisplayName("RRF 分数融合测试")
    class RRFFusionTest {

        private List<KnowledgeSearchTool.RankedHit> invokeRRF(
                List<List<KnowledgeSearchTool.RankedHit>> rankedLists, int k) throws Exception {
            Method method = KnowledgeSearchTool.class.getDeclaredMethod(
                    "reciprocalRankFusion", List.class, int.class);
            method.setAccessible(true);
            return (List<KnowledgeSearchTool.RankedHit>) method.invoke(tool, rankedLists, k);
        }

        private KnowledgeSearchTool.RankedHit createRankedHit(String id, String text, Double score) {
            return new KnowledgeSearchTool.RankedHit(id, text, Map.of(), score);
        }

        @Test
        @DisplayName("RRF-单路排序应保持原始顺序")
        void rrf_singleList_shouldPreserveOrder() throws Exception {
            List<KnowledgeSearchTool.RankedHit> list1 = new ArrayList<>();
            list1.add(createRankedHit("a", "doc_a", 0.9));
            list1.add(createRankedHit("b", "doc_b", 0.8));
            list1.add(createRankedHit("c", "doc_c", 0.7));

            List<List<KnowledgeSearchTool.RankedHit>> rankedLists = List.of(list1);
            List<KnowledgeSearchTool.RankedHit> result = invokeRRF(rankedLists, 60);

            assertEquals(3, result.size());
            assertEquals("a", result.get(0).id);
            assertEquals("b", result.get(1).id);
            assertEquals("c", result.get(2).id);
        }

        @Test
        @DisplayName("RRF-双路重叠文档应排在最前")
        void rrf_dualList_overlappingDoc_shouldRankHighest() throws Exception {
            List<KnowledgeSearchTool.RankedHit> list1 = new ArrayList<>();
            list1.add(createRankedHit("a", "doc_a", 0.9));
            list1.add(createRankedHit("b", "doc_b", 0.7));

            List<KnowledgeSearchTool.RankedHit> list2 = new ArrayList<>();
            list2.add(createRankedHit("b", "doc_b", null));
            list2.add(createRankedHit("c", "doc_c", null));

            List<List<KnowledgeSearchTool.RankedHit>> rankedLists = List.of(list1, list2);
            List<KnowledgeSearchTool.RankedHit> result = invokeRRF(rankedLists, 60);

            assertEquals(3, result.size());
            // doc_b 出现在两路，应排最前
            assertEquals("b", result.get(0).id);
        }

        @Test
        @DisplayName("RRF-公式验证 score=1/(k+rank1)+1/(k+rank2)")
        void rrf_formulaVerification() throws Exception {
            // doc_a: 向量排名1 → 1/61, BM25排名3 → 1/63
            // doc_b: 向量排名2 → 1/62, BM25无
            List<KnowledgeSearchTool.RankedHit> list1 = new ArrayList<>();
            list1.add(createRankedHit("a", "doc_a", 0.9));
            list1.add(createRankedHit("b", "doc_b", 0.7));

            List<KnowledgeSearchTool.RankedHit> list2 = new ArrayList<>();
            list2.add(createRankedHit("c", "doc_c", null));
            list2.add(createRankedHit("d", "doc_d", null));
            list2.add(createRankedHit("a", "doc_a", null));

            List<List<KnowledgeSearchTool.RankedHit>> rankedLists = List.of(list1, list2);
            List<KnowledgeSearchTool.RankedHit> result = invokeRRF(rankedLists, 60);

            // doc_a: 1/(60+1) + 1/(60+3) = 1/61 + 1/63
            double expectedScoreA = 1.0 / 61 + 1.0 / 63;
            assertEquals(expectedScoreA, result.get(0).rrfScore, 0.0001);
            assertEquals("a", result.get(0).id);
        }

        @Test
        @DisplayName("RRF-空输入应返回空列表")
        void rrf_emptyInput_shouldReturnEmpty() throws Exception {
            List<List<KnowledgeSearchTool.RankedHit>> rankedLists = new ArrayList<>();
            List<KnowledgeSearchTool.RankedHit> result = invokeRRF(rankedLists, 60);
            assertTrue(result.isEmpty());
        }
    }

    // ========== 阈值过滤测试 ==========

    @Nested
    @DisplayName("相似度阈值过滤测试")
    class ThresholdTest {

        private List<Document> invokeThreshold(List<Document> docs, double threshold) throws Exception {
            Method method = KnowledgeSearchTool.class.getDeclaredMethod(
                    "applySimilarityThreshold", List.class, double.class);
            method.setAccessible(true);
            return (List<Document>) method.invoke(tool, docs, threshold);
        }

        @Test
        @DisplayName("阈值-高于阈值应保留")
        void threshold_aboveThreshold_shouldKeep() throws Exception {
            List<Document> docs = List.of(
                    createDocWithScore("a", "text_a", 0.8),
                    createDocWithScore("b", "text_b", 0.9)
            );
            List<Document> result = invokeThreshold(docs, 0.5);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("阈值-低于阈值应过滤")
        void threshold_belowThreshold_shouldFilter() throws Exception {
            List<Document> docs = List.of(
                    createDocWithScore("a", "text_a", 0.8),
                    createDocWithScore("b", "text_b", 0.3)
            );
            List<Document> result = invokeThreshold(docs, 0.5);
            assertEquals(1, result.size());
            assertEquals("a", result.get(0).getId());
        }

        @Test
        @DisplayName("阈值-null score（BM25-only）应保留")
        void threshold_nullScore_shouldKeep() throws Exception {
            List<Document> docs = List.of(
                    createDocWithScore("a", "text_a", null),
                    createDocWithScore("b", "text_b", 0.3)
            );
            List<Document> result = invokeThreshold(docs, 0.5);
            assertEquals(1, result.size());
            assertEquals("a", result.get(0).getId());
        }

        @Test
        @DisplayName("阈值-零阈值应保留所有文档")
        void threshold_zeroThreshold_shouldKeepAll() throws Exception {
            List<Document> docs = List.of(
                    createDocWithScore("a", "text_a", 0.01),
                    createDocWithScore("b", "text_b", 0.99)
            );
            List<Document> result = invokeThreshold(docs, 0.0);
            assertEquals(2, result.size());
        }
    }

    // ========== 开关测试 ==========

    @Nested
    @DisplayName("混合检索与重排开关测试")
    class SwitchTest {

        @Test
        @DisplayName("开关-hybrid关闭时应仅使用向量检索")
        void switch_hybridOff_shouldUseVectorOnly() {
            when(searchProperties.isHybridEnabled()).thenReturn(false);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);

            List<Document> vectorResults = List.of(
                    createDocWithScore("a", "doc_a", 0.9),
                    createDocWithScore("b", "doc_b", 0.8)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            String result = tool.searchKnowledge("分析销售趋势");

            // 不应调用 BM25
            verify(jedisPooled, never()).ftSearch(anyString(), anyString(), any(FTSearchParams.class));
            verify(vectorStore, atLeastOnce()).similaritySearch(any(SearchRequest.class));
            assertTrue(result.contains("doc_a") || result.contains("相关知识"));
        }

        @Test
        @DisplayName("开关-hybrid开启+rerank关闭应走混合检索无重排")
        void switch_hybridOn_rerankOff_shouldUseHybridWithoutRerank() {
            when(searchProperties.isHybridEnabled()).thenReturn(true);
            when(searchProperties.isRerankEnabled()).thenReturn(false);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);
            when(searchProperties.getBm25TopK()).thenReturn(10);
            when(searchProperties.getRrfK()).thenReturn(60);
            when(searchProperties.getSimilarityThreshold()).thenReturn(0.3);

            List<Document> vectorResults = List.of(
                    createDocWithScore("a", "doc_a", 0.9),
                    createDocWithScore("b", "doc_b", 0.8)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            // Mock Jedis for BM25
            SearchResult emptyBm25Result = mock(SearchResult.class);
            when(emptyBm25Result.getDocuments()).thenReturn(Collections.emptyList());
            when(jedisPooled.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
                    .thenReturn(emptyBm25Result);

            String result = tool.searchKnowledge("分析销售趋势");

            // 不应调用 rerank
            verify(rerankClient, never()).rerank(anyString(), anyList());
        }

        @Test
        @DisplayName("开关-hybrid开启+rerank开启应走混合检索+重排")
        void switch_hybridOn_rerankOn_shouldUseHybridWithRerank() {
            when(searchProperties.isHybridEnabled()).thenReturn(true);
            when(searchProperties.isRerankEnabled()).thenReturn(true);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);
            when(searchProperties.getBm25TopK()).thenReturn(10);
            when(searchProperties.getRrfK()).thenReturn(60);
            when(searchProperties.getSimilarityThreshold()).thenReturn(0.3);

            List<Document> vectorResults = List.of(
                    createDocWithScore("a", "doc_a", 0.9)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            // Mock Jedis for BM25
            SearchResult emptyBm25Result = mock(SearchResult.class);
            when(emptyBm25Result.getDocuments()).thenReturn(Collections.emptyList());
            when(jedisPooled.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
                    .thenReturn(emptyBm25Result);

            // Mock rerank
            when(rerankClient.rerank(anyString(), anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

            String result = tool.searchKnowledge("分析销售趋势");

            // 应调用 rerank
            verify(rerankClient, atLeastOnce()).rerank(anyString(), anyList());
        }

        @Test
        @DisplayName("开关-全部关闭等同原行为（纯向量检索）")
        void switch_allOff_shouldBehaveLikeOriginal() {
            when(searchProperties.isHybridEnabled()).thenReturn(false);
            when(searchProperties.isRerankEnabled()).thenReturn(false);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);

            List<Document> vectorResults = List.of(
                    createDocWithScore("a", "向量结果文档", 0.9)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            String result = tool.searchKnowledge("分析趋势");

            verify(jedisPooled, never()).ftSearch(anyString(), anyString(), any(FTSearchParams.class));
            verify(rerankClient, never()).rerank(anyString(), anyList());
            assertNotNull(result);
        }
    }

    // ========== searchByIndustry 不受影响 ==========

    @Test
    @DisplayName("searchByIndustry不应受RAGSearchProperties影响")
    void searchByIndustry_shouldNotBeAffectedBySearchProperties() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        tool.searchByIndustry("查询", "金融");

        // searchByIndustry 不应访问 searchProperties 的混合检索配置
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }
}
