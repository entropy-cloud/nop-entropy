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
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.PanelDataResult;
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
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * getDashboardData 查询结果缓存 focused tests（plan 2026-08-15-0004-3 Phase 3）。
 *
 * <p>覆盖 Phase 3 Exit Criteria 的行为证明（裁定依据 runtime-design.md §8.3/§8.4/§8.5）：</p>
 *
 * <pre>
 * - 命中路径不再触发 SQL（可观测断言：hit/miss 计数 + 数据 staleness 语义断言，非耗时推断）
 * - 不同求值参数互不串台（键无碰撞：buildKey 单元断言 + 不同参数必 miss 重查）
 * - TTL 过期后重新执行（短 TTL 直构实例 + 裕量等待，确定性）
 * - TTL 内数据集变更 → staleness 契约（读旧值，显式断言；开关关闭后读新值）
 * - 缓存关闭回归现状（每次查询执行 SQL）
 * - 无数据集条目与面板级失败条目不缓存（失败不固化：修复 datasetRef 后下次调用即成功）
 * - binder 既有调用方回归（cache.enabled=true 下 getPanelData 单面板路径仍每次直查，Non-Goal 边界证明）
 * - 单条目行数准入上界（超限不缓存不截断）
 * </pre>
 */
public class TestNopDatavDashboardQueryCache extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavPanelBiz panelBiz;

    @Inject
    NopDatavDashboardBizModel dashboardBizModel;

    /**
     * 命中路径不再执行 SQL：第一次调用后插入新行，第二次同参调用返回旧行数（staleness 即命中证明）
     * 且 hitCount=1；参数变化（region=north → south）必 miss 重查（行数跟随新参数）。
     */
    @Test
    public void testCacheHitSkipsSqlAndParamChangeMisses() {
        IServiceContext context = newContext("cache-user");
        String dashboardId = setupDashboard("dash-cache-hit", "ds-cache-hit", "ref-cache-hit",
                "panel-cache-chart");
        dashboardBizModel.resetQueryResultCacheForTest();

        Boolean origEnabled = CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, true);
        try {
            DashboardPanelQueryCache cache = dashboardBizModel.getQueryResultCacheForTest();

            // 第一次：miss → 执行 SQL（north 2 行）→ 回填
            DashboardDataResult first = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(2, findItem(first, "panel-cache-chart").getRows().size());
            assertEquals(0, cache.getHitCount(), "first call is a miss");
            assertEquals(1, cache.getMissCount());

            // 插入新行：若第二次调用执行 SQL 则会看到 3 行
            insertSalesRow("north", "latecomer", 300);

            // 第二次同参：命中 → 返回 TTL 内的旧结果（2 行，不含 latecomer）
            DashboardDataResult second = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(2, findItem(second, "panel-cache-chart").getRows().size(),
                    "cache hit returns stale result within TTL (SQL not re-executed)");
            assertEquals(1, cache.getHitCount(), "second call hits the cache");

            // 参数变化 → 不同键 → miss 重查（south 1 行 + 上一步未影响 south；新行是 north）
            DashboardDataResult south = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "south"), null, context);
            assertEquals(1, findItem(south, "panel-cache-chart").getRows().size(),
                    "different params miss the cache and re-execute SQL");
            // south 命中过 north 键? no: 新键 miss；再调一次 south → 命中
            DashboardDataResult southAgain = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "south"), null, context);
            assertEquals(1, findItem(southAgain, "panel-cache-chart").getRows().size());
            assertEquals(2, cache.getHitCount(), "south key now cached and hit");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, origEnabled);
            dashboardBizModel.resetQueryResultCacheForTest();
        }
    }

    /**
     * TTL 过期后重新执行：短 TTL（200ms）直构实例，put → 命中 → 裕量等待（500ms）→ 过期 miss。
     */
    @Test
    public void testTtlExpiryReExecutes() throws InterruptedException {
        DashboardPanelQueryCache cache = new DashboardPanelQueryCache(10, 200L, 1000);
        PanelDataResult result = new PanelDataResult("p", "chart", true,
                List.of("region"), List.of(Map.of("region", "north")));
        String key = DashboardPanelQueryCache.buildKey("ds", Map.of("region", "north"), null);

        cache.tryPut(key, result);
        assertNotNull(cache.get(key), "fresh entry is readable within TTL");
        assertEquals(1, cache.getHitCount());

        Thread.sleep(500);
        assertNull(cache.get(key), "entry expired after TTL — next query re-executes SQL");
    }

    /**
     * TTL 内数据集变更的 staleness 契约（§8.4 显式断言）：TTL 窗口内修改数据集 SQL 文本，
     * 同键命中返回旧结果（staleness 上界=TTL 契约）；缓存关闭后读新值。
     */
    @Test
    public void testStalenessContractWithinTtl() {
        IServiceContext context = newContext("cache-user");
        String dashboardId = setupDashboard("dash-cache-stale", "ds-cache-stale", "ref-cache-stale",
                "panel-stale-chart");
        dashboardBizModel.resetQueryResultCacheForTest();

        Boolean origEnabled = CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, true);
        try {
            // 基线：north 2 行
            DashboardDataResult before = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(2, findItem(before, "panel-stale-chart").getRows().size());

            // TTL 内修改数据集 SQL（加 AMOUNT > 150 过滤 → 1 行）
            NopReportDataset ds = daoProvider.daoFor(NopReportDataset.class).getEntityById("ds-cache-stale");
            ds.setDsText("select REGION as region, AMOUNT as amount from TEST_DATAV_SALES "
                    + "where REGION = ${region} and AMOUNT > 150");
            daoProvider.daoFor(NopReportDataset.class).updateEntityDirectly(ds);

            // 同键命中：TTL 内读旧结果（2 行）——staleness 契约
            DashboardDataResult stale = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(2, findItem(stale, "panel-stale-chart").getRows().size(),
                    "dataset change invisible within TTL (staleness upper bound = TTL, by contract)");
            assertEquals(before.getPanels().get(0).getRows(), stale.getPanels().get(0).getRows());

            // 缓存关闭：立即读新值（每次直查）
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, false);
            DashboardDataResult fresh = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(1, findItem(fresh, "panel-stale-chart").getRows().size(),
                    "cache disabled reads current dataset definition");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, origEnabled);
            dashboardBizModel.resetQueryResultCacheForTest();
        }
    }

    /**
     * 缓存关闭回归现状：每次查询执行 SQL（两次调用间插入的行对第二次调用可见，零 staleness）。
     */
    @Test
    public void testCacheDisabledReExecutesEveryTime() {
        IServiceContext context = newContext("cache-user");
        String dashboardId = setupDashboard("dash-cache-off", "ds-cache-off", "ref-cache-off",
                "panel-off-chart");

        Boolean origEnabled = CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, false);
        try {
            DashboardDataResult first = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(2, findItem(first, "panel-off-chart").getRows().size());

            insertSalesRow("north", "latecomer", 300);

            DashboardDataResult second = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(3, findItem(second, "panel-off-chart").getRows().size(),
                    "cache disabled: every call re-executes SQL (no staleness)");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, origEnabled);
            dashboardBizModel.resetQueryResultCacheForTest();
        }
    }

    /**
     * P4 裁定断言：面板级失败条目不缓存（修复 datasetRef 后下次调用即成功——失败不在 TTL 内固化）；
     * 无数据集面板条目不经缓存（恒定重建）。
     */
    @Test
    public void testFailureAndNoDatasetEntriesNotCached() {
        IServiceContext context = newContext("cache-user");
        // 看板：text(0) + broken-chart(1)（datasetRefId 指向不存在引用）
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset("ds-cache-fix", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-cache-fix", "cache-fix");
        dashboard.setParamConfig(JsonTool.stringify(List.of(Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef("ref-cache-fix", dashboard.getDashboardId(), "ds-cache-fix", "Fix DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel text = newPanel("panel-fix-text", dashboard.getDashboardId(), "Text", 0);
        text.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(text);

        NopDatavPanel broken = newPanel("panel-fix-chart", dashboard.getDashboardId(), "Chart", 1);
        broken.setPanelType(TYPE_CHART);
        broken.setDatasetRefId("ref-does-not-exist");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(broken);

        Boolean origEnabled = CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, true);
        dashboardBizModel.resetQueryResultCacheForTest();
        try {
            // 第一次：失败条目（dataset-ref-not-found），无固化
            DashboardDataResult first = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "north"), null, context);
            assertTrue(!findItem(first, "panel-fix-chart").isSuccess());
            assertEquals("nop.err.datav.dataset-ref-not-found",
                    findItem(first, "panel-fix-chart").getErrorCode());
            assertTrue(findItem(first, "panel-fix-text").isSuccess()
                    && !findItem(first, "panel-fix-text").isHasDataset());

            // 修复 datasetRef → 第二次（同参，TTL 内）：失败条目未被缓存，修复立即生效
            broken.setDatasetRefId("ref-cache-fix");
            daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(broken);

            DashboardDataResult second = dashboardBiz.getDashboardData(
                    dashboard.getDashboardId(), Map.of("region", "north"), null, context);
            DashboardPanelDataItem fixed = findItem(second, "panel-fix-chart");
            assertTrue(fixed.isSuccess(), "failure entries are not cached — fixed ref takes effect immediately");
            assertEquals(2, fixed.getRows().size());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, origEnabled);
            dashboardBizModel.resetQueryResultCacheForTest();
        }
    }

    /**
     * binder 既有调用方回归（Non-Goal 边界证明）：cache.enabled=true 下 getPanelData 单面板路径
     * 仍每次直查（两次调用间插入的行对第二次可见）——缓存仅接入 getDashboardData 批量路径。
     */
    @Test
    public void testGetPanelDataPathNotCachedEvenWhenEnabled() {
        IServiceContext context = newContext("cache-user");
        String dashboardId = setupDashboard("dash-cache-single", "ds-cache-single", "ref-cache-single",
                "panel-single-chart");
        Map<String, Object> resolved = dashboardBiz.resolveFilterValues(dashboardId,
                Map.of("region", "north"), context);

        Boolean origEnabled = CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, true);
        try {
            PanelDataResult first = panelBiz.getPanelData("panel-single-chart", resolved, context);
            assertEquals(2, first.getRows().size());

            insertSalesRow("north", "latecomer", 300);

            PanelDataResult second = panelBiz.getPanelData("panel-single-chart", resolved, context);
            assertEquals(3, second.getRows().size(),
                    "getPanelData path is not wired to the cache (Non-Goal boundary)");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_CACHE_ENABLED, origEnabled);
            dashboardBizModel.resetQueryResultCacheForTest();
        }
    }

    /**
     * 键无碰撞断言（buildKey 单元）：相同输入 → 相同键；refDatasetId/求值参数/rowLimit 任一不同 → 不同键。
     */
    @Test
    public void testBuildKeyDiscrimination() {
        Map<String, Object> north = Map.of("region", "north");
        Map<String, Object> south = Map.of("region", "south");

        assertEquals(DashboardPanelQueryCache.buildKey("ds1", north, null),
                DashboardPanelQueryCache.buildKey("ds1", north, null), "same inputs → same key");

        assertNotEquals(DashboardPanelQueryCache.buildKey("ds1", north, null),
                DashboardPanelQueryCache.buildKey("ds2", north, null), "dataset identity in key");
        assertNotEquals(DashboardPanelQueryCache.buildKey("ds1", north, null),
                DashboardPanelQueryCache.buildKey("ds1", south, null), "evaluated params in key");
        assertNotEquals(DashboardPanelQueryCache.buildKey("ds1", north, null),
                DashboardPanelQueryCache.buildKey("ds1", north, 100), "rowLimit in key");
        assertNotEquals(DashboardPanelQueryCache.buildKey("ds1", north, 100),
                DashboardPanelQueryCache.buildKey("ds1", north, 200), "different rowLimit → different key");
        assertEquals("ds:ds1|params:{\"region\":\"north\"}|limit:-",
                DashboardPanelQueryCache.buildKey("ds1", north, null), "null rowLimit renders as unlimited");
        assertEquals("ds:ds1|params:{}|limit:-", DashboardPanelQueryCache.buildKey("ds1", null, null),
                "null params render as empty");
    }

    /**
     * 单条目行数准入上界（§8.5）：结果行数超过 max-rows-per-entry → 不缓存（不截断——
     * 超限结果永远直查，缓存绝不返回截断数据）。
     */
    @Test
    public void testMaxRowsPerEntryAdmission() {
        DashboardPanelQueryCache cache = new DashboardPanelQueryCache(10, 60_000L, 1);

        PanelDataResult twoRows = new PanelDataResult("p", "chart", true,
                List.of("region"), List.of(Map.of("region", "a"), Map.of("region", "b")));
        String key = DashboardPanelQueryCache.buildKey("ds", Map.of("region", "x"), null);

        cache.tryPut(key, twoRows);
        assertNull(cache.get(key), "result exceeding max-rows-per-entry is not cached");

        PanelDataResult oneRow = new PanelDataResult("p", "chart", true,
                List.of("region"), List.of(Map.of("region", "a")));
        cache.tryPut(key, oneRow);
        assertNotNull(cache.get(key), "result within max-rows-per-entry is cached");
        assertEquals(1, cache.get(key).getRows().size(), "cached result is the full result, never truncated");
    }

    // ==================== Helpers ====================

    private String setupDashboard(String dashboardId, String dsId, String refId, String chartPanelId) {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset(dsId, "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard(dashboardId, dashboardId);
        dashboard.setParamConfig(JsonTool.stringify(List.of(Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef(refId, dashboard.getDashboardId(), dsId, "Cache DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel chart = newPanel(chartPanelId, dashboard.getDashboardId(), "Chart", 0);
        chart.setPanelType(TYPE_CHART);
        chart.setDatasetRefId(refId);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(chart);
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
