package com.suke;

import com.suke.common.AnalysisResult;
import com.suke.constant.BIConstant;
import com.suke.context.UserContext;
import com.suke.datamq.Init;
import com.suke.datamq.MessageProducer;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.dto.user.UserLoginDTO;
import com.suke.domain.dto.user.UserRegisterDTO;
import com.suke.domain.entity.User;
import com.suke.domain.vo.GenChartVO;
import com.suke.domain.vo.LoginUserVO;
import com.suke.rag.KnowledgeMap;
import com.suke.service.IChartService;
import com.suke.service.IUserService;
import com.suke.utils.AIDocking;
import com.suke.utils.FileUtils;
import com.suke.utils.ParseAIResponse;
import com.suke.utils.RedisUtils;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.annotation.BeforeTestMethod;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author 自然醒
 * @version 1.0
 */
@SpringBootTest
@Slf4j
@Tag("integration")
public class AnalysisApplicationTest {

    @Resource
    private MessageProducer messageProducer;

    @Resource(name = "qwenChatClient")
    private ChatClient qwenClient;

    @Resource
    private AIDocking aiDocking;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private Debug debug;

    @Autowired
    private IChartService chartService;

    @Autowired
    private IUserService userService;

    @Resource
    private KnowledgeMap knowledgeMap;

    @Autowired
    private com.suke.tool.KnowledgeSearchTool knowledgeSearchTool;

    @Autowired
    private com.suke.utils.PromptBuilder promptBuilder;

    @BeforeEach
    void login(){
        UserLoginDTO userLoginDTO = new UserLoginDTO();
        userLoginDTO.setUserAccount("admin");
        userLoginDTO.setUserPassword("admin123456");
        LoginUserVO loginUserVO = userService.userLogin(userLoginDTO, null);
        UserContext.setCurrentId(loginUserVO.getId());
        assertNotNull(loginUserVO);
        System.out.println(loginUserVO);
    }




    @Test
    void init(){
        UserRegisterDTO userRegisterDTO = new UserRegisterDTO();
        userRegisterDTO.setUserAccount("admin");
        userRegisterDTO.setUserPassword("admin123456");
        userRegisterDTO.setCheckPassword("admin123456");
        userService.userRegister(userRegisterDTO, null);
    }



    @Test
    void testSmallFileUpload() {
        // 创建测试文件（1MB）
        MultipartFile mockFile = ExcelTest.createSmallExcelFile();

        UploadFileDTO dto = new UploadFileDTO();
        dto.setFileName("测试文件");
        dto.setGoal("分析销售额趋势");
        dto.setChartType("line");
        dto.setEnableSampling(false);



        GenChartVO result = chartService.analysisFile(mockFile, dto);

        assertNotNull(result);
        assertNotNull(result.getChartId());
    }

    @Test
    void testBigFileUpload() {
        MultipartFile mockFile = ExcelTest.createLargeExcelFile(30);

        UploadFileDTO dto = new UploadFileDTO();
        dto.setFileName("大型测试文件");
        dto.setGoal("分析各个城市工资情况");
        dto.setChartType("line");

        GenChartVO result = chartService.analysisFile(mockFile, dto);

        assertNotNull(result);
        assertNotNull(result.getChartId());
    }



    @Test
    void MinioTest(){
        log.info("Minio测试");
        System.out.println(minioClient);
    }

