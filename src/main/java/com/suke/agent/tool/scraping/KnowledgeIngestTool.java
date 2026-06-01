/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 知识入库工具，将文本内容向量化后写入VectorStore
 */

package com.suke.agent.tool.scraping;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@ConditionalOnBean(VectorStore.class)
public class KnowledgeIngestTool {

    private static final int CHUNK_SIZE = 500;

    private final VectorStore vectorStore;

    public KnowledgeIngestTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "将文本内容向量化后写入知识库")
    public String ingestContent(
            @ToolParam(description = "要入库的文本内容") String content,
            @ToolParam(description = "元数据JSON，如 {\"industry\":\"金融\",\"source\":\"web\"}") String metadata) {

        if (content == null || content.isBlank()) {
            return CsvUtils.errorJson("内容为空，无法入库");
        }

        Map<String, Object> meta = parseMetadata(metadata);
        List<String> chunks = splitContent(content);

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> docMeta = new HashMap<>(meta);
            docMeta.put("chunk_index", i);
            docMeta.put("total_chunks", chunks.size());
            documents.add(new Document(chunks.get(i), docMeta));
        }

        try {
            vectorStore.add(documents);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("chunks", chunks.size());
            return result.toJSONString();
        } catch (Exception e) {
            return CsvUtils.errorJson("入库失败: " + e.getMessage());
        }
    }

    List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();

        if (content.length() <= CHUNK_SIZE) {
            chunks.add(content);
            return chunks;
        }

        String[] paragraphs = content.split("\n+");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append("\n");
            }
            current.append(paragraph);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank() || "{}".equals(metadata.trim())) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(metadata, Map.class);
            return parsed != null ? parsed : new HashMap<>();
        } catch (Exception e) {
            log.warn("Invalid metadata JSON, using empty metadata", e);
            return new HashMap<>();
        }
    }

}
