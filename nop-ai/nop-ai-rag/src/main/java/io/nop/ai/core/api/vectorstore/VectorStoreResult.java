package io.nop.ai.core.api.vectorstore;

import io.nop.ai.core.api.support.Metadata;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
/**
 * <b>Reserved（P2 round-4 可靠性面裁定，2026-09-15）</b>：当前无生产消费者（全仓 main/test 零
 * import），vector store SPI 契约族的 value 类型（与 {@code IVectorStore} 的 P1-MA5-003 SPI 裁定
 * 一致）。保留为公共 API 预留；删除需单独 plan + 迁移评估。
 */
public class VectorStoreResult extends Metadata {
    private List<Object> ids;

    public List<Object> getIds() {
        return ids;
    }

    public void setIds(List<Object> ids) {
        this.ids = ids;
    }
}
