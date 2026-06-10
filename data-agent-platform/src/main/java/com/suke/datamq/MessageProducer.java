package com.suke.datamq;

import com.suke.constant.BIConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MessageProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(String message) {
        try {
            rabbitTemplate.convertAndSend(BIConstant.BI_EXCHANGE, BIConstant.BI_ROUTING_KEY, message);
            log.info("消息发送成功: exchange={}, routingKey={}", BIConstant.BI_EXCHANGE, BIConstant.BI_ROUTING_KEY);
        } catch (Exception e) {
            log.error("消息发送失败: exchange={}, routingKey={}, message={}",
                    BIConstant.BI_EXCHANGE, BIConstant.BI_ROUTING_KEY, message, e);
            throw new RuntimeException("消息发送失败", e);
        }
    }
}
