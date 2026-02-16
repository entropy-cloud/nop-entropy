package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.api.core.json.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息转换器。
 * <p>
 * 负责将内部消息对象转换为不同 API 风格的格式。
 * 支持多种 AI 提供商的特定格式：
 * <ul>
 *   <li>OpenAI/Ollama - 标准 messages 格式</li>
 *   <li>Anthropic (Claude) - content 数组格式</li>
 *   <li>Google (Gemini) - parts 数组格式</li>
 * </ul>
 */
public class MessageConverter {

    /**
     * 转换消息为指定 API 风格的格式
     *
     * @param message 消息对象
     * @param apiStyle API 风格
     * @param modelConfig 模型配置
     * @param isLast 是否是最后一条消息
     * @param options 聊天选项
     * @return 转换后的 Map
     */
    public static Map<String, Object> convert(ChatMessage message, ApiStyle apiStyle,
                                               LlmModelModel modelConfig, boolean isLast, 
                                               ChatOptions options) {
        if (apiStyle == null) {
            apiStyle = ApiStyle.openai;
        }
        
        switch (apiStyle) {
            case anthropic:
                return convertAnthropic(message, modelConfig, isLast, options);
            case gemini:
                return convertGemini(message, modelConfig, isLast, options);
            case openai:
            case ollama:
            default:
                return convertOpenAi(message, modelConfig, isLast, options);
        }
    }

