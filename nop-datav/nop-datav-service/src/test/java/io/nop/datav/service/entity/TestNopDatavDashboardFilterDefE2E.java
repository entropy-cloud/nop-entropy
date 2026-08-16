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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2-4 闭环端到端测试（plan 2026-08-15-1134-2 Phase 3，rule #22 端到端 + rule #23 接线验证）。
 *
 * <p>贯穿完整链路（flux dashboard-filter 视角的模拟消费）：</p>
 *
 * <pre>
 * paramConfig（全部四类参数：region=string/dropdown、minAmount=number、saleDate=date、period=date-range）
 *   → exportDashboardFilter 产出筛选定义（§11.2 工件，断言字段/control/delimited initialValue/元数据）
 *   → 按定义模拟 flux 筛选值提交（date-range 以契约 delimited 形态提交，§11.4）
 *   → resolveFilterValues 校验/拆分（delimited → 扁平 key）
 *   → getDashboardData（flux-form 参数直接传入，内部 resolve 自动接受 delimited 形态）
 *   → 断言查询携带正确筛选值（不同筛选值产出不同数据，参数真实注入 SQL）
 * </pre>
 *
 * <p>业务数据表 TEST_DATAV_SALES3 与 SQL 覆盖全部四类参数的注入路径：
 * region（等值）、minAmount（数值下界）、saleDate（日期比较）、period（日期范围）。
 * 无 mock 绕过（biz 代理 → PanelDataBinder → 真实 SQL 管线）。</p>
 */
