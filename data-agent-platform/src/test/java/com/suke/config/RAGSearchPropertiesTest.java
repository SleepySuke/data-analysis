package com.suke.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RAGSearchProperties 默认值验证")
class RAGSearchPropertiesTest {

    @Test
    @DisplayName("默认值-混合检索应关闭")
    void defaults_hybridEnabled_shouldBeFalse() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertFalse(props.isHybridEnabled());
    }

    @Test
    @DisplayName("默认值-重排应关闭")
    void defaults_rerankEnabled_shouldBeFalse() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertFalse(props.isRerankEnabled());
    }

    @Test
    @DisplayName("默认值-相似度阈值应为0.5")
    void defaults_similarityThreshold_shouldBePoint5() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals(0.5, props.getSimilarityThreshold(), 0.001);
    }

    @Test
    @DisplayName("默认值-topK应为5")
    void defaults_topK_shouldBe5() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals(5, props.getTopK());
    }

    @Test
    @DisplayName("默认值-vectorTopK应为10")
    void defaults_vectorTopK_shouldBe10() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals(10, props.getVectorTopK());
    }

    @Test
    @DisplayName("默认值-bm25TopK应为10")
    void defaults_bm25TopK_shouldBe10() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals(10, props.getBm25TopK());
    }

    @Test
    @DisplayName("默认值-rrfK应为60")
    void defaults_rrfK_shouldBe60() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals(60, props.getRrfK());
    }

    @Test
    @DisplayName("默认值-rerankModel应为gte-rerank-v2")
    void defaults_rerankModel_shouldBeGteRerankV2() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals("gte-rerank-v2", props.getRerankModel());
    }

    @Test
    @DisplayName("默认值-rerankTopN应为5")
    void defaults_rerankTopN_shouldBe5() {
        RAGSearchProperties props = new RAGSearchProperties();
        assertEquals(5, props.getRerankTopN());
    }

    @Test
    @DisplayName("Setter应能覆盖默认值")
    void setter_shouldOverrideDefaults() {
        RAGSearchProperties props = new RAGSearchProperties();
        props.setHybridEnabled(true);
        props.setRerankEnabled(true);
        props.setSimilarityThreshold(0.8);
        props.setTopK(3);
        props.setVectorTopK(20);
        props.setBm25TopK(15);
        props.setRrfK(100);
        props.setRerankModel("qwen3-rerank");
        props.setRerankTopN(10);

        assertTrue(props.isHybridEnabled());
        assertTrue(props.isRerankEnabled());
        assertEquals(0.8, props.getSimilarityThreshold(), 0.001);
        assertEquals(3, props.getTopK());
        assertEquals(20, props.getVectorTopK());
        assertEquals(15, props.getBm25TopK());
        assertEquals(100, props.getRrfK());
        assertEquals("qwen3-rerank", props.getRerankModel());
        assertEquals(10, props.getRerankTopN());
    }
}
