package io.nop.ai.core.api.vectorstore;

import io.nop.ai.core.api.support.Metadata;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class VectorStoreResult extends Metadata {
    private List<Object> ids;

    public List<Object> getIds() {
        return ids;
    }

    public void setIds(List<Object> ids) {
        this.ids = ids;
    }
}
