
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聚合查询结果 DTO（来源：{@code NopMetaTableBizModel.queryAggregation}）。
 *
 * <p>对应原 {@code Map<String,Object>}：{@code {items:[{dimensions:{...}, measures:{...}}]}}。
 */
@DataBean
public class AggregationResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Map<String, Object>> items = new ArrayList<>();

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }
}