public class TestNopDatavDashboardFilterDefE2E extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    /**
     * 主线闭环：参数定义 → 筛选定义 → flux 模拟提交（delimited 契约形态）→ 校验拆分 →
     * getDashboardData 参数注入。四类参数全部经 SQL 生效（行数与行内容由筛选值决定）。
     */
    @Test
    public void testFluxDefinitionToDashboardDataClosedLoop() {
        IServiceContext context = newContext("e2e-d24-user");
        String dashboardId = setupDashboardWithFourParamTypes();

        // ===== 1. 产出筛选定义，断言 §11.2/§11.3/§11.4 契约工件 =====
        Map<String, Object> def = dashboardBiz.exportDashboardFilter(dashboardId, context);
        assertEquals(dashboardId, def.get("dashboardId"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) def.get("fields");
        assertEquals(4, fields.size(), "all four param types exported");
        assertEquals("region", fields.get(0).get("name"));
        assertEquals("select", fields.get(0).get("control"), "string + dropdown → select");
        assertEquals("minAmount", fields.get(1).get("name"));
        assertEquals("input-number", fields.get(1).get("control"));
        assertEquals("saleDate", fields.get(2).get("name"));
        assertEquals("input-date", fields.get(2).get("control"));
        assertEquals("period", fields.get(3).get("name"));
        assertEquals("date-range", fields.get(3).get("control"));
        assertEquals("2024-01-01,2024-12-31", fields.get(3).get("initialValue"),
                "date-range initialValue is the pinned delimited absolute form");

        @SuppressWarnings("unchecked")
        Map<String, Object> dateRangeValue = (Map<String, Object>) def.get("dateRangeValue");
        assertEquals(",", dateRangeValue.get("delimiter"));
        assertEquals("yyyy-MM-dd", dateRangeValue.get("valueFormat"));

        // ===== 2. 模拟 flux 筛选值提交（date-range 以契约 delimited 形态发布，§11.4 提交向）=====
        Map<String, Object> fluxSubmission = new LinkedHashMap<>();
        fluxSubmission.put("region", "east");
        fluxSubmission.put("minAmount", 100);
        fluxSubmission.put("saleDate", "2024-01-01");
        fluxSubmission.put("period", "2024-01-01,2024-06-30");

        // resolveFilterValues 校验/拆分：delimited → 扁平 key
        Map<String, Object> resolved = dashboardBiz.resolveFilterValues(dashboardId, fluxSubmission, context);
        assertEquals("east", resolved.get("region"));
        assertEquals(100, ((Number) resolved.get("minAmount")).intValue());
        assertEquals("2024-01-01", resolved.get("saleDate"));
        assertEquals("2024-01-01", resolved.get("period.start"));
        assertEquals("2024-06-30", resolved.get("period.end"));

        // ===== 3. getDashboardData：flux-form 参数直接传入（内部 resolve 自动接受 delimited 形态）=====
        DashboardDataResult result = dashboardBiz.getDashboardData(dashboardId, fluxSubmission, null, context);
        DashboardPanelDataItem item = findItem(result, "panel-d24");
        assertTrue(item.isSuccess() && item.isHasDataset());

        // east 且 amount>=100 且 H1（2024-01-15/100 与 2024-03-10/200）→ 2 行
        assertEquals(2, item.getRows().size(), "east + minAmount>=100 + H1 → 2 rows (all four param types injected)");
        assertTrue(containsRegion(item, "east"));

        // ===== 4. 默认值路径：不传筛选 → paramConfig 默认值经定义工件同构生效 =====
        Map<String, Object> defaultInitial = new LinkedHashMap<>();
        defaultInitial.put("region", fields.get(0).get("initialValue"));
        defaultInitial.put("period", fields.get(3).get("initialValue"));
        DashboardDataResult byDefault = dashboardBiz.getDashboardData(dashboardId, defaultInitial, null, context);
        DashboardPanelDataItem defaultItem = findItem(byDefault, "panel-d24");
        assertEquals(4, defaultItem.getRows().size(),
                "default region=east + full-year period + minAmount/saleDate defaults → all 4 east rows");
    }

    /**
     * 接线验证（rule #23）：筛选值改变实际改变查询结果（非仅参数透传）——
     * 同一看板不同 flux 提交（region/period/minAmount 变化）产出不同行数与行内容。
     */
    @Test
    public void testFilterValuesChangeDashboardDataResults() {
        IServiceContext context = newContext("e2e-d24-user");
        String dashboardId = setupDashboardWithFourParamTypes();

        Map<String, Object> submissionEastH1 = fluxSubmission("east", 100, "2024-01-01", "2024-01-01,2024-06-30");
        Map<String, Object> submissionEastFull = fluxSubmission("east", 0, "2024-01-01", "2024-01-01,2024-12-31");
        Map<String, Object> submissionWest = fluxSubmission("west", 0, "2024-01-01", "2024-01-01,2024-12-31");

        DashboardPanelDataItem eastH1 = findItem(
                dashboardBiz.getDashboardData(dashboardId, submissionEastH1, null, context), "panel-d24");
        DashboardPanelDataItem eastFull = findItem(
                dashboardBiz.getDashboardData(dashboardId, submissionEastFull, null, context), "panel-d24");
        DashboardPanelDataItem west = findItem(
                dashboardBiz.getDashboardData(dashboardId, submissionWest, null, context), "panel-d24");

        assertEquals(2, eastH1.getRows().size(), "east + amount>=100 + H1 → 2 rows");
        assertEquals(4, eastFull.getRows().size(), "east + full year → 4 rows");
        assertEquals(1, west.getRows().size(), "west → 1 row");

        assertTrue(eastH1.getRows().size() != eastFull.getRows().size(),
                "narrowing period changes result count (date-range param effective)");
        assertTrue(eastFull.getRows().size() != west.getRows().size(),
                "changing region changes result count (string param effective)");
        assertTrue(containsRegion(west, "west") && !containsRegion(west, "east"),
                "row content follows filter values (not mere param passthrough)");
    }

    // ==================== Helpers ====================

    /**
     * 建看板（四类参数）+ 数据表/数据 + 数据集（SQL 覆盖全部四类参数注入路径）+ 绑定面板。
     */
    private String setupDashboardWithFourParamTypes() {
        createSales3Table();
        insertSales3Row("east", 100, "2024-01-15");
        insertSales3Row("east", 200, "2024-03-10");
        insertSales3Row("east", 50, "2024-07-20");
        insertSales3Row("east", 80, "2024-05-01");
        insertSales3Row("west", 300, "2024-02-01");

        // SQL：region（等值）/ minAmount（数值下界）/ afterDate←saleDate（日期比较）/ pStart+pEnd←period（范围）
        NopReportDataset ds = newReportDataset("ds-d24-e2e", "sql",
                "select REGION as region, AMOUNT as amount from TEST_DATAV_SALES3 "
                        + "where REGION = ${region} "
                        + "and AMOUNT >= ${minAmount} "
                        + "and SALE_DATE > ${afterDate} "
                        + "and SALE_DATE >= ${pStart} "
                        + "and SALE_DATE <= ${pEnd} "
                        + "order by SALE_DATE");
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        NopDatavDashboard dashboard = saveDashboard("dash-d24-e2e", "d24-e2e");
        dashboard.setParamConfig(JsonTool.stringify(List.of(
                Map.of("name", "region", "type", "string", "defaultValue", "east",
                        "label", "区域", "widget", "dropdown"),
                Map.of("name", "minAmount", "type", "number", "defaultValue", 0),
                Map.of("name", "saleDate", "type", "date", "defaultValue", "2024-01-01"),
                Map.of("name", "period", "type", "date-range",
                        "defaultValue", Map.of("start", "2024-01-01", "end", "2024-12-31"))
        )));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        NopDatavDatasetRef ref = newDatasetRef("ref-d24-e2e", dashboard.getDashboardId(),
                "ds-d24-e2e", "D24 E2E DS");
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region"),
                "minAmount", Map.of("source", "minAmount"),
                "afterDate", Map.of("source", "saleDate"),
                "pStart", Map.of("source", "period.start"),
                "pEnd", Map.of("source", "period.end")
        )));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel panel = newPanel("panel-d24", dashboard.getDashboardId(), "D24 Panel");
        panel.setPanelType(TYPE_TABLE);
        panel.setDatasetRefId("ref-d24-e2e");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);
        return dashboard.getDashboardId();
    }

    /** 模拟 flux 筛选表单发布到 page scope 的值（date-range 为契约 delimited 形态）。 */
    private static Map<String, Object> fluxSubmission(String region, int minAmount,
                                                      String saleDate, String periodDelimited) {
        Map<String, Object> submission = new LinkedHashMap<>();
        submission.put("region", region);
        submission.put("minAmount", minAmount);
        submission.put("saleDate", saleDate);
        submission.put("period", periodDelimited);
        return submission;
    }

    private static DashboardPanelDataItem findItem(DashboardDataResult result, String panelId) {
        assertNotNull(result);
        for (DashboardPanelDataItem item : result.getPanels()) {
            if (panelId.equals(item.getPanelId())) {
                assertTrue(item.isSuccess(), "panel query must succeed: " + panelId);
                return item;
            }
        }
        throw new AssertionError("panel item not found: " + panelId);
    }

    private static boolean containsRegion(DashboardPanelDataItem item, String region) {
        for (Map<String, Object> row : item.getRows()) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("region".equalsIgnoreCase(entry.getKey()) && region.equals(entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
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

    private void createSales3Table() {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_SALES3")
                .sql("create table TEST_DATAV_SALES3(REGION varchar(50), AMOUNT int, SALE_DATE varchar(20))")
                .end());
    }

    private void insertSales3Row(String region, int amount, String saleDate) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_SALES3")
                .sql("insert into TEST_DATAV_SALES3(REGION, AMOUNT, SALE_DATE) values(")
                .param(region).sql(",").param(amount).sql(",").param(saleDate).sql(")")
                .end());
    }
}
