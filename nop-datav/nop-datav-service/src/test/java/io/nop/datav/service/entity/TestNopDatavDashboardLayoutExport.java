package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavPanel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CONTAINER;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_IFRAME;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_MAP;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_METRIC;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_PIVOT_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出 API 测试（plan 2026-08-15-1134-1 Phase 2，契约见 runtime-design.md §九）。
 *
 * <p>覆盖：有/无数据集面板（source 描述）、全类型词表映射、未知类型显式报错、多 tab 平铺、
 * 存量无几何默认布局合成、部分几何追加合成、存储几何与网格参数、props 保护区剔除、
 * title 列派生、layoutConfig 解析失败显式报错。</p>
 */
public class TestNopDatavDashboardLayoutExport extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    // ==================== 有/无数据集面板 + title 派生 + props ====================

    @Test
    public void testExportDatasetAndTextPanels() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-1", "export-1");
        NopDatavPanel chart = savePanel("panel-export-chart", dashboard.getDashboardId(), "Chart Row", 0,
                TYPE_CHART, "ref-export-1", null, "{\"fieldMapping\":{\"x\":\"region\"}}");
        chart.setDisplayName("Sales Chart");
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(chart);
        savePanel("panel-export-text", dashboard.getDashboardId(), "Text Row", 1,
                TYPE_TEXT, null, null, "{\"content\":\"hello\"}");

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));

        assertEquals("dashboard", layout.get("type"));
        assertEquals(12, layout.get("cols"), "default cols");
        assertEquals(40, layout.get("rowHeight"), "default rowHeight");
        assertEquals(8, layout.get("gap"), "default gap");
        assertFalse(layout.containsKey("height"), "height omitted when absent");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertEquals(2, panels.size());

        Map<String, Object> chartJson = panels.get(0);
        assertEquals("panel-export-chart", chartJson.get("id"));
        assertEquals("chart", chartJson.get("type"));
        assertEquals("Sales Chart", chartJson.get("title"), "title takes displayName when non-empty");
        assertEquals("datasetRef:ref-export-1", chartJson.get("source"), "binding descriptor");
        @SuppressWarnings("unchecked")
        Map<String, Object> chartProps = (Map<String, Object>) chartJson.get("props");
        assertNotNull(chartProps);
        assertTrue(chartProps.containsKey("fieldMapping"));

        Map<String, Object> textJson = panels.get(1);
        assertEquals("text", textJson.get("type"));
        assertEquals("Text Row", textJson.get("title"), "title falls back to panelName when displayName empty");
        assertFalse(textJson.containsKey("source"), "unbound panel omits source");
        @SuppressWarnings("unchecked")
        Map<String, Object> textProps = (Map<String, Object>) textJson.get("props");
        assertEquals("hello", textProps.get("content"));
    }

    @Test
    public void testExportTitleFallsBackToPanelName() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-title", "export-title");
        NopDatavPanel panel = savePanel("panel-title-only", dashboard.getDashboardId(), "SystemName", 0,
                TYPE_TEXT, null, null, null);
        panel.setDisplayName(null);
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(panel);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertEquals("SystemName", panels.get(0).get("title"));
    }

    // ==================== 类型词表映射（全部 8 类）与未知类型 ====================

    @Test
    public void testExportAllTypeMappings() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-types", "export-types");
        savePanel("t-chart", dashboard.getDashboardId(), "c", 0, TYPE_CHART, null, null, null);
        savePanel("t-table", dashboard.getDashboardId(), "t", 1, TYPE_TABLE, null, null, null);
        savePanel("t-stat", dashboard.getDashboardId(), "s", 2, TYPE_METRIC, null, null, null);
        savePanel("t-text", dashboard.getDashboardId(), "x", 3, TYPE_TEXT, null, null, null);
        savePanel("t-container", dashboard.getDashboardId(), "k", 4, TYPE_CONTAINER, null, null, null);
        savePanel("t-pivot", dashboard.getDashboardId(), "p", 5, TYPE_PIVOT_TABLE, null, null, null);
        savePanel("t-map", dashboard.getDashboardId(), "m", 6, TYPE_MAP, null, null, null);
        savePanel("t-iframe", dashboard.getDashboardId(), "i", 7, TYPE_IFRAME, null, null, null);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        String[] expected = {"chart", "table", "stat-tile", "text", "container", "pivot-table", "map", "iframe"};
        assertEquals(expected.length, panels.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], panels.get(i).get("type"), "type mapping at index " + i);
        }
    }

    @Test
    public void testExportUnknownPanelTypeExplicitError() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-unknown", "export-unknown");
        savePanel("panel-bad-type", dashboard.getDashboardId(), "bad", 0,
                99, null, null, null);

        NopException e = assertThrows(NopException.class, () -> dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user")));
        assertEquals("nop.err.datav.unknown-component-type", e.getErrorCode());
    }

    // ==================== 多 tab 平铺（§9.8） ====================

    @Test
    public void testExportMultiTabFlattened() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-tabs", "export-tabs");
        saveTab("tab-1", dashboard.getDashboardId(), "Tab One");
        saveTab("tab-2", dashboard.getDashboardId(), "Tab Two");
        savePanel("panel-tab-a", dashboard.getDashboardId(), "a", 0, TYPE_CHART, null, "tab-1", null);
        savePanel("panel-tab-b", dashboard.getDashboardId(), "b", 1, TYPE_TABLE, null, "tab-2", null);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertEquals(2, panels.size(), "all panels exported regardless of tabId (flatten)");
        assertEquals("panel-tab-a", panels.get(0).get("id"));
        assertEquals("panel-tab-b", panels.get(1).get("id"));
    }

    // ==================== 存量无几何：默认布局合成（§9.5） ====================

    @Test
    public void testExportLegacyNoGeometrySynthesis() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-legacy", "export-legacy");
        savePanel("panel-legacy-1", dashboard.getDashboardId(), "one", 0, TYPE_CHART, null, null, null);
        savePanel("panel-legacy-2", dashboard.getDashboardId(), "two", 1, TYPE_TABLE, null, null, null);
        savePanel("panel-legacy-3", dashboard.getDashboardId(), "three", 2, TYPE_TEXT, null, null, null);
        // layoutConfig 为自由结构（无 panels 几何图）——存量形态
        dashboard.setLayoutConfig("{\"grid\":\"2x2\"}");
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertEquals(3, panels.size());
        assertGeometry(panels.get(0), 0, 0, 12, 4, "first synthesized panel stacks at y=0, full width");
        assertGeometry(panels.get(1), 0, 4, 12, 4, "second synthesized panel stacks at y=4");
        assertGeometry(panels.get(2), 0, 8, 12, 4, "third synthesized panel stacks at y=8");
    }

    @Test
    public void testExportPartialGeometrySynthesisAppended() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-partial", "export-partial");
        savePanel("panel-no-geom", dashboard.getDashboardId(), "new", 0, TYPE_CHART, null, null, null);
        savePanel("panel-geom", dashboard.getDashboardId(), "old", 1, TYPE_TABLE, null, null, null);
        // 仅 panel-geom 有存储几何（y=2,h=3 → 底缘 5）：无几何面板合成追加在 max(y+h)=5 之后
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of(
                "panels", Map.of("panel-geom", Map.of("x", 6, "y", 2, "w", 4, "h", 3)))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertGeometry(panels.get(0), 0, 5, 12, 4,
                "synthesized panel appended after max bottom edge of stored geometry");
        assertGeometry(panels.get(1), 6, 2, 4, 3, "stored geometry read verbatim");
    }

    // ==================== 存储几何与网格参数（§9.4） ====================

    @Test
    public void testExportStoredGeometryAndGridParams() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-grid", "export-grid");
        savePanel("panel-g1", dashboard.getDashboardId(), "g1", 0, TYPE_CHART, null, null, null);
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of(
                "cols", 24, "rowHeight", 30, "gap", 12, "height", 1234,
                "panels", Map.of("panel-g1", Map.of("x", 2, "y", 6, "w", 8, "h", 5)))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        assertEquals(24, layout.get("cols"));
        assertEquals(30, layout.get("rowHeight"));
        assertEquals(12, layout.get("gap"));
        assertEquals(1234, layout.get("height"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertGeometry(panels.get(0), 2, 6, 8, 5, "stored geometry");
    }

    // ==================== props 保护区剔除（§9.6/9.9） ====================

    @Test
    public void testExportPropsExcludesDataBinding() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-props", "export-props");
        savePanel("panel-props", dashboard.getDashboardId(), "p", 0, TYPE_CHART, "ref-props", null,
                "{\"fieldMapping\":{\"x\":\"region\"},\"dataBinding\":{\"paramOverrides\":{}},\"refresh\":{\"enabled\":true}}");

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) panels.get(0).get("props");
        assertNotNull(props);
        assertTrue(props.containsKey("fieldMapping"), "non-protected region exported");
        assertTrue(props.containsKey("refresh"), "non-protected region exported");
        assertFalse(props.containsKey("dataBinding"), "protected dataBinding region never exported in props");
    }

    @Test
    public void testExportEmptyPanelConfigOmitsProps() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-noprops", "export-noprops");
        savePanel("panel-noprops", dashboard.getDashboardId(), "n", 0, TYPE_TEXT, null, null, null);

        Map<String, Object> layout = dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) layout.get("panels");
        assertFalse(panels.get(0).containsKey("props"), "empty panelConfig omits props");
    }

    // ==================== 存量 layoutConfig 解析失败（§9.5 显式报错） ====================

    @Test
    public void testExportMalformedLayoutConfigExplicitError() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-badjson", "export-badjson");
        savePanel("panel-badjson", dashboard.getDashboardId(), "b", 0, TYPE_TEXT, null, null, null);
        dashboard.setLayoutConfig("{not valid json");
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopException e = assertThrows(NopException.class, () -> dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user")));
        assertEquals("nop.err.datav.invalid-layout", e.getErrorCode());
    }

    @Test
    public void testExportStoredGeometryMalformedExplicitError() {
        NopDatavDashboard dashboard = saveDashboard("dash-export-badgeom", "export-badgeom");
        savePanel("panel-badgeom", dashboard.getDashboardId(), "b", 0, TYPE_TEXT, null, null, null);
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of(
                "panels", Map.of("panel-badgeom", Map.of("x", 0, "y", 0, "w", 4)))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopException e = assertThrows(NopException.class, () -> dashboardBiz.exportDashboardLayout(
                dashboard.getDashboardId(), newContext("export-user")));
        assertEquals("nop.err.datav.invalid-layout", e.getErrorCode(), "incomplete stored geometry is explicit error");
    }

    @Test
    public void testExportDashboardNotFound() {
        NopException e = assertThrows(NopException.class, () -> dashboardBiz.exportDashboardLayout(
                "no-such-dashboard", newContext("export-user")));
        assertNotNull(e);
    }

    // ==================== Helpers ====================

    private static void assertGeometry(Map<String, Object> panelJson, int x, int y, int w, int h, String message) {
        assertEquals(x, panelJson.get("x"), message + " (x)");
        assertEquals(y, panelJson.get("y"), message + " (y)");
        assertEquals(w, panelJson.get("w"), message + " (w)");
        assertEquals(h, panelJson.get("h"), message + " (h)");
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new io.nop.api.core.context.TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavDashboard saveDashboard(String id, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private NopDatavPanel savePanel(String id, String dashboardId, String name, int sortOrder,
                                    Integer panelType, String datasetRefId, String tabId, String panelConfig) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(name);
        p.setDisplayName(name);
        p.setPanelType(panelType);
        p.setDatasetRefId(datasetRefId);
        p.setTabId(tabId);
        p.setSortOrder(sortOrder);
        p.setPanelConfig(panelConfig);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }

    private void saveTab(String id, String dashboardId, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboardTab t = new NopDatavDashboardTab();
        t.setTabId(id);
        t.setDashboardId(dashboardId);
        t.setTabName(name);
        t.setSortOrder(0);
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboardTab.class).saveEntityDirectly(t);
    }
}
