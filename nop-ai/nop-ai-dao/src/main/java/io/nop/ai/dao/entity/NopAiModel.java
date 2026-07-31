package io.nop.ai.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.ai.dao.entity._gen._NopAiModel;

@BizObjName("NopAiModel")
public class NopAiModel extends _NopAiModel {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("NopAiModel{");
        sb.append("id=").append(getId());
        sb.append(", provider=").append(getProvider());
        sb.append(", modelName=").append(getModelName());
        sb.append(", apiKey=").append(getApiKey() != null ? "***" : null);
        sb.append("}");
        return sb.toString();
    }
}
