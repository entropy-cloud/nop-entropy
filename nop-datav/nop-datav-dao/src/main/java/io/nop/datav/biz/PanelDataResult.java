package io.nop.datav.biz;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 面板数据查询结果。{@code getPanelData} / {@code refreshPanel} 的统一返回类型。
 *
 * <p>仅使用 nop-datav 自有类型与平台基础类型（Map/List），不引用 nop-report 实体类型，保持依赖方向正确
 * （INopDatavPanelBiz 接口位于 nop-datav-dao，不应依赖 nop-report-dao）。</p>
 */
public class PanelDataResult {

    private final String panelId;
    private final boolean hasDataset;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final String componentType;

    public PanelDataResult(String panelId, String componentType, boolean hasDataset,
                           List<String> columns, List<Map<String, Object>> rows) {
        this.panelId = panelId;
        this.componentType = componentType;
        this.hasDataset = hasDataset;
        this.columns = columns == null ? Collections.emptyList() : columns;
        this.rows = rows == null ? Collections.emptyList() : rows;
    }

    /**
     * 面板 ID。
     */
    public String getPanelId() {
        return panelId;
    }

    /**
     * 组件类型标识（如 "chart"/"text"）。
     */
    public String getComponentType() {
        return componentType;
    }

    /**
     * 是否有数据集绑定。text/iframe 等无数据集组件返回 false。
     */
    public boolean isHasDataset() {
        return hasDataset;
    }

    /**
     * 查询结果字段名列表（按 SQL ResultSet 列顺序）。无数据集时为空列表。
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * 查询结果行列表（每行为 Map：列名 → 值）。无数据集时为空列表。
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }
}