    @Test
    void RAGTest(){
        List<Document> documents = List.of(new Document("The World is Big and Salvation Lurks Around the Corner"));
        vectorStore.add(documents);
        List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());
        System.out.println(results);
    }

    @Test
    void FileUtilsTest() {
        FileUtils.excelToCsv(null);
    }

    @Test
    void AIModelTest(){
        String msg = "你是谁";
        String content = qwenClient.prompt().user(msg).call().content();
        System.out.println(content);
    }

    @Test
    void AIDockingTest(){
        String res = aiDocking.doChat("\n"+"分析需求：\n"+"分析网站用户的增长情况\n"+"原始数据：\n"+
                "日期,用户数\n" + "1号,10\n" + "2号,20\n" + "3号,30");
        System.out.println(res);
    }

    @Test
    void ParseAIResponseTest(){
        String aiResponse = """
            【数据分析结论】
            从数据可以看出，用户数量呈现稳定增长趋势。
            - 第1天：10个用户
            - 第2天：20个用户，增长100%
            - 第3天：30个用户，增长50%
            整体呈现线性增长模式。
            
            【可视化图表代码】
            ```json
            {
                "title": {"text": "用户增长趋势"},
                "tooltip": {"trigger": "axis"},
                "xAxis": {
                    "type": "category",
                    "data": ["1号", "2号", "3号"]
                },
                "yAxis": {"type": "value"},
                "series": [{
                    "data": [10, 20, 30],
                    "type": "line",
                    "smooth": true
                }]
            }
            ```
            """;
        log.info("原始AI响应:\n{}", aiResponse);
        AnalysisResult result = ParseAIResponse.parseResponse(aiResponse);

        log.info("解析结果:");
        log.info("分析结论: \n{}", result.getAnalysis());
        log.info("图表配置: \n{}", result.getChartConfig());
    }

    @Test
    void testKnowledgeMap() throws InterruptedException {
        log.info("========== 知识库检索测试开始 ==========");

        try {
            // 1. 验证初始化是否成功
            log.info("步骤1：调用 KnowledgeInit() 方法");
            boolean initResult = knowledgeMap.KnowledgeInit();
            Assertions.assertTrue(initResult, "知识库初始化应该返回 true");
            log.info("知识库初始化成功");

            // 2. 等待一下，确保 Redis 写入完成
            Thread.sleep(2000);

            // 3. 诊断 Redis 存储情况
            diagnoseRedisStorage();

            // 4. 测试知识检索功能
            testKnowledgeSearch("金融");
            testKnowledgeSearch("医疗");
            testKnowledgeSearch("流动比率");
            testKnowledgeSearch("股票");

            log.info("========== 知识库检索测试结束 ==========");
        } catch (Exception e) {
            log.error("知识库检索测试失败", e);
            throw e;
        }
    }

    /**
     * 诊断 Redis 中的存储情况
     */
    private void diagnoseRedisStorage() throws InterruptedException {
        log.info("========== 开始诊断 Redis 存储 ==========");
        try {
            // 1. 测试嵌入模型是否正常工作
            log.info("步骤1：测试嵌入模型");
            testEmbeddingModel();

            // 2. 检查 VectorStore 类型
            log.info("步骤2：检查 VectorStore 类型");
            log.info("VectorStore 类型: {}", vectorStore.getClass().getName());

            // 3. 添加一个测试文档到 VectorStore
            log.info("步骤3：添加测试文档到 VectorStore");
            Document testDoc = new Document("这是一个金融相关的测试文档，用于验证向量存储功能");
            testDoc.getMetadata().put("knowledge_type", "金融");
            testDoc.getMetadata().put("test", "true");

            try {
                vectorStore.add(List.of(testDoc));
                log.info("测试文档添加成功，文档ID: {}", testDoc.getId());
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("添加测试文档失败", e);
                throw e;
            }

            // 4. 搜索测试文档 - 使用多个查询词
            log.info("步骤4：搜索测试文档");
            testDocumentSearch(testDoc);

            // 5. 查询所有已存在的文档
            log.info("步骤5：查询所有文档");
            List<Document> allDocs = vectorStore.similaritySearch(
                    SearchRequest.builder().query("数据").topK(100).build()
            );
            log.info("Redis 中找到 {} 个文档（通过向量搜索）", allDocs.size());

            if (!allDocs.isEmpty()) {
                log.info("文档详情:");
                for (int i = 0; i < Math.min(5, allDocs.size()); i++) {
                    Document doc = allDocs.get(i);
                    log.info("  文档 {}: ID={}, 内容前50字符={}, 元数据={}",
                            i + 1,
                            doc.getId(),
                            doc.getText().substring(0, Math.min(50, doc.getText().length())),
                            doc.getMetadata());
                }
            } else {
                log.warn("Redis 中没有文档，向量存储可能有问题");
            }

        } catch (Exception e) {
            log.error("诊断 Redis 存储时出错", e);
            throw e;
        }
        log.info("========== Redis 存储诊断结束 ==========");
    }

    /**
     * 测试文档搜索 - 使用多个查询词
     */
    private void testDocumentSearch(Document testDoc) {
        log.info("--- 测试文档搜索，文档ID: {} ---", testDoc.getId());

        String[] testQueries = {
                "金融相关测试文档",
                "金融测试文档",
                "测试文档",
                "金融",
                "test",
                "测试"
        };

        boolean found = false;
        for (String query : testQueries) {
            try {
                List<Document> results = vectorStore.similaritySearch(
                        SearchRequest.builder().query(query).topK(3).build()
                );
                log.info("查询 '{}': 找到 {} 个结果", query, results.size());

                if (!results.isEmpty()) {
                    found = true;
                    for (int j = 0; j < results.size(); j++) {
                        Document doc = results.get(j);
                        log.info("  结果[{}]: ID={}, 是否为测试文档: {}",
                                j + 1, doc.getId(), testDoc.getId().equals(doc.getId()));
                        if (testDoc.getId().equals(doc.getId())) {
                            log.info("  找到测试文档! 内容: {}", doc.getText());
                            return; // 找到后直接返回
                        }
                    }
                }
            } catch (Exception e) {
                log.error("查询 '{}' 时出错", query, e);
            }
        }

        if (!found) {
            log.warn("所有查询都未找到测试文档，向量搜索可能有问题");
        }
    }

    /**
     * 测试嵌入模型是否正常工作
     */
    private void testEmbeddingModel() {
        try {
            log.info("嵌入模型类型: {}", embeddingModel.getClass().getName());
            String testText = "测试文本";
            log.info("测试文本: {}", testText);

            var result = embeddingModel.embed(testText);
            log.info("嵌入结果类型: {}", result != null ? result.getClass().getName() : "null");
            log.info("嵌入结果: {}", result);

        } catch (Exception e) {
            log.error("嵌入模型测试失败", e);
        }
    }

    /**
     * 测试知识库搜索功能
     *
     * @param query 查询关键词
     */
    private void testKnowledgeSearch(String query) {
        log.info("--- 搜索关键词: {} ---", query);
        try {
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(3)
                            .build()
            );

            if (results.isEmpty()) {
                log.warn("未找到与 '{}' 相关的知识", query);
            } else {
                log.info("找到 {} 个与 '{}' 相关的知识:", results.size(), query);
                for (int i = 0; i < results.size(); i++) {
                    Document doc = results.get(i);
                    log.info("  [{}] ID={}, 内容: {}",
                            i + 1,
                            doc.getId(),
                            doc.getText());
                }
            }
        } catch (Exception e) {
            log.error("搜索关键词 '{}' 时出错", query, e);
        }
    }



    //todo 可以有一个无query改写的tool调用使用进行对比召回率

    /**
     * KnowledgeSearchTool 基准测试
     * 测试知识库搜索工具与 LLM 的集成
     */
    @Test
    void testKnowledgeSearchTool() {
        log.info("========== KnowledgeSearchTool 基准测试开始 ==========");

        try {
            // 1. 测试场景：模拟用户上传文件时的分析目标
            String[] testGoals = {
                    "分析金融行业的流动比率趋势",
                    "分析股票市盈率的变化",
                    "分析医疗数据的相关性"
            };

            for (String goal : testGoals) {
                log.info("\n========== 测试场景: {} ==========", goal);
                testSingleGoal(goal);
                log.info("------------------------------------------------\n");
            }

        } catch (Exception e) {
            log.error("KnowledgeSearchTool 基准测试失败", e);
            throw e;
        }

        log.info("========== KnowledgeSearchTool 基准测试结束 ==========");
    }

    /**
     * 测试单个目标场景
     */
    private void testSingleGoal(String goal) {
        long startTime = System.currentTimeMillis();

        // 模拟用户上传的 CSV 数据
        String mockCsvData = "日期,销售额,利润率\n" +
                "2024-01,100000,15.5\n" +
                "2024-02,120000,18.2\n" +
                "2024-03,115000,16.8\n" +
                "2024-04,130000,19.5\n" +
                "2024-05,125000,17.3";

        String chartType = "折线图";

        // ========== 步骤1：Agent 自主决定是否调用 Tool ==========
        log.info("【步骤1】Agent 判断是否需要调用知识库工具");
        String toolDecisionPrompt = String.format("""
                你是一个数据分析助手。用户有以下分析需求：
                "%s"

                请判断是否需要调用知识库搜索工具来获取相关的专业知识（如金融指标定义、医疗数据分析方法等）。

                只需要回答：YES 或 NO
                """, goal);

        String decision = qwenClient.prompt()
                .user(toolDecisionPrompt)
                .call()
                .content()
                .trim()
                .toUpperCase();

        log.info("  - Agent 决策: {}", decision);

        String knowledgeResult = "";
        long toolStartTime = 0;
        long toolEndTime = 0;

        // ========== 步骤2：根据决策调用 Tool ==========
        if (decision.contains("YES")) {
            log.info("【步骤2】Agent 决定调用 KnowledgeSearchTool");
            log.info("  - 输入目标: {}", goal);
            toolStartTime = System.currentTimeMillis();

            knowledgeResult = knowledgeSearchTool.searchKnowledge(goal);

            toolEndTime = System.currentTimeMillis();
            log.info("  - Tool 执行耗时: {} ms", toolEndTime - toolStartTime);
            log.info("  - Tool 返回结果:\n{}", knowledgeResult);
        } else {
            log.info("【步骤2】Agent 决定不调用知识库工具，直接进行分析");
        }

        // ========== 步骤3：使用 PromptBuilder 构建增强的 Prompt ==========
        log.info("【步骤3】使用 PromptBuilder 构建增强的 Prompt");

        String enhancedPrompt;
        if (knowledgeResult.isEmpty()) {
            // 不使用知识库增强
            enhancedPrompt = promptBuilder.buildPrompt(chartType) + "\n\n分析需求：" + goal + "\n\n原始数据：\n" + mockCsvData;
            log.info("  - 使用普通 Prompt");
        } else {
            // 使用知识库增强
            enhancedPrompt = promptBuilder.buildEnhancedPrompt(goal, knowledgeResult, mockCsvData, chartType);
            log.info("  - 使用知识库增强 Prompt");
        }
        log.info("  - Prompt 长度: {} 字符", enhancedPrompt.length());

        // ========== 步骤4：调用 LLM 进行分析 ==========
        log.info("【步骤4】调用 LLM 进行分析");
        long llmStartTime = System.currentTimeMillis();

        String llmResponse = qwenClient.prompt()
                .user(enhancedPrompt)
                .call()
                .content();

        long llmEndTime = System.currentTimeMillis();
        log.info("  - LLM 执行耗时: {} ms", llmEndTime - llmStartTime);
        log.info("  - LLM 原始响应:\n{}", llmResponse);

        // ========== 步骤5：解析 LLM 响应 ==========
        log.info("【步骤5】使用 ParseAIResponse 解析 LLM 响应");
        AnalysisResult result = ParseAIResponse.parseResponse(llmResponse);

        log.info("  - 解析后的分析结论:\n{}", result.getAnalysis());
        log.info("  - 解析后的图表配置:\n{}", result.getChartConfig());

        // ========== 步骤6：验证结果 ==========
        log.info("【步骤6】验证结果");
        Assertions.assertNotNull(result.getAnalysis(), "分析结论不应为空");
        Assertions.assertNotNull(result.getChartConfig(), "图表配置不应为空");

        // 验证图表配置是否为有效的 JSON
        try {
            com.alibaba.fastjson2.JSON.parseObject(result.getChartConfig());
            log.info("  - 图表配置 JSON 格式验证: 通过");
        } catch (Exception e) {
            log.warn("  - 图表配置 JSON 格式验证: 失败 - {}", e.getMessage());
        }

        long totalEndTime = System.currentTimeMillis();
        log.info("【总结】总耗时: {} ms", totalEndTime - startTime);
    }

    @Test
    void RateLimitTest() throws InterruptedException {
        String userId = "1";
        for(int i = 0; i < 2; i++){
            redisUtils.doRateLimit(userId);
            System.out.println("用户" + userId + "已访问");
        }
        Thread.sleep(1000);
        for(int i = 0; i < 5; i++){
            redisUtils.doRateLimit(userId);
            System.out.println("用户" + userId + "已访问");
        }
    }

    @Test
    void ThreadPoolExecutorTest() throws InterruptedException {
        //Java并发包下的异步执行，一般用于不返回数据值情况下
        for(int i = 0; i < 10; i++){
            CompletableFuture.runAsync(() -> {
                int count = 0;
                count ++;
                System.out.println("任务"+ count +"执行中，" + "当前线程：" + Thread.currentThread().getName());
                try {
                    Thread.sleep(600000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, threadPoolExecutor);


//            Thread.sleep(1000);

            //存储线程池状态
            Map<String,Object> map = new HashMap<>();
            //获取线程池的队列长度
            int size = threadPoolExecutor.getQueue().size();
            map.put("队列长度", size);
            //获取线程池已接收的任务总数
            long completedTaskCount = threadPoolExecutor.getTaskCount();
            map.put("任务总数", completedTaskCount);
            //获取线程池已执行完成的任务总数
            long completedTaskCount1 = threadPoolExecutor.getCompletedTaskCount();
            map.put("已执行完成的任务总数", completedTaskCount1);
            //正在执行的任务数
            int activeCount = threadPoolExecutor.getActiveCount();
            map.put("正在执行的任务", activeCount);
            System.out.println(map);
        }

    }

}
