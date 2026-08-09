package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.export.PanelDataExporter;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.ooxml.xlsx.util.ExcelSheetData;
import io.nop.report.dao.entity.NopReportDataset;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TABLE;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PanelDataExporter} 单元测试（D3-3 Phase 4）。
 *
 * <p>覆盖：CSV/xlsx 两种格式的写出与读回校验、行数限额（防 OOM）、看板级多面板遍历、
 * 无数据集面板跳过、看板无可导出面板显式拒绝。复用 D1 数据绑定管线取数。</p>
 */
public class TestPanelDataExporter extends AbstractNopDatavTest {

    @jakarta.inject.Inject
    IDaoProvider daoProvider;

    /**
     * CSV 写出：BOM + 表头 + 数据行；读回断言列与行数与 getPanelData 同源。
     */
    @Test
    public void testExportPanelCsvContainsColumnsAndRows() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-csv");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-csv", dashboardId, "ref-csv", "ds-csv", "Sales Chart");

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        PanelDataExporter.ExportFile file = exporter.exportPanel(panel, Map.of("region", "north"),
                PanelDataExporter.FORMAT_CSV, 1000);

        assertEquals("Sales Chart.csv", file.getFileName());
        assertEquals(PanelDataExporter.MIME_CSV, file.getMimeType());
        assertEquals(2, file.getRowCount(), "north region has 2 rows");

        String content = readUtf8(file.getResource());
        assertTrue(content.startsWith("\uFEFF"), "CSV starts with UTF-8 BOM for Excel compatibility");
        assertTrue(content.toLowerCase().contains("region"),
                "header column present: " + content);
        assertTrue(content.contains("north"), "data row present");
    }

    /**
     * xlsx 写出：表头 + 数据行；读回断言列与行数。
     */
    @Test
    public void testExportPanelXlsxContainsColumnsAndRows() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-xlsx");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-xlsx", dashboardId, "ref-xlsx", "ds-xlsx", "Sales Chart");

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        PanelDataExporter.ExportFile file = exporter.exportPanel(panel, Map.of("region", "north"),
                PanelDataExporter.FORMAT_XLSX, 1000);

        assertEquals("Sales Chart.xlsx", file.getFileName());
        assertEquals(PanelDataExporter.MIME_XLSX, file.getMimeType());
        assertEquals(2, file.getRowCount(), "north region has 2 rows");

        List<ExcelSheetData> sheets = ExcelHelper.readAllSheets(file.getResource());
        assertEquals(1, sheets.size(), "single panel -> single sheet");
        ExcelSheetData sheet = sheets.get(0);
        assertFalse(sheet.getHeaders().isEmpty(), "headers written");
        assertTrue(sheet.getHeaders().stream().anyMatch(h -> "region".equalsIgnoreCase(String.valueOf(h))),
                "region column in headers");
        assertEquals(2, sheet.getData().size(), "2 data rows");
    }

    /**
     * 行数限额防 OOM：取数阶段 LIMIT 探测 maxRows+1，超出抛 ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED。
     */
    @Test
    public void testRowLimitExceededThrowsAtQueryStage() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-limit");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-limit", dashboardId, "ref-limit", "ds-limit", "Chart");

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        // north 返回 2 行，maxRows=1 → 取数探测 maxRows+1=2，2>1 触发限额
        NopException ex = assertThrows(NopException.class, () ->
                exporter.exportPanel(panel, Map.of("region", "north"), PanelDataExporter.FORMAT_CSV, 1));
        assertEquals(ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 无数据集面板（text）：导出返回 hasDataset=false 的空结果（不报错，文件含 0 行）。
     */
    @Test
    public void testNoDatasetPanelExportsEmptyWithoutError() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-text");
        NopDatavPanel panel = savePanel("panel-text", dashboardId, "Text Panel");
        panel.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(panel);

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        PanelDataExporter.ExportFile file = exporter.exportPanel(panel, null,
                PanelDataExporter.FORMAT_CSV, 1000);
        assertEquals(0, file.getRowCount(), "no-dataset panel exports 0 rows");
    }

    /**
     * 看板级多面板导出：遍历 needsDataset 面板，多 sheet xlsx。
     */
    @Test
    public void testDashboardExportProducesMultiSheetXlsx() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-multi");
        saveChartPanelWithDataset("panel-multi-1", dashboardId, "ref-multi-1", "ds-multi-1", "Chart A");
        saveTablePanelWithDataset("panel-multi-2", dashboardId, "ref-multi-2", "ds-multi-2", "Table B");
        NopDatavPanel text = savePanel("panel-multi-text", dashboardId, "Text");
        text.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(text);

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        PanelDataExporter.ExportFile file = exporter.exportDashboard(dashboardId, null, 1000);
        assertEquals(PanelDataExporter.MIME_XLSX, file.getMimeType());

        List<ExcelSheetData> sheets = ExcelHelper.readAllSheets(file.getResource());
        assertEquals(2, sheets.size(), "2 needsDataset panels -> 2 sheets (text panel skipped)");
    }

    /**
     * 看板无 needsDataset 面板时显式拒绝（非静默空文件）。
     */
    @Test
    public void testDashboardWithNoExportablePanelsThrows() {
        String dashboardId = setupDashboard("dash-empty");
        NopDatavPanel text = savePanel("panel-empty-text", dashboardId, "Only Text");
        text.setPanelType(TYPE_TEXT);
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(text);

        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        NopException ex = assertThrows(NopException.class, () ->
                exporter.exportDashboard(dashboardId, null, 1000));
        assertEquals(ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS.getErrorCode(), ex.getErrorCode());
    }

    // ==================== Helpers ====================

    private static String readUtf8(IResource resource) {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    private void setupSalesData() {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
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

    private NopDatavPanel saveChartPanelWithDataset(String panelId, String dashboardId, String refId,
                                                    String dsId, String panelName) {
        return savePanelWithDataset(panelId, dashboardId, refId, dsId, TYPE_CHART, panelName);
    }

    private NopDatavPanel saveTablePanelWithDataset(String panelId, String dashboardId, String refId,
                                                    String dsId, String panelName) {
        return savePanelWithDataset(panelId, dashboardId, refId, dsId, TYPE_TABLE, panelName);
    }

    private NopDatavPanel savePanelWithDataset(String panelId, String dashboardId, String refId, String dsId,
                                               int panelType, String panelName) {
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId); ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc");
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
        ref.setRefDatasetName(panelName);
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "south"))));
        ref.setVersion(0L);
        ref.setCreatedBy("test");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("test");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel panel = savePanel(panelId, dashboardId, panelName);
        panel.setPanelType(panelType);
        panel.setDatasetRefId(refId);
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(panel);
        return panel;
    }

    private NopDatavPanel savePanel(String id, String dashboardId, String name) {
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
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }
}
