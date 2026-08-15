package io.nop.datav.service.entity;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.DashboardDataResult;
import io.nop.datav.biz.DashboardPanelDataItem;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.query.DashboardPanelQueryCache;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量查询性能收口端到端测试（plan 2026-08-15-0004-3 Phase 4，rule #22 端到端验证 +
 * Anti-Hollow Check 证据）。
 *
 * <p>贯穿完整后端链路（并行 + 缓存同时生效，裁定见 runtime-design.md §八）：</p>
 *
 * <pre>
 * 多面板看板（全局筛选 region + 无数据集面板 text + 两个同数据集面板 chart/table + 一个失败面板 broken）
 *   → getDashboardData（默认并行执行 + 缓存启用）
 *        首次调用：面板查询并行执行，结果与「顺序 + 无缓存」基线逐条目一致（含失败条目/顺序）
 *        立即重复调用（同参）：缓存命中（hitCount 增长 + 结果与首次逐条目一致，SQL 不重查）
 *        修改筛选参数（region=north → south）：重新执行 SQL（miss）且结果正确（1 行）
 * </pre>
 *
 * <p>经 biz 层入口（{@link INopDatavDashboardBiz}）调用真实 PanelDataBinder 管线 → worker 线程
 * 新 ORM session → 缓存判定 → IJdbcTemplate 执行 SQL → 批量结果回传，无 mock 绕过。</p>
 */
