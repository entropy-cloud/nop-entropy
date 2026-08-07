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
 * 自动累积 item 增量 chunk，最终组装成完整的 ChatAssistantMessage。
 * <p>
 * Plan 328 Phase 3：改造为按 itemType 累积（text/reasoning/tool_call）。
 * 支持：
 * 1. 累积文本内容（text item delta）
 * 2. 累积思考过程（reasoning item delta）
 * 3. 累积工具调用（tool_call item，按 itemIndex/callId 区分多调用），
 *    包括 arguments 的逐步组装
 */
public class ChatStreamAccumulator {

    private String id;
    private String role = "assistant";
    private String model;

    // 累积的文本内容
    private final StringBuilder contentBuilder = new StringBuilder();

    // 累积的思考过程
    private final StringBuilder thinkingBuilder = new StringBuilder();

    // 工具调用累积器（key = itemIndex）
    private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();

    // 结束原因
    private String finishReason;

    // Token使用统计
    private ChatUsage usage;

    /**
     * 累积一个新的 item 增量数据块
     *
     * @param chunk 流式数据块
     */
    public void accumulate(ChatStreamChunk chunk) {
        // 累积基本元数据（只设置一次）
        if (this.id == null && chunk.getId() != null) {
            this.id = chunk.getId();
        }
        if (this.model == null && chunk.getModel() != null) {
            this.model = chunk.getModel();
        }

        // 累积结束信息
        if (chunk.getFinishReason() != null) {
            this.finishReason = chunk.getFinishReason();
        }
        if (chunk.getUsage() != null) {
            this.usage = chunk.getUsage();
        }

        StreamItemType type = chunk.getItemType();
        if (type == null) {
            return;
        }

        switch (type) {
            case text:
                if (chunk.getDelta() != null) {
                    contentBuilder.append(chunk.getDelta());
                }
                break;
            case reasoning:
                if (chunk.getDelta() != null) {
                    thinkingBuilder.append(chunk.getDelta());
                }
                break;
            case tool_call:
                accumulateToolCall(chunk);
                break;
            default:
                break;
        }
    }

    /**
     * 累积工具调用 item 增量
     */
    private void accumulateToolCall(ChatStreamChunk chunk) {
        Integer index = chunk.getItemIndex() != null ? chunk.getItemIndex() : 0;

        ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());
        acc.apply(chunk);
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
     * 组装成完整的助手消息（plan 329：assistant 文本消息仅承载 content；推理/工具调用
     * 不再寄居于此，分别由 {@link #getAccumulatedThinking()} / {@link #getAccumulatedToolCalls()} 暴露）。
     *
     * @return ChatAssistantMessage
     */
    public ChatAssistantMessage toAssistantMessage() {
        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setMessageId(id);
        message.setContent(getAccumulatedContent());
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
     * <p>
     * ADDED 阶段 delta = 函数名，callId = 调用 id；DELTA 阶段 delta = arguments 片段。
     */
    private static class ToolCallAccumulator {
        private String id;
        private String type = "function";
        private String name;
        private final StringBuilder argumentsBuilder = new StringBuilder();

        public void apply(ChatStreamChunk chunk) {
            if (chunk.getCallId() != null) {
                this.id = chunk.getCallId();
            }
            if (chunk.getDelta() == null) {
                return;
            }
            if (chunk.getPhase() == StreamItemPhase.ADDED) {
                this.name = chunk.getDelta();
            } else {
                argumentsBuilder.append(chunk.getDelta());
            }
        }

        public ChatToolCall toChatToolCall() {
            if (id == null && name == null && argumentsBuilder.length() == 0) {
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
