package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.DashboardDataResult;
import io.nop.datav.biz.DashboardPanelDataItem;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_IFRAME;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量面板查询 focused tests（plan 2026-08-14-2020-2 Phase 2）。
 *
 * <p>覆盖 Phase 2 Exit Criteria 的行为证明：批量与逐面板组合语义一致、筛选一次求值全面板生效、
 * 面板级失败隔离（D3 形态断言）、无数据集面板纳入（hasDataset=false 条目）、panelIds 子集语义（D2）、
 * 超限显式拒绝（D4）、接线验证（经 biz 层入口调用真实 PanelDataBinder 管线，非 mock）。
 * 裁定依据 {@code ai-dev/design/nop-datav/runtime-design.md} §四。</p>
 */
public class TestNopDatavDashboardBatchQuery extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavPanelBiz panelBiz;

    /**
     * 多面板看板单次调用返回全部面板数据，且各面板结果与「resolveFilterValues + getPanelData」
     * 组合语义一致（同一筛选参数下 columns/rows/hasDataset 逐一相等）。
     * 同时验证：条目按 sortOrder 排序、无数据集面板（text/iframe）以 hasDataset=false 条目纳入而非排除。
     */
    @Test
    public void testBatchMatchesPerPanelComboSemantics() {
        IServiceContext context = newContext("batch-user");
        String dashboardId = setupDashboardWithPanels("dash-batch-combo", "ds-batch-combo", "ref-batch-combo",
                "panel-batch-chart", "panel-batch-table", "panel-batch-text", "panel-batch-iframe");

        Map<String, Object> rawFilters = Map.of("region", "north");

        // 批量一次调用
        DashboardDataResult batch = dashboardBiz.getDashboardData(dashboardId, rawFilters, null, context);

        assertNotNull(batch);
        assertEquals(dashboardId, batch.getDashboardId());
        assertEquals(4, batch.getPanels().size(), "all panels (incl. no-dataset) returned");

        // 条目顺序跟随面板 sortOrder：text(0) → chart(1) → table(2) → iframe(3)
        assertEquals("panel-batch-text", batch.getPanels().get(0).getPanelId());
        assertEquals("panel-batch-chart", batch.getPanels().get(1).getPanelId());
        assertEquals("panel-batch-table", batch.getPanels().get(2).getPanelId());
        assertEquals("panel-batch-iframe", batch.getPanels().get(3).getPanelId());

        // 与逐面板组合语义一致：resolveFilterValues 一次 + getPanelData 逐面板
        Map<String, Object> resolved = dashboardBiz.resolveFilterValues(dashboardId, rawFilters, context);
        for (DashboardPanelDataItem item : batch.getPanels()) {
            assertTrue(item.isSuccess(), "panel " + item.getPanelId() + " should succeed");
            assertNull(item.getErrorCode(), "no errorCode on success entries");
            PanelDataResult perPanel = panelBiz.getPanelData(item.getPanelId(), resolved, context);
            assertEquals(perPanel.isHasDataset(), item.isHasDataset());
            assertEquals(perPanel.getComponentType(), item.getComponentType());
            assertEquals(perPanel.getColumns(), item.getColumns(), "columns equal for " + item.getPanelId());
            assertEquals(perPanel.getRows(), item.getRows(), "rows equal for " + item.getPanelId());
        }

        // 无数据集面板：success=true + hasDataset=false 条目（非排除）
        DashboardPanelDataItem textItem = findItem(batch, "panel-batch-text");
        assertTrue(textItem.isSuccess());
        assertTrue(!textItem.isHasDataset(), "text panel hasDataset=false");
        assertEquals("text", textItem.getComponentType());
        assertTrue(textItem.getRows().isEmpty());
        DashboardPanelDataItem iframeItem = findItem(batch, "panel-batch-iframe");
        assertTrue(iframeItem.isSuccess());
        assertTrue(!iframeItem.isHasDataset(), "iframe panel hasDataset=false");
        assertEquals("iframe", iframeItem.getComponentType());

        // 有数据集面板拿到真实 SQL 数据（接线验证：真实 PanelDataBinder 管线 + jdbcTemplate 执行）
        DashboardPanelDataItem chartItem = findItem(batch, "panel-batch-chart");
        assertTrue(chartItem.isHasDataset());
        assertEquals(2, chartItem.getRows().size(), "north has 2 rows");
    }

    /**
     * 全局筛选参数一次求值后对所有面板生效：改变筛选值 → 多面板结果同步变化。
     */
    @Test
    public void testFilterChangePropagatesToAllPanels() {
        IServiceContext context = newContext("batch-user");
        String dashboardId = setupDashboardWithPanels("dash-batch-filter", "ds-batch-filter", "ref-batch-filter",
                "panel-f-chart", "panel-f-table", "panel-f-text", "panel-f-iframe");

        DashboardDataResult north = dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"), null, context);
        DashboardDataResult south = dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "south"), null, context);

        for (String panelId : new String[]{"panel-f-chart", "panel-f-table"}) {
            DashboardPanelDataItem northItem = findItem(north, panelId);
            DashboardPanelDataItem southItem = findItem(south, panelId);
            assertEquals(2, northItem.getRows().size(), panelId + " north has 2 rows");
            assertEquals(1, southItem.getRows().size(), panelId + " south has 1 row");
        }

        // 无数据集面板不随筛选变化（恒 hasDataset=false 空数据条目）
        assertTrue(!findItem(north, "panel-f-text").isHasDataset());
        assertTrue(!findItem(south, "panel-f-text").isHasDataset());
    }

    /**
     * 面板级失败隔离（D3）：datasetRef 失效的面板条目携带 success=false + errorCode + errorMessage，
     * 其余面板（含无数据集面板）正常返回；无静默空结果填充。
     */
    @Test
    public void testPartialFailureIsolation() {
        IServiceContext context = newContext("batch-user");
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset("ds-batch-broken", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-batch-broken", "batch-broken");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef goodRef = newDatasetRef("ref-batch-good", dashboard.getDashboardId(),
                "ds-batch-broken", "Good DS");
        goodRef.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(goodRef);

        NopDatavPanel goodPanel = newPanel("panel-good", dashboard.getDashboardId(), "Good", 1);
        goodPanel.setPanelType(TYPE_CHART);
        goodPanel.setDatasetRefId("ref-batch-good");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(goodPanel);

        // datasetRef 失效面板：datasetRefId 指向不存在的 ref
        NopDatavPanel brokenPanel = newPanel("panel-broken", dashboard.getDashboardId(), "Broken", 2);
        brokenPanel.setPanelType(TYPE_TABLE);
        brokenPanel.setDatasetRefId("ref-does-not-exist");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(brokenPanel);

        NopDatavPanel textPanel = newPanel("panel-text", dashboard.getDashboardId(), "Text", 0);
        textPanel.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(textPanel);

        DashboardDataResult batch = dashboardBiz.getDashboardData(
                dashboard.getDashboardId(), Map.of("region", "north"), null, context);

        assertEquals(3, batch.getPanels().size());

        DashboardPanelDataItem broken = findItem(batch, "panel-broken");
        assertTrue(!broken.isSuccess(), "broken panel entry success=false");
        assertEquals("nop.err.datav.dataset-ref-not-found", broken.getErrorCode());
        assertNotNull(broken.getErrorMessage(), "failure entry carries explicit error message");
        assertTrue(broken.getRows().isEmpty(), "failure entry has no data rows");

        // 其余面板正常返回（失败不拖垮整体）
        DashboardPanelDataItem good = findItem(batch, "panel-good");
        assertTrue(good.isSuccess());
        assertTrue(good.isHasDataset());
        assertEquals(2, good.getRows().size(), "good panel still returns north data");
        DashboardPanelDataItem text = findItem(batch, "panel-text");
        assertTrue(text.isSuccess());
        assertTrue(!text.isHasDataset());
    }

    /**
     * panelIds 子集语义（D2）：子集返回仅含指定面板（条目顺序跟随 sortOrder 不跟随传入顺序）、
     * 重复 id 去重；不存在或不属于该看板的 id 整体显式报错 ERR_DATAV_PANEL_NOT_IN_DASHBOARD。
     */
    @Test
    public void testPanelIdsSubsetSemantics() {
        IServiceContext context = newContext("batch-user");
        String dashboardId = setupDashboardWithPanels("dash-batch-subset", "ds-batch-subset", "ref-batch-subset",
                "panel-s-chart", "panel-s-table", "panel-s-text", "panel-s-iframe");
        // 另一看板的面板（不属于目标看板）
        NopDatavDashboard other = saveDashboard("dash-batch-other", "other");
        NopDatavPanel otherPanel = newPanel("panel-other", other.getDashboardId(), "Other", 0);
        otherPanel.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(otherPanel);

        // 子集：传入顺序与 sortOrder 相反 + 重复 id → 去重 + 按 sortOrder 返回
        DashboardDataResult subset = dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"),
                List.of("panel-s-table", "panel-s-chart", "panel-s-table"), context);
        assertEquals(2, subset.getPanels().size(), "subset filtered + duplicate deduped");
        assertEquals("panel-s-chart", subset.getPanels().get(0).getPanelId(), "order follows sortOrder");
        assertEquals("panel-s-table", subset.getPanels().get(1).getPanelId());

        // 空列表 = 全部面板（与 null 等价）
        DashboardDataResult emptyMeansAll = dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"), List.of(), context);
        assertEquals(4, emptyMeansAll.getPanels().size());

        // 不存在的 id → 整体显式报错
        NopException notExist = assertThrows(NopException.class, () -> dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"), List.of("panel-s-chart", "no-such-panel"), context));
        assertEquals("nop.err.datav.panel-not-in-dashboard", notExist.getErrorCode());

        // 属于其他看板的 id → 同样整体显式报错（不静默忽略）
        NopException crossDashboard = assertThrows(NopException.class, () -> dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"), List.of("panel-other"), context));
        assertEquals("nop.err.datav.panel-not-in-dashboard", crossDashboard.getErrorCode());
    }

    /**
     * 超限显式拒绝（D4）：纳入集面板数超过 max-panels 上限时抛 ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED，
     * 无部分响应。上限经配置项 NOP_DATAV_DASHBOARD_QUERY_MAX_PANELS 覆盖验证。
     */
    @Test
    public void testMaxPanelLimitExceeded() {
        IServiceContext context = newContext("batch-user");
        String dashboardId = setupDashboardWithPanels("dash-batch-limit", "ds-batch-limit", "ref-batch-limit",
                "panel-l-chart", "panel-l-table", "panel-l-text", "panel-l-iframe");

        // 覆盖上限为 2（纳入集 4 > 2 → 拒绝）
        Integer origMax = io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get();
        io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS, 2);
        try {
            NopException ex = assertThrows(NopException.class, () -> dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context));
            assertEquals("nop.err.datav.dashboard-panel-limit-exceeded", ex.getErrorCode());

            // 子集把纳入集缩到上限内 → 正常返回（上限对纳入集生效）
            DashboardDataResult subset = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), List.of("panel-l-chart", "panel-l-table"), context);
            assertEquals(2, subset.getPanels().size());
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                    io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS, origMax);
        }
    }

    /**
     * 看板级失败：看板不存在 → 整体报错（requireEntity 存在性校验，D5）。
     */
    @Test
    public void testDashboardNotFoundFailsWholeRequest() {
        IServiceContext context = newContext("batch-user");
        // 未创建任何看板，直接查询不存在的 id（requireEntity 抛 UnknownEntityException）
        assertThrows(Exception.class, () -> dashboardBiz.getDashboardData(
                "no-such-dashboard", null, null, context));
    }

    // ==================== Helpers ====================

    /**
     * 标准四面板看板：text(0) + chart(1) + table(2) + iframe(3)，chart/table 共用一个带 region 参数的
     * 数据集引用，看板 paramConfig 定义 region=string（无默认值，测试显式传值）。
     */
    private String setupDashboardWithPanels(String dashboardId, String dsId, String refId,
                                            String chartPanelId, String tablePanelId,
                                            String textPanelId, String iframePanelId) {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset(dsId, "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard(dashboardId, dashboardId);
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef(refId, dashboard.getDashboardId(), dsId, "Batch DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel text = newPanel(textPanelId, dashboard.getDashboardId(), "Text", 0);
        text.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(text);

        NopDatavPanel chart = newPanel(chartPanelId, dashboard.getDashboardId(), "Chart", 1);
        chart.setPanelType(TYPE_CHART);
        chart.setDatasetRefId(refId);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(chart);

        NopDatavPanel table = newPanel(tablePanelId, dashboard.getDashboardId(), "Table", 2);
        table.setPanelType(TYPE_TABLE);
        table.setDatasetRefId(refId);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(table);

        NopDatavPanel iframe = newPanel(iframePanelId, dashboard.getDashboardId(), "Iframe", 3);
        iframe.setPanelType(TYPE_IFRAME);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(iframe);

        return dashboard.getDashboardId();
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
        context.setContext(new TenantProxyContext(context.getContext()));
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

    private NopDatavPanel newPanel(String id, String dashboardId, String name, int sortOrder) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(name);
        p.setDisplayName(name);
        p.setSortOrder(sortOrder);
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
