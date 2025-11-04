package com.suke;

import com.suke.common.AnalysisResult;
import com.suke.utils.AIDocking;
import com.suke.utils.FileUtils;
import com.suke.utils.ParseAIResponse;
import com.suke.utils.RedisUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author 自然醒
 * @version 1.0
 */
@SpringBootTest
@Slf4j
public class AnalysisApplicationTest {

    @Resource(name = "qwenChatClient")
    private ChatClient qwenClient;

    @Resource
    private AIDocking aiDocking;
    @Autowired
    private RedisUtils redisUtils;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
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
