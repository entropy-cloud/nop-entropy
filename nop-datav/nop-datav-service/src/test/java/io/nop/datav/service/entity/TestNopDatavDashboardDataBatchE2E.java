package io.nop.datav.service.entity;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量面板查询端到端测试（plan 2026-08-14-2020-2 Phase 3）。
 *
 * <p>贯穿完整后端链路（rule #22 端到端验证 + Anti-Hollow Check 证据）：</p>
 *
 * <pre>
 * 创建看板（paramConfig 全局筛选参数 region=string/default=all）
 *   → 创建测试业务数据表 + 插入测试数据
 *   → 创建 NopReportDataset（dsType=sql，dsText 含 ${region} 命名参数）
 *   → 创建 DatasetRef（paramMapping：region → region）
 *   → 创建多面板（chart + table 有数据集；text + iframe 无数据集）
 *   → 单次 getDashboardData：
 *        不传筛选 → 默认值 all 生效（筛选前后结果差异）
 *        传 region=north → 多面板结果同步变化
 *        无数据集面板以 hasDataset=false 条目存在（非排除）
 *   → 破坏一个面板的 datasetRef → 再次批量 → 面板级错误隔离（失败条目 + 成功面板并存）
 * </pre>
 *
 * <p>经 biz 层入口（{@link INopDatavDashboardBiz}）调用真实 {@code PanelDataBinder} 管线 →
 * {@code IJdbcTemplate} 执行 SQL → 批量结果回传，无 mock 绕过。</p>
 */
public class TestNopDatavDashboardDataBatchE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    /**
     * 主线 E2E：默认筛选 → 显式筛选 → 筛选变化传播 → 无数据集面板纳入 → 面板级错误隔离。
     */
    @Test
    public void testEndToEndBatchQueryFullLifecycle() {
        IServiceContext context = newContext("e2e-batch-user");

        // ===== 准备：业务数据 + 数据集 + 看板（全局筛选参数）+ 多面板（含无数据集面板）=====
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
        insertSalesRow("all", "mixed", 30);

        NopReportDataset ds = newReportDataset("ds-batch-e2e", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES where REGION = ${region} "
                        + "order by AMOUNT desc");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-batch-e2e", "batch-e2e");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "all"))));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef("ref-batch-e2e", dashboard.getDashboardId(),
                "ds-batch-e2e", "Batch E2E DS");
        ref.setParamMapping(JsonTool.stringify(Map.of("region", Map.of("source", "region"))));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel textPanel = newPanel("panel-e2e-text", dashboard.getDashboardId(), "Note", 0);
        textPanel.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(textPanel);

        NopDatavPanel chartPanel = newPanel("panel-e2e-chart", dashboard.getDashboardId(), "Sales Chart", 1);
        chartPanel.setPanelType(TYPE_CHART);
        chartPanel.setDatasetRefId("ref-batch-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(chartPanel);

        NopDatavPanel tablePanel = newPanel("panel-e2e-table", dashboard.getDashboardId(), "Sales Table", 2);
        tablePanel.setPanelType(TYPE_TABLE);
        tablePanel.setDatasetRefId("ref-batch-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(tablePanel);

        NopDatavPanel iframePanel = newPanel("panel-e2e-iframe", dashboard.getDashboardId(), "Embed", 3);
        iframePanel.setPanelType(TYPE_IFRAME);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(iframePanel);

        // ===== 1. 不传筛选 → 默认值 all 生效（单次调用，四面板一次取回）=====
        DashboardDataResult byDefault = dashboardBiz.getDashboardData(
                dashboard.getDashboardId(), null, null, context);

        assertNotNull(byDefault);
        assertEquals(dashboard.getDashboardId(), byDefault.getDashboardId());
        assertEquals(4, byDefault.getPanels().size(), "single call returns all 4 panels");
        assertEquals("panel-e2e-text", byDefault.getPanels().get(0).getPanelId(), "entries follow sortOrder");

        DashboardPanelDataItem chartByDefault = findItem(byDefault, "panel-e2e-chart");
        assertTrue(chartByDefault.isSuccess() && chartByDefault.isHasDataset());
        assertEquals(1, chartByDefault.getRows().size(), "default region=all matches 1 row");

        // ===== 2. 传 region=north → 筛选前后结果差异 + 多面板同步变化 =====
        DashboardDataResult north = dashboardBiz.getDashboardData(
                dashboard.getDashboardId(), Map.of("region", "north"), null, context);

        DashboardPanelDataItem chartNorth = findItem(north, "panel-e2e-chart");
        DashboardPanelDataItem tableNorth = findItem(north, "panel-e2e-table");
        assertEquals(2, chartNorth.getRows().size(), "chart north has 2 rows");
        assertEquals(2, tableNorth.getRows().size(), "table north has 2 rows (filter propagated to all panels)");
        assertTrue(chartByDefault.getRows().size() != chartNorth.getRows().size(),
                "changing filter value changes batch results");

        // ===== 3. 无数据集面板条目存在（text/iframe → hasDataset=false，非排除）=====
        DashboardPanelDataItem text = findItem(north, "panel-e2e-text");
        assertTrue(text.isSuccess());
        assertTrue(!text.isHasDataset(), "text panel entry hasDataset=false");
        assertEquals("text", text.getComponentType());
        DashboardPanelDataItem iframe = findItem(north, "panel-e2e-iframe");
        assertTrue(iframe.isSuccess() && !iframe.isHasDataset());
        assertEquals("iframe", iframe.getComponentType());

        // ===== 4. 面板级错误隔离：破坏 table 面板的 datasetRef → 失败条目 + 其余正常 =====
        tablePanel.setDatasetRefId("ref-broken-e2e");
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(tablePanel);

        DashboardDataResult withBroken = dashboardBiz.getDashboardData(
                dashboard.getDashboardId(), Map.of("region", "north"), null, context);

        assertEquals(4, withBroken.getPanels().size(), "broken panel still has an entry");

        DashboardPanelDataItem broken = findItem(withBroken, "panel-e2e-table");
        assertTrue(!broken.isSuccess(), "broken panel entry success=false");
        assertEquals("nop.err.datav.dataset-ref-not-found", broken.getErrorCode(),
                "failure entry carries explicit error code");
        assertNotNull(broken.getErrorMessage());

        // 其余面板不受拖累：chart 正常返回 north 数据，无数据集面板保持 hasDataset=false 条目
        DashboardPanelDataItem chartAfter = findItem(withBroken, "panel-e2e-chart");
        assertTrue(chartAfter.isSuccess() && chartAfter.isHasDataset());
        assertEquals(2, chartAfter.getRows().size(), "healthy panels unaffected by broken sibling");
        assertTrue(findItem(withBroken, "panel-e2e-text").isSuccess());
        assertTrue(findItem(withBroken, "panel-e2e-iframe").isSuccess());
    }

    // ==================== Helpers ====================

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
