package io.nop.ai.core.api.embedding;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
/**
 * Embedding 调用选项（K1，plan knowledge-rag/01）：{@code IEmbeddingModel} 生产实现
 * {@code EmbeddingServiceImpl} 的路由/模型选择载体。
 *
 * <p>历史裁定（P2 round-4，2026-09-15）的"零消费者预留"状态已由 K1 落地终结：本类现为
 * 活跃 SPI 契约族的 value 类型。additive 扩展（新增 {@code provider} 字段）不违反
 * "删除需单独 plan + 迁移评估"约束。
 */
public class EmbeddingOptions {
    private String model;

    private String tenantId;

    /**
     * Optional LLM provider name (the {@code /nop/ai/llm/{provider}.llm.xml} config name)
     * to route the embedding call to. When null, falls back to the
     * {@code nop.ai.embedding.default-llm} config variable; when both are unset the
     * embedding call fails loud (no implicit provider).
     */
    private String provider;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
