package io.nop.datav.service.entity;

import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.DashboardDataResult;
import io.nop.datav.biz.DashboardPanelDataItem;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端 roundtrip 验证（plan 2026-08-15-1134-1 Phase 4，等价判据见 runtime-design.md §9.11）。
 *
 * <p>贯穿完整后端链路（rule #22 端到端 + rule #23 接线验证）：创建看板（含数据集面板 + 无数据集面板）
 * → 导出 → 模拟编辑器编辑（移动几何/新增/删除/改标题/改 props）→ 保存 → 再导出，断言 roundtrip
 * 语义等价；保存后面板行被 {@code getDashboardData} 实际消费（真实 SQL 管线）；保存后看板可发布
 * （既有发布链路回归）。</p>
 */
public class TestNopDatavDashboardLayoutRoundtripE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    /**
     * 主线 E2E：导出 → 编辑 → 保存 → 再导出，按 §9.11 等价判据断言；含 no-op roundtrip 幂等。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testFullRoundtripSemanticEquivalence() {
        IServiceContext context = newContext("roundtrip-user");

        // ===== 准备：存量看板（chart 绑定 + text 无绑定；无 layoutConfig → 导出走合成几何）=====
        NopDatavDashboard dashboard = saveDashboard("dash-rt", "roundtrip");
        savePanel("p-rt-chart", dashboard.getDashboardId(), "chart-system-name", "Sales", 0,
                TYPE_CHART, "ref-rt", null,
                "{\"fieldMapping\":{\"x\":\"region\"},\"dataBinding\":{\"paramOverrides\":{\"region\":\"north\"}},"
                        + "\"refresh\":{\"enabled\":true,\"intervalSeconds\":30}}");
        savePanel("p-rt-text", dashboard.getDashboardId(), "Note", null, 1,
                TYPE_TEXT, null, null, "{\"content\":\"hello\"}");

        // ===== 1. 导出 L1（存量合成几何：chart y=0、text y=4）=====
        Map<String, Object> l1 = dashboardBiz.exportDashboardLayout(dashboard.getDashboardId(), context);
        List<Map<String, Object>> l1Panels = (List<Map<String, Object>>) l1.get("panels");
        assertEquals(2, l1Panels.size());
        assertEquals("p-rt-chart", l1Panels.get(0).get("id"));
        assertEquals("Sales", l1Panels.get(0).get("title"), "title from displayName");
        assertEquals("datasetRef:ref-rt", l1Panels.get(0).get("source"));
        assertEquals(0, l1Panels.get(0).get("y"), "synthesized geometry stacks by sortOrder");
        assertEquals(4, l1Panels.get(1).get("y"));

        // ===== 2. 模拟编辑器编辑 =====
        // chart：移动几何 + 改标题 + props 全量替换（fieldMapping 换新/styleOptions 新增/refresh 删除）
        Map<String, Object> editedChart = panelJson("p-rt-chart", "chart", "Sales North", 3, 2, 9, 5,
                Map.of("fieldMapping", Map.of("x", "city"), "styleOptions", Map.of("theme", "dark")));
        editedChart.put("source", "datasetRef:ref-rt"); // 编辑器回显（保存忽略）
        // 新增 stat-tile（编辑器客户端 id）
        Map<String, Object> addedKpi = panelJson("client-new-kpi", "stat-tile", "KPI", 0, 10, 3, 3, null);
        // text 面板删除（载荷省略）

        Map<String, Object> saveResponse = dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layoutOf(editedChart, addedKpi), context);

        // ===== 3. 保存响应 ≡ 再导出 L2 =====
        Map<String, Object> l2 = dashboardBiz.exportDashboardLayout(dashboard.getDashboardId(), context);
        assertEquals(l2, saveResponse, "save response equals fresh re-export");

        // ===== 4. 按 §9.11 等价判据断言 =====
        // 网格参数：载荷未含 → 保留缺省
        assertEquals(l1.get("cols"), l2.get("cols"));
        assertEquals(l1.get("rowHeight"), l2.get("rowHeight"));
        assertEquals(l1.get("gap"), l2.get("gap"));

        List<Map<String, Object>> l2Panels = (List<Map<String, Object>>) l2.get("panels");
        assertEquals(2, l2Panels.size(), "1 survived + 1 added, deleted panel gone");
        assertEquals(List.of("p-rt-chart", l2Panels.get(1).get("id")),
                List.of(l2Panels.get(0).get("id"), l2Panels.get(1).get("id")), "order = payload order");

        // 存活面板：id 稳定 + 编辑语义生效
        Map<String, Object> chartAfter = l2Panels.get(0);
        assertEquals("p-rt-chart", chartAfter.get("id"), "surviving panel id stable");
        assertEquals("chart", chartAfter.get("type"));
        assertEquals("Sales North", chartAfter.get("title"), "edited title");
        assertEquals(3, chartAfter.get("x"));
        assertEquals(2, chartAfter.get("y"));
        assertEquals(9, chartAfter.get("w"));
        assertEquals(5, chartAfter.get("h"));
        assertEquals("datasetRef:ref-rt", chartAfter.get("source"), "binding preserved (predicate #5)");
        Map<String, Object> chartProps = (Map<String, Object>) chartAfter.get("props");
        assertEquals(Map.of("x", "city"), chartProps.get("fieldMapping"), "edited props region");
        assertEquals("dark", ((Map<?, ?>) chartProps.get("styleOptions")).get("theme"));
        assertFalse(chartProps.containsKey("refresh"), "removed region dropped (full replace)");
        assertFalse(chartProps.containsKey("dataBinding"), "protected region never in props");

        // 新增面板：服务端 id + 编辑器载荷语义
        Map<String, Object> kpiAfter = l2Panels.get(1);
        assertEquals(32, ((String) kpiAfter.get("id")).length(), "new panel gets server id");
        assertEquals("stat-tile", kpiAfter.get("type"));
        assertEquals("KPI", kpiAfter.get("title"));
        assertEquals(10, kpiAfter.get("y"));

        // 归一化行：panelName 稳定 / displayName 回写 / 绑定与 dataBinding 保留
        NopDatavPanel chartRow = panelById("p-rt-chart");
        assertEquals("chart-system-name", chartRow.getPanelName(), "panelName stable across save");
        assertEquals("Sales North", chartRow.getDisplayName());
        assertEquals("ref-rt", chartRow.getDatasetRefId());
        Map<String, Object> config = JsonTool.parseMap(chartRow.getPanelConfig());
        assertNotNull(config.get("dataBinding"), "protected region preserved in panelConfig");
        assertFalse(config.containsKey("refresh"), "refresh dropped by props full-replace");
        assertTrue(config.containsKey("styleOptions"));

        // ===== 5. no-op roundtrip：保存 L2 原样 → 再导出 L3 ≡ L2（幂等）=====
        Map<String, Object> l3Save = dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layoutOf(l2Panels.toArray()), context);
        Map<String, Object> l3 = dashboardBiz.exportDashboardLayout(dashboard.getDashboardId(), context);
        assertEquals(l2, l3, "no-op roundtrip must be equivalent (L3 == L2)");
        assertEquals(l3, l3Save, "second save response also equals re-export");
    }

    /**
     * 接线验证（rule #23）：保存后的面板行被 getDashboardData 实际消费——绑定面板经真实 SQL
     * 管线返回数据行（非仅面板行存在）；新增无绑定面板以 hasDataset=false 条目存在。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testGetDashboardDataConsumesSavedPanels() {
        IServiceContext context = newContext("wiring-user");

        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(newReportDataset("ds-rt-wiring", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}"));

        NopDatavDashboard dashboard = saveDashboard("dash-wiring", "wiring");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);
        NopDatavDatasetRef ref = newDatasetRef("ref-wiring", dashboard.getDashboardId(),
                "ds-rt-wiring", "Wiring DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        savePanel("p-wiring-chart", dashboard.getDashboardId(), "chart", "Chart", 0,
                TYPE_CHART, "ref-wiring", null, "{\"fieldMapping\":{\"x\":\"region\"}}");
        savePanel("p-wiring-text", dashboard.getDashboardId(), "note", "Note", 1,
                TYPE_TEXT, null, null, null);

        // 经布局保存编辑：保留绑定 chart（移动）、删除 text、新增未绑定 text
        Map<String, Object> saved = dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layoutOf(
                        panelJson("p-wiring-chart", "chart", "Chart", 0, 0, 8, 6,
                                Map.of("fieldMapping", Map.of("x", "region"))),
                        panelJson("client-note-2", "text", "New Note", 8, 0, 4, 6, null)),
                context);
        List<Map<String, Object>> savedPanels = (List<Map<String, Object>>) saved.get("panels");
        String newNoteId = (String) savedPanels.get(1).get("id");

        // 保存后的面板集被批量查询实际消费
        DashboardDataResult result = dashboardBiz.getDashboardData(
                dashboard.getDashboardId(), Map.of("region", "north"), null, context);
        assertEquals(2, result.getPanels().size(), "saved panel set drives batch query");

        DashboardPanelDataItem chartItem = findItem(result, "p-wiring-chart");
        assertTrue(chartItem.isSuccess() && chartItem.isHasDataset(),
                "bound panel still queries dataset after layout save");
        assertEquals(2, chartItem.getRows().size(), "real SQL rows through preserved binding");
        assertTrue(chartItem.getRows().get(0).containsValue("north"),
                "region filter value present in query result rows");

        DashboardPanelDataItem noteItem = findItem(result, newNoteId);
        assertTrue(noteItem.isSuccess() && !noteItem.isHasDataset(),
                "new unbound panel returns hasDataset=false entry");
    }

    /**
     * 发布路径回归：保存后看板可 publishDashboard，快照内容含保存后布局（既有发布链路不被破坏）。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testPublishAfterLayoutSave() {
        IServiceContext context = newContext("publish-user");

        NopDatavDashboard dashboard = saveDashboard("dash-rt-publish", "rt-publish");
        savePanel("p-rt-pub-chart", dashboard.getDashboardId(), "chart", "Chart", 0,
                TYPE_CHART, "ref-rt-pub", null, "{\"fieldMapping\":{\"x\":\"region\"}}");

        dashboardBiz.saveDashboardLayout(dashboard.getDashboardId(),
                layoutOf(panelJson("p-rt-pub-chart", "chart", "Chart Moved", 1, 1, 10, 7,
                        Map.of("fieldMapping", Map.of("x", "region")))),
                context);

        NopDatavDashboardSnapshot snapshot = dashboardBiz.publishDashboard(dashboard.getDashboardId(), context);
        assertNotNull(snapshot);
        assertEquals(1L, snapshot.getSnapshotVersion());

        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        Map<String, Object> layoutConfig = (Map<String, Object>) content.get("layoutConfig");
        assertNotNull(layoutConfig, "snapshot carries saved layoutConfig");
        Map<String, Object> storedPanels = (Map<String, Object>) layoutConfig.get("panels");
        Map<String, Object> chartGeom = (Map<String, Object>) storedPanels.get("p-rt-pub-chart");
        assertEquals(1, chartGeom.get("x"), "saved geometry captured by publish snapshot");
        assertEquals(7, chartGeom.get("h"));

        List<Map<String, Object>> panels = (List<Map<String, Object>>) content.get("panels");
        assertEquals(1, panels.size());
        assertEquals("Chart Moved", panels.get(0).get("displayName"), "saved displayName in snapshot");
    }

    // ==================== Helpers ====================

    private static Map<String, Object> layoutOf(Object... panels) {
        Map<String, Object> layout = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        for (Object panel : panels) {
            list.add(panel);
        }
        layout.put("panels", list);
        return layout;
    }

    private static Map<String, Object> panelJson(String id, String type, String title,
                                                 Object x, Object y, Object w, Object h,
                                                 Map<String, Object> props) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("type", type);
        m.put("title", title);
        m.put("x", x);
        m.put("y", y);
        m.put("w", w);
        m.put("h", h);
        if (props != null) {
            m.put("props", props);
        }
        return m;
    }

    private static DashboardPanelDataItem findItem(DashboardDataResult result, String panelId) {
        for (DashboardPanelDataItem item : result.getPanels()) {
            if (panelId.equals(item.getPanelId())) {
                return item;
            }
        }
        throw new AssertionError("panel item not found: " + panelId);
    }

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new io.nop.api.core.context.TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavPanel panelById(String id) {
        return daoProvider.daoFor(NopDatavPanel.class).getEntityById(id);
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

    private NopDatavPanel savePanel(String id, String dashboardId, String panelName, String displayName,
                                    int sortOrder, Integer panelType, String datasetRefId, String tabId,
                                    String panelConfig) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelName);
        p.setDisplayName(displayName);
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

    private NopDatavDatasetRef newDatasetRef(String id, String dashboardId, String refDsId, String refDsName) {
        long now = System.currentTimeMillis();
        NopDatavDatasetRef r = new NopDatavDatasetRef();
        r.setDatasetRefId(id);
        r.setDashboardId(dashboardId);
        r.setRefDatasetId(refDsId);
        r.setRefDatasetName(refDsName);
        r.setVersion(0L);
        r.setCreatedBy("test");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("test");
        r.setUpdateTime(new Timestamp(now));
        return r;
    }

    private NopReportDataset newReportDataset(String sid, String dsType, String dsText) {
        long now = System.currentTimeMillis();
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(sid);
        ds.setDsName(sid);
        ds.setIsSingleRow(false);
        ds.setDsType(dsType);
        ds.setDsText(dsText);
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(now));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(now));
        return ds;
    }

    private void createSalesTable() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }
}
