package io.nop.gateway.conversion;

import io.nop.gateway.conversion.ai.AiBackendMessageConverter;
import io.nop.gateway.conversion.ai.AiBackendType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AiBackendMessageConverterTest {

    @Test
    void throws_whenFallbackEmpty() {
        // FALLBACK_CONVERTERS 现在为空，CLAUDE 不再有默认回退
        // 用户应使用 AI_DIALECT 或通过 IoC 注册自定义 converter
        assertThrows(IllegalArgumentException.class, () ->
                AiBackendMessageConverter.getConverter(AiBackendType.CLAUDE));
    }
}
