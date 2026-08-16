package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.export.PanelDataExporter;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-3 回归测试（plan 2026-08-15-2146-2 Phase 4）：exportDashboard 面板数前置上限。
 *
 * <p>缺陷机制：修复前 {@code PanelDataExporter.exportDashboard} 遍历全部 needsDataset 面板无数量
 * 上限（每面板 1 条 SQL + maxRows=100000 行取数 + 全量内存 workbook）——对照 getDashboardData
 * （CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS，默认 50）与 saveDashboardLayout（50）均有闸，导出路径独缺。</p>
 *
 * <p><b>修复后语义（裁定：复用 CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS，同为性能界——防单请求放大为
 * 海量 SQL）</b>：超限抛 {@code ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED}（结构化失败，非静默截断），
 * 校验先于任何面板取数；边界值（=上限）放行。</p>
 *
 * <p>mutate-fail：移除上限校验 → 超限用例返回文件而非抛错 → 断言失败。</p>
 */
public class TestPanelDataExporterPanelCap extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    /**
     * 超限（51 > 50）：结构化错误（显式失败，非静默截断）。
     */
    @Test
    public void testDashboardExportOverPanelCapThrowsStructuredError() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ar3-over");
        seedPanelsSharingDataset(dashboardId, "ar3over", CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get() + 1);

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        NopException ex = assertThrows(NopException.class,
                () -> exporter.exportDashboard(dashboardId, Map.of("region", "north"), 1000));
        assertEquals(ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED.getErrorCode(), ex.getErrorCode(),
                "over-cap dashboard export fails explicitly (no silent truncation)");
        assertEquals(CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get() + 1,
                ex.getParam("panelCount"), "panelCount param carried");
        assertEquals(CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get(),
                ex.getParam("maxPanels"), "maxPanels param carried");
    }

    /**
     * 边界值（= 50）：放行（不误伤），产出 50 sheet。
     */
    @Test
    public void testDashboardExportAtPanelCapPasses() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ar3-boundary");
        seedPanelsSharingDataset(dashboardId, "ar3bnd", CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS.get());

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        PanelDataExporter.ExportFile file = exporter.exportDashboard(dashboardId, Map.of("region", "north"), 1000);
        assertNotNull(file, "boundary (= cap) export passes");
        assertEquals(PanelDataExporter.MIME_XLSX, file.getMimeType());
        assertTrue(file.getRowCount() > 0, "rows exported from cap-count panels");
    }

    // ==================== Helpers ====================

    /**
     * 建一个 dataset + 一个 datasetRef，然后 N 个 chart 面板共享该 ref（导出器按面板遍历取数，
     * 共享 ref 不影响面板数计数，且大幅降低种子成本）。tag 为短标识（列宽 32 上限内）。
     */
    private void seedPanelsSharingDataset(String dashboardId, String tag, int panelCount) {
        String dsId = tag + "-ds";
        String refId = tag + "-ref";

        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, AMOUNT as amount "
                + "from TEST_DATAV_SALES where REGION = ${region}");
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(System.currentTimeMillis()));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        long now = System.currentTimeMillis();
        NopDatavDatasetRef ref = new NopDatavDatasetRef();
        ref.setDatasetRefId(refId);
        ref.setDashboardId(dashboardId);
        ref.setRefDatasetId(dsId);
        ref.setRefDatasetName("shared");
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "south"))));
        ref.setVersion(0L);
        ref.setCreatedBy("test");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("test");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        for (int i = 0; i < panelCount; i++) {
            NopDatavPanel p = new NopDatavPanel();
            p.setPanelId(dashboardId.substring(0,8) + "-panel-" + i);
            p.setDashboardId(dashboardId);
            p.setPanelName("Panel " + i);
            p.setDisplayName("Panel " + i);
            p.setPanelType(TYPE_CHART);
            p.setDatasetRefId(refId);
            p.setSortOrder(i);
            p.setVersion(0L);
            p.setCreatedBy("test");
            p.setCreateTime(new Timestamp(now));
            p.setUpdatedBy("test");
            p.setUpdateTime(new Timestamp(now));
            daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        }
    }

    private String setupDashboard(String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(SQL.begin().name("drop:TEST_DATAV_SALES")
                    .sql("drop table TEST_DATAV_SALES").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
        jdbcTemplate.executeUpdate(SQL.begin().name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin().name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }
}
