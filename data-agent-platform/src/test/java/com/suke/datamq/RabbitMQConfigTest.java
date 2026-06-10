package com.suke.datamq;

import com.suke.constant.BIConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RabbitMQ 配置测试")
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("#37+#38 biQueue 应为持久化队列，名称匹配常量")
    void biQueue_shouldBeDurableWithCorrectName() {
        Queue queue = config.biQueue();

        assertEquals(BIConstant.BI_QUEUE, queue.getName(),
                "队列名应匹配 BIConstant");
        assertTrue(queue.isDurable(), "队列应为持久化");
    }

    @Test
    @DisplayName("#37+#38 biExchange 应为持久化直连交换机")
    void biExchange_shouldBeDirectAndDurable() {
        DirectExchange exchange = config.biExchange();

        assertEquals(BIConstant.BI_EXCHANGE, exchange.getName(),
                "交换机名应匹配 BIConstant");
        assertTrue(exchange.isDurable(), "交换机应为持久化");
        assertEquals("direct", exchange.getType(),
                "交换机类型应为 direct");
    }

    @Test
    @DisplayName("#37+#38 biBinding 应正确绑定队列到交换机")
    void biBinding_shouldBindQueueToExchangeWithRoutingKey() {
        Queue queue = config.biQueue();
        DirectExchange exchange = config.biExchange();
        Binding binding = config.biBinding(queue, exchange);

        assertEquals(BIConstant.BI_QUEUE, binding.getDestination(),
                "绑定目标应为队列名");
        assertEquals(BIConstant.BI_EXCHANGE, binding.getExchange(),
                "绑定交换机应匹配");
        assertEquals(BIConstant.BI_ROUTING_KEY, binding.getRoutingKey(),
                "路由键应匹配");
    }
}
