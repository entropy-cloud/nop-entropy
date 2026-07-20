package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚合查询单行 DTO（来源：{@code NopMetaTableBizModel.queryAggregation}）。
 */
@DataBean
public class AggregationRowDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> dimensions = new LinkedHashMap<>();
    private Map<String, Object> measures = new LinkedHashMap<>();

    public Map<String, Object> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, Object> dimensions) {
        this.dimensions = dimensions;
    }

    public Map<String, Object> getMeasures() {
        return measures;
    }

    public void setMeasures(Map<String, Object> measures) {
        this.measures = measures;
    }
}
