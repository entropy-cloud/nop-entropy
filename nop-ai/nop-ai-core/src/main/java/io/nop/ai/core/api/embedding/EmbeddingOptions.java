package io.nop.ai.core.api.embedding;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），embedding SPI 契约族的 value 类型（与 {@code IEmbeddingModel} 的 P1-MA5-003 SPI 裁定
 * 一致）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
public class EmbeddingOptions {
    private String model;

    private String tenantId;

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
}
