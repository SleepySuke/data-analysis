package com.suke.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-02-08 02:07
 * @description 知识库的引入构建，增强agent的检索
 */
@Component
@Slf4j
public class KnowledgeMap {
    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RedissonClient redissonClient;

    @Value("${rag.knowledge.path}")
    private String knowledgePath;
    @Value("${rag.knowledge.strategy}")
    private String strategy;

    // 分批存储时的每批文档数量
    private static final int BATCH_SIZE = 100;
    // 分批存储时的批次间延迟（毫秒）
    private static final long BATCH_DELAY_MS = 100;
    // 批次写入最大重试次数
    private static final int MAX_RETRY_ATTEMPTS = 3;
    // 重试间隔（毫秒）
    private static final long RETRY_DELAY_MS = 100;

    // 缓存已加载的知识 用于判断是否需要重新更新加载
    private Map<String, String> loadedKnowledgeCache;
    private static final String CACHE_KEY_PREFIX = "knowledge:hash:";

    @PostConstruct
    public void init() {
        initCache();
        KnowledgeInit();
    }

    void initCache() {
        try {
            RMap<String, String> redisCache = redissonClient.getMap(CACHE_KEY_PREFIX + "cache");
            redisCache.size(); // 验证连接
            this.loadedKnowledgeCache = redisCache;
            log.info("知识库缓存使用Redis持久化");
        } catch (Exception e) {
            log.warn("Redis缓存初始化失败，回退到内存缓存", e);
            this.loadedKnowledgeCache = new ConcurrentHashMap<>();
        }
    }

    public boolean KnowledgeInit() {
        log.info("知识库初始化开始，初始化策略 -> {}", strategy);
        try {
            long startTime = System.currentTimeMillis();
            List<Document> knowledgeDocs = loadKnowledgeDocuments();

            if (knowledgeDocs.isEmpty()) {
                log.warn("未找到任何知识文档");
                return false;
            }

            if ("full".equalsIgnoreCase(strategy)) {
                performFullUpdate(knowledgeDocs);
            } else {
                performIncrementalUpdate(knowledgeDocs);
            }

            long endTime = System.currentTimeMillis();
            log.info("知识库初始化完成，共处理 {} 个文档，耗时 {} ms",
                    knowledgeDocs.size(), (endTime - startTime));
            return true;
        } catch (Exception e) {
            log.error("知识库初始化失败", e);
        }
        return false;
    }

    private void performIncrementalUpdate(List<Document> knowledgeDocs) {
        log.info("进行增量更新，待处理文档数 -> {}", knowledgeDocs.size());

        // 从 VectorStore 获取已存在的知识文档 ID（通过缓存判断）
        Set<String> existingDocIds = loadedKnowledgeCache.keySet();
        log.info("缓存中已存在的知识文档数 -> {}", existingDocIds.size());

        Set<String> currentDocIds = new HashSet<>();
        List<Document> docsToAdd = new ArrayList<>();
        List<String> idsToDelete = new ArrayList<>();
        int updateCount = 0;

        for (Document knowledgeDoc : knowledgeDocs) {
            String id = knowledgeDoc.getId();
            currentDocIds.add(id);
            String textHash = getContentHash(knowledgeDoc);

            // 检查缓存中是否已存在该文档
            if (existingDocIds.contains(id)) {
                String currentHash = loadedKnowledgeCache.get(id);
                // 如果内容发生变化，需要更新
                if (!textHash.equals(currentHash)) {
                    idsToDelete.add(id); // 先删除旧的
                    docsToAdd.add(knowledgeDoc); // 再添加新的
                    loadedKnowledgeCache.put(id, textHash);
                    updateCount++;
                    log.info("检测到文档内容变化，准备更新 -> id: {}", id);
                }
            } else {
                // 新文档，直接添加
                docsToAdd.add(knowledgeDoc);
                loadedKnowledgeCache.put(id, textHash);
                updateCount++;
                log.info("检测到新文档，准备添加 -> id: {}", id);
            }
        }

        // 查找在缓存中存在但当前文档列表中不存在的文档（需要删除）
        for (String existingId : existingDocIds) {
            if (!currentDocIds.contains(existingId)) {
                idsToDelete.add(existingId);
                loadedKnowledgeCache.remove(existingId);
                log.info("检测到已删除的文档，准备移除 -> id: {}", existingId);
            }
        }

        // 执行删除操作（分批删除）
        if (!idsToDelete.isEmpty()) {
            deleteDocumentsInBatches(idsToDelete);
        }

        // 执行添加操作（分批添加）
        if (!docsToAdd.isEmpty()) {
            addDocumentsInBatches(docsToAdd);
        }

        log.info("增量更新完成，共处理 {} 个文档", updateCount);
    }

