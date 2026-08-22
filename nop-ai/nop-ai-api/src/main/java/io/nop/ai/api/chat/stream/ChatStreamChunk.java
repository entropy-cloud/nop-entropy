/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.api.chat.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.api.core.annotations.data.DataBean;

/**
 * 流式响应 item 增量数据块。
 *
 * <p>表示 AI 流式响应中的一个 item 增量片段。每个 chunk 携带一个 item 的维度信息：
 * <ul>
 *   <li>{@link #itemType} — item 类型（text / reasoning / tool_call）</li>
 *   <li>{@link #itemIndex} — item 序号（多 tool_call 时靠它区分并行调用）</li>
 *   <li>{@link #callId} — 仅 tool_call 有效，工具调用 id</li>
 *   <li>{@link #delta} — 增量载荷：text/reasoning 为内容片段；
 *       tool_call 在 {@link StreamItemPhase#ADDED} 时为函数名、
 *       {@link StreamItemPhase#DELTA} 时为 arguments JSON 片段</li>
 *   <li>{@link #arguments} — tool_call 完整 arguments JSON 文本：provider 在单事件内
 *       完整下发 name + args（Gemini/Ollama 型）时与 {@link #delta}（函数名）同载，
 *       弥补单 chunk 无法再发一个 DELTA 片段的通道缺口</li>
 *   <li>{@link #phase} — 生命周期阶段（ADDED / DELTA / DONE）</li>
 * </ul>
 *
 * <p>重构自旧的「折叠 delta」模型（content/thinking/toolCall 三字段粘在一个 chunk），
 * 使流式 chunk 与非流式 {@code ChatResponse.messages} 同构（item 边界在 canonical 层保留），
 * 为 Responses 具名事件流提供统一解析模型。
 *
 * <p>设计来源：{@code ai-dev/design/nop-ai-responses-migration-design.md} §3.3。
 */
@DataBean
public class ChatStreamChunk {

    /**
     * 流 ID
     */
    private String id;

    /**
     * 模型名称
     */
    private String model;

    /**
     * item 类型
     */
    private StreamItemType itemType;

    /**
     * item 序号（多 tool_call 区分）
     */
    private Integer itemIndex;

    /**
     * 工具调用 id（仅 tool_call 有效）
     */
    private String callId;

    /**
     * 增量载荷
     */
    private String delta;

    /**
     * tool_call 完整 arguments JSON 文本（可选）。
     * <p>
     * 适用于在单个事件内完整下发 name + args 的 provider（Gemini functionCall.args /
     * Ollama tool_calls.function.arguments）。此类 provider 无法按 OpenAI 惯例
     * 拆成 ADDED(name) + DELTA(args 片段) 两个 chunk（{@code parseStreamChunk}
     * 单事件单返回），故在 ADDED chunk 上经本字段同载完整 args，由聚合端与
     * DELTA 片段一并拼装。为 null 时序列化忽略（通道可选，向后兼容）。
     */
    private String arguments;

    /**
     * 生命周期阶段
     */
    private StreamItemPhase phase;

    /**
     * 结束原因（DONE 阶段或终止 chunk 会有）
     */
    private String finishReason;

    /**
     * Token 使用统计（终止 chunk 可能有）
     */
    private ChatUsage usage;

    public ChatStreamChunk() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public StreamItemType getItemType() {
        return itemType;
    }

    public void setItemType(StreamItemType itemType) {
        this.itemType = itemType;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(Integer itemIndex) {
        this.itemIndex = itemIndex;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getDelta() {
        return delta;
    }

    public void setDelta(String delta) {
        this.delta = delta;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public StreamItemPhase getPhase() {
        return phase;
    }

    public void setPhase(StreamItemPhase phase) {
        this.phase = phase;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ChatUsage getUsage() {
        return usage;
    }

    public void setUsage(ChatUsage usage) {
        this.usage = usage;
    }

    /**
     * 检查是否为终止 chunk（携带 finishReason）
     */
    public boolean isLastChunk() {
        return finishReason != null;
    }

    /**
     * 检查是否有增量载荷
     */
    public boolean hasDelta() {
        return delta != null && !delta.isEmpty();
    }

    /**
     * item 类型判定：文本
     */
    public boolean isTextItem() {
        return itemType == StreamItemType.text;
    }

    /**
     * item 类型判定：推理
     */
    public boolean isReasoningItem() {
        return itemType == StreamItemType.reasoning;
    }

    /**
     * item 类型判定：工具调用
     */
    public boolean isToolCallItem() {
        return itemType == StreamItemType.tool_call;
    }

    /**
     * 阶段判定：item 首次声明
     */
    public boolean isAdded() {
        return phase == StreamItemPhase.ADDED;
    }

    /**
     * 阶段判定：增量累加
     */
    public boolean isDelta() {
        return phase == StreamItemPhase.DELTA;
    }

    /**
     * 阶段判定：结束
     */
    public boolean isDone() {
        return phase == StreamItemPhase.DONE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ChatStreamChunk{");
        if (id != null) sb.append("id='").append(id).append('\'');
        if (itemType != null) sb.append(", itemType=").append(itemType);
        if (itemIndex != null) sb.append(", itemIndex=").append(itemIndex);
        if (callId != null) sb.append(", callId='").append(callId).append('\'');
        if (phase != null) sb.append(", phase=").append(phase);
        if (delta != null) sb.append(", delta='").append(delta).append('\'');
        if (arguments != null) sb.append(", arguments='").append(arguments).append('\'');
        if (finishReason != null) sb.append(", finishReason='").append(finishReason).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
