package io.nop.ai.core.service;

import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.api.core.json.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用帮助类。
 * 负责工具定义和工具调用的格式转换。
 */
public class ToolCallHelper {

    /**
     * 转换工具定义为API格式
     */
    public static List<Map<String, Object>> convertOpenAiToolDefinitions(List<ChatToolDefinition> tools) {
        List<Map<String, Object>> toolsJson = new ArrayList<>();
        for (ChatToolDefinition tool : tools) {
            Map<String, Object> toolJson = new LinkedHashMap<>();
            toolJson.put("type", "function");

            Map<String, Object> functionJson = new LinkedHashMap<>();
            functionJson.put("name", tool.getName());
            functionJson.put("description", tool.getDescription());
            if (tool.getParameters() != null) {
                functionJson.put("parameters", tool.getParameters());
            }

            toolJson.put("function", functionJson);
            toolsJson.add(toolJson);
        }
        return toolsJson;
    }

    /**
     * 转换 Anthropic 工具定义格式
     * <pre>
     * {
     *   "name": "...",
     *   "description": "...",
     *   "input_schema": {...}
     * }
     * </pre>
     */
    public static List<Map<String, Object>> convertAnthropicToolDefinitions(
            List<io.nop.ai.api.chat.messages.ChatToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (io.nop.ai.api.chat.messages.ChatToolDefinition tool : tools) {
            Map<String, Object> toolJson = new LinkedHashMap<>();
            toolJson.put("name", tool.getName());
            toolJson.put("description", tool.getDescription());
            if (tool.getParameters() != null) {
                toolJson.put("input_schema", tool.getParameters());
            }
            result.add(toolJson);
        }
        return result;
    }

    /**
     * 转换工具调用为API格式
     */
    public static List<Map<String, Object>> convertToolCalls(List<ChatToolCall> toolCalls) {
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
