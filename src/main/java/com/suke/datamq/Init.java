package com.suke.datamq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.suke.constant.BIConstant;

/**
 * @author 自然醒
 * @version 1.0
 */
//用于初始化消息队列
public class Init {
    public static void main(String[] args) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost("localhost");
            Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel();
            String queueName = BIConstant.BI_QUEUE;
            channel.queueDeclare(queueName, true, false, false, null);
            String exchangeName = BIConstant.BI_EXCHANGE;
            channel.exchangeDeclare(exchangeName, "direct");
            channel.queueBind(queueName, exchangeName, BIConstant.BI_ROUTING_KEY);
        } catch (Exception e) {
            System.out.println("创建队列失败：{}" + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
