package io.nop.datav.biz;

import java.util.Collections;
import java.util.List;

/**
 * 批量面板查询结果。{@code getDashboardData} 的返回类型。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §四 批量面板查询。</p>
 *
 * <p>一次调用返回该看板（或指定 panelIds 子集）各面板的数据条目。看板级失败（看板不存在、panelIds
 * 越权引用、面板数超上限）不产出本对象，由 API 整体显式抛 {@code NopException}；面板级失败隔离在
 * {@link DashboardPanelDataItem} 条目内。</p>
 */
public class DashboardDataResult {

    private final String dashboardId;
    private final List<DashboardPanelDataItem> panels;

    public DashboardDataResult(String dashboardId, List<DashboardPanelDataItem> panels) {
        this.dashboardId = dashboardId;
        this.panels = panels == null ? Collections.emptyList() : panels;
    }

    /**
     * 看板 ID。
     */
    public String getDashboardId() {
        return dashboardId;
    }

    /**
     * 面板数据条目列表（按面板 sortOrder 排序；含无数据集面板的 {@code hasDataset=false} 条目）。
     */
    public List<DashboardPanelDataItem> getPanels() {
        return panels;
    }
}
