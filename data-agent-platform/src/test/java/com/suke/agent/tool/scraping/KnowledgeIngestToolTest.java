package com.suke.agent.tool.scraping;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class KnowledgeIngestToolTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeIngestTool tool = new KnowledgeIngestTool(vectorStore);

    @Test
    void ingestContentReturnsSuccess() {
        String result = tool.ingestContent("This is test content for knowledge base.", "{}");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertTrue(json.getIntValue("chunks") >= 1);
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestContentWithMetadata() {
        String result = tool.ingestContent("Content here", "{\"industry\":\"金融\",\"source\":\"web\"}");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestEmptyContentReturnsError() {
        String result = tool.ingestContent("", "{}");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void ingestNullContentReturnsError() {
        String result = tool.ingestContent(null, "{}");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void ingestLongContentIsChunked() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Paragraph ").append(i).append(": ");
            sb.append("This is a sentence with enough words to make a meaningful paragraph. ");
            sb.append("Adding more content to exceed 500 characters per chunk. ");
            sb.append("\n");
        }
        String longContent = sb.toString();

        String result = tool.ingestContent(longContent, "{}");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertTrue(json.getIntValue("chunks") > 1, "Expected multiple chunks for long content, got: " + json.getIntValue("chunks"));
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestContentWithNullMetadata() {
        // null metadata is handled by parseMetadata: returns empty HashMap
        String result = tool.ingestContent("Some valid content", null);
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"), "Expected success with null metadata");
        assertTrue(json.getIntValue("chunks") >= 1);
        verify(vectorStore).add(anyList());
    }

    @Test
    void ingestContentWithInvalidMetadataJson() {
        // "not json" metadata is caught by parseMetadata's catch block → empty HashMap
        String result = tool.ingestContent("Some valid content", "not json");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"), "Expected success with invalid metadata JSON");
        assertTrue(json.getIntValue("chunks") >= 1);
        verify(vectorStore).add(anyList());
    }
}
