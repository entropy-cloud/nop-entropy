package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DATASET_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DATASET_REF_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNSUPPORTED_DATASET_TYPE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_IFRAME;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面板数据查询测试。覆盖 Phase 2 数据绑定管线 + Phase 3 刷新机制的基础场景。
 *
 * <p>Phase 4 端到端测试见 {@code TestNopDatavPanelDataE2E}。</p>
 */
public class TestNopDatavPanelBizModel extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavPanelBiz panelBiz;

    // ==================== Normal path: getPanelData with dataset ====================

    @Test
    public void testGetPanelDataReturnsStructuredResultForChartPanel() {
        // 1. 建业务数据表
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        // 2. 建 NopReportDataset（dsType=sql）
        NopReportDataset ds = newReportDataset("ds-chart-sales", "sql",
                "select REGION as region, PRODUCT as product, AMOUNT as amount from TEST_DATAV_SALES order by AMOUNT desc");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        // 3. 建 Dashboard + DatasetRef + Panel
        NopDatavDashboard dashboard = saveDashboard("dash-panel-chart", "panel-chart");
        NopDatavDatasetRef ref = newDatasetRef("ref-chart", dashboard.getDashboardId(), "ds-chart-sales", "Sales");
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);
        NopDatavPanel panel = newPanel("panel-chart-1", dashboard.getDashboardId(), "Sales Chart");
        panel.setPanelType(TYPE_CHART);
        panel.setDatasetRefId("ref-chart");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        // 4. 调用 getPanelData
        IServiceContext context = newContext("tester");
        PanelDataResult result = panelBiz.getPanelData(panel.getPanelId(), null, context);

        // 5. 断言结构化结果
        assertNotNull(result);
        assertEquals(panel.getPanelId(), result.getPanelId());
        assertTrue(result.isHasDataset());
        assertEquals("chart", result.getComponentType());

        List<String> columns = result.getColumns();
        // H2 may upper-case column names; assert via case-insensitive
        assertEquals(3, columns.size());

        List<Map<String, Object>> rows = result.getRows();
        assertEquals(3, rows.size());
        // First row (ordered by AMOUNT desc) should be the highest amount
        Map<String, Object> firstRow = rows.get(0);
        Object firstAmount = findCaseInsensitive(firstRow, "amount");
        assertNotNull(firstAmount);
        assertEquals(200, ((Number) firstAmount).intValue());
    }

    // ==================== No-dataset panel (text/iframe) ====================

    @Test
    public void testGetPanelDataReturnsNoDatasetFlagForIframePanel() {
        NopDatavDashboard dashboard = saveDashboard("dash-iframe", "iframe");
        NopDatavPanel panel = newPanel("panel-iframe", dashboard.getDashboardId(), "IFrame Panel");
        panel.setPanelType(TYPE_IFRAME);
        panel.setPanelConfig(JsonTool.stringify(Map.of(
                "content", Map.of("url", "https://example.com")
        )));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");
        PanelDataResult result = panelBiz.getPanelData(panel.getPanelId(), null, context);

        assertNotNull(result);
        assertFalse(result.isHasDataset(), "iframe should not have dataset");
        assertEquals("iframe", result.getComponentType());
        assertTrue(result.getRows().isEmpty(), "iframe rows should be empty");
        assertTrue(result.getColumns().isEmpty(), "iframe columns should be empty");
    }

    // ==================== Parameter evaluation ====================

    @Test
    public void testGetPanelDataHonorsParameterMapping() {
        createSalesTable();
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);

        NopReportDataset ds = newReportDataset("ds-param", "sql",
                "select REGION as region, sum(AMOUNT) as total from TEST_DATAV_SALES " +
                        " where REGION = ${region} group by REGION");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-param", "param-test");
        NopDatavDatasetRef ref = newDatasetRef("ref-param", dashboard.getDashboardId(),
                "ds-param", "Param DS");
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "south")
        )));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);
        NopDatavPanel panel = newPanel("panel-param", dashboard.getDashboardId(), "Param Panel");
        panel.setPanelType(TYPE_TABLE);
        panel.setDatasetRefId("ref-param");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");

        // Case A: 请求传 region=north
        PanelDataResult result = panelBiz.getPanelData(panel.getPanelId(),
                Map.of("region", "north"), context);
        assertNotNull(result);
        assertTrue(result.isHasDataset());
        assertEquals(1, result.getRows().size(), "north has 2 rows summed into 1");
        Map<String, Object> row = result.getRows().get(0);
        Object total = findCaseInsensitive(row, "total");
        assertEquals(300, ((Number) total).intValue());

        // Case B: 请求未传 region，使用 defaultValue=south
        PanelDataResult result2 = panelBiz.getPanelData(panel.getPanelId(), null, context);
        assertEquals(1, result2.getRows().size());
        Object total2 = findCaseInsensitive(result2.getRows().get(0), "total");
        assertEquals(50, ((Number) total2).intValue());
    }

    // ==================== Error paths ====================

    @Test
    public void testGetPanelDataThrowsWhenDatasetRefNotFound() {
        NopDatavDashboard dashboard = saveDashboard("dash-no-ref", "no-ref");
        NopDatavPanel panel = newPanel("panel-no-ref", dashboard.getDashboardId(), "Missing Ref");
        panel.setPanelType(TYPE_CHART);
        panel.setDatasetRefId("non-existent-ref");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");
        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.getPanelData(panel.getPanelId(), null, context));
        assertEquals(ERR_DATAV_DATASET_REF_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testGetPanelDataThrowsWhenReportDatasetNotFound() {
        NopDatavDashboard dashboard = saveDashboard("dash-no-ds", "no-ds");
        NopDatavDatasetRef ref = newDatasetRef("ref-no-ds", dashboard.getDashboardId(),
                "non-existent-dataset", "Missing DS");
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);
        NopDatavPanel panel = newPanel("panel-no-ds", dashboard.getDashboardId(), "Missing DS");
        panel.setPanelType(TYPE_CHART);
        panel.setDatasetRefId("ref-no-ds");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");
        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.getPanelData(panel.getPanelId(), null, context));
        assertEquals(ERR_DATAV_DATASET_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testGetPanelDataThrowsWhenDsTypeNotSql() {
        createSalesTable();
        NopReportDataset ds = newReportDataset("ds-not-sql", "json", "{}");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-not-sql", "not-sql");
        NopDatavDatasetRef ref = newDatasetRef("ref-not-sql", dashboard.getDashboardId(),
                "ds-not-sql", "JSON DS");
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);
        NopDatavPanel panel = newPanel("panel-not-sql", dashboard.getDashboardId(), "JSON Panel");
        panel.setPanelType(TYPE_TABLE);
        panel.setDatasetRefId("ref-not-sql");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");
        NopException ex = assertThrows(NopException.class,
                () -> panelBiz.getPanelData(panel.getPanelId(), null, context));
        assertEquals(ERR_DATAV_UNSUPPORTED_DATASET_TYPE.getErrorCode(), ex.getErrorCode());
    }

    // ==================== Refresh mechanism (Phase 3) ====================

    @Test
    public void testRefreshPanelReturnsLatestData() {
        // 初始数据：1 行
        createSalesTable();
        insertSalesRow("north", "widget", 100);

        NopReportDataset ds = newReportDataset("ds-refresh", "sql",
                "select count(*) as cnt from TEST_DATAV_SALES");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-refresh", "refresh");
        NopDatavDatasetRef ref = newDatasetRef("ref-refresh", dashboard.getDashboardId(),
                "ds-refresh", "Refresh DS");
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);
        NopDatavPanel panel = newPanel("panel-refresh", dashboard.getDashboardId(), "Refresh Panel");
        panel.setPanelType(TYPE_TABLE);
        panel.setDatasetRefId("ref-refresh");
        // 配置刷新策略
        panel.setPanelConfig(JsonTool.stringify(Map.of(
                "refresh", Map.of("enabled", true, "intervalSeconds", 30)
        )));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");

        // 首次查询：cnt = 1
        PanelDataResult first = panelBiz.refreshPanel(panel.getPanelId(), context);
        assertNotNull(first);
        assertTrue(first.isHasDataset());
        assertEquals(1, first.getRows().size());
        Object firstCnt = findCaseInsensitive(first.getRows().get(0), "cnt");
        assertEquals(1, ((Number) firstCnt).intValue());

        // 插入新数据后再次刷新
        insertSalesRow("north", "gadget", 200);
        PanelDataResult refreshed = panelBiz.refreshPanel(panel.getPanelId(), context);
        assertNotNull(refreshed);
        Object refreshedCnt = findCaseInsensitive(refreshed.getRows().get(0), "cnt");
        assertEquals(2, ((Number) refreshedCnt).intValue(),
                "refreshPanel should return latest data via re-running the data binding pipeline");
    }

    @Test
    public void testRefreshPanelForNoDatasetPanelReturnsHasDatasetFalse() {
        NopDatavDashboard dashboard = saveDashboard("dash-refresh-text", "refresh-text");
        NopDatavPanel panel = newPanel("panel-refresh-text", dashboard.getDashboardId(), "Text Panel");
        panel.setPanelType(TYPE_IFRAME);
        panel.setPanelConfig(JsonTool.stringify(Map.of("content", Map.of("url", "https://x"))));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        IServiceContext context = newContext("tester");
        PanelDataResult result = panelBiz.refreshPanel(panel.getPanelId(), context);
        assertNotNull(result);
        assertFalse(result.isHasDataset(), "iframe refresh should return hasDataset=false");
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
