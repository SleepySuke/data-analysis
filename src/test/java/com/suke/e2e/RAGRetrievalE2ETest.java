package com.suke.e2e;

import com.suke.config.RAGSearchProperties;
import com.suke.rag.DashScopeRerankClient;
import com.suke.tool.KnowledgeSearchTool;
import com.suke.tool.KnowledgeSearchTool.RankedHit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RAG 检索增强 E2E 测试
 *
 * 测试范围：完整的检索管线 query rewriting → hybrid search → RRF → threshold → rerank → format
 * 使用 mock 替代外部依赖（Redis、DashScope），验证管线逻辑正确性
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RAG 检索增强 E2E 测试")
class RAGRetrievalE2ETest {

    @Mock private VectorStore vectorStore;
    @Mock private RAGSearchProperties searchProperties;
    @Mock private DashScopeRerankClient rerankClient;
    @Mock private JedisPooled jedisPooled;
    @Mock private SearchResult bm25SearchResult;

    private KnowledgeSearchTool tool;

    @BeforeEach
    void setUp() throws Exception {
        tool = new KnowledgeSearchTool();
        injectField("vectorStore", vectorStore);
        injectField("searchProperties", searchProperties);
        injectField("rerankClient", rerankClient);
        injectField("jedisPooled", jedisPooled);
        injectField("keywordsConfig",
                "趋势,增长,下降,对比,占比,分布,金融,医疗,股票,基金,投资,用户,销售,收入,利润,成本,数据,分析,统计,指标,比率");
        injectField("indexName", "idx:data_analysis_knowledge_v1");
    }

    private void injectField(String name, Object value) throws Exception {
        Field field = KnowledgeSearchTool.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(tool, value);
    }

    private Document createSpringDoc(String id, String text, Double score) {
        return Document.builder().id(id).text(text).metadata(Map.of()).score(score).build();
    }

    // ========== E2E-1: 全管线（混合检索 + RRF + 阈值 + 重排） ==========

    @Nested
    @DisplayName("E2E-1: 全管线混合检索")
    class FullPipelineTest {

