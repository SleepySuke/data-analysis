package com.suke.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class DashScopeRerankClient {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-rerank/text-rerank";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final int topN;

    public DashScopeRerankClient(RestTemplate restTemplate,
                                 @Value("${spring.ai.dashscope.api-key}") String apiKey,
                                 @Value("${rag.search.rerank-model:gte-rerank-v2}") String model,
                                 @Value("${rag.search.rerank-top-n:5}") int topN) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.topN = topN;
    }

    public List<Document> rerank(String query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);
            headers.set("Content-Type", "application/json");

            List<String> docTexts = new ArrayList<>();
            for (Document doc : documents) {
                docTexts.add(doc.getText());
            }

            JSONObject body = new JSONObject();
            body.put("model", model);

            JSONObject input = new JSONObject();
            input.put("query", query);
            input.put("documents", docTexts);
            body.put("input", input);

            JSONObject parameters = new JSONObject();
            parameters.put("top_n", Math.min(topN, documents.size()));
            parameters.put("return_documents", true);
            body.put("parameters", parameters);

            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    RERANK_URL, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("DashScope rerank 返回非成功状态: {}", response.getStatusCode());
                return documents;
            }

            return parseRerankResponse(response.getBody(), documents);

        } catch (Exception e) {
            log.warn("DashScope rerank 调用失败，返回原始文档列表: {}", e.getMessage());
            return documents;
        }
    }

    private List<Document> parseRerankResponse(String responseBody, List<Document> originalDocs) {
        try {
            JSONObject json = JSON.parseObject(responseBody);
            JSONObject output = json.getJSONObject("output");
            if (output == null) {
                return originalDocs;
            }

            JSONArray results = output.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return originalDocs;
            }

            List<Document> reranked = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                int index = item.getIntValue("index");
                double score = item.getDoubleValue("relevance_score");
                if (index >= 0 && index < originalDocs.size()) {
                    Document original = originalDocs.get(index);
                    Document scored = Document.builder()
                            .id(original.getId())
                            .text(original.getText())
                            .metadata(original.getMetadata())
                            .score(score)
                            .build();
                    reranked.add(scored);
                }
            }

            return reranked.isEmpty() ? originalDocs : reranked;
        } catch (Exception e) {
            log.warn("解析 DashScope rerank 响应失败: {}", e.getMessage());
            return originalDocs;
        }
    }
}
