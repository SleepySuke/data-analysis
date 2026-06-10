package com.suke.agent.memory;

import org.springframework.ai.chat.messages.Message;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

/**
 * 测试用的 ContextCompressor 工厂。
 * 生产代码只有 @Autowired 构造函数，测试通过反射设置私有字段。
 */
public class TestContextCompressor {

    public static ContextCompressor create(int maxTokens, int keepRecentRounds) {
        return create(maxTokens, keepRecentRounds, null);
    }

    public static ContextCompressor create(int maxTokens, int keepRecentRounds,
                                            Function<List<Message>, String> summaryFn) {
        ContextCompressor compressor = new ContextCompressor(null, maxTokens, keepRecentRounds);
        if (summaryFn != null) {
            setField(compressor, "summaryFn", summaryFn);
        }
        return compressor;
    }

    @SuppressWarnings("unchecked")
    private static <T> void setField(Object target, String fieldName, T value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field '" + fieldName + "' on " + target.getClass().getSimpleName(), e);
        }
    }
}
