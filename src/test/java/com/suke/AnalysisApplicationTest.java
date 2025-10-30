package com.suke;

import com.suke.common.AnalysisResult;
import com.suke.utils.AIDocking;
import com.suke.utils.FileUtils;
import com.suke.utils.ParseAIResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

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
}
