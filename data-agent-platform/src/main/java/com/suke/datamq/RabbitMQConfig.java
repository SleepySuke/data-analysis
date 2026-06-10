package com.suke.datamq;

import com.suke.constant.BIConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue biQueue() {
        return QueueBuilder.durable(BIConstant.BI_QUEUE).build();
    }

    @Bean
    public DirectExchange biExchange() {
        return ExchangeBuilder.directExchange(BIConstant.BI_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding biBinding(Queue biQueue, DirectExchange biExchange) {
        return BindingBuilder.bind(biQueue).to(biExchange).with(BIConstant.BI_ROUTING_KEY);
    }
}
