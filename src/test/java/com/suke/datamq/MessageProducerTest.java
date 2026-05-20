package com.suke.datamq;

import com.suke.constant.BIConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageProducer 测试")
class MessageProducerTest {

    @InjectMocks
    private MessageProducer messageProducer;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("#39 正常发送应调用 rabbitTemplate.convertAndSend")
    void sendMessage_success_shouldCallRabbitTemplate() {
        messageProducer.sendMessage("123");

        verify(rabbitTemplate).convertAndSend(
                BIConstant.BI_EXCHANGE, BIConstant.BI_ROUTING_KEY, "123");
    }

    @Test
    @DisplayName("#39 发送失败应抛出 RuntimeException")
    void sendMessage_failure_shouldThrowRuntimeException() {
        doThrow(new RuntimeException("connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> messageProducer.sendMessage("123"));

        assertTrue(exception.getMessage().contains("发送失败"),
                "异常消息应包含'发送失败'");
    }

    @Test
    @DisplayName("#39 发送失败异常应包含原始异常作为 cause")
    void sendMessage_failure_shouldWrapOriginalException() {
        RuntimeException cause = new RuntimeException("broker unreachable");
        doThrow(cause).when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> messageProducer.sendMessage("123"));

        assertSame(cause, exception.getCause(), "应保留原始异常链");
    }
}