    /**
     * 转换为 OpenAI 风格格式
     */
    private static Map<String, Object> convertOpenAi(ChatMessage message, LlmModelModel modelConfig,
                                                      boolean isLast, ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();

        msgMap.put("role", getRole(message, ApiStyle.openai));

        String content = message.getContent();
        if (isLast && modelConfig != null) {
            content = applyThinkingPrompt(content, modelConfig, options);
        }
        msgMap.put("content", content);

        if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            if (assistantMsg.getThink() != null) {
                msgMap.put("thinking", assistantMsg.getThink());
            }
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                msgMap.put("tool_calls", convertOpenAiToolCalls(assistantMsg.getToolCalls()));
            }
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            msgMap.put("tool_call_id", toolMsg.getToolCallId());
            msgMap.put("name", toolMsg.getName());
        }

        return msgMap;
    }

    /**
     * 转换为 Anthropic 风格格式（content 数组）
     */
    private static Map<String, Object> convertAnthropic(ChatMessage message, LlmModelModel modelConfig,
                                                         boolean isLast, ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();
        
        msgMap.put("role", getRole(message, ApiStyle.anthropic));

        List<Map<String, Object>> contentBlocks = new ArrayList<>();

        String textContent = message.getContent();
        if (isLast && modelConfig != null) {
            textContent = applyThinkingPrompt(textContent, modelConfig, options);
        }
        
        if (textContent != null && !textContent.isEmpty()) {
            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", textContent);
            contentBlocks.add(textBlock);
        }

        if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            
            if (assistantMsg.getThink() != null) {
                Map<String, Object> thinkingBlock = new LinkedHashMap<>();
                thinkingBlock.put("type", "thinking");
                thinkingBlock.put("thinking", assistantMsg.getThink());
                contentBlocks.add(thinkingBlock);
            }
            
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                for (ChatToolCall toolCall : assistantMsg.getToolCalls()) {
                    Map<String, Object> toolBlock = new LinkedHashMap<>();
                    toolBlock.put("type", "tool_use");
                    toolBlock.put("id", toolCall.getId());
                    toolBlock.put("name", toolCall.getName());
                    toolBlock.put("input", toolCall.getArguments());
                    contentBlocks.add(toolBlock);
                }
            }
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            Map<String, Object> toolResultBlock = new LinkedHashMap<>();
            toolResultBlock.put("type", "tool_result");
            toolResultBlock.put("tool_use_id", toolMsg.getToolCallId());
            toolResultBlock.put("content", toolMsg.getContent());
            contentBlocks.add(toolResultBlock);
        }

        msgMap.put("content", contentBlocks);
        return msgMap;
    }

    /**
     * 转换为 Gemini 风格格式（parts 数组）
     */
    private static Map<String, Object> convertGemini(ChatMessage message, LlmModelModel modelConfig,
                                                      boolean isLast, ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();
        
        msgMap.put("role", getRole(message, ApiStyle.gemini));

        List<Map<String, Object>> parts = new ArrayList<>();

        String textContent = message.getContent();
        if (isLast && modelConfig != null) {
            textContent = applyThinkingPrompt(textContent, modelConfig, options);
        }
        
        if (textContent != null && !textContent.isEmpty()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", textContent);
            parts.add(textPart);
        }

        if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            
            if (assistantMsg.getThink() != null) {
                Map<String, Object> thinkingPart = new LinkedHashMap<>();
                thinkingPart.put("text", "<thinking>" + assistantMsg.getThink() + "</thinking>");
                parts.add(thinkingPart);
            }
            
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                for (ChatToolCall toolCall : assistantMsg.getToolCalls()) {
                    Map<String, Object> functionCall = new LinkedHashMap<>();
                    functionCall.put("name", toolCall.getName());
                    functionCall.put("args", toolCall.getArguments());
                    
                    Map<String, Object> toolPart = new LinkedHashMap<>();
                    toolPart.put("functionCall", functionCall);
                    parts.add(toolPart);
                }
            }
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            Map<String, Object> functionResponse = new LinkedHashMap<>();
            functionResponse.put("name", toolMsg.getName());
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("result", toolMsg.getContent());
            functionResponse.put("response", response);
            
            Map<String, Object> toolPart = new LinkedHashMap<>();
            toolPart.put("functionResponse", functionResponse);
            parts.add(toolPart);
        }

        msgMap.put("parts", parts);
        return msgMap;
    }

    /**
     * 获取消息角色
     * 
     * @param message 消息对象
     * @param apiStyle API 风格（Anthropic 和 Gemini 使用 "model" 而非 "assistant"）
     */
    public static String getRole(ChatMessage message, ApiStyle apiStyle) {
        String role;
        if (message instanceof ChatUserMessage) {
            role = "user";
        } else if (message instanceof ChatAssistantMessage) {
            role = "assistant";
        } else if (message instanceof ChatSystemMessage) {
            role = "system";
        } else if (message instanceof ChatToolResponseMessage) {
            role = "tool";
        } else {
            role = "user";
        }
        
        if ("assistant".equals(role) && (apiStyle == ApiStyle.anthropic || apiStyle == ApiStyle.gemini)) {
            return "model";
        }
        
        return role;
    }

    /**
     * 获取消息角色（默认 OpenAI 风格）
     */
    public static String getRole(ChatMessage message) {
        return getRole(message, ApiStyle.openai);
    }

    /**
     * 应用思考模式提示词
     */
    public static String applyThinkingPrompt(String content, LlmModelModel modelConfig, ChatOptions options) {
        if (content == null) {
            return null;
        }

        boolean enableThinking = options != null && Boolean.TRUE.equals(options.getEnableThinking());

        if (enableThinking && modelConfig.getEnableThinkingPrompt() != null) {
            return content + "\n" + modelConfig.getEnableThinkingPrompt();
        } else if (!enableThinking && modelConfig.getDisableThinkingPrompt() != null) {
            return content + "\n" + modelConfig.getDisableThinkingPrompt();
        }

        return content;
    }

    /**
     * 转换为 OpenAI 风格的工具调用列表
     */
    public static List<Map<String, Object>> convertOpenAiToolCalls(List<ChatToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatToolCall toolCall : toolCalls) {
            Map<String, Object> callJson = new LinkedHashMap<>();
            callJson.put("id", toolCall.getId());
            callJson.put("type", "function");

            Map<String, Object> functionJson = new LinkedHashMap<>();
            functionJson.put("name", toolCall.getName());
            functionJson.put("arguments", JSON.stringify(toolCall.getArguments()));

            callJson.put("function", functionJson);
            result.add(callJson);
        }
        return result;
    }
}
