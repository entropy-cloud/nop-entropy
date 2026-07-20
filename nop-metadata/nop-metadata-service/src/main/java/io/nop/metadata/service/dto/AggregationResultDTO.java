package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合查询结果 DTO（来源：{@code NopMetaTableBizModel.queryAggregation}）。
 *
 * <p>对应原 {@code Map<String,Object>}：{@code {items:[{dimensions:{...}, measures:{...}}]}}。
 */
@DataBean
public class AggregationResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<AggregationRowDTO> items = new ArrayList<>();

    public List<AggregationRowDTO> getItems() {
        return items;
    }

    public void setItems(List<AggregationRowDTO> items) {
        this.items = items;
    }
}
