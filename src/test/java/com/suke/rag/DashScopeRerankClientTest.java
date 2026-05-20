package com.suke.rag;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashScopeRerankClient 单元测试")
class DashScopeRerankClientTest {

    @Mock
    private RestTemplate restTemplate;

    private DashScopeRerankClient client;

    @BeforeEach
    void setUp() {
        client = new DashScopeRerankClient(restTemplate, "test-api-key", "gte-rerank-v2", 5);
    }

    private Document createDoc(String text) {
        return Document.builder().id("doc_" + text.hashCode()).text(text).metadata(Map.of()).build();
    }

    private String buildSuccessResponse(List<int[]> indexScorePairs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"output\":{\"results\":[");
        for (int i = 0; i < indexScorePairs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"index\":").append(indexScorePairs.get(i)[0])
              .append(",\"relevance_score\":").append(indexScorePairs.get(i)[1])
              .append(",\"document\":{\"text\":\"doc" + indexScorePairs.get(i)[0] + "\"}}");
        }
        sb.append("]},\"usage\":{\"total_tokens\":100}}");
        return sb.toString();
    }

    @Test
    @DisplayName("rerank-成功应返回按相关性重排的文档")
    void rerank_success_shouldReturnReorderedDocs() {
        List<Document> docs = new ArrayList<>();
        docs.add(createDoc("文档内容关于销售数据分析"));
        docs.add(createDoc("文档内容关于用户增长趋势"));
        docs.add(createDoc("金融投资策略分析"));

        // index 2 排最前，index 0 其次，index 1 最后
        String responseBody = buildSuccessResponse(List.of(
                new int[]{2, 95},
                new int[]{0, 80},
                new int[]{1, 60}
        ));

        ResponseEntity<String> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Document> result = client.rerank("金融投资分析", docs);

        assertEquals(3, result.size());
        assertEquals("金融投资策略分析", result.get(0).getText());
        assertEquals("文档内容关于销售数据分析", result.get(1).getText());
        assertEquals("文档内容关于用户增长趋势", result.get(2).getText());
    }

    @Test
    @DisplayName("rerank-API异常应返回原始文档列表")
    void rerank_apiFailure_shouldReturnOriginalDocs() {
        List<Document> docs = new ArrayList<>();
        docs.add(createDoc("文档A"));
        docs.add(createDoc("文档B"));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        List<Document> result = client.rerank("查询", docs);

        assertEquals(2, result.size());
        assertEquals("文档A", result.get(0).getText());
        assertEquals("文档B", result.get(1).getText());
    }

    @Test
    @DisplayName("rerank-空输入应返回空列表")
    void rerank_emptyInput_shouldReturnEmpty() {
        List<Document> result = client.rerank("查询", Collections.emptyList());
        assertTrue(result.isEmpty());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("rerank-错误响应体应返回原始文档列表")
    void rerank_errorResponse_shouldReturnOriginalDocs() {
        List<Document> docs = new ArrayList<>();
        docs.add(createDoc("文档A"));

        ResponseEntity<String> response = new ResponseEntity<>("invalid json", HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Document> result = client.rerank("查询", docs);

        assertEquals(1, result.size());
        assertEquals("文档A", result.get(0).getText());
    }

    @Test
    @DisplayName("rerank-非200响应应返回原始文档列表")
    void rerank_non200Response_shouldReturnOriginalDocs() {
        List<Document> docs = new ArrayList<>();
        docs.add(createDoc("文档A"));

        ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<Document> result = client.rerank("查询", docs);

        assertEquals(1, result.size());
        assertEquals("文档A", result.get(0).getText());
    }
}
