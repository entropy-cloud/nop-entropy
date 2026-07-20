package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 血缘记录结果 DTO（来源：{@code NopMetaLineageEdgeBizModel.recordLineage}）。
 */
@DataBean
public class LineageRecordResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int edgeCount;

    public int getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }
}
