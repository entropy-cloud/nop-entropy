package io.nop.datav.service.entity;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.DashboardDataResult;
import io.nop.datav.biz.DashboardPanelDataItem;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLELISM;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量面板查询并行执行 focused tests（plan 2026-08-15-0004-3 Phase 2）。
 *
 * <p>覆盖 Phase 2 Exit Criteria 的行为证明（裁定依据 runtime-design.md §8.1/§8.2）：</p>
 *
 * <pre>
 * - 并行结果与顺序版逐面板等价（含面板级失败条目、无数据集条目、条目顺序）
 * - 并发执行证明（latch 确定性断言，非计时推断：串行执行必然超时失败）
 * - 并行度上界不被突破（经 decorator 并发计数器断言 max in-flight ≤ parallelism）
 * - 非 NopException 按看板级失败传播（D3 语义在并行下保持）
 * - 上限校验先于任何查询执行（并行模式下零任务提交）
 * - 执行器跨请求共享复用（identity 断言 globalWorker，无每请求新建/关闭）
 * </pre>
 *
 * <p>经 biz 层入口（{@link INopDatavDashboardBiz}）真实调用链验证（接线验证：并行路径在
 * getDashboardData 上生效，非独立执行器存在但入口串行）；测试可观测 seam 为
 * {@link NopDatavDashboardBizModel#setPanelQueryTaskDecorator}（P1 裁定）。</p>
 */
public class TestNopDatavDashboardParallelQuery extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    NopDatavDashboardBizModel dashboardBizModel;

    /**
     * 等价性：同一看板（含成功面板 + 无数据集面板 + 失败面板）在并行与顺序（开关关）两种模式下
     * 返回结果完全一致——逐条目 panelId/componentType/success/hasDataset/columns/rows/errorCode
     * 相等且条目顺序一致（sortOrder）。
     */
    @Test
    public void testParallelResultsMatchSequentialBaseline() {
        IServiceContext context = newContext("parallel-user");
        String dashboardId = setupDashboardWithMixedPanels("dash-par-eq", "ds-par-eq", "ref-par-eq",
                "panel-eq-text", "panel-eq-chart", "panel-eq-broken");

        Map<String, Object> filters = Map.of("region", "north");

        // 顺序基线（开关关 = 并行前原路径）
        List<DashboardPanelDataItem> sequential = withParallelEnabled(false, () ->
                dashboardBiz.getDashboardData(dashboardId, filters, null, context)).getPanels();

        // 并行（开关开）
        List<DashboardPanelDataItem> parallel = withParallelEnabled(true, () ->
                dashboardBiz.getDashboardData(dashboardId, filters, null, context)).getPanels();

        assertEquals(sequential.size(), parallel.size(), "same entry count");
        assertEquals(3, parallel.size());
        for (int i = 0; i < sequential.size(); i++) {
            DashboardPanelDataItem seq = sequential.get(i);
            DashboardPanelDataItem par = parallel.get(i);
            assertEquals(seq.getPanelId(), par.getPanelId(), "entry order identical at index " + i);
            assertEquals(seq.getComponentType(), par.getComponentType(), "componentType@" + i);
            assertEquals(seq.isSuccess(), par.isSuccess(), "success@" + i);
            assertEquals(seq.isHasDataset(), par.isHasDataset(), "hasDataset@" + i);
            assertEquals(seq.getColumns(), par.getColumns(), "columns@" + i);
            assertEquals(seq.getRows(), par.getRows(), "rows@" + i);
            assertEquals(seq.getErrorCode(), par.getErrorCode(), "errorCode@" + i);
        }

        // 形态断言：条目顺序（sortOrder：text(0) → chart(1) → broken(2)），失败条目 + 无数据集条目保留
        assertEquals("panel-eq-text", parallel.get(0).getPanelId());
        assertTrue(parallel.get(0).isSuccess() && !parallel.get(0).isHasDataset());
        assertTrue(parallel.get(1).isSuccess() && parallel.get(1).isHasDataset());
        assertEquals(2, parallel.get(1).getRows().size(), "chart north has 2 rows");
        assertTrue(!parallel.get(2).isSuccess(), "broken panel entry success=false");
        assertEquals("nop.err.datav.dataset-ref-not-found", parallel.get(2).getErrorCode());
    }

    /**
     * 并发执行证明（确定性，非计时推断）：decorator 内 latch 要求 ≥2 面板任务同时在场才能放行；
     * 若执行实际串行，第一个任务等待 latch 超时抛 AssertionError → 测试失败。
     */
    @Test
    public void testQueriesExecuteConcurrently() {
        IServiceContext context = newContext("parallel-user");
        String dashboardId = setupDashboardWithMixedPanels("dash-par-conc", "ds-par-conc", "ref-par-conc",
                "panel-conc-text", "panel-conc-chart", "panel-conc-table");

        CountDownLatch bothStarted = new CountDownLatch(2);
        dashboardBizModel.setPanelQueryTaskDecorator((panel, task) -> () -> {
            if (!panel.getPanelId().startsWith("panel-conc-text")) {
                bothStarted.countDown();
                if (!bothStarted.await(20, TimeUnit.SECONDS)) {
                    throw new AssertionError("panel queries did not overlap within 20s — execution is not parallel");
                }
            }
            return task.call();
        });
        try {
            DashboardDataResult result = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(3, result.getPanels().size(), "all panels returned");
            for (DashboardPanelDataItem item : result.getPanels()) {
                assertTrue(item.isSuccess(), "panel " + item.getPanelId() + " succeeded under overlap gate");
            }
        } finally {
            dashboardBizModel.setPanelQueryTaskDecorator(null);
        }
    }

    /**
     * 并行度上界不被突破：6 个数据集面板、parallelism=2，decorator 并发计数器断言
     * max in-flight ≤ 2（且确实 ≥2，非假并行串行）；全部条目成功返回。
     */
    @Test
    public void testParallelismBoundNotExceeded() {
        IServiceContext context = newContext("parallel-user");
        String dashboardId = setupDashboardWithDatasetPanels("dash-par-bound", "ds-par-bound", "ref-par-bound",
                "panel-bound", 6);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        dashboardBizModel.setPanelQueryTaskDecorator((panel, task) -> () -> {
            int current = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(current, Math::max);
            try {
                // 放大重叠窗口，使并行度约束可观测（decorator 体在 Semaphore 许可区内执行）
                Thread.sleep(100);
                return task.call();
            } finally {
                inFlight.decrementAndGet();
            }
        });
        Integer origParallelism = CFG_DATAV_DASHBOARD_QUERY_PARALLELISM.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_PARALLELISM, 2);
        try {
            DashboardDataResult result = dashboardBiz.getDashboardData(
                    dashboardId, Map.of("region", "north"), null, context);
            assertEquals(6, result.getPanels().size(), "all 6 panels returned");
            for (DashboardPanelDataItem item : result.getPanels()) {
                assertTrue(item.isSuccess() && item.isHasDataset(), "panel " + item.getPanelId() + " succeeded");
                assertEquals(2, item.getRows().size());
            }
            assertTrue(maxInFlight.get() <= 2,
                    "parallelism bound not exceeded, max in-flight = " + maxInFlight.get());
            assertTrue(maxInFlight.get() >= 2,
                    "queries really ran concurrently, max in-flight = " + maxInFlight.get());
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_PARALLELISM, origParallelism);
            dashboardBizModel.setPanelQueryTaskDecorator(null);
        }
    }

    /**
     * D3 语义在并行下保持：任务抛非 NopException → 按看板级失败传播（整体抛出，不降级为面板级失败条目，
     * 不吞掉）。
     */
    @Test
    public void testNonNopExceptionPropagatesAsDashboardLevelFailure() {
        IServiceContext context = newContext("parallel-user");
        String dashboardId = setupDashboardWithMixedPanels("dash-par-sys", "ds-par-sys", "ref-par-sys",
                "panel-sys-text", "panel-sys-chart", "panel-sys-table");

        dashboardBizModel.setPanelQueryTaskDecorator((panel, task) -> () -> {
            if ("panel-sys-chart".equals(panel.getPanelId())) {
                throw new IllegalStateException("simulated system failure");
            }
            return task.call();
        });
        try {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    dashboardBiz.getDashboardData(dashboardId, Map.of("region", "north"), null, context));
            assertEquals("simulated system failure", ex.getMessage(),
                    "non-NopException propagates as dashboard-level failure, not swallowed");
        } finally {
            dashboardBizModel.setPanelQueryTaskDecorator(null);
        }
    }

    /**
     * 上限校验仍先于任何查询执行（D4 在并行模式下保持）：max-panels 超限时整体抛
     * ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED 且零面板任务被提交（decorator 计数 = 0）。
     */
    @Test
    public void testMaxPanelLimitPrecedesAnyQueryUnderParallel() {
        IServiceContext context = newContext("parallel-user");
        String dashboardId = setupDashboardWithMixedPanels("dash-par-limit", "ds-par-limit", "ref-par-limit",
                "panel-lim-text", "panel-lim-chart", "panel-lim-table");

        AtomicInteger taskCount = new AtomicInteger();
        dashboardBizModel.setPanelQueryTaskDecorator((panel, task) -> () -> {
            taskCount.incrementAndGet();
            return task.call();
        });
        Integer origMax = CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get();
        AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS, 2);
        try {
            NopException ex = assertThrows(NopException.class, () ->
                    dashboardBiz.getDashboardData(dashboardId, Map.of("region", "north"), null, context));
            assertEquals("nop.err.datav.dashboard-panel-limit-exceeded", ex.getErrorCode());
            assertEquals(0, taskCount.get(), "no panel query task submitted before limit rejection");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS, origMax);
            dashboardBizModel.setPanelQueryTaskDecorator(null);
        }
    }

    /**
     * 执行器共享复用（无线程资源泄漏面，§8.1）：面板查询执行器恒为平台共享 globalWorker，
     * 两次真实调用前后 identity 不变——无每请求线程池新建/关闭。
     */
    @Test
    public void testExecutorSharedReuseAcrossRequests() {
        IServiceContext context = newContext("parallel-user");
        String dashboardId = setupDashboardWithMixedPanels("dash-par-exec", "ds-par-exec", "ref-par-exec",
                "panel-exec-text", "panel-exec-chart", "panel-exec-table");

        assertSame(GlobalExecutors.globalWorker(), dashboardBizModel.getPanelQueryExecutor(),
                "panel query executor is the shared global worker pool");

        DashboardDataResult first = dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"), null, context);
        DashboardDataResult second = dashboardBiz.getDashboardData(
                dashboardId, Map.of("region", "north"), null, context);
        assertEquals(3, first.getPanels().size());
        assertEquals(3, second.getPanels().size());

        assertSame(GlobalExecutors.globalWorker(), dashboardBizModel.getPanelQueryExecutor(),
                "same shared executor after requests — no per-request pool creation/disposal");
    }

    // ==================== Helpers ====================

    private DashboardDataResult withParallelEnabled(boolean enabled,
                                                    java.util.function.Supplier<DashboardDataResult> call) {
        Boolean orig = io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED.get();
        AppConfig.getConfigProvider().updateConfigValue(
                io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED, enabled);
        try {
            return call.get();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(
                    io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_PARALLEL_ENABLED, orig);
        }
    }

    /**
     * 混合面板看板：text(0) 无数据集 + chart(1) 有数据集 + 第三个面板由调用方破坏或复用
     * （broken 标记时 datasetRefId 指向不存在引用 → 面板级失败条目）。
     */
    private String setupDashboardWithMixedPanels(String dashboardId, String dsId, String refId,
                                                 String textPanelId, String chartPanelId, String thirdPanelId) {
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

        NopDatavDatasetRef ref = newDatasetRef(refId, dashboard.getDashboardId(), dsId, "Parallel DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel text = newPanel(textPanelId, dashboard.getDashboardId(), "Text", 0);
        text.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(text);

        NopDatavPanel chart = newPanel(chartPanelId, dashboard.getDashboardId(), "Chart", 1);
        chart.setPanelType(TYPE_CHART);
        chart.setDatasetRefId(refId);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(chart);

        NopDatavPanel third = newPanel(thirdPanelId, dashboard.getDashboardId(), "Third", 2);
        if (thirdPanelId.contains("broken")) {
            // 面板级失败：datasetRefId 指向不存在的引用
            third.setPanelType(TYPE_TABLE);
            third.setDatasetRefId("ref-does-not-exist");
        } else {
            third.setPanelType(TYPE_TABLE);
            third.setDatasetRefId(refId);
        }
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(third);

        return dashboard.getDashboardId();
    }

    /** N 个数据集面板看板（共享同一数据集引用），用于并行度上界断言。 */
    private String setupDashboardWithDatasetPanels(String dashboardId, String dsId, String refId,
                                                   String panelIdPrefix, int panelCount) {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);

        NopReportDataset ds = newReportDataset(dsId, "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard(dashboardId, dashboardId);
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef(refId, dashboard.getDashboardId(), dsId, "Parallel DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        List<NopDatavPanel> panels = new ArrayList<>(panelCount);
        for (int i = 0; i < panelCount; i++) {
            NopDatavPanel panel = newPanel(panelIdPrefix + "-" + i, dashboard.getDashboardId(),
                    "Panel " + i, i);
            panel.setPanelType(TYPE_CHART);
            panel.setDatasetRefId(refId);
            daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);
            panels.add(panel);
        }
        return dashboard.getDashboardId();
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
        // drop-then-create（镜像 TestNopDatavExportE2E.setupSalesData 先例：异步 worker 场景下跨方法表状态
        // 不保证干净，drop 失败忽略——表不存在）
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
