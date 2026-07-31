package io.nop.ai.core.api.embedding;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
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
