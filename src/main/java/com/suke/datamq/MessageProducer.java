package com.suke.datamq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.suke.constant.BIConstant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * @author 自然醒
 * @version 1.0
 */
@Component
@Slf4j
public class MessageProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息
     * @param message    发送的消息
     */
    public void sendMessage(String message) {
        //发送消息到指定的交换机和路由键
        rabbitTemplate.convertAndSend(BIConstant.BI_EXCHANGE, BIConstant.BI_ROUTING_KEY, message);
    }

}
