
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 血缘抽取结果 DTO（来源：{@code NopMetaLineageEdgeBizModel.extractLineageFromSql} /
 * {@code extractColumnLineageFromSql} / {@code extractMeasureLineage}）。
 *
 * <p>字段语义（P1-3 契约裁定，plan 2026-08-15-1913-2）：
 * <ul>
 *   <li>{@code sourceTables}：**已解析**源表的 metaTable ID 集（去重保序）。表级/列级为目录命中
 *       的源表 ID；指标级为宿主表自身 {@code [metaTableId]}（measure 边为自环，仅当产出 ≥1 条边，
 *       否则空列表）。</li>
 *   <li>{@code unresolved}：未解析引用的描述（与 sourceTables 异质）——表级为未命中目录的完整表名；
 *       列级为 {@code "targetCol <- sourceRef (reason)"} 诊断串；指标级为
 *       {@code "measure <- ident (reason)"} 诊断串。</li>
 * </ul>
 */
@DataBean
public class LineageExtractResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metaTableId;
    private int edgeCount;
    /** 已解析源表 metaTable ID 集（去重保序；指标级 = 宿主表自身，0 条边时空列表）。 */
    private List<String> sourceTables = new ArrayList<>();
    /** 未解析引用描述（表级=完整表名；列级/指标级=诊断串）。与 sourceTables 异质。 */
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
