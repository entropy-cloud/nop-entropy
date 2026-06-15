package io.nop.ai.dao.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.math.BigDecimal;

/**
 * 聚合查询结果 DTO：一个 session 内按模型维度聚合的 token 用量（design
 * {@code nop-ai-agent-usage-and-billing.md} §3.2 / plan 203 L2-20 + plan 204 L2-19）。
 *
 * <p>由 {@code NopAiChatResponseBizModel.summarizeByModel(sessionId)} 通过 SQL
 * GROUP BY {@code model_id, ai_provider, ai_model} 聚合 {@code nop_ai_chat_response}
 * 表得到。不是持久化实体，仅作为查询出参。
 *
 * <p>{@code estimatedCost} 不再恒为 {@code null}（plan 204 / L2-19 落地）：聚合 SQL 通过
 * {@code LEFT JOIN nop_ai_model} 计算定价。为 {@code null} 的两种 graceful degradation 情形：
 * (1) {@code model_id} 为 null（无匹配 {@code nop_ai_model} 行可 join）；(2) {@code model_id}
 * 非 null 但 {@code nop_ai_model} 行的 {@code input_price_per_1m} 或 {@code output_price_per_1m} 为 null。
 */
@DataBean
public class ModelUsageSummary {

    /** 模型ID（{@code nop_ai_model} 主键），可能为 null（DbUsageRecorder 按 provider+model 找不到匹配时） */
    private String modelId;

    /** 供应商，GROUP BY 键之一 */
    private String aiProvider;

    /** 模型名称，GROUP BY 键之一 */
    private String aiModel;

    /** 该组 promptTokens 总和 */
    private long totalPromptTokens;

    /** 该组 completionTokens 总和 */
    private long totalCompletionTokens;

    /** 该组 LLM 调用次数 */
    private long callCount;

    /** 该组 responseDurationMs 总和 */
    private long totalDurationMs;

    /**
     * 预估成本。当 {@code nop_ai_model} 有定价数据且 {@code model_id} 可 join 时返回计算值
     * （{@code SUM(prompt_tokens * input_price_per_1m / 1000000
     * + completion_tokens * output_price_per_1m / 1000000)}）；否则为 {@code null}（见类 javadoc 的
     * 两种 graceful degradation 情形）。
     */
    private BigDecimal estimatedCost;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public long getTotalPromptTokens() {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(long totalPromptTokens) {
        this.totalPromptTokens = totalPromptTokens;
    }

    public long getTotalCompletionTokens() {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(long totalCompletionTokens) {
        this.totalCompletionTokens = totalCompletionTokens;
    }

    public long getCallCount() {
        return callCount;
    }

    public void setCallCount(long callCount) {
        this.callCount = callCount;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(BigDecimal estimatedCost) {
        this.estimatedCost = estimatedCost;
    }
}
