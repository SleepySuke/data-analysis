package com.suke.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeMap 单元测试")
class KnowledgeMapTest {

    @InjectMocks
    private KnowledgeMap knowledgeMap;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RMap<String, String> redisCacheMap;

    @BeforeEach
    void setUp() throws Exception {
        setField("knowledgePath", "classpath:test-knowledge/*.csv");
        setField("strategy", "full");
    }

    private void setField(String name, Object value) throws Exception {
        Field field = KnowledgeMap.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(knowledgeMap, value);
    }

    // ========== #32: CSV header 解析 ==========

    @Test
    @DisplayName("#32 CSV带注释行：应正确解析header并生成文档")
    void loadCsvDocuments_withCommentLine_shouldParseHeaderCorrectly() {
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        assertNotNull(docs, "文档列表不应为null");
        assertEquals(2, docs.size(), "应生成2个文档（2行数据）");

        Document doc0 = docs.get(0);
        // "content" 列应映射为 Document text
        assertEquals("测试内容第一条", doc0.getText(),
                "content列应映射为Document文本");
        // metadata 应包含 industry/chart_type 等列
        assertEquals("测试", doc0.getMetadata().get("knowledge_type"));
        assertEquals("测试行业", doc0.getMetadata().get("industry"));
        assertEquals("柱状图", doc0.getMetadata().get("chart_type"));
    }

    @Test
    @DisplayName("#32 CSV无注释行：第一行直接作为header")
    void loadCsvDocuments_withoutCommentLine_shouldParseHeaderCorrectly() {
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_no_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        assertNotNull(docs, "文档列表不应为null");
        assertEquals(1, docs.size(), "应生成1个文档");

        Document doc0 = docs.get(0);
        assertEquals("无注释内容", doc0.getText(),
                "content列应映射为Document文本");
        assertEquals("通用", doc0.getMetadata().get("industry"));
    }

    @Test
    @DisplayName("#32 CSV仅有header无数据：应返回空列表")
    void loadCsvDocuments_headerOnly_shouldReturnEmptyList() {
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_header_only.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        assertNotNull(docs, "文档列表不应为null");
        assertTrue(docs.isEmpty(), "仅有header时应返回空列表");
    }

    @Test
    @DisplayName("#32 CSV content字段正确映射为Document.text")
    void loadCsvDocuments_contentFieldMappedCorrectly() {
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        for (Document doc : docs) {
            assertNotNull(doc.getText(), "Document text 不应为null");
            assertFalse(doc.getText().isEmpty(), "Document text 不应为空");
            // text 不应是列名（如 "content"）
            assertNotEquals("content", doc.getText(),
                    "Document text 不应是header列名");
        }
    }

    @Test
    @DisplayName("#32 CSV metadata不应包含header列名作为值")
    void loadCsvDocuments_metadataShouldNotContainHeaderValues() {
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        assertFalse(docs.isEmpty(), "应有文档");
        for (Document doc : docs) {
            for (Map.Entry<String, Object> entry : doc.getMetadata().entrySet()) {
                String key = entry.getKey();
                if (key.equals("source_file") || key.equals("load_time")) continue;
                assertNotEquals("content", entry.getValue(),
                        "metadata value 不应是列名 'content'");
                assertNotEquals("knowledge_type", entry.getValue(),
                        "metadata value 不应是列名 'knowledge_type'");
                assertNotEquals("industry", entry.getValue(),
                        "metadata value 不应是列名 'industry'");
            }
        }
    }

    // ========== #33: 批量写入重试 ==========

    private void invokeAddDocumentsInBatches(List<Document> docs) throws Exception {
        Method method = KnowledgeMap.class.getDeclaredMethod("addDocumentsInBatches", List.class);
        method.setAccessible(true);
        method.invoke(knowledgeMap, docs);
    }

