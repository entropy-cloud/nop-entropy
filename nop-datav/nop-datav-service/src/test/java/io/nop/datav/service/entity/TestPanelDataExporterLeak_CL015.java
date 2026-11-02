package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.export.PanelDataExporter;
import io.nop.report.dao.entity.NopReportDataset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-015 导出器临时文件失败路径泄漏红⑪钉（plan 2526 裁定⑥，C10 泄漏检测钩子首例）。
 *
 * <p>异常注入面：counter 型 cancelChecker——「取数后、写出前」主检查点（:127）放行，行写出循环内
 * 首处 checkCancelled 命中抛 NopException——该异常发生在 {@code getTempResource} 创建之后
 * （BOM 已写出）：旧实现 writeCsv catch(IOException) 不覆盖 NopException → 只关流不删资源（泄漏）；
 * 修复后 catch(Exception) → resource.delete() → 原样 rethrow。</p>
 *
 * <p>@AfterEach 泄漏钩子：temp 目录 {@code datav-export*} 零残留（模块首例，C10 :61-63 缺口兑现）。
 * xlsx 路径的 writeXlsx(workbook) 同 catch 面同修（结构覆盖——其失败注入需 saveExcel 内部故障，
 * 公共 API 不可达，登记于 red-green 总册）。</p>
 */
public class TestPanelDataExporterLeak_CL015 extends AbstractNopDatavTest {

    @jakarta.inject.Inject
    IDaoProvider daoProvider;

    private File tempDir;
    private List<String> filesBefore;

    @BeforeEach
    public void snapshotTempDir() {
        IResource probe = ResourceHelper.getTempResource("datav-export");
        File file = ((FileResource) probe).toFile();
        File dir = file.getParentFile();
        while (dir != null && !"datav-export".equals(dir.getName())) {
            dir = dir.getParentFile();
        }
        tempDir = dir;
        filesBefore = listDatavExportFiles();
    }

    @AfterEach
    public void assertNoLeak() {
        List<String> after = listDatavExportFiles();
        assertEquals(filesBefore, after,
                "failed export must not leave datav-export* residue in temp dir (CL-015)");
    }

    private List<String> listDatavExportFiles() {
        List<String> names = new ArrayList<>();
        if (tempDir == null || !tempDir.exists()) {
            return names;
        }
        try (Stream<java.nio.file.Path> walk = Files.walk(tempDir.toPath())) {
            walk.filter(Files::isRegularFile)
                    .forEach(path -> names.add(tempDir.toPath().relativize(path).toString()));
        } catch (Exception e) {
            throw new IllegalStateException("temp dir walk failed", e);
        }
        return names;
    }

    /**
     * csv 路径：主检查点放行（第 1 次调用 false）→ writeCsv 创建临时资源并写出 BOM → 行循环首处
     * cancel 命中（第 2 次调用 true）→ NopException → 临时资源必须已删除（旧实现残留 = 红）。
     */
    @Test
    public void testWriteCsvInLoopCancelLeavesNoTempResidue() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-leak-csv");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-leak-csv", dashboardId,
                "ref-leak-csv", "ds-leak-csv", "Leak Chart");

        AtomicInteger cancelCalls = new AtomicInteger();
        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        NopException ex = assertThrows(NopException.class, () -> exporter.exportPanel(panel,
                Map.of("region", "north"), PanelDataExporter.FORMAT_CSV, 1000,
                () -> cancelCalls.incrementAndGet() >= 2));
        assertTrue(ex.getErrorCode() != null, "cancellation propagated as NopException");
        assertTrue(cancelCalls.get() >= 2, "cancel must trip inside the write loop "
                + "(after pre-write checkpoint), calls=" + cancelCalls.get());
    }

    /**
     * xlsx 路径（sheet 构建循环内 cancel）：临时资源尚未创建即抛出——零残留不变式同样成立
     * （与 @AfterEach 钩子共同覆盖 xlsx 面的守恒断言）。
     */
    @Test
    public void testWriteXlsxInLoopCancelLeavesNoTempResidue() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-leak-xlsx");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-leak-xlsx", dashboardId,
                "ref-leak-xlsx", "ds-leak-xlsx", "Leak Chart X");

        AtomicInteger cancelCalls = new AtomicInteger();
        PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
        assertNotNull(assertThrows(NopException.class, () -> exporter.exportPanel(panel,
                Map.of("region", "north"), PanelDataExporter.FORMAT_XLSX, 1000,
                () -> cancelCalls.incrementAndGet() >= 2)));
        assertTrue(cancelCalls.get() >= 2, "cancel must trip inside sheet building, calls=" + cancelCalls.get());
    }

    // ==================== Helpers（沿 TestPanelDataExporter 既有 harness） ====================

    private void setupSalesData() {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES_LEAK")
                .sql("create table TEST_DATAV_SALES_LEAK(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES_LEAK")
                .sql("insert into TEST_DATAV_SALES_LEAK(REGION, PRODUCT, AMOUNT) values(")
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
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES_LEAK where REGION = ${region} order by AMOUNT desc");
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
        ref.setParamMapping(io.nop.core.lang.json.JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "south"))));
        ref.setVersion(0L);
        ref.setCreatedBy("test");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("test");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelName);
        p.setDisplayName(panelName);
        p.setPanelType(TYPE_CHART);
        p.setDatasetRefId(refId);
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