    private void performFullUpdate(List<Document> knowledgeDocs) {
        log.info("进行全量更新，待处理文档数 -> {}", knowledgeDocs.size());

        // 从缓存获取所有已存在的知识文档 ID
        Set<String> existingDocIds = loadedKnowledgeCache.keySet();
        log.info("缓存中已存在的知识文档数 -> {}", existingDocIds.size());

        // 删除所有已存在的文档（分批删除）
        if (!existingDocIds.isEmpty()) {
            deleteDocumentsInBatches(new ArrayList<>(existingDocIds));
            loadedKnowledgeCache.clear();
        }

        // 添加所有新文档（分批添加）
        if (!knowledgeDocs.isEmpty()) {
            addDocumentsInBatches(knowledgeDocs);
            // 更新内存缓存
            for (Document doc : knowledgeDocs) {
                loadedKnowledgeCache.put(doc.getId(), getContentHash(doc));
            }
        }

        log.info("全量更新完成");
    }

    /**
     * 分批添加文档到 VectorStore，避免一次性添加过多文档导致容量问题
     *
     * @param documents 要添加的文档列表
     */
    private void addDocumentsInBatches(List<Document> documents) {
        int totalDocs = documents.size();
        int totalBatches = (totalDocs + BATCH_SIZE - 1) / BATCH_SIZE;

        log.info("开始分批添加文档，总文档数: {}, 每批: {}, 总批次数: {}",
                totalDocs, BATCH_SIZE, totalBatches);

        for (int i = 0; i < totalDocs; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalDocs);
            List<Document> batch = documents.subList(i, end);
            int batchNum = (i / BATCH_SIZE) + 1;

            boolean batchSuccess = false;
            for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
                try {
                    vectorStore.add(batch);
                    batchSuccess = true;
                    log.info("批次 {}/{} 成功添加 {} 个文档", batchNum, totalBatches, batch.size());
                    if (end < totalDocs) {
                        Thread.sleep(BATCH_DELAY_MS);
                    }
                    break;
                } catch (Exception e) {
                    log.warn("批次 {}/{} 添加失败 (尝试 {}/{})", batchNum, totalBatches, attempt, MAX_RETRY_ATTEMPTS);
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            if (!batchSuccess) {
                log.error("批次 {}/{} 最终失败，已重试 {} 次", batchNum, totalBatches, MAX_RETRY_ATTEMPTS);
            }
        }

        log.info("所有批次添加完成，共添加 {} 个文档", totalDocs);
    }

    /**
     * 分批删除文档从 VectorStore
     *
     * @param docIds 要删除的文档 ID 列表
     */
    private void deleteDocumentsInBatches(List<String> docIds) {
        int totalIds = docIds.size();
        int totalBatches = (totalIds + BATCH_SIZE - 1) / BATCH_SIZE;

        log.info("开始分批删除文档，总文档数: {}, 每批: {}, 总批次数: {}",
                totalIds, BATCH_SIZE, totalBatches);

        for (int i = 0; i < totalIds; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalIds);
            List<String> batch = docIds.subList(i, end);
            int batchNum = (i / BATCH_SIZE) + 1;

            try {
                vectorStore.delete(batch);
                log.info("批次 {}/{} 成功删除 {} 个文档", batchNum, totalBatches, batch.size());
            } catch (Exception e) {
                log.error("批次 {}/{} 删除文档失败", batchNum, totalBatches, e);
            }
        }