public class TestNopDatavDashboardPerfE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    NopDatavDashboardBizModel dashboardBizModel;

    /**
     * 主线 E2E：并行 + 缓存联合生效的完整生命周期（顺序基线等价 → 命中 → 参数变化重查）。
     */
    @Test
    public void testEndToEndParallelWithCacheFullLifecycle() {
        IServiceContext context = newContext("perf-e2e-user");

        // ===== 准备：业务数据 + 数据集 + 看板（全局筛选 + 四类面板，含一个失败面板）=====
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset("ds-perf-e2e", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-perf-e2e", "perf-e2e");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef("ref-perf-e2e", dashboard.getDashboardId(),
                "ds-perf-e2e", "Perf E2E DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel textPanel = newPanel("panel-perf-text", dashboard.getDashboardId(), "Note", 0);
        textPanel.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(textPanel);

        NopDatavPanel chartPanel = newPanel("panel-perf-chart", dashboard.getDashboardId(), "Chart", 1);
        chartPanel.setPanelType(TYPE_CHART);
        chartPanel.setDatasetRefId("ref-perf-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(chartPanel);

        NopDatavPanel tablePanel = newPanel("panel-perf-table", dashboard.getDashboardId(), "Table", 2);
        tablePanel.setPanelType(TYPE_TABLE);
        tablePanel.setDatasetRefId("ref-perf-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(tablePanel);

        NopDatavPanel brokenPanel = newPanel("panel-perf-broken", dashboard.getDashboardId(), "Broken", 3);
        brokenPanel.setPanelType(TYPE_TABLE);
        brokenPanel.setDatasetRefId("ref-broken-perf");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(brokenPanel);

        // ===== 1. 顺序 + 无缓存基线（并行前原语义）=====
        Boolean origCacheEnabled = CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get();
        Boolean origParallelEnabled = CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, false);
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED, false);
        DashboardDataResult baseline;
        try {
            baseline = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "north"), null, context);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, true);
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED, true);
        }
        assertResultShape(baseline, "baseline");
        dashboardBizModel.resetQueryResultCacheForTest();

        try {
            DashboardPanelQueryCache cache = dashboardBizModel.getQueryResultCacheForTest();

            // ===== 2. 并行 + 缓存首次调用：结果与基线逐条目一致（含失败条目、无数据集条目、顺序）=====
            DashboardDataResult first = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "north"), null, context);
            assertResultsEqual(baseline, first);
            assertTrue(cache.getMissCount() >= 1, "first call misses the cache (SQL executed)");
            // 首调 hitCount 不作断言：chart/table 并行共享同一键，table 命中 chart 先写入的条目是
            // §8.4 请求内去重的合法形态（调度相关，两种结果均符合契约）

            // ===== 3. 立即重复调用（同参）：缓存命中且结果一致（SQL 不重查——chart/table 共享同一缓存条目）=====
            DashboardDataResult second = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "north"), null, context);
            assertResultsEqual(first, second);
            assertTrue(cache.getHitCount() >= 1, "immediate repeat call hits the cache");
            // 同参二次调用后未命中数不增长（无新 SQL：table 首调经请求内去重已命中 chart 写入的条目）
            long missAfterFirst = cache.getMissCount();
            DashboardDataResult third = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "north"), null, context);
            assertResultsEqual(second, third);
            assertEquals(missAfterFirst, cache.getMissCount(), "repeat calls within TTL execute no new SQL");

            // ===== 4. 修改筛选参数（region=south）：重新执行 SQL 且结果正确 =====
            DashboardDataResult south = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "south"), null, context);
            assertEquals(4, south.getPanels().size(), "south call returns all panels");
            assertEquals(1, findItem(south, "panel-perf-chart").getRows().size(),
                    "changed filter re-executes SQL with correct result (south has 1 row)");
            assertEquals(1, findItem(south, "panel-perf-table").getRows().size());
            assertTrue(cache.getMissCount() > missAfterFirst, "changed filter is a new cache key (miss)");
            assertTrue(!findItem(south, "panel-perf-broken").isSuccess(),
                    "broken panel still isolated under parallel+cache");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, origCacheEnabled);
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED, origParallelEnabled);
            dashboardBizModel.resetQueryResultCacheForTest();
        }
    }

    // ==================== Helpers ====================

    /** 结果形态断言：4 条目、顺序（text→chart→table→broken）、无数据集条目、失败隔离、数据行数。 */
    private void assertResultShape(DashboardDataResult result, String label) {
        assertNotNull(result);
        assertEquals(4, result.getPanels().size(), label + ": all 4 panels returned");
        assertEquals("panel-perf-text", result.getPanels().get(0).getPanelId(), label + ": sortOrder order");
        assertEquals("panel-perf-chart", result.getPanels().get(1).getPanelId());
        assertEquals("panel-perf-table", result.getPanels().get(2).getPanelId());
        assertEquals("panel-perf-broken", result.getPanels().get(3).getPanelId());

        DashboardPanelDataItem text = findItem(result, "panel-perf-text");
        assertTrue(text.isSuccess() && !text.isHasDataset(), label + ": no-dataset panel entry preserved");
        DashboardPanelDataItem chart = findItem(result, "panel-perf-chart");
        assertTrue(chart.isSuccess() && chart.isHasDataset() && chart.getRows().size() == 2,
                label + ": chart north 2 rows");
        DashboardPanelDataItem table = findItem(result, "panel-perf-table");
        assertTrue(table.isSuccess() && table.isHasDataset() && table.getRows().size() == 2,
                label + ": table north 2 rows");
        DashboardPanelDataItem broken = findItem(result, "panel-perf-broken");
        assertTrue(!broken.isSuccess() && "nop.err.datav.dataset-ref-not-found".equals(broken.getErrorCode()),
                label + ": broken panel isolated with explicit error code");
    }

    /** 两次结果逐条目等价（panelId/componentType/success/hasDataset/columns/rows/errorCode/errorMessage）。 */
    private void assertResultsEqual(DashboardDataResult expected, DashboardDataResult actual) {
        assertEquals(expected.getPanels().size(), actual.getPanels().size());
        for (int i = 0; i < expected.getPanels().size(); i++) {
            DashboardPanelDataItem e = expected.getPanels().get(i);
            DashboardPanelDataItem a = actual.getPanels().get(i);
            assertEquals(e.getPanelId(), a.getPanelId(), "panelId@" + i);
            assertEquals(e.getComponentType(), a.getComponentType(), "componentType@" + i);
            assertEquals(e.isSuccess(), a.isSuccess(), "success@" + i);
            assertEquals(e.isHasDataset(), a.isHasDataset(), "hasDataset@" + i);
            assertEquals(e.getColumns(), a.getColumns(), "columns@" + i);
            assertEquals(e.getRows(), a.getRows(), "rows@" + i);
            assertEquals(e.getErrorCode(), a.getErrorCode(), "errorCode@" + i);
            assertEquals(e.getErrorMessage() != null, a.getErrorMessage() != null, "errorMessage presence@" + i);
        }
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
        try {
            jdbcTemplate.executeUpdate(SQL.begin()
                    .name("drop:TEST_DATAV_SALES")
                    .sql("drop table TEST_DATAV_SALES").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
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
