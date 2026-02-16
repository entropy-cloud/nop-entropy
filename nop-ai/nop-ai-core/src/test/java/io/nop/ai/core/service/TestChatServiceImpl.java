/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.LlmModel;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.http.api.client.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatServiceImpl 单元测试
 * 验证不同模型的配置兼容性和请求构建
 */
public class TestChatServiceImpl {

    private ChatServiceImpl chatService;

    @BeforeEach
    public void setUp() {
        chatService = new ChatServiceImpl();
    }

    /**
     * 测试 OpenAI 风格模型配置加载
     */
    @Test
    public void testOpenAiConfigLoad() {
        LlmModel config = loadConfig("default");
        assertNotNull(config);
        assertNotNull(config.getRequest());
        assertNotNull(config.getResponse());
        assertEquals("choices.0.message.content", config.getResponse().getContentPath());
    }

    /**
     * 测试 Ollama 模型配置加载
     */
    @Test
    public void testOllamaConfigLoad() {
        LlmModel config = loadConfig("ollama");
        assertNotNull(config);
        assertNotNull(config.getRequest());
        assertNotNull(config.getResponse());
        // Ollama 特有的字段路径
        assertEquals("message.content", config.getResponse().getContentPath());
        assertEquals("options.num_predict", config.getRequest().getMaxTokensPath());
    }

    /**
     * 测试 DeepSeek 模型配置继承
     */
    @Test
    public void testDeepSeekConfigLoad() {
        LlmModel config = loadConfig("deepseek");
        assertNotNull(config);
        assertEquals("https://api.deepseek.com", config.getBaseUrl());
        assertEquals("/chat/completions", config.getChatUrl());
        // 应该继承 default 的配置
        assertNotNull(config.getResponse());
    }

    /**
     * 测试 Azure 配置
     */
    @Test
    public void testAzureConfigLoad() {
        LlmModel config = loadConfig("azure");
        assertNotNull(config);
        assertEquals("api-key", config.getApiKeyHeader());
        assertEquals(0.1, config.getRateLimit());
        // 验证模型配置
        assertNotNull(config.getModel("gpt-4o"));
        assertEquals(16384, config.getModel("gpt-4o").getMaxTokensLimit());
    }

    /**
     * 测试 Claude 配置
     */
    @Test
    public void testClaudeConfigLoad() {
        LlmModel config = loadConfig("claude");
        assertNotNull(config);
        assertEquals("x-api-key", config.getApiKeyHeader());
        assertTrue(config.isSupportToolCalls());
        // Claude 特有的响应路径
        assertEquals("content.0.text", config.getResponse().getContentPath());
        assertEquals("usage.input_tokens", config.getResponse().getPromptTokensPath());
    }

    /**
     * 测试 Gemini 配置
     */
    @Test
    public void testGeminiConfigLoad() {
        LlmModel config = loadConfig("gemini");
        assertNotNull(config);
        // Gemini 使用 URL 模板
        assertTrue(config.getChatUrl().contains("{model}"));
        // 验证别名
        assertNotNull(config.getAliasMap());
        assertEquals("gemini-1.5-pro", config.getAliasMap().get("gemini-pro"));
    }

    /**
     * 测试模型别名解析
     */
    @Test
    public void testModelAliasResolution() {
        LlmModel config = loadConfig("volcengine");
        
        ChatOptions options = new ChatOptions();
        options.setModel("deepseek-chat"); // 别名
        
        String resolvedModel = chatService.resolveModel(config, options);
        assertEquals("deepseek-v3-250324", resolvedModel);
    }

    /**
     * 测试模型特定配置查找
     */
    @Test
    public void testModelSpecificConfig() {
        LlmModel config = loadConfig("volcengine");
        
        // 测试完整模型名
        assertNotNull(chatService.getModelConfig(config, "qwen3"));
        
        // 测试带版本的模型名（应该能找到基础配置）
        assertNotNull(chatService.getModelConfig(config, "qwen3:14b"));
    }

    /**
     * 测试 OpenAI 风格请求构建
     */
    @Test
    public void testOpenAiRequestBuilding() {
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatSystemMessage("You are a helpful assistant"));
        request.addMessage(new ChatUserMessage("Hello"));
        
        ChatOptions options = new ChatOptions();
        options.setModel("gpt-4");
        options.setTemperature(0.7f);
        options.setMaxTokens(100);
        request.setOptions(options);
        
        LlmModel config = loadConfig("default");
        Map<String, Object> body = new java.util.HashMap<>();
        chatService.buildRequestBody(body, "default", config, "gpt-4", request);
        