    private List<Document> createTestDocs(int count) {
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            docs.add(Document.builder().id("doc_" + i).text("text_" + i).metadata(Map.of()).build());
        }
        return docs;
    }

    @Test
    @DisplayName("#33 暂时失败应重试并在第3次成功")
    void addDocumentsInBatches_transientFailure_shouldRetryAndSucceed() throws Exception {
        List<Document> docs = createTestDocs(1);
        // 前2次失败，第3次成功
        doThrow(new RuntimeException("connection error"))
                .doThrow(new RuntimeException("connection error"))
                .doNothing()
                .when(vectorStore).add(anyList());

        invokeAddDocumentsInBatches(docs);

        verify(vectorStore, times(3)).add(anyList());
    }

    @Test
    @DisplayName("#33 永久失败应耗尽重试次数且不抛异常")
    void addDocumentsInBatches_permanentFailure_shouldExhaustRetries() throws Exception {
        List<Document> docs = createTestDocs(1);
        doThrow(new RuntimeException("permanent failure"))
                .when(vectorStore).add(anyList());

        // 不应抛出异常
        invokeAddDocumentsInBatches(docs);

        verify(vectorStore, times(3)).add(anyList());
    }

    @Test
    @DisplayName("#33 首次成功不应重试")
    void addDocumentsInBatches_firstAttemptSuccess_shouldNotRetry() throws Exception {
        List<Document> docs = createTestDocs(1);
        doNothing().when(vectorStore).add(anyList());

        invokeAddDocumentsInBatches(docs);

        verify(vectorStore, times(1)).add(anyList());
    }

    // ========== #31: Redis 缓存持久化 ==========

    @Test
    @DisplayName("#31 initCache应使用Redisson RMap")
    void initCache_shouldUseRedissonRMap() throws Exception {
        doReturn(redisCacheMap).when(redissonClient).getMap(anyString());
        when(redisCacheMap.size()).thenReturn(0);

        invokeInitCache();

        verify(redissonClient).getMap("knowledge:hash:cache");
        // 验证 loadedKnowledgeCache 已被设置为 RMap
        Map<String, String> cache = getLoadedCache();
        assertSame(redisCacheMap, cache, "缓存应使用Redis RMap");
    }

    @Test
    @DisplayName("#31 Redis不可用时应降级为内存Map")
    void initCache_redisUnavailable_shouldFallbackToInMemory() throws Exception {
        doThrow(new RuntimeException("connection refused")).when(redissonClient).getMap(anyString());

        invokeInitCache();

        Map<String, String> cache = getLoadedCache();
        assertNotNull(cache, "缓存不应为null");
        assertTrue(cache instanceof ConcurrentHashMap,
                "降级后应使用ConcurrentHashMap");
    }

    @Test
    @DisplayName("#31 增量更新：已有文档应跳过")
    void incrementalUpdate_withExistingDocs_shouldSkipUnchanged() throws Exception {
        doReturn(redisCacheMap).when(redissonClient).getMap(anyString());
        when(redisCacheMap.size()).thenReturn(0);

        // 先初始化缓存
        invokeInitCache();

        // 加载文档，模拟已存在的缓存
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);
        assertFalse(docs.isEmpty());

        // 预设缓存：所有文档已存在且hash匹配
        Map<String, String> existingCache = new ConcurrentHashMap<>();
        for (Document doc : docs) {
            existingCache.put(doc.getId(), getHash(doc));
        }

        // 替换缓存为预设值
        Field cacheField = KnowledgeMap.class.getDeclaredField("loadedKnowledgeCache");
        cacheField.setAccessible(true);
        cacheField.set(knowledgeMap, existingCache);

        // 执行增量更新
        invokePerformIncrementalUpdate(docs);

        // 不应有任何 VectorStore 操作
        verify(vectorStore, never()).add(anyList());
        verify(vectorStore, never()).delete(anyList());
    }

    @Test
    @DisplayName("#31 增量更新：新文档应添加")
    void incrementalUpdate_withNewDoc_shouldAdd() throws Exception {
        doReturn(redisCacheMap).when(redissonClient).getMap(anyString());
        when(redisCacheMap.size()).thenReturn(0);
        invokeInitCache();

        // 空缓存 → 所有文档都是新的
        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        invokePerformIncrementalUpdate(docs);

        verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("#31 全量更新应清除缓存后添加所有文档")
    void fullUpdate_shouldClearCacheThenAddAll() throws Exception {
        doReturn(redisCacheMap).when(redissonClient).getMap(anyString());
        when(redisCacheMap.size()).thenReturn(0);
        invokeInitCache();

        // 预设缓存有已存在的文档
        Map<String, String> existingCache = new ConcurrentHashMap<>();
        existingCache.put("old_doc_1", "hash1");
        Field cacheField = KnowledgeMap.class.getDeclaredField("loadedKnowledgeCache");
        cacheField.setAccessible(true);
        cacheField.set(knowledgeMap, existingCache);

        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        invokePerformFullUpdate(docs);

        verify(vectorStore).delete(anyList());
        verify(vectorStore).add(anyList());
    }

    @Test
    @DisplayName("#31 增量更新：内容变化应先删后加")
    void incrementalUpdate_withChangedDoc_shouldDeleteAndReAdd() throws Exception {
        doReturn(redisCacheMap).when(redissonClient).getMap(anyString());
        when(redisCacheMap.size()).thenReturn(0);
        invokeInitCache();

        ClassPathResource resource = new ClassPathResource("test-knowledge/test_with_comment.csv");
        List<Document> docs = knowledgeMap.loadCsvDocuments(resource);

        // 预设缓存：文档ID匹配但hash不匹配（模拟内容变化）
        Map<String, String> changedCache = new ConcurrentHashMap<>();
        for (Document doc : docs) {
            changedCache.put(doc.getId(), "wrong_hash_value");
        }
        Field cacheField = KnowledgeMap.class.getDeclaredField("loadedKnowledgeCache");
        cacheField.setAccessible(true);
        cacheField.set(knowledgeMap, changedCache);

        invokePerformIncrementalUpdate(docs);

        verify(vectorStore).delete(anyList());
        verify(vectorStore).add(anyList());
    }

    private void invokeInitCache() throws Exception {
        Method method = KnowledgeMap.class.getDeclaredMethod("initCache");
        method.setAccessible(true);
        method.invoke(knowledgeMap);
    }

    private Map<String, String> getLoadedCache() throws Exception {
        Field field = KnowledgeMap.class.getDeclaredField("loadedKnowledgeCache");
        field.setAccessible(true);
        return (Map<String, String>) field.get(knowledgeMap);
    }

    private void invokePerformIncrementalUpdate(List<Document> docs) throws Exception {
        Method method = KnowledgeMap.class.getDeclaredMethod("performIncrementalUpdate", List.class);
        method.setAccessible(true);
        method.invoke(knowledgeMap, docs);
    }

    private void invokePerformFullUpdate(List<Document> docs) throws Exception {
        Method method = KnowledgeMap.class.getDeclaredMethod("performFullUpdate", List.class);
        method.setAccessible(true);
        method.invoke(knowledgeMap, docs);
    }

    private String getHash(Document doc) throws Exception {
        Method method = KnowledgeMap.class.getDeclaredMethod("getContentHash", Document.class);
        method.setAccessible(true);
        return (String) method.invoke(knowledgeMap, doc);
    }
}
