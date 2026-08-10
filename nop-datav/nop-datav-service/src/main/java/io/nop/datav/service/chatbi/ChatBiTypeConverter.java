package io.nop.datav.service.chatbi;

import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolModel;
import io.nop.core.lang.xml.XNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * chat-api ↔ toolkit 类型转换工具类（裁定 B）。
 *
 * <p>三个转换逻辑参考 {@code AgentToolDispatcher:194-196} / {@code :307-324} +
 * {@code ToolSchemaConverter.convert(XNode)}，以**内联复制**方式落地（不引入 nop-ai-agent 依赖）。
 * 见 {@code ai-dev/design/nop-datav/ai-design.md} §2 裁定 B 的字段映射表。</p>
 */
public final class ChatBiTypeConverter {

    private ChatBiTypeConverter() {
    }

    /**
     * {@link ChatToolCall}（chat-api）→ {@link AiToolCall}（toolkit）。
     *
     * <p>参考 {@code AgentToolDispatcher:194-196}：{@code name}→{@code toolName}、
     * {@code argumentsText}(JSON)→{@code input}。{@link AiToolCall} 无 toolCallId 字段，
     * toolCallId 由调用方保留原 {@link ChatToolCall} 引用单独追踪。</p>
     */
    public static AiToolCall toAiToolCall(ChatToolCall chatToolCall) {
        AiToolCall aiToolCall = new AiToolCall();
        aiToolCall.setToolName(chatToolCall.getName());
        aiToolCall.setInput(chatToolCall.getArgumentsText());
        return aiToolCall;
    }

    /**
     * {@link AiToolModel}（toolkit）→ {@link ChatToolDefinition}（chat-api）。
     *
     * <p>{@code name}→{@code name}、{@code description}→{@code description}；
     * {@code schema} XNode 经内联的 {@code ToolSchemaConverter.convert()} 逻辑转 {@code parameters} Map。</p>
     */
    public static ChatToolDefinition toChatToolDefinition(AiToolModel toolModel) {
        ChatToolDefinition def = new ChatToolDefinition();
        def.setName(toolModel.getName());
        def.setDescription(toolModel.getDescription());
        Map<String, Object> parameters = convertSchemaToParameters(toolModel.getSchema());
        if (parameters != null) {
            def.setParameters(parameters);
        }
        return def;
    }

    /**
     * {@link AiToolCallResult}（toolkit）→ {@link ChatToolResponseMessage}（chat-api）。
     *
     * <p>参考 {@code AgentToolDispatcher:307-324}：成功时取 {@code output.body}，失败时取 {@code error.body}；
     * **用保留的原 {@link ChatToolCall} 引用恢复 toolCallId**（{@link AiToolCall} 不携带 toolCallId）。</p>
     */
    public static ChatToolResponseMessage toChatToolResponseMessage(ChatToolCall chatToolCall,
                                                                    AiToolCallResult result) {
        if ("success".equals(result.getStatus()) && result.getError() == null) {
            String resultText = result.getOutput() != null ? result.getOutput().getBody() : "";
            resultText = resultText != null ? resultText : "";
            return ChatToolResponseMessage.fromToolCall(chatToolCall, resultText);
        }
        String errorMsg = result.getError() != null ? result.getError().getBody() : "unknown error";
        return ChatToolResponseMessage.error(
                chatToolCall.getId(),
                chatToolCall.getName(),
                errorMsg != null ? errorMsg : "unknown error");
    }

    /**
     * 内联复制 {@code ToolSchemaConverter.convert(XNode)} 的逻辑（不引入 nop-ai-agent 依赖）。
     * 将工具 XML schema 节点转为 LLM-facing JSON schema Map。
     */
    private static Map<String, Object> convertSchemaToParameters(XNode schema) {
        if (schema == null) {
            return null;
        }
        try {
            return ToolSchemaConverterInline.convert(schema);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 批量转换工具模型。
     */
    public static List<ChatToolDefinition> toChatToolDefinitions(List<AiToolModel> toolModels) {
        List<ChatToolDefinition> result = new ArrayList<>();
        if (toolModels == null) {
            return result;
        }
        for (AiToolModel model : toolModels) {
            result.add(toChatToolDefinition(model));
        }
        return result;
    }
}
