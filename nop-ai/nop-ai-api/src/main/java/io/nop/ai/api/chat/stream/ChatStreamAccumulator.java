/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUsage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式响应累积器
 * <p>
 * 自动累积流式响应的增量数据，最终组装成完整的 ChatAssistantMessage。
 * <p>
 * 支持：
 * 1. 累积文本内容（content）
 * 2. 累积思考过程（thinking）
 * 3. 累积工具调用（tool_calls），包括 arguments 的逐步组装
 * 4. 支持多工具调用的并行累积
 */
public class ChatStreamAccumulator {

    private String id;
    private String role = "assistant";
    private String model;

    // 累积的文本内容
    private final StringBuilder contentBuilder = new StringBuilder();

    // 累积的思考过程
    private final StringBuilder thinkingBuilder = new StringBuilder();

    // 工具调用累积器（key = toolCallIndex）
    private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();

    // 结束原因
    private String finishReason;

    // Token使用统计
    private ChatUsage usage;

    /**
     * 累积一个新的数据块
     *
     * @param chunk 流式数据块
     */
    public void accumulate(ChatStreamChunk chunk) {
        // 累积基本元数据（只设置一次）
        if (this.id == null && chunk.getId() != null) {
            this.id = chunk.getId();
        }
        if (chunk.getRole() != null) {
            this.role = chunk.getRole();
        }
        if (this.model == null && chunk.getModel() != null) {
            this.model = chunk.getModel();
        }

        // 累积内容
        if (chunk.hasContent()) {
            contentBuilder.append(chunk.getContent());
        }

        // 累积思考过程
        if (chunk.hasThinking()) {
            thinkingBuilder.append(chunk.getThinking());
        }

        // 累积工具调用
        if (chunk.hasToolCall()) {
            accumulateToolCall(chunk.getToolCall());
        }

        // 累积结束信息
        if (chunk.getFinishReason() != null) {
            this.finishReason = chunk.getFinishReason();
        }
        if (chunk.getUsage() != null) {
            this.usage = chunk.getUsage();
        }
    }

    /**
     * 累积工具调用增量
     */
    private void accumulateToolCall(ChatToolCallChunk toolCallChunk) {
        Integer index = toolCallChunk.getIndex() != null ? toolCallChunk.getIndex() : 0;

        ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());
        acc.accumulate(toolCallChunk);
    }

    /**
     * 获取累积的完整内容
     */
    public String getAccumulatedContent() {
        return contentBuilder.toString();
    }

    /**
     * 获取累积的思考过程
     */
    public String getAccumulatedThinking() {
        return thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null;
    }

    /**
     * 是否有工具调用
     */
    public boolean hasToolCalls() {
        return !toolCallAccumulators.isEmpty();
    }

    /**
     * 获取累积的工具调用列表
     */
    public List<ChatToolCall> getAccumulatedToolCalls() {
        List<ChatToolCall> result = new ArrayList<>();

        // 按索引排序
        List<Integer> sortedIndexes = new ArrayList<>(toolCallAccumulators.keySet());
        Collections.sort(sortedIndexes);

        for (Integer index : sortedIndexes) {
            ToolCallAccumulator acc = toolCallAccumulators.get(index);
            ChatToolCall toolCall = acc.toChatToolCall();
            if (toolCall != null) {
                result.add(toolCall);
            }
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * 获取结束原因
     */
    public String getFinishReason() {
        return finishReason;
    }

    /**
     * 检查是否已完成（收到 finishReason）
     */
    public boolean isComplete() {
        return finishReason != null;
    }

    /**
     * 组装成完整的助手消息
     *
     * @return ChatAssistantMessage
     */
    public ChatAssistantMessage toAssistantMessage() {
        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setMessageId(id);
        message.setContent(getAccumulatedContent());
        message.setThink(getAccumulatedThinking());

        List<ChatToolCall> toolCalls = getAccumulatedToolCalls();
        if (toolCalls != null) {
            message.setToolCalls(toolCalls);
        }

        return message;
    }

    @Override
    public String toString() {
        return "ChatStreamAccumulator{" +
                "content='" + getAccumulatedContent() + '\'' +
                (thinkingBuilder.length() > 0 ? ", thinking='" + getAccumulatedThinking() + '\'' : "") +
                (hasToolCalls() ? ", toolCalls=" + getAccumulatedToolCalls() : "") +
                (finishReason != null ? ", finishReason='" + finishReason + '\'' : "") +
                '}';
    }


    /**
     * 内部类：单个工具调用累积器
     */
    private static class ToolCallAccumulator {
        private String id;
        private String type = "function";
        private String name;
        private final StringBuilder argumentsBuilder = new StringBuilder();

        public void accumulate(ChatToolCallChunk chunk) {
            if (chunk.getId() != null) {
                this.id = chunk.getId();
            }
            if (chunk.getType() != null) {
                this.type = chunk.getType();
            }
            if (chunk.getName() != null) {
                // 名字可能是增量的，累积
                this.name = chunk.getName();
            }
            if (chunk.getArguments() != null) {
                argumentsBuilder.append(chunk.getArguments());
            }
        }

        public ChatToolCall toChatToolCall() {
            if (id == null && name == null) {
                return null;
            }

            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(id);
            toolCall.setName(name);

            // 解析 arguments JSON
            String argsStr = argumentsBuilder.toString();
            if (argsStr != null && !argsStr.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) io.nop.api.core.json.JSON.parse(argsStr);
                    toolCall.setArguments(args);
                } catch (Exception e) {
                    // JSON 不完整，设置为空或原始字符串
                    toolCall.setArguments(Collections.singletonMap("_raw", argsStr));
                }
            }

            return toolCall;
        }
    }
}
