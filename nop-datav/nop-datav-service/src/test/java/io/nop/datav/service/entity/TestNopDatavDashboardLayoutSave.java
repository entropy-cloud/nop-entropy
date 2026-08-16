package io.nop.datav.service.entity;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavPanel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static io.nop.datav.service.report.NopDatavReportTaskStatus.DISABLED;
import static io.nop.datav.service.report.NopDatavReportTaskStatus.ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保存回写 API 测试（plan 2026-08-15-1134-1 Phase 3，契约见 runtime-design.md §九）。
 *
 * <p>覆盖：纯新增（服务端 id + panelName 派生）/纯删除（含 AlertRule 级联置 DISABLED）/混合
 * reconcile/重复 id/未知类型（含 flux html）/超上限/绑定与 tabId 保留（source 回显不消费）/
 * props 全量替换语义与 dataBinding 保护区/跨看板 id/校验失败零落库/网格参数更新与保留/
 * 未知遗留键保留/容量超限（layoutConfig 与 panelConfig）/载荷结构错误分支。</p>
 */
public class TestNopDatavDashboardLayoutSave extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    // ==================== 纯新增 ====================

    @Test
    public void testSavePureCreate() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-create", "save-create");

        Map<String, Object> saved = dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layout(payloadPanel("client-id-1", "chart", "Revenue", 0, 0, 6, 4,
                        Map.of("fieldMapping", Map.of("x", "region"))),
                        payloadPanel("client-id-2", "text", null, 6, 0, 6, 4, null)),
                newContext("layout-editor"));

        // 响应为再导出布局：新建面板携带服务端生成的 32 字符 id（载荷 id 丢弃）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) saved.get("panels");
        assertEquals(2, panels.size());
        assertEquals("dashboard", saved.get("type"));
        String chartId = (String) panels.get(0).get("id");
        String textId = (String) panels.get(1).get("id");
        assertNotEquals("client-id-1", chartId, "payload client id discarded for new panel");
        assertEquals(32, chartId.length(), "server-generated panelId (UUID-32)");
        assertEquals("chart", panels.get(0).get("type"));
        assertEquals("Revenue", panels.get(0).get("title"));

        // 归一化行断言
        NopDatavPanel chartRow = panelById(chartId);
        assertEquals("Revenue", chartRow.getPanelName(), "panelName derived from title");
        assertEquals("Revenue", chartRow.getDisplayName());
        assertEquals(TYPE_CHART, chartRow.getPanelType());
        assertEquals(0, chartRow.getSortOrder());
        assertNull(chartRow.getDatasetRefId(), "new panel is always unbound");
        assertNull(chartRow.getTabId());
        assertTrue(chartRow.getPanelConfig().contains("fieldMapping"));

        NopDatavPanel textRow = panelById(textId);
        assertEquals("panel-2", textRow.getPanelName(), "empty title derives panel-{index+1}");
        assertNull(textRow.getDisplayName());
        assertEquals(TYPE_TEXT, textRow.getPanelType());
        assertEquals(1, textRow.getSortOrder());
        assertNull(textRow.getPanelConfig());

        // layoutConfig 固化为钉死结构（含新建面板几何）
        Map<String, Object> layoutConfig = JsonTool.parseMap(dashboardById(
                dashboard.getDashboardId()).getLayoutConfig());
        assertEquals(12, layoutConfig.get("cols"));
        assertEquals(40, layoutConfig.get("rowHeight"));
        assertEquals(8, layoutConfig.get("gap"));
        @SuppressWarnings("unchecked")
        Map<String, Object> storedPanels = (Map<String, Object>) layoutConfig.get("panels");
        assertEquals(2, storedPanels.size());
        assertTrue(storedPanels.containsKey(chartId));
        assertTrue(storedPanels.containsKey(textId));
    }

    // ==================== 纯删除 + AlertRule 级联 ====================

    @Test
    public void testSavePureDeleteDisablesAlertRules() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-del", "save-del");
        savePanel("p-del-1", dashboard.getDashboardId(), "keep", 0, TYPE_CHART, null, null, null);
        savePanel("p-del-2", dashboard.getDashboardId(), "drop", 1, TYPE_TABLE, null, null, null);
        seedAlertRule("rule-del-layout", "p-del-2", ENABLED);

        Map<String, Object> saved = dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layout(payloadPanel("p-del-1", "chart", "keep", 0, 0, 12, 4, null)),
                newContext("layout-editor"));

        assertEquals(1, allPanels(dashboard.getDashboardId()).size(), "dropped panel physically deleted");
        assertNotNull(panelById("p-del-1"));

        NopDatavAlertRule rule = daoProvider.daoFor(NopDatavAlertRule.class).getEntityById("rule-del-layout");
        assertEquals(DISABLED, rule.getStatus(), "alert rule of removed panel disabled (mirror delete cascade)");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) saved.get("panels");
        assertEquals(1, panels.size());
    }

    @Test
    public void testSaveEmptyPanelsClearsAll() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-clear", "save-clear");
        savePanel("p-clear-1", dashboard.getDashboardId(), "a", 0, TYPE_CHART, null, null, null);
        savePanel("p-clear-2", dashboard.getDashboardId(), "b", 1, TYPE_TEXT, null, null, null);

        dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layout(), newContext("layout-editor"));

        assertEquals(0, allPanels(dashboard.getDashboardId()).size(), "empty panels array clears all panels");
        Map<String, Object> layoutConfig = JsonTool.parseMap(
                dashboardById(dashboard.getDashboardId()).getLayoutConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> storedPanels = (Map<String, Object>) layoutConfig.get("panels");
        assertTrue(storedPanels.isEmpty());
    }

    // ==================== 混合 reconcile（改+留+增+删） ====================

    @Test
    public void testSaveMixedReconcile() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-mix", "save-mix");
        savePanel("p-mix-1", dashboard.getDashboardId(), "one", 0, TYPE_CHART, "ref-mix", null,
                "{\"refresh\":{\"enabled\":true}}");
        savePanel("p-mix-2", dashboard.getDashboardId(), "two", 1, TYPE_TABLE, null, null, null);
        savePanel("p-mix-3", dashboard.getDashboardId(), "three", 2, TYPE_TEXT, null, "tab-mix", null);

        // 编辑器操作：p3 移动置顶、p1 改标题+改类型 chart→table、新增 stat-tile、删除 p2
        Map<String, Object> saved = dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layout(payloadPanel("p-mix-3", "text", "three", 0, 0, 12, 3, null),
                        payloadPanel("p-mix-1", "table", "One Renamed", 0, 3, 8, 6,
                                Map.of("content", Map.of("v", 1))),
                        payloadPanel("client-new", "stat-tile", "KPI", 8, 3, 4, 6, null)),
                newContext("layout-editor"));

        List<NopDatavPanel> rows = allPanels(dashboard.getDashboardId());
        assertEquals(3, rows.size(), "1 deleted, 1 added");

        NopDatavPanel p3 = panelById("p-mix-3");
        assertEquals(0, p3.getSortOrder(), "payload array index 0");
        assertEquals("tab-mix", p3.getTabId(), "existing tabId preserved");

        NopDatavPanel p1 = panelById("p-mix-1");
        assertEquals(1, p1.getSortOrder());
        assertEquals("One Renamed", p1.getDisplayName(), "title written back to displayName");
        assertEquals("one", p1.getPanelName(), "panelName stable for existing panel");
        assertEquals(TYPE_TABLE, p1.getPanelType(), "type change applied");
        assertEquals("ref-mix", p1.getDatasetRefId(), "binding preserved on update");
        assertTrue(p1.getPanelConfig().contains("content"), "props written back");
        assertFalse(p1.getPanelConfig().contains("refresh"),
                "props present = authoritative full replace (removed region dropped)");

        NopDatavPanel added = rows.stream()
                .filter(r -> !"p-mix-1".equals(r.getPanelId()) && !"p-mix-3".equals(r.getPanelId()))
                .findFirst().orElseThrow();
        assertEquals(TYPE_METRIC_VALUE, added.getPanelType(), "stat-tile -> METRIC=20");
        assertEquals(2, added.getSortOrder());

        // 响应顺序 = 载荷顺序（sortOrder 序）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> panels = (List<Map<String, Object>>) saved.get("panels");
        assertEquals(List.of("p-mix-3", "p-mix-1", added.getPanelId()),
                List.of(panels.get(0).get("id"), panels.get(1).get("id"), panels.get(2).get("id")));
    }

    private static final int TYPE_METRIC_VALUE = 20;

    // ==================== 失败路径（全部显式报错，零落库） ====================

    @Test
    public void testSaveDuplicateIdError() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-dup", "save-dup");
        savePanel("p-dup-1", dashboard.getDashboardId(), "a", 0, TYPE_CHART, null, null, null);

        NopException e = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                dashboard.getDashboardId(),
                layout(payloadPanel("same-id", "chart", "a", 0, 0, 6, 4, null),
                        payloadPanel("same-id", "text", "b", 6, 0, 6, 4, null)),
                newContext("layout-editor")));
        assertEquals("nop.err.datav.layout-duplicate-panel-id", e.getErrorCode());
        assertEquals(1, allPanels(dashboard.getDashboardId()).size(), "zero writes on validation failure");
    }

    @Test
    public void testSaveUnknownTypeErrorFluxHtmlAndDecorative() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-html", "save-html");

        // flux palette 的 html 类型：nop-datav 无对应项（§9.3 显式拒绝）
        NopException html = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                dashboard.getDashboardId(),
                layout(payloadPanel("c1", "html", "h", 0, 0, 6, 4, null)),
                newContext("layout-editor")));
        assertEquals("nop.err.datav.unknown-component-type", html.getErrorCode());

        // 装饰/媒体类型（screen-only，panelType dict 不含）：同样 fail-fast
        NopException decorative = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                dashboard.getDashboardId(),
                layout(payloadPanel("c2", "video", "v", 0, 0, 6, 4, null)),
                newContext("layout-editor")));
        assertEquals("nop.err.datav.unknown-component-type", decorative.getErrorCode());
    }

    @Test
    public void testSaveOverMaxPanelsRejected() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-max", "save-max");
        Integer origMax = CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS, 2);
        try {
            NopException e = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                    dashboard.getDashboardId(),
                    layout(payloadPanel("a", "chart", "a", 0, 0, 4, 4, null),
                            payloadPanel("b", "chart", "b", 4, 0, 4, 4, null),
                            payloadPanel("c", "chart", "c", 8, 0, 4, 4, null)),
                    newContext("layout-editor")));
            assertEquals("nop.err.datav.dashboard-panel-limit-exceeded", e.getErrorCode());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS, origMax);
        }
    }

    @Test
    public void testSaveForeignPanelIdError() {
        NopDatavDashboard mine = saveDashboard("dash-save-mine", "save-mine");
        NopDatavDashboard other = saveDashboard("dash-save-other", "save-other");
        savePanel("p-foreign", other.getDashboardId(), "belongs-to-other", 0, TYPE_CHART, null, null, null);

        NopException e = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                mine.getDashboardId(),
                layout(payloadPanel("p-foreign", "chart", "steal", 0, 0, 6, 4, null)),
                newContext("layout-editor")));
        assertEquals("nop.err.datav.layout-foreign-panel-id", e.getErrorCode());
        // 原属主不受影响
        assertEquals(1, allPanels(other.getDashboardId()).size());
        assertEquals(other.getDashboardId(), panelById("p-foreign").getDashboardId());
    }

    @Test
    public void testSavePayloadStructureErrors() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-struct", "save-struct");
        savePanel("p-struct", dashboard.getDashboardId(), "a", 0, TYPE_CHART, null, null, "{\"k\":1}");
        String beforeConfig = panelById("p-struct").getPanelConfig();

        // panels 缺失
        assertInvalidLayout(Map.of("cols", 12));
        // 面板非对象
        assertInvalidLayout(layout((Object) "not-an-object"));
        // id 缺失
        assertInvalidLayout(layout(panelMap(null, "chart", "t", 0, 0, 6, 4, null)));
        // type 缺失
        assertInvalidLayout(layout(panelMap("x", null, "t", 0, 0, 6, 4, null)));
        // 小数几何
        assertInvalidLayout(layout(panelMap("x", "chart", "t", 0.5, 0, 6, 4, null)));
        // w=0 非法
        assertInvalidLayout(layout(panelMap("x", "chart", "t", 0, 0, 0, 4, null)));
        // title 超长
        assertInvalidLayout(layout(panelMap("x", "chart", "t".repeat(201), 0, 0, 6, 4, null)));
        // 网格参数非法（cols=0）
        Map<String, Object> badGrid = layout(payloadPanel("p-struct", "chart", "t", 0, 0, 6, 4, null));
        badGrid.put("cols", 0);
        assertInvalidLayout(badGrid);
        // props 非对象
        assertInvalidLayout(layout(panelMap("p-struct", "chart", "t", 0, 0, 6, 4, "not-a-map")));

        // 全部失败后零落库
        assertEquals(1, allPanels(dashboard.getDashboardId()).size());
        assertEquals(beforeConfig, panelById("p-struct").getPanelConfig());
        assertNull(dashboardById(dashboard.getDashboardId()).getLayoutConfig(), "layoutConfig untouched");
    }

    @Test
    public void testSaveLayoutConfigOverflowRejected() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-overflow", "save-overflow");
        Integer origMax = CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS, 100);
        try {
            List<Map<String, Object>> panels = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                panels.add(payloadPanel("c" + i, "chart", "p" + i, 0, i * 4, 12, 4, null));
            }
            Map<String, Object> payload = layout(panels.toArray());
            NopException e = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                    dashboard.getDashboardId(), payload, newContext("layout-editor")));
            assertEquals("nop.err.datav.layout-config-overflow", e.getErrorCode());
            assertEquals(0, allPanels(dashboard.getDashboardId()).size(),
                    "overflow detected before any write (zero rows created)");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_LAYOUT_MAX_PANELS, origMax);
        }
    }

    @Test
    public void testSavePanelConfigOverflowRejected() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-pc-overflow", "save-pc-overflow");
        savePanel("p-pc", dashboard.getDashboardId(), "a", 0, TYPE_TEXT, null, null, null);

        NopException e = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                dashboard.getDashboardId(),
                layout(payloadPanel("p-pc", "text", "t", 0, 0, 12, 4,
                        Map.of("content", "x".repeat(4500)))),
                newContext("layout-editor")));
        assertEquals("nop.err.datav.panel-config-overflow", e.getErrorCode());
        assertNull(panelById("p-pc").getPanelConfig(), "zero writes on overflow");
    }

    // ==================== 绑定/tabId 保留与保护区（§9.6/9.8/9.9） ====================

    @Test
    public void testSavePreservesBindingTabAndDataBindingRegion() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-bind", "save-bind");
        savePanel("p-bind", dashboard.getDashboardId(), "bound", 0, TYPE_CHART, "ref-bind", "tab-bind",
                "{\"dataBinding\":{\"paramOverrides\":{\"region\":\"north\"}},\"fieldMapping\":{\"x\":\"region\"}}");

        // 载荷 source 回显错误值（应被忽略）+ props 不含 dataBinding
        Map<String, Object> panelJson = payloadPanel("p-bind", "chart", "bound", 0, 0, 6, 4,
                Map.of("fieldMapping", Map.of("x", "city")));
        panelJson.put("source", "datasetRef:ref-evil");
        dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(), layout(panelJson), newContext("layout-editor"));

        NopDatavPanel row = panelById("p-bind");
        assertEquals("ref-bind", row.getDatasetRefId(), "binding preserved, payload source never consumed");
        assertEquals("tab-bind", row.getTabId(), "tabId preserved");
        Map<String, Object> config = JsonTool.parseMap(row.getPanelConfig());
        @SuppressWarnings("unchecked")
        Map<String, Object> binding = (Map<String, Object>) config.get("dataBinding");
        assertNotNull(binding, "protected dataBinding region preserved");
        assertEquals("north", ((Map<?, ?>) binding.get("paramOverrides")).get("region"));
        assertEquals("city", ((Map<?, ?>) config.get("fieldMapping")).get("x"), "non-protected region updated");
    }

    @Test
    public void testSavePropsAbsentKeepsPanelConfig() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-noop-props", "save-noop-props");
        savePanel("p-keep", dashboard.getDashboardId(), "a", 0, TYPE_CHART, null, null,
                "{\"styleOptions\":{\"theme\":\"dark\"}}");

        Map<String, Object> panelJson = payloadPanel("p-keep", "chart", "a", 3, 3, 6, 6, null);
        dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(), layout(panelJson), newContext("layout-editor"));

        assertEquals("{\"styleOptions\":{\"theme\":\"dark\"}}", panelById("p-keep").getPanelConfig(),
                "props absent = panelConfig untouched");
    }

    // ==================== 网格参数（§9.7 含则更新/缺省保留）与未知遗留键（§9.4） ====================

    @Test
    public void testSaveGridParamsUpdateRetainAndLegacyKeysPreserved() {
        NopDatavDashboard dashboard = saveDashboard("dash-save-grid", "save-grid");
        savePanel("p-grid", dashboard.getDashboardId(), "a", 0, TYPE_CHART, null, null, null);
        dashboard.setLayoutConfig("{\"grid\":\"2x2\",\"cols\":12,\"height\":800}");
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        // 第一次保存：cols 更新为 24（含则更新）；rowHeight/gap 缺省 → 由缺省值补齐；height 保留 800；未知键 grid 保留
        dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layoutWithGrid(24, null, null, null, payloadPanel("p-grid", "chart", "a", 0, 0, 6, 4, null)),
                newContext("layout-editor"));

        Map<String, Object> config1 = JsonTool.parseMap(dashboardById(dashboard.getDashboardId()).getLayoutConfig());
        assertEquals(24, config1.get("cols"), "grid param present = updated");
        assertEquals(40, config1.get("rowHeight"), "absent grid param filled with default");
        assertEquals(8, config1.get("gap"));
        assertEquals(800, config1.get("height"), "absent height retained");
        assertEquals("2x2", config1.get("grid"), "unknown legacy key preserved");

        // 第二次保存：网格参数全缺省 → 保留第一次的值
        dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layout(payloadPanel("p-grid", "chart", "a", 0, 0, 6, 4, null)),
                newContext("layout-editor"));

        Map<String, Object> config2 = JsonTool.parseMap(dashboardById(dashboard.getDashboardId()).getLayoutConfig());
        assertEquals(24, config2.get("cols"), "absent grid params retained");
        assertEquals(40, config2.get("rowHeight"));
        assertEquals("2x2", config2.get("grid"));
    }

    // ==================== Helpers ====================

    private void assertInvalidLayout(Map<String, Object> payload) {
        NopDatavDashboard dashboard = dashboardById("dash-save-struct");
        NopException e = assertThrows(NopException.class, () -> dashboardBiz.saveDashboardLayout(
                dashboard.getDashboardId(), payload, newContext("layout-editor")));
        assertEquals("nop.err.datav.invalid-layout", e.getErrorCode());
    }

    private static Map<String, Object> layout(Object... panels) {
        return layoutWithGrid(null, null, null, null, panels);
    }

    private static Map<String, Object> layoutWithGrid(Integer cols, Integer rowHeight, Integer gap, Integer height,
                                                      Object... panels) {
        Map<String, Object> layout = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        for (Object panel : panels) {
            list.add(panel);
        }
        layout.put("panels", list);
        if (cols != null) {
            layout.put("cols", cols);
        }
        if (rowHeight != null) {
            layout.put("rowHeight", rowHeight);
        }
        if (gap != null) {
            layout.put("gap", gap);
        }
        if (height != null) {
            layout.put("height", height);
        }
        return layout;
    }

    /** 载荷面板构造（title 为 null 时省略键；props 为 null 时省略键）。 */
    private static Map<String, Object> payloadPanel(String id, String type, String title,
                                                    Object x, Object y, Object w, Object h,
                                                    Map<String, Object> props) {
        Map<String, Object> m = panelMap(id, type, title, x, y, w, h, props);
        return m;
    }

    private static Map<String, Object> panelMap(String id, String type, String title,
                                                Object x, Object y, Object w, Object h, Object props) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (id != null) {
            m.put("id", id);
        }
        if (type != null) {
            m.put("type", type);
        }
        if (title != null) {
            m.put("title", title);
        }
        m.put("x", x);
        m.put("y", y);
        m.put("w", w);
        m.put("h", h);
        if (props != null) {
            m.put("props", props);
        }
        return m;
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new io.nop.api.core.context.TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavDashboard dashboardById(String id) {
        return daoProvider.daoFor(NopDatavDashboard.class).getEntityById(id);
    }

    private NopDatavPanel panelById(String id) {
        return daoProvider.daoFor(NopDatavPanel.class).getEntityById(id);
    }

    private List<NopDatavPanel> allPanels(String dashboardId) {
        io.nop.api.core.beans.query.QueryBean query = new io.nop.api.core.beans.query.QueryBean();
        query.addFilter(io.nop.api.core.beans.FilterBeans.eq("dashboardId", dashboardId));
        return daoProvider.daoFor(NopDatavPanel.class).findAllByQuery(query);
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

    private void seedAlertRule(String ruleId, String panelId, int status) {
        long now = System.currentTimeMillis();
        NopDatavAlertRule r = new NopDatavAlertRule();
        r.setAlertRuleId(ruleId);
        r.setRuleName(ruleId + "-name");
        r.setDisplayName(ruleId + "-display");
        r.setPanelId(panelId);
        r.setValueField("amount");
        r.setAggregation("sum");
        r.setOperator("gt");
        r.setThresholdValue(new BigDecimal("100"));
        r.setRearmSeconds(0);
        r.setCronExpr("0 0 8 * * ?");
        r.setNotifyChannels("[\"email\"]");
        r.setRecipients("[\"a@example.com\"]");
        r.setStatus(status);
        r.setVersion(0L);
        r.setCreatedBy("test");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("test");
        r.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavAlertRule.class).saveEntityDirectly(r);
    }
}
