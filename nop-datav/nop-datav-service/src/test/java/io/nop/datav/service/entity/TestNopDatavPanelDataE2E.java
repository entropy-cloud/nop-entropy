package io.nop.datav.service.entity;

import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端测试（D1-5）。贯穿完整后端链路：
 *
 * <pre>
 * 创建看板
 *   → 创建测试业务数据表 + 插入测试数据（jdbcTemplate.executeUpdate 手动建表）
 *   → 创建 NopReportDataset 行（dsType=sql，dsText 为查询 SQL）
 *   → 创建 DatasetRef（引用 dataset + 配置 paramMapping）
 *   → 创建 Panel（设定组件类型 + panelConfig + 绑定 DatasetRef）
 *   → 调用 getPanelData 断言返回正确数据
 *   → 调用 refreshPanel 断言刷新生效
 * </pre>
 *
 * <p>覆盖至少 2 种组件类型（chart + table）+ 参数化查询场景。</p>
 *
 * <p>本测试由独立子 agent closure audit 作为端到端验证证据（rule #22 + Anti-Hollow Check）。</p>
 */
public class TestNopDatavPanelDataE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavPanelBiz panelBiz;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    /**
     * 主线 E2E：创建看板 → 业务数据表 → NopReportDataset → DatasetRef → Panel（chart + table）
     * → 调用 getPanelData 断言正确数据 → refreshPanel 断言刷新生效
     * → publishDashboard 断言快照记录了已配置面板
     */
    @Test
    public void testEndToEndCreateQueryRefreshAndPublish() {
        IServiceContext context = newContext("e2e-user");

        // 1. 业务数据表 + 数据
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        // 2. NopReportDataset（chart 用 + table 用，共用一个 SQL 数据集）
        NopReportDataset ds = newReportDataset("ds-e2e-sales", "sql",
                "select REGION as region, PRODUCT as product, AMOUNT as amount " +
                        "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        // 3. Dashboard
        NopDatavDashboard dashboard = saveDashboard("dash-e2e", "e2e-dashboard");
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of("grid", "2x2")));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        // 4. DatasetRef（含参数映射）
        NopDatavDatasetRef ref = newDatasetRef("ref-e2e-sales", dashboard.getDashboardId(),
                "ds-e2e-sales", "Sales DS");
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "south")
        )));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        // 5a. Panel: chart
        NopDatavPanel chartPanel = newPanel("panel-e2e-chart", dashboard.getDashboardId(), "Sales Chart");
        chartPanel.setPanelType(TYPE_CHART);
        chartPanel.setDatasetRefId("ref-e2e-sales");
        chartPanel.setPanelConfig(JsonTool.stringify(Map.of(
                "refresh", Map.of("enabled", true, "intervalSeconds", 30),
                "fieldMapping", Map.of("x", "region", "y", "amount")
        )));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(chartPanel);

        // 5b. Panel: table
        NopDatavPanel tablePanel = newPanel("panel-e2e-table", dashboard.getDashboardId(), "Sales Table");
        tablePanel.setPanelType(TYPE_TABLE);
        tablePanel.setDatasetRefId("ref-e2e-sales");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(tablePanel);

        // 6. getPanelData on chart panel with region=north
        PanelDataResult chartResult = panelBiz.getPanelData(
                chartPanel.getPanelId(), Map.of("region", "north"), context);
        assertNotNull(chartResult);
        assertTrue(chartResult.isHasDataset(), "chart panel should have dataset");
        assertEquals("chart", chartResult.getComponentType());
        assertEquals(2, chartResult.getRows().size(), "north has 2 sales rows");
        // First row should be the highest amount (ordered desc)
        Object firstAmount = findCaseInsensitive(chartResult.getRows().get(0), "amount");
        assertEquals(200, ((Number) firstAmount).intValue());

        // 7. getPanelData on table panel with default param (region=south)
        PanelDataResult tableResult = panelBiz.getPanelData(
                tablePanel.getPanelId(), null, context);
        assertNotNull(tableResult);
        assertTrue(tableResult.isHasDataset());
        assertEquals("table", tableResult.getComponentType());
        assertEquals(1, tableResult.getRows().size(), "default region=south has 1 row");
        Object southAmount = findCaseInsensitive(tableResult.getRows().get(0), "amount");
        assertEquals(50, ((Number) southAmount).intValue());

        // 8. refreshPanel on chart panel
        PanelDataResult refreshed = panelBiz.refreshPanel(chartPanel.getPanelId(), context);
        assertNotNull(refreshed);
        assertTrue(refreshed.isHasDataset());
        // refresh without params → default region=south → 1 row
        assertEquals(1, refreshed.getRows().size(), "refresh uses default param region=south");

        // 9. Insert new data and refresh again → confirm latest data
        insertSalesRow("south", "gadget", 75);
        PanelDataResult refreshedAgain = panelBiz.refreshPanel(chartPanel.getPanelId(), context);
        assertEquals(2, refreshedAgain.getRows().size(), "after inserting new south row, refresh returns 2 rows");

        // 10. publishDashboard → snapshot should record both panels
        NopDatavDashboardSnapshot snapshot = dashboardBiz.publishDashboard(dashboard.getDashboardId(), context);
        assertNotNull(snapshot);
        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        List<?> panels = (List<?>) content.get("panels");
        assertEquals(2, panels.size(), "snapshot should contain both chart + table panels");
    }

    /**
     * 参数化查询验证：同一 Panel 同一 DatasetRef，请求参数变化导致查询结果变化。
     * 证明参数求值真正生效（非硬编码）。
     */
    @Test
    public void testParameterizedQueryChangesResultsByParamValue() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
        insertSalesRow("east", "widget", 300);

        NopReportDataset ds = newReportDataset("ds-param-e2e", "sql",
                "select count(*) as cnt from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-param-e2e", "param-e2e");
        NopDatavDatasetRef ref = newDatasetRef("ref-param-e2e", dashboard.getDashboardId(),
                "ds-param-e2e", "Param E2E");
        // No defaultValue here; tests must always pass region
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel panel = newPanel("panel-param-e2e", dashboard.getDashboardId(), "Param Panel");
        panel.setPanelType(TYPE_CHART);
        panel.setDatasetRefId("ref-param-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("e2e-user");

        // north → 2 rows
        PanelDataResult north = panelBiz.getPanelData(
                panel.getPanelId(), Map.of("region", "north"), context);
        assertEquals(1, north.getRows().size());
        assertEquals(2, ((Number) findCaseInsensitive(north.getRows().get(0), "cnt")).intValue());

        // south → 1 row
        PanelDataResult south = panelBiz.getPanelData(
                panel.getPanelId(), Map.of("region", "south"), context);
        assertEquals(1, ((Number) findCaseInsensitive(south.getRows().get(0), "cnt")).intValue());

        // east → 1 row
        PanelDataResult east = panelBiz.getPanelData(
                panel.getPanelId(), Map.of("region", "east"), context);
        assertEquals(1, ((Number) findCaseInsensitive(east.getRows().get(0), "cnt")).intValue());

        // Different params yield different result counts: parameter evaluation truly active
        int northCnt = ((Number) findCaseInsensitive(north.getRows().get(0), "cnt")).intValue();
        int southCnt = ((Number) findCaseInsensitive(south.getRows().get(0), "cnt")).intValue();
        int eastCnt = ((Number) findCaseInsensitive(east.getRows().get(0), "cnt")).intValue();
        assertTrue(northCnt != southCnt || northCnt != eastCnt,
                "Different region values must produce different result counts (proving param evaluation is real)");
    }

    // ==================== Helpers ====================

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

    private NopDatavPanel newPanel(String id, String dashboardId, String name) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(name);
        p.setDisplayName(name);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
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
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private static Object findCaseInsensitive(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