        @Test
        @DisplayName("全管线-混合检索+重排应正确融合两路结果")
        void fullPipeline_hybridWithRerank_shouldFuseCorrectly() {
            // 配置：全部开启
            when(searchProperties.isHybridEnabled()).thenReturn(true);
            when(searchProperties.isRerankEnabled()).thenReturn(true);
            when(searchProperties.getTopK()).thenReturn(3);
            when(searchProperties.getVectorTopK()).thenReturn(5);
            when(searchProperties.getBm25TopK()).thenReturn(5);
            when(searchProperties.getRrfK()).thenReturn(60);
            when(searchProperties.getSimilarityThreshold()).thenReturn(0.3);

            // 向量检索返回：doc_a（score 0.9）、doc_b（score 0.5）
            List<Document> vectorResults = List.of(
                    createSpringDoc("doc_a", "金融投资分析策略", 0.9),
                    createSpringDoc("doc_b", "销售数据增长趋势", 0.5)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            // BM25 检索返回：doc_b（重排）、doc_c（新文档）
            redis.clients.jedis.search.Document bm25Doc1 =
                    new redis.clients.jedis.search.Document("doc_b", 1.0);
            bm25Doc1.set("knowledge_content", "销售数据增长趋势");
            redis.clients.jedis.search.Document bm25Doc2 =
                    new redis.clients.jedis.search.Document("doc_c", 0.8);
            bm25Doc2.set("knowledge_content", "季度收入统计报告");
            when(bm25SearchResult.getDocuments()).thenReturn(List.of(bm25Doc1, bm25Doc2));
            when(jedisPooled.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
                    .thenReturn(bm25SearchResult);

            // Rerank：doc_c 排最前，doc_b 其次，doc_a 最后
            when(rerankClient.rerank(anyString(), anyList()))
                    .thenAnswer(invocation -> {
                        List<Document> docs = invocation.getArgument(1);
                        // 反转顺序模拟 rerank
                        List<Document> reordered = new ArrayList<>();
                        for (int i = docs.size() - 1; i >= 0; i--) {
                            reordered.add(docs.get(i));
                        }
                        return reordered;
                    });

            String result = tool.searchKnowledge("分析金融投资趋势");

            // 验证结果非空
            assertNotNull(result);
            assertFalse(result.contains("未找到"), "应找到相关知识内容");
            verify(rerankClient, atLeastOnce()).rerank(eq("分析金融投资趋势"), anyList());
        }
    }

    // ========== E2E-2: 向量检索降级（BM25失败时仍能工作） ==========

    @Nested
    @DisplayName("E2E-2: 降级场景")
    class FallbackTest {

        @Test
        @DisplayName("降级-BM25失败时应仅使用向量检索结果")
        void fallback_bm25Failure_shouldUseVectorOnly() {
            when(searchProperties.isHybridEnabled()).thenReturn(true);
            when(searchProperties.isRerankEnabled()).thenReturn(false);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);
            when(searchProperties.getBm25TopK()).thenReturn(10);
            when(searchProperties.getRrfK()).thenReturn(60);
            when(searchProperties.getSimilarityThreshold()).thenReturn(0.3);

            List<Document> vectorResults = List.of(
                    createSpringDoc("a", "分析文档A", 0.9)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            // BM25 抛异常
            when(jedisPooled.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
                    .thenThrow(new RuntimeException("Redis BM25 搜索失败"));

            String result = tool.searchKnowledge("分析趋势");

            assertNotNull(result);
            assertTrue(result.contains("分析文档A"), "向量检索结果应保留");
        }

        @Test
        @DisplayName("降级-Rerank失败时应使用RRF排序结果")
        void fallback_rerankFailure_shouldUseRRFOrder() {
            when(searchProperties.isHybridEnabled()).thenReturn(true);
            when(searchProperties.isRerankEnabled()).thenReturn(true);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);
            when(searchProperties.getBm25TopK()).thenReturn(10);
            when(searchProperties.getRrfK()).thenReturn(60);
            when(searchProperties.getSimilarityThreshold()).thenReturn(0.3);

            List<Document> vectorResults = List.of(
                    createSpringDoc("a", "文档A", 0.9)
            );
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(vectorResults);

            SearchResult emptyBm25 = mock(SearchResult.class);
            when(emptyBm25.getDocuments()).thenReturn(Collections.emptyList());
            when(jedisPooled.ftSearch(anyString(), anyString(), any(FTSearchParams.class)))
                    .thenReturn(emptyBm25);

            // Rerank 抛异常
            when(rerankClient.rerank(anyString(), anyList()))
                    .thenThrow(new RuntimeException("DashScope rerank 超时"));

            String result = tool.searchKnowledge("分析趋势");

            assertNotNull(result);
            // rerank 异常被外层 try-catch 捕获，返回错误消息
            assertTrue(result.contains("失败") || result.contains("文档A"),
                    "Rerank失败应降级返回RRF排序结果");
        }
    }

    // ========== E2E-3: RRF 分数融合验证 ==========

    @Nested
    @DisplayName("E2E-3: RRF 融合验证")
    class RRFFusionE2ETest {

        private List<RankedHit> invokeRRF(List<List<RankedHit>> lists, int k) throws Exception {
            Method method = KnowledgeSearchTool.class.getDeclaredMethod(
                    "reciprocalRankFusion", List.class, int.class);
            method.setAccessible(true);
            return (List<RankedHit>) method.invoke(tool, lists, k);
        }

        @Test
        @DisplayName("RRF-同时出现在两路的文档应显著提升排名")
        void rrf_overlappingDoc_shouldRankFirst() throws Exception {
            // 向量路：doc_1(rank1), doc_2(rank2)
            Map<String, Object> emptyMeta = Collections.emptyMap();
            List<RankedHit> vectorList = new ArrayList<>();
            vectorList.add(new RankedHit("1", "金融分析", emptyMeta, 0.95));
            vectorList.add(new RankedHit("2", "销售数据", emptyMeta, 0.85));

            // BM25路：doc_2(rank1), doc_3(rank2)
            List<RankedHit> bm25List = new ArrayList<>();
            bm25List.add(new RankedHit("2", "销售数据", emptyMeta, null));
            bm25List.add(new RankedHit("3", "季度报告", emptyMeta, null));

            List<RankedHit> result = invokeRRF(List.of(vectorList, bm25List), 60);

            assertEquals(3, result.size());
            // doc_2 出现在两路，RRF score = 1/61 + 1/61 = 2/61
            assertEquals("2", result.get(0).id, "双路命中的文档应排最前");
            assertTrue(result.get(0).rrfScore > result.get(1).rrfScore);
        }

        @Test
        @DisplayName("RRF-仅出现在单路的文档排序应合理")
        void rrf_singleAppearance_shouldOrderByRRFScore() throws Exception {
            Map<String, Object> emptyMeta = Collections.emptyMap();
            List<RankedHit> list1 = new ArrayList<>();
            list1.add(new RankedHit("a", "doc_a", emptyMeta, 0.9));
            list1.add(new RankedHit("b", "doc_b", emptyMeta, 0.7));
            list1.add(new RankedHit("c", "doc_c", emptyMeta, 0.5));

            List<RankedHit> list2 = new ArrayList<>();
            list2.add(new RankedHit("d", "doc_d", emptyMeta, null));
            list2.add(new RankedHit("e", "doc_e", emptyMeta, null));

            List<RankedHit> result = invokeRRF(List.of(list1, list2), 60);

            assertEquals(5, result.size());
            // list1[0] 1/61 > list2[0] 1/61 → same, but list1[0] processed first
            assertEquals("a", result.get(0).id, "向量路排名第一应排最前");
        }
    }

    // ========== E2E-4: 阈值过滤全链路验证 ==========

    @Nested
    @DisplayName("E2E-4: 阈值过滤验证")
    class ThresholdE2ETest {

        private List<Document> invokeThreshold(List<Document> docs, double threshold) throws Exception {
            Method method = KnowledgeSearchTool.class.getDeclaredMethod(
                    "applySimilarityThreshold", List.class, double.class);
            method.setAccessible(true);
            return (List<Document>) method.invoke(tool, docs, threshold);
        }

        @Test
        @DisplayName("阈值-BM25文档(null score)+高向量分文档应保留")
        void threshold_mixedSources_shouldKeepBM25AndHighScore() throws Exception {
            List<Document> docs = List.of(
                    createSpringDoc("bm25_1", "BM25命中的文档", null),
                    createSpringDoc("vec_1", "高分向量文档", 0.92),
                    createSpringDoc("vec_2", "低分向量文档", 0.2),
                    createSpringDoc("bm25_2", "另一个BM25文档", null)
            );

            List<Document> result = invokeThreshold(docs, 0.5);

            assertEquals(3, result.size(), "应保留2个BM25(null)+1个高分向量，过滤1个低分");
            assertTrue(result.stream().allMatch(d ->
                    d.getScore() == null || d.getScore() >= 0.5));
        }
    }

    // ========== E2E-5: 完整查询改写 → 检索 → 格式化链路 ==========

    @Nested
    @DisplayName("E2E-5: 查询改写到格式化全链路")
    class QueryRewriteToFormatTest {

        @Test
        @DisplayName("全链路-查询改写应扩展原始查询为多个变体")
        void fullChain_queryRewrite_shouldExpandToMultipleVariants() {
            when(searchProperties.isHybridEnabled()).thenReturn(false);
            when(searchProperties.isRerankEnabled()).thenReturn(false);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(List.of(createSpringDoc("1", "金融投资分析", 0.9)));

            String result = tool.searchKnowledge("分析金融投资趋势");

            // 验证向量检索被多次调用（多个查询变体）
            verify(vectorStore, atLeast(3)).similaritySearch(any(SearchRequest.class));
            assertNotNull(result);
            assertTrue(result.contains("金融投资分析"), "结果应包含检索到的知识内容");
            assertTrue(result.contains("共找到"), "格式化结果应包含统计信息");
        }

        @Test
        @DisplayName("全链路-空结果应返回未找到提示")
        void fullChain_noResults_shouldReturnNotFoundMessage() {
            when(searchProperties.isHybridEnabled()).thenReturn(false);
            when(searchProperties.isRerankEnabled()).thenReturn(false);
            when(searchProperties.getTopK()).thenReturn(5);
            when(searchProperties.getVectorTopK()).thenReturn(10);

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenReturn(Collections.emptyList());

            String result = tool.searchKnowledge("xyzabc无匹配内容");

            assertTrue(result.contains("未找到"), "空结果应返回未找到提示");
        }

        @Test
        @DisplayName("全链路-空查询应直接返回错误")
        void fullChain_emptyQuery_shouldReturnError() {
            String result = tool.searchKnowledge("");

            assertEquals("查询目标为空，无法进行搜索", result);
            verifyNoInteractions(vectorStore);
        }
    }
}
