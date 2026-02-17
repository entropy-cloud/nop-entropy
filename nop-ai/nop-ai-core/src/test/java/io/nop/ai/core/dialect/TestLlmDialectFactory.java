package io.nop.ai.core.dialect;

import io.nop.ai.core.model.ApiStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmDialectFactory 测试
 */
public class TestLlmDialectFactory {

    @Test
    public void testGetDialect() {
        ILlmDialect openai = LlmDialectFactory.getDialect(ApiStyle.openai);
        assertNotNull(openai);
        assertEquals("openai", openai.getName());
        
        ILlmDialect anthropic = LlmDialectFactory.getDialect(ApiStyle.anthropic);
        assertNotNull(anthropic);
        assertEquals("anthropic", anthropic.getName());
        
        ILlmDialect gemini = LlmDialectFactory.getDialect(ApiStyle.gemini);
        assertNotNull(gemini);
        assertEquals("gemini", gemini.getName());
        
        ILlmDialect ollama = LlmDialectFactory.getDialect(ApiStyle.ollama);
        assertNotNull(ollama);
        assertEquals("ollama", ollama.getName());
    }

    @Test
    public void testGetDialectNullReturnsOpenAi() {
        ILlmDialect dialect = LlmDialectFactory.getDialect(null);
        assertNotNull(dialect);
        assertEquals("openai", dialect.getName());
    }

    @Test
    public void testIsSupported() {
        assertTrue(LlmDialectFactory.isSupported(ApiStyle.openai));
        assertTrue(LlmDialectFactory.isSupported(ApiStyle.anthropic));
        assertTrue(LlmDialectFactory.isSupported(ApiStyle.gemini));
        assertTrue(LlmDialectFactory.isSupported(ApiStyle.ollama));
        assertFalse(LlmDialectFactory.isSupported(null));
    }

    @Test
    public void testRegisterWithOverwrite() {
        // 保存原始方言
        ILlmDialect originalOpenai = LlmDialectFactory.getDialect(ApiStyle.openai);
        
        // 创建自定义方言
        ILlmDialect customDialect = new CustomTestDialect();
        
        // 使用 overwrite=false 注册，由于 openai 已存在，应该失败
        boolean registered = LlmDialectFactory.register(ApiStyle.openai, customDialect, false);
        assertFalse(registered, "Should not register when overwrite=false and dialect exists");
        
        // 验证原始方言未被替换
        ILlmDialect current = LlmDialectFactory.getDialect(ApiStyle.openai);
        assertEquals("openai", current.getName(), "Original dialect should not be replaced");
        
        // 使用 overwrite=true 注册
        registered = LlmDialectFactory.register(ApiStyle.openai, customDialect, true);
        assertTrue(registered, "Should register when overwrite=true");
        
        // 验证方言已被替换
        current = LlmDialectFactory.getDialect(ApiStyle.openai);
        assertEquals("custom-test", current.getName(), "Should be replaced with custom dialect");
        
        // 恢复原始方言
        LlmDialectFactory.register(ApiStyle.openai, originalOpenai, true);
    }

    @Test
    public void testRegisterWithoutOverwrite() {
        // 保存原始方言
        ILlmDialect originalOpenai = LlmDialectFactory.getDialect(ApiStyle.openai);
        
        // 使用无参数的 register 方法（默认覆盖）
        ILlmDialect customDialect = new CustomTestDialect();
        LlmDialectFactory.register(ApiStyle.openai, customDialect);
        
        // 验证方言已被替换
        ILlmDialect current = LlmDialectFactory.getDialect(ApiStyle.openai);
        assertEquals("custom-test", current.getName());
        
        // 恢复原始方言
        LlmDialectFactory.register(ApiStyle.openai, originalOpenai);
    }

    /**
     * 用于测试的自定义方言
     */
    private static class CustomTestDialect extends AbstractLlmDialect implements ILlmDialect {
        @Override
        public String getName() {
            return "custom-test";
        }

        @Override
        public String buildUrl(String baseUrl, String chatUrl, String apiKey) {
            return baseUrl + chatUrl;
        }

        @Override
        public void setHeaders(io.nop.http.api.client.HttpRequest httpRequest, String apiKey, String apiKeyHeader) {
            httpRequest.setHeader("Content-Type", "application/json");
        }

        @Override
        public java.util.Map<String, Object> buildBody(io.nop.ai.api.chat.ChatRequest request,
                io.nop.ai.core.model.LlmModel config, io.nop.ai.core.model.LlmModelModel modelConfig,
                String model, boolean stream) {
            return new java.util.LinkedHashMap<>();
        }

        @Override
        public io.nop.ai.api.chat.ChatResponse parseResponse(String responseBody, io.nop.ai.core.model.LlmModel config) {
            return new io.nop.ai.api.chat.ChatResponse();
        }

        @Override
        public io.nop.ai.api.chat.stream.ChatStreamChunk parseStreamChunk(String data) {
            return null;
        }

        @Override
        public java.util.Map<String, Object> convertMessage(io.nop.ai.api.chat.messages.ChatMessage message,
                io.nop.ai.core.model.LlmModelModel modelConfig, boolean isLast, io.nop.ai.api.chat.ChatOptions options) {
            return new java.util.LinkedHashMap<>();
        }

        @Override
        public String getRole(io.nop.ai.api.chat.messages.ChatMessage message) {
            return getBaseRole(message);
        }
    }
}