        log.info("所有批次删除完成，共删除 {} 个文档", totalIds);
    }

    private List<Document> loadKnowledgeDocuments() {
        List<Document> documents = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            log.info("加载知识文档路径 -> {}",knowledgePath);
            Resource[] resources = resolver.getResources(knowledgePath);
            log.info("加载知识文档 -> {}",resources.length);
            for (Resource resource : resources) {
                try{
                    List<Document> csvDocs = loadCsvDocuments(resource);
                    if(csvDocs == null || csvDocs.isEmpty()){
                        log.warn("未找到任何知识文档");
                        continue;
                    }
                    documents.addAll(csvDocs);

                }catch (Exception e){
                    log.error("加载知识文档失败", e);
                }
            }
        } catch (Exception e) {
            log.error("加载知识文档失败", e);
        }
        return documents;
    }

    List<Document> loadCsvDocuments(Resource resource) {
        List<Document> documents = new ArrayList<>();
        Map<Integer, String> header = new HashMap<>();
        boolean headerProcessed = false;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                List<String> csvList = loadCsvLine(line);
                if (!headerProcessed) {
                    for (int i = 0; i < csvList.size(); i++) {
                        header.put(i, csvList.get(i));
                    }
                    log.info("csv表头->{}", header);
                    headerProcessed = true;
                } else {
                    try {
                        Document doc = createDoc(csvList, header, resource.getFilename());
                        if (doc != null) {
                            documents.add(doc);
                        }
                    } catch (Exception e) {
                        log.error("加载csv文档失败", e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("加载csv文档失败", e);
            return null;
        }
        return documents;
    }

    private Document createDoc(List<String> csvList, Map<Integer, String> header, String filename) {
        if(csvList.isEmpty()){
            return null;
        }
        String content = null;
        Map<String,Object> metaData = new HashMap<>();
        for(int i = 0; i < csvList.size(); i++){
            if(i >= header.size()){
                break;
            }
            String head = header.get(i);
            String value = csvList.get(i);
            if(value == null || value.trim().isEmpty()){
                continue;
            }
            if(head.equals("content") || head.equals("知识内容") || head.equals("text")){
                content = value;
            }else{
                metaData.put(head, value);
            }
        }
        if(content == null || content.isEmpty()){
            return null;
        }
        String contentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        String id = "knowledge_" + contentHash;
        Document doc = Document
                .builder()
                .id(id)
                .text(content)
                .metadata(metaData)
                .metadata("source_file", filename)
                .metadata("load_time",System.currentTimeMillis())
                .build();

        ensureRequiredMetadata(doc);
        return doc;
    }

    private void ensureRequiredMetadata(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();

        // 如果没有知识类型，尝试从文件名推断
        if (!metadata.containsKey("knowledge_type") && !metadata.containsKey("type")) {
            String fileName = (String) metadata.get("source_file");
            if (fileName != null) {
                metadata.put("knowledge_type", inferKnowledgeTypeFromFileName(fileName));
            } else {
                metadata.put("knowledge_type", "通用");
            }
        }

        // 如果没有行业，默认通用行业
        if (!metadata.containsKey("industry") && !metadata.containsKey("industry_type")) {
            metadata.put("industry", "通用行业");
        }

        // 如果没有来源，使用文件名
        if (!metadata.containsKey("source") && !metadata.containsKey("来源")) {
            metadata.put("source", metadata.get("source_file"));
        }
    }

    private Object inferKnowledgeTypeFromFileName(String fileName) {
        if (fileName == null) return "通用";
        fileName = fileName.toLowerCase();
        if (fileName.contains("金融") || fileName.contains("finance")) return "金融";
        if (fileName.contains("医疗") || fileName.contains("medical")) return "医疗";
        if (fileName.contains("科技") || fileName.contains("tech")) return "科技";
        if (fileName.contains("人口") || fileName.contains("population")) return "人口";
        if (fileName.contains("教育") || fileName.contains("education")) return "教育";
        if (fileName.contains("经济") || fileName.contains("economy")) return "经济";
        if (fileName.contains("零售") || fileName.contains("retail")) return "零售";
        if (fileName.contains("电商") || fileName.contains("ecommerce")) return "电商";
        if (fileName.contains("制造") || fileName.contains("manufacturing")) return "制造";
        if (fileName.contains("能源") || fileName.contains("energy")) return "能源";

        return "通用";
    }

    private List<String> loadCsvLine(String line) {
        List<String> csvList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean flag = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if(i + 1< line.length() && line.charAt(i + 1) == '"'){
                    sb.append('"');
                    i++;
                }else{
                    flag = !flag;
                }
            }else if(c == ',' && !flag){
                csvList.add(sb.toString().trim());
                sb = new StringBuilder();
            }else {
                sb.append(c);
            }
        }
        csvList.add(sb.toString().trim());
        return csvList;
    }

    private String getContentHash(Document doc) {
        String content = doc.getText();
        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }
}
