package com.suke.agent.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopicExtractorTest {

    private TopicExtractor extractor;

    @BeforeEach
    void setUp() {
        // 使用无 LLM 的构造函数，mock embedding
        extractor = new TopicExtractor(null, null);
    }

    @Nested
    @DisplayName("余弦相似度计算")
    class CosineSimilarity {

        @Test
        @DisplayName("相同向量相似度为 1.0")
        void sameVector_similarityOne() {
            float[] vec = {0.1f, 0.2f, 0.3f};
            double sim = TopicExtractor.cosineSimilarity(vec, vec);
            assertEquals(1.0, sim, 0.001);
        }

        @Test
        @DisplayName("正交向量相似度为 0")
        void orthogonalVector_similarityZero() {
            float[] a = {1.0f, 0.0f};
            float[] b = {0.0f, 1.0f};
            double sim = TopicExtractor.cosineSimilarity(a, b);
            assertEquals(0.0, sim, 0.001);
        }

        @Test
        @DisplayName("相似向量相似度 > 0.85")
        void similarVector_highSimilarity() {
            float[] a = {0.5f, 0.5f, 0.5f, 0.5f};
            float[] b = {0.52f, 0.48f, 0.51f, 0.49f};
            double sim = TopicExtractor.cosineSimilarity(a, b);
            assertTrue(sim > 0.99);
        }

        @Test
        @DisplayName("不同方向向量相似度 < 0.5")
        void differentVector_lowSimilarity() {
            float[] a = {1.0f, 0.0f, 0.0f};
            float[] b = {0.0f, 1.0f, 0.0f};
            double sim = TopicExtractor.cosineSimilarity(a, b);
            assertTrue(sim < 0.5);
        }
    }

    @Nested
    @DisplayName("Topic 标签提取")
    class LabelExtraction {

        @Test
        @DisplayName("短消息直接作为标签")
        void shortMessage_usedAsLabel() {
            String label = TopicExtractor.extractLabel("分析销售趋势");
            assertEquals("分析销售趋势", label);
        }

        @Test
        @DisplayName("长消息截取前 20 字符并加省略号")
        void longMessage_truncated() {
            String msg = "请帮我分析一下最近一个季度各区域的销售数据变化趋势和利润率表现";
            String label = TopicExtractor.extractLabel(msg);
            assertTrue(label.length() <= 23); // 20 chars + "..."
            assertTrue(label.startsWith("请帮我分析一下最近一个季度各区域的"));
        }

        @Test
        @DisplayName("空消息返回空标签")
        void emptyMessage_returnsEmpty() {
            assertEquals("", TopicExtractor.extractLabel(""));
            assertEquals("", TopicExtractor.extractLabel(null));
        }
    }

    @Nested
    @DisplayName("语义聚类匹配")
    class SemanticClustering {

        @Test
        @DisplayName("无已有 topic 时创建新 topic")
        void noExistingTopics_createsNew() {
            String existing = "[]";
            float[] newEmbedding = {0.5f, 0.5f};

            String result = extractor.matchOrCreateTopic(existing, newEmbedding, "销售分析");

            assertTrue(result.contains("销售分析"));
        }

        @Test
        @DisplayName("相似度 > 阈值时归入已有 topic，count+1")
        void highSimilarity_incrementExisting() {
            String existing = "[{\"topic\":\"销售趋势\",\"count\":3,\"embedding\":[0.5,0.5,0.5,0.5]}]";
            float[] newEmbedding = {0.52f, 0.48f, 0.51f, 0.49f}; // very similar

            String result = extractor.matchOrCreateTopic(existing, newEmbedding, "分析销售趋势");

            assertTrue(result.contains("\"count\":4"), "相似 topic 应 count+1");
        }

        @Test
        @DisplayName("相似度 < 阈值时创建新 topic")
        void lowSimilarity_createNew() {
            String existing = "[{\"topic\":\"销售趋势\",\"count\":3,\"embedding\":[1.0,0.0,0.0,0.0]}]";
            float[] newEmbedding = {0.0f, 1.0f, 0.0f, 0.0f}; // orthogonal

            String result = extractor.matchOrCreateTopic(existing, newEmbedding, "SQL查询优化");

            assertTrue(result.contains("SQL查询优化"));
            assertTrue(result.contains("销售趋势")); // 旧 topic 保留
        }

        @Test
        @DisplayName("已达上限时替换 count 最低的 topic")
        void atCapacity_replacesLowestCount() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 10; i++) {
                if (i > 0) sb.append(",");
                sb.append(String.format("{\"topic\":\"topic_%d\",\"count\":%d,\"embedding\":[%d.0,%d.0]}",
                        i, i + 1, i, i + 1));
            }
            sb.append("]");
            String existing = sb.toString();

            float[] newEmbedding = {0.0f, 0.0f, 0.0f}; // 3D vs 2D → length mismatch → similarity=0
            String result = extractor.matchOrCreateTopic(existing, newEmbedding, "新topic");

            JSONArray parsed = JSON.parseArray(result);
            assertEquals(10, parsed.size(), "应保持 MAX_TOPICS=10");
            // 新 topic 应存在
            boolean hasNew = false;
            for (int i = 0; i < parsed.size(); i++) {
                if ("新topic".equals(parsed.getJSONObject(i).getString("topic"))) {
                    hasNew = true;
                    break;
                }
            }
            assertTrue(hasNew, "新 topic 应存在");
            // topic_0 (count=1) 应被替换
            boolean hasOld = false;
            for (int i = 0; i < parsed.size(); i++) {
                if ("topic_0".equals(parsed.getJSONObject(i).getString("topic"))) {
                    hasOld = true;
                    break;
                }
            }
            assertFalse(hasOld, "count 最低的 topic_0 应被替换");
        }

        @Test
        @DisplayName("空 JSON 或 null 返回新 topic")
        void emptyJson_createsNew() {
            String result1 = extractor.matchOrCreateTopic("null", new float[]{0.5f}, "新主题");
            assertTrue(result1.contains("新主题"));

            String result2 = extractor.matchOrCreateTopic("", new float[]{0.5f}, "新主题");
            assertTrue(result2.contains("新主题"));
        }
    }
}
