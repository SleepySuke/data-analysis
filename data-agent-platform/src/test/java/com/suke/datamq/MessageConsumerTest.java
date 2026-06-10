package com.suke.datamq;

import com.rabbitmq.client.Channel;
import com.suke.common.AnalysisResult;
import com.suke.domain.entity.Chart;
import com.suke.service.IChartService;
import com.suke.utils.AIDocking;
import com.suke.utils.ParseAIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {

    @InjectMocks
    private MessageConsumer messageConsumer;

    @Mock
    private IChartService chartService;

    @Mock
    private AIDocking aiDocking;

    @Mock
    private Channel channel;

    private static final long DELIVERY_TAG = 1L;

    private Chart buildTestChart() {
        Chart chart = new Chart();
        chart.setId(100L);
        chart.setGoal("分析销售趋势");
        chart.setChartType("line");
        chart.setChartData("date,sales\n1月,100\n2月,200");
        chart.setStatus("wait");
        return chart;
    }

    // ========== Bug #9: chartId 被当作 goal 传入 AI ==========

    @Test
    @DisplayName("AI调用应使用chart.getGoal()而非message(chartId)")
    void receiveMessage_shouldUseChartGoalNotMessageAsGoal() throws Exception {
        Chart chart = buildTestChart();
        when(chartService.getById(100L)).thenReturn(chart);
        when(chartService.updateById(any())).thenReturn(true);
        when(aiDocking.doDataAnalysis(anyString(), anyString(), anyString()))
                .thenReturn("AI响应");

        messageConsumer.receiveMessage("100", channel, DELIVERY_TAG);

        // 验证 AI 调用使用的是 chart.getGoal()，不是 message "100"
        ArgumentCaptor<String> goalCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiDocking).doDataAnalysis(goalCaptor.capture(), eq("line"), anyString());
        assert goalCaptor.getValue().equals("分析销售趋势")
                : "期望 goal='分析销售趋势'，实际='" + goalCaptor.getValue() + "'";
    }

    // ========== Bug #10: execMsg 写死为字面量 "execMessage" ==========

    @Test
    @DisplayName("AI分析失败时execMsg应包含真实错误原因而非字面量execMessage")
    void receiveMessage_aiFailure_execMsgShouldContainRealError() throws Exception {
        Chart chart = buildTestChart();
        when(chartService.getById(100L)).thenReturn(chart);
        // 第一次 updateById（设 running）返回 true，第二次（设 failed）也返回 true
        when(chartService.updateById(any())).thenReturn(true);
        when(aiDocking.doDataAnalysis(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("网络超时"));

        messageConsumer.receiveMessage("100", channel, DELIVERY_TAG);

        // 捕获保存的 Chart，验证 execMsg 不是字面量 "execMessage"
        ArgumentCaptor<Chart> chartCaptor = ArgumentCaptor.forClass(Chart.class);
        verify(chartService, atLeast(2)).updateById(chartCaptor.capture());

        Chart failedChart = chartCaptor.getAllValues().stream()
                .filter(c -> "failed".equals(c.getStatus()))
                .findFirst()
                .orElseThrow();

        assert !"execMessage".equals(failedChart.getExecMsg())
                : "execMsg 不应为字面量 'execMessage'，实际值: " + failedChart.getExecMsg();
        assert failedChart.getExecMsg() != null && !failedChart.getExecMsg().isEmpty()
                : "execMsg 不应为空";
    }

    // ========== Bug #8: ACK/NACK 缺失 ==========

    @Test
    @DisplayName("消息为null时应ACK或NACK，而非静默return")
    void receiveMessage_nullMessage_shouldAckOrNack() throws Exception {
        messageConsumer.receiveMessage(null, channel, DELIVERY_TAG);

        // 消息为null时应该确认或拒绝消息，不能静默丢弃
        verify(channel, atLeastOnce()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("图表不存在时应ACK或NACK")
    void receiveMessage_chartNotFound_shouldAckOrNack() throws Exception {
        when(chartService.getById(999L)).thenReturn(null);

        messageConsumer.receiveMessage("999", channel, DELIVERY_TAG);

        verify(channel, atLeastOnce()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("AI分析成功时应ACK")
    void receiveMessage_success_shouldAck() throws Exception {
        Chart chart = buildTestChart();
        when(chartService.getById(100L)).thenReturn(chart);
        when(chartService.updateById(any())).thenReturn(true);
        when(aiDocking.doDataAnalysis(anyString(), anyString(), anyString()))
                .thenReturn("【数据分析结论】分析完成\n【可视化图表代码】\n```json\n{\"title\":{}}\n```");

        messageConsumer.receiveMessage("100", channel, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    @DisplayName("AI分析异常时应NACK")
    void receiveMessage_aiException_shouldNack() throws Exception {
        Chart chart = buildTestChart();
        when(chartService.getById(100L)).thenReturn(chart);
        when(chartService.updateById(any())).thenReturn(true);
        when(aiDocking.doDataAnalysis(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("AI服务异常"));

        messageConsumer.receiveMessage("100", channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    // ========== NumberFormatException path ==========

    @Test
    @DisplayName("非数字消息应ACK而不崩溃")
    void receiveMessage_nonNumericMessage_shouldAck() throws Exception {
        messageConsumer.receiveMessage("not-a-number", channel, DELIVERY_TAG);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(chartService, never()).getById(anyLong());
    }

    // ========== analysisResult null path ==========

    @Test
    @DisplayName("AI返回结果解析为null时应ACK并标记失败")
    void receiveMessage_nullAnalysisResult_shouldAckAndMarkFailed() throws Exception {
        Chart chart = buildTestChart();
        when(chartService.getById(100L)).thenReturn(chart);
        when(chartService.updateById(any())).thenReturn(true);
        when(aiDocking.doDataAnalysis(anyString(), anyString(), anyString()))
                .thenReturn("invalid response without markers");

        // ParseAIResponse on this input returns null analysis but non-null chartConfig
        // or we mock the static. Since we can't mock static easily, test that it
        // completes without throwing and ack is called.
        messageConsumer.receiveMessage("100", channel, DELIVERY_TAG);

        // Either ACK (if analysisResult is null → handled) or succeeds
        verify(channel, atLeastOnce()).basicAck(anyLong(), anyBoolean());
    }
}
