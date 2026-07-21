/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 血缘抽取结果 DTO（来源：{@code NopMetaLineageEdgeBizModel.extractLineageFromSql} /
 * {@code extractColumnLineageFromSql} / {@code extractMeasureLineage}）。
 */
@DataBean
public class LineageExtractResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metaTableId;
    private int edgeCount;
    private List<String> sourceTables = new ArrayList<>();
    private List<String> unresolved = new ArrayList<>();
    private List<Map<String, Object>> errors = new ArrayList<>();

    public String getMetaTableId() {
        return metaTableId;
    }

    public void setMetaTableId(String metaTableId) {
        this.metaTableId = metaTableId;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }

    public List<String> getSourceTables() {
        return sourceTables;
    }

    public void setSourceTables(List<String> sourceTables) {
        this.sourceTables = sourceTables;
    }

    public List<String> getUnresolved() {
        return unresolved;
    }

    public void setUnresolved(List<String> unresolved) {
        this.unresolved = unresolved;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public void setErrors(List<Map<String, Object>> errors) {
        this.errors = errors;
    }
}
