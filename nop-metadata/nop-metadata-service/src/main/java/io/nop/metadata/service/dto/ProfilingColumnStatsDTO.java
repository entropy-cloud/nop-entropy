/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据剖析列统计 DTO（来源：{@code NopMetaTableBizModel.profileTable}）。
 */
@DataBean
public class ProfilingColumnStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String columnName;
    private Long rowCount;
    private Long nullCount;
    private Double nullRatio;
    private Object minValue;
    private Object maxValue;
    private Map<String, Object> extra = new LinkedHashMap<>();

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Long getNullCount() {
        return nullCount;
    }

    public void setNullCount(Long nullCount) {
        this.nullCount = nullCount;
    }

    public Double getNullRatio() {
        return nullRatio;
    }

    public void setNullRatio(Double nullRatio) {
        this.nullRatio = nullRatio;
    }

    public Object getMinValue() {
        return minValue;
    }

    public void setMinValue(Object minValue) {
        this.minValue = minValue;
    }

    public Object getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Object maxValue) {
        this.maxValue = maxValue;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }
}
