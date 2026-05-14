package com.suke.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeSearchTool 单元测试")
class KnowledgeSearchToolTest {

    @InjectMocks
    private KnowledgeSearchTool tool;

    @Mock
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    private void setField(String name, Object value) throws Exception {
        Field field = KnowledgeSearchTool.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(tool, value);
    }

    // ========== #36: 去重排序 ==========

    private List<Document> invokeDeduplicate(List<Document> input) throws Exception {
        Method method = KnowledgeSearchTool.class.getDeclaredMethod("deduplicateResults", List.class);
        method.setAccessible(true);
        return (List<Document>) method.invoke(tool, input);
    }

    private Document createDoc(String id, String text) {
        return Document.builder().id(id).text(text).metadata(Map.of()).build();
    }

    @Test
    @DisplayName("#36 去重应保留首次出现顺序（相关性排序）")
    void deduplicateResults_shouldPreserveFirstOccurrenceOrder() throws Exception {
        List<Document> input = new ArrayList<>();
        input.add(createDoc("a", "text_a_first"));  // 最高相关性
        input.add(createDoc("b", "text_b"));
        input.add(createDoc("a", "text_a_duplicate"));  // 重复，低相关性
        input.add(createDoc("c", "text_c"));

        List<Document> result = invokeDeduplicate(input);

        assertEquals(3, result.size(), "去重后应有3个文档");
        assertEquals("a", result.get(0).getId());
        assertEquals("b", result.get(1).getId());
        assertEquals("c", result.get(2).getId());
        // 验证保留了首次出现的版本
        assertEquals("text_a_first", result.get(0).getText(),
                "应保留首次出现的文档（高相关性）");
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
        assertEquals("first_version", result.get(0).getText(),
                "应保留首次出现的版本");
    }

    // ========== #35: 关键词外部化 ==========

    private List<String> invokeExtractKeywords(String query) throws Exception {
        Method method = KnowledgeSearchTool.class.getDeclaredMethod("extractKeywords", String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(tool, query);
    }

    @Test
    @DisplayName("#35 默认关键词应匹配金融术语")
    void extractKeywords_defaultKeywords_shouldMatchFinancialTerms() throws Exception {
        setField("keywordsConfig", "趋势,增长,下降,对比,占比,分布,金融,医疗,股票,基金,投资,用户,销售,收入,利润,成本,数据,分析,统计,指标,比率");
        List<String> keywords = invokeExtractKeywords("分析金融投资趋势");
        assertFalse(keywords.isEmpty(), "应匹配到关键词");
        assertTrue(keywords.contains("金融"), "应匹配'金融'");
        assertTrue(keywords.contains("投资"), "应匹配'投资'");
        assertTrue(keywords.contains("趋势"), "应匹配'趋势'");
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

    // ========== #34: 服务端行业过滤 ==========

    @Test
    @DisplayName("#34 searchByIndustry应传递filterExpression给VectorStore")
    void searchByIndustry_shouldPassFilterExpressionToVectorStore() {
        // Mock 返回空列表
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(Collections.emptyList());

        tool.searchByIndustry("销售趋势", "金融");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest request = captor.getValue();
        assertNotNull(request.getFilterExpression(),
                "SearchRequest 应包含 filterExpression");
        String filterStr = request.getFilterExpression().toString();
        assertTrue(filterStr.contains("industry"),
                "filterExpression 应包含 'industry' 字段");
        assertTrue(filterStr.contains("金融"),
                "filterExpression 应包含行业值 '金融'");
    }

    @Test
    @DisplayName("#34 searchByIndustry不应做客户端post-filtering")
    void searchByIndustry_shouldNotDoPostFiltering() {
        // Mock 返回5个不同行业的文档
        List<Document> mockResults = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mockResults.add(Document.builder().id("doc_" + i).text("内容" + i)
                    .metadata(Map.of("industry", i == 0 ? "金融" : "其他")).build());
        }
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(mockResults);

        String result = tool.searchByIndustry("查询", "金融");
        // 结果应包含所有5个文档（服务端已过滤，客户端不再二次过滤）
        assertTrue(result.contains("共找到 5 条") || result.contains("5 条"),
                "应返回所有服务端结果，不做客户端过滤");
    }

    @Test
    @DisplayName("#34 searchByIndustry VectorStore异常应返回错误信息")
    void searchByIndustry_vectorStoreException_shouldReturnError() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("connection error"));

        String result = tool.searchByIndustry("查询", "金融");
        assertTrue(result.contains("失败"), "异常时应返回失败提示");
    }
}
