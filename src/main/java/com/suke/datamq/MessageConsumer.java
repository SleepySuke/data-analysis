package com.suke.datamq;

import com.rabbitmq.client.Channel;
import com.suke.common.AnalysisResult;
import com.suke.constant.BIConstant;
import com.suke.domain.entity.Chart;
import com.suke.service.IChartService;
import com.suke.utils.AIDocking;
import com.suke.utils.ParseAIResponse;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * @author 自然醒
 * @version 1.0
 */
@Component
@Slf4j
public class MessageConsumer {

    @Autowired
    private IChartService chartService;

    @Resource
    private AIDocking aiDocking;

    /**
     * 接收消息的方法
     * @param message     接收到消息的内容，是一个字符串类型
     * @param channel     消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等
     * @param deliveryTag 消息的投递标签，用于唯一标识一条消息
     */
    // 使用@SneakyThrows注解简化异常处理
    @SneakyThrows
    // 使用@RabbitListener注解指定要监听的队列名称为"suke_queue"，并设置消息的确认机制为手动确认
    @RabbitListener(queues = {BIConstant.BI_QUEUE}, ackMode = "MANUAL")
    // @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,用于从消息头中获取投递标签(deliveryTag),
    // 在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，用于标识该消息在通道中的投递状态和顺序。
    // 通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        // 使用日志记录器打印接收到的消息内容
        log.info("receiveMessage message = {}", message);
        if (message == null) {
            log.error("接收到的消息为空");
            channel.basicAck(deliveryTag, false);
            return;
        }
        Long chartId;
        try {
            chartId = Long.parseLong(message);
        } catch (NumberFormatException e) {
            log.error("消息格式错误，无法解析chartId: {}", message);
            channel.basicAck(deliveryTag, false);
            return;
        }
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            log.error("图表不存在, chartId={}", chartId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("running");
        boolean updateResult = chartService.updateById(updateChart);
        if (!updateResult) {
            channel.basicNack(deliveryTag, false, false);
            log.error("更新图表失败状态失败" + updateChart.getId());
            handleChartUpdateError(updateChart.getId(), "更新图表失败");
            return;
        }
        try {
            String response = aiDocking.doDataAnalysis(chart.getGoal(), chart.getChartType(), chart.getChartData());
            AnalysisResult analysisResult = ParseAIResponse.parseResponse(response);
            if (analysisResult == null) {
                handleChartUpdateError(updateChart.getId(), "分析结果为空");
                channel.basicAck(deliveryTag, false);
                return;
            }
            Chart resChart = new Chart();
            resChart.setId(updateChart.getId());
            resChart.setGenChart(analysisResult.getChartConfig());
            resChart.setGenResult(analysisResult.getAnalysis());
            resChart.setStatus("succeed");
            resChart.setExecMsg("分析成功");
            boolean updateResult1 = chartService.updateById(resChart);
            if (!updateResult1) {
                channel.basicNack(deliveryTag, false, false);
                handleChartUpdateError(updateChart.getId(), "更新图表失败");
                return;
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("AI分析异常, chartId={}: {}", chart.getId(), e.getMessage(), e);
            handleChartUpdateError(chart.getId(), "分析失败: " + e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMsg(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

}