        assertEquals("gpt-4", body.get("model"));
        assertFalse((Boolean) body.get("stream"));
        
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> messages = (java.util.List<Map<String, Object>>) body.get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertEquals("user", messages.get(1).get("role"));
    }

    /**
     * 测试 Ollama 风格请求构建（嵌套字段路径）
     */
    @Test
    public void testOllamaRequestBuilding() {
        ChatRequest request = new ChatRequest();
        request.addMessage(new ChatUserMessage("Hello"));
        
        ChatOptions options = new ChatOptions();
        options.setModel("qwen3");
        options.setTemperature(0.5f);
        options.setMaxTokens(500);
        request.setOptions(options);
        
        LlmModel config = loadConfig("ollama");
        Map<String, Object> body = new java.util.HashMap<>();
        chatService.buildRequestBody(body, "ollama", config, "qwen3", request);
        
        // Ollama 使用嵌套路径 options.temperature
        @SuppressWarnings("unchecked")
        Map<String, Object> optionsMap = (Map<String, Object>) body.get("options");
        assertNotNull(optionsMap);
        assertEquals(0.5f, optionsMap.get("temperature"));
        assertEquals(500, optionsMap.get("num_predict"));
    }

    /**
     * 测试思考模式提示词应用
     */
    @Test
    public void testThinkingPrompt() {
        LlmModel config = loadConfig("volcengine");
        
        String content = "What is 2+2?";
        ChatOptions enableThinkingOptions = new ChatOptions();
        enableThinkingOptions.setEnableThinking(true);
        
        String result = chatService.applyThinkingPrompt(
            content, 
            config.getModel("qwen3"), 
            enableThinkingOptions
        );
        
        // qwen3 配置中有 disableThinkingPrompt: /no_think
        // 启用思考模式时不应该添加 /no_think
        assertEquals(content, result);
        
        ChatOptions disableThinkingOptions = new ChatOptions();
        disableThinkingOptions.setEnableThinking(false);
        
        String result2 = chatService.applyThinkingPrompt(
            content,
            config.getModel("qwen3"),
            disableThinkingOptions
        );
        
        // 禁用思考模式时应该添加 /no_think
        assertTrue(result2.contains("/no_think"));
    }

    /**
     * 测试思考内容提取
     */
    @Test
    public void testThinkContentExtraction() {
        String content = "<think>\nLet me think about this...\n</think>\nThe answer is 4.";
        
        LlmModel config = loadConfig("default");
        String thinkContent = chatService.extractThinkContent(content, config, null);
        String remainingContent = chatService.removeThinkContent(content, config, null);
        
        assertEquals("Let me think about this...", thinkContent);
        assertEquals("The answer is 4.", remainingContent.trim());
    }

    /**
     * 测试自定义思考标记
     */
    @Test
    public void testCustomThinkMarkers() {
        LlmModel config = loadConfig("deepseek");
        // deepseek-chat 可能有自定义的思考标记配置
        
        // 测试标准标记
        String content = "<think>Thinking...</think>Answer";
        String thinkContent = chatService.extractThinkContent(content, config, "deepseek-chat");
        
        // 如果没配置自定义标记，使用默认的 <think>
        assertNotNull(thinkContent);
    }

    /**
     * 测试 MaxTokens 限制
     */
    @Test
    public void testMaxTokensLimit() {
        LlmModel config = loadConfig("azure");
        
        // gpt-4o 的 maxTokensLimit 是 16384
        ChatOptions options = new ChatOptions();
        options.setMaxTokens(20000); // 超过限制
        
        Integer resolvedTokens = chatService.resolveMaxTokens(config, "gpt-4o", options);
        assertEquals(16384, resolvedTokens); // 应该被限制到 16384
    }

    /**
     * 测试工具调用格式转换
     */
    @Test
    public void testToolCallConversion() {
        io.nop.ai.api.chat.messages.ChatToolCall toolCall = 
            new io.nop.ai.api.chat.messages.ChatToolCall();
        toolCall.setId("call_123");
        toolCall.setName("getWeather");
        toolCall.setArguments(Map.of("location", "Beijing"));
        
        java.util.List<Map<String, Object>> result = 
            chatService.convertToolCalls(java.util.List.of(toolCall));
        
        assertEquals(1, result.size());
        assertEquals("call_123", result.get(0).get("id"));
        assertEquals("getWeather", result.get(0).get("name"));
    }

    /**
     * 测试消息角色映射
     */
    @Test
    public void testRoleMapping() {
        assertEquals("user", chatService.getRole(new ChatUserMessage("test")));
        assertEquals("assistant", chatService.getRole(new ChatAssistantMessage("test")));
        assertEquals("system", chatService.getRole(new ChatSystemMessage("test")));
    }

    // ==================== 辅助方法 ====================

    private LlmModel loadConfig(String provider) {
        String path = "/nop/ai/llm/" + provider + ".llm.xml";
        return (LlmModel) ResourceComponentManager.instance().loadComponentModel(path);
    }
}
