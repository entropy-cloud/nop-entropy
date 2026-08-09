package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavExportTaskBiz;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavExportTask;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.export.NopDatavExportTaskRecovery;
import io.nop.datav.service.export.NopDatavExportTaskStatus;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.orm.IOrmTemplate;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_CONCURRENCY_LIMIT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_NOT_FINISHED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_NOT_OWNER;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED;
import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据导出端到端测试（D3-3 Phase 4）。
 *
 * <p>从 createExportTask 入口到 downloadExportFile 输出文件完整链路跑通，断言：
 * 取数（PanelDataBinder.queryPanelData）真正发生、IFileStore.saveFile 落库 NopFileRecord、
 * 文件内容与 getPanelData 同源。覆盖 csv/xlsx、看板级多 sheet、限额、owner 校验、
 * 状态机（succeeded/failed/cancelled）、重启清理。</p>
 */
@NopTestProperty(name = "nop.file.store-dir", value = "target/test-file-store")
public class TestNopDatavExportE2E extends AbstractNopDatavTest {

    private static final long POLL_TIMEOUT_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 100L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavExportTaskBiz exportBiz;

    @Inject
    INopDatavPanelBiz panelBiz;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    NopDatavExportTaskRecovery recoveryBean;

    // ==================== 主线 E2E ====================

    /**
     * 全链路：发起 panel CSV 导出 → 轮询至 succeeded → 下载 → 文件内容包含期望数据。
     * 同时断言取数发生（getPanelData 同源）与 NopFileRecord 落库（IFileStore.saveFile 被调用）。
     */
    @Test
    public void testE2ePanelCsvExportCreatePollDownload() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-e2e-csv");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-e2e-csv", dashboardId, "Sales Chart");

        // 先取一份 getPanelData 作为同源校验基准
        PanelDataResult baseline = panelBiz.getPanelData(panel.getPanelId(), Map.of("region", "north"), ctx);
        assertEquals(2, baseline.getRows().size(), "baseline: north has 2 rows");

        // 发起导出
        NopDatavExportTask task = exportBiz.createExportTask(
                "panel", panel.getPanelId(), "csv", Map.of("region", "north"), ctx);
        assertNotNull(task.getTaskId());

        // 轮询至终态
        NopDatavExportTask done = pollUntilTerminal(task.getTaskId(), ctx);
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED, done.getStatus(),
                "task succeeded, errorMsg=" + done.getErrorMsg());
        assertNotNull(done.getFileRecordId(), "file record id recorded");
        assertEquals(2, done.getRowCount(), "exported row count matches baseline");

        // 断言 NopFileRecord 落库（IFileStore.saveFile 被调用）
        NopFileRecord record = daoProvider.daoFor(NopFileRecord.class).getEntityById(done.getFileRecordId());
        assertNotNull(record, "NopFileRecord persisted by IFileStore.saveFile");
        assertEquals("nopDatavExportTask", record.getBizObjName(), "file attached to export task biz obj");
        assertEquals(task.getTaskId(), record.getBizObjId(), "file bizObjId = taskId");

        // 下载并校验内容
        IResource resource = exportBiz.downloadExportFile(task.getTaskId(), ctx);
        assertNotNull(resource);
        String content = readUtf8(resource);
        assertTrue(content.toLowerCase().contains("region"), "downloaded CSV contains header");
        assertTrue(content.contains("north"), "downloaded CSV contains data");
    }

    /**
     * 看板级 xlsx 多 sheet 导出：两个 needsDataset 面板 → 2 sheet。
     */
    @Test
    public void testE2eDashboardXlsxMultiSheet() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-e2e-xlsx");
        saveChartPanelWithDataset("panel-dash-1", dashboardId, "Chart A");
        saveChartPanelWithDataset("panel-dash-2", dashboardId, "Chart B");

        NopDatavExportTask task = exportBiz.createExportTask(
                "dashboard", dashboardId, "xlsx", null, ctx);
        NopDatavExportTask done = pollUntilTerminal(task.getTaskId(), ctx);
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED, done.getStatus(),
                "dashboard export succeeded, errorMsg=" + done.getErrorMsg());

        IResource resource = exportBiz.downloadExportFile(task.getTaskId(), ctx);
        assertNotNull(resource);
        List<io.nop.ooxml.xlsx.util.ExcelSheetData> sheets =
                io.nop.ooxml.xlsx.util.ExcelHelper.readAllSheets(resource);
        assertEquals(2, sheets.size(), "2 panels -> 2 sheets");
    }

    // ==================== 限额与拒绝 ====================

    /**
     * image 格式（pdf）显式拒绝（非静默跳过）。
     */
    @Test
    public void testImageFormatExplicitlyRejected() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-img");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-img", dashboardId, "Chart");
        NopException ex = assertThrows(NopException.class, () ->
                exportBiz.createExportTask("panel", panel.getPanelId(), "pdf", null, ctx));
        assertEquals(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 看板级 csv 不支持（csv 仅单面板）→ 显式拒绝。
     */
    @Test
    public void testDashboardCsvRejected() {
        IServiceContext ctx = ownerContext("alice");
        NopException ex = assertThrows(NopException.class, () ->
                exportBiz.createExportTask("dashboard", "anyDash", "csv", null, ctx));
        assertEquals(ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 并发限额：为当前用户预置 max（默认3）条 running 任务，再发起即拒绝。
     * 直接 seed running 行确保计数确定（不依赖异步时序）。
     */
    @Test
    public void testConcurrencyLimitRejects() {
        IServiceContext ctx = ownerContext("alice");
        String alice = "alice";
        int max = io.nop.datav.service.NopDatavConfigs.CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER.get();
        assertTrue(max > 0);
        for (int i = 0; i < max; i++) {
            seedTask("running-task-" + i, alice, NopDatavExportTaskStatus.RUNNING);
        }
        setupSalesData();
        String dashboardId = setupDashboard("dash-conc");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-conc", dashboardId, "Chart");
        NopException ex = assertThrows(NopException.class, () ->
                exportBiz.createExportTask("panel", panel.getPanelId(), "csv", null, ctx));
        assertEquals(ERR_DATAV_EXPORT_CONCURRENCY_LIMIT.getErrorCode(), ex.getErrorCode());
    }

    // ==================== owner / 下载校验 ====================

    /**
     * 非 owner 下载被拒（ERR_DATAV_EXPORT_NOT_OWNER）。
     */
    @Test
    public void testNonOwnerDownloadRejected() {
        setupSalesData();
        IServiceContext alice = ownerContext("alice");
        IServiceContext bob = ownerContext("bob");
        String dashboardId = setupDashboard("dash-owner");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-owner", dashboardId, "Chart");

        NopDatavExportTask task = exportBiz.createExportTask(
                "panel", panel.getPanelId(), "csv", Map.of("region", "north"), alice);
        pollUntilTerminal(task.getTaskId(), alice);

        NopException ex = assertThrows(NopException.class, () ->
                exportBiz.downloadExportFile(task.getTaskId(), bob));
        assertEquals(ERR_DATAV_EXPORT_NOT_OWNER.getErrorCode(), ex.getErrorCode());
    }

    /**
     * 下载未完成任务被拒（ERR_DATAV_EXPORT_NOT_FINISHED）。seed 一条 running 任务直接尝试下载。
     */
    @Test
    public void testDownloadUnfinishedRejected() {
        IServiceContext ctx = ownerContext("alice");
        NopDatavExportTask task = seedTask("unfinished-task", "alice", NopDatavExportTaskStatus.RUNNING);
        NopException ex = assertThrows(NopException.class, () ->
                exportBiz.downloadExportFile(task.getTaskId(), ctx));
        assertEquals(ERR_DATAV_EXPORT_NOT_FINISHED.getErrorCode(), ex.getErrorCode());
    }

    // ==================== 状态机分支 ====================

    /**
     * failed 路径：构造一个查询失败的数据集（不存在的表），任务记 failed + errorMsg。
     */
    @Test
    public void testFailedPathRecordsErrorMsg() {
        IServiceContext ctx = ownerContext("alice");
        // dataset 指向不存在的表 → 查询失败
        String dashboardId = setupDashboard("dash-fail");
        NopDatavPanel panel = savePanelWithBadDataset("panel-fail", dashboardId, "Bad Chart");

        NopDatavExportTask task = exportBiz.createExportTask(
                "panel", panel.getPanelId(), "csv", null, ctx);
        NopDatavExportTask done = pollUntilTerminal(task.getTaskId(), ctx);
        assertTrue(done.getStatus() == NopDatavExportTaskStatus.FAILED,
                "bad dataset -> task failed, status=" + done.getStatus());
        assertNotNull(done.getErrorMsg(), "errorMsg recorded");
        assertFalse(done.getErrorMsg().isEmpty(), "errorMsg non-empty");
    }

    /**
     * cancelled 路径：seed running 任务 → cancelExportTask → 转 cancelled。
     */
    @Test
    public void testCancelRunningTaskTransitionsToCancelled() {
        IServiceContext ctx = ownerContext("alice");
        NopDatavExportTask task = seedTask("cancel-task", "alice", NopDatavExportTaskStatus.RUNNING);
        NopDatavExportTask cancelled = exportBiz.cancelExportTask(task.getTaskId(), ctx);
        assertEquals(NopDatavExportTaskStatus.CANCELLED, cancelled.getStatus());
        assertEquals(NopDatavExportTaskStatus.CANCELLED,
                daoProvider.daoFor(NopDatavExportTask.class).getEntityById(task.getTaskId()).getStatus(),
                "cancelled status persisted");
    }

    /**
     * 重启清理：seed running/pending 任务 → 触发 recovery → 全部转 failed。
     */
    @Test
    public void testRestartRecoveryMarksInterruptedAsFailed() {
        seedTask("recover-running", "alice", NopDatavExportTaskStatus.RUNNING);
        seedTask("recover-pending", "alice", NopDatavExportTaskStatus.PENDING);

        // 直接通过注入的 recovery bean 触发（init 时表未建会跳过，这里显式调用，表已存在）
        recoveryBean.recoverInterruptedTasks();

        NopDatavExportTask r1 = daoProvider.daoFor(NopDatavExportTask.class).getEntityById("recover-running");
        NopDatavExportTask r2 = daoProvider.daoFor(NopDatavExportTask.class).getEntityById("recover-pending");
        assertEquals(NopDatavExportTaskStatus.FAILED, r1.getStatus(), "running -> failed on recovery");
        assertEquals(NopDatavExportTaskStatus.FAILED, r2.getStatus(), "pending -> failed on recovery");
        assertTrue(r1.getErrorMsg().contains("restart"), "reason indicates restart");
    }

    // ==================== Helpers ====================

    private NopDatavExportTask pollUntilTerminal(String taskId, IServiceContext ctx) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            NopDatavExportTask task = exportBiz.getExportTask(taskId, ctx);
            if (NopDatavExportTaskStatus.isTerminal(task.getStatus())) {
                return task;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            }
        }
        throw new AssertionError("task " + taskId + " did not reach terminal state within "
                + POLL_TIMEOUT_MS + "ms");
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavExportTask seedTask(String taskId, String owner, int status) {
        long now = System.currentTimeMillis();
        NopDatavExportTask t = new NopDatavExportTask();
        t.setTaskId(taskId);
        t.setSourceType("panel");
        t.setSourceId("seed-panel");
        t.setFormat("csv");
        t.setStatus(status);
        t.setDelFlag((byte) 0);
        t.setVersion(0L);
        t.setCreatedBy(owner);
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy(owner);
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavExportTask.class).saveEntityDirectly(t);
        return t;
    }

    private String setupDashboard(String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .name("drop:TEST_DATAV_SALES")
                    .sql("drop table TEST_DATAV_SALES").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
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

    private NopDatavPanel saveChartPanelWithDataset(String panelId, String dashboardId, String panelName) {
        return savePanelWithDataset(panelId, dashboardId, panelId + "-ref", panelId + "-ds",
                "select REGION as region, PRODUCT as product, AMOUNT as amount "
                        + "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc",
                TYPE_CHART, panelName);
    }

    private NopDatavPanel savePanelWithBadDataset(String panelId, String dashboardId, String panelName) {
        return savePanelWithDataset(panelId, dashboardId, panelId + "-ref", panelId + "-ds",
                "select * from NONEXISTENT_TABLE_BADOBJECT where 1=1", TYPE_CHART, panelName);
    }

    private NopDatavPanel savePanelWithDataset(String panelId, String dashboardId, String refId, String dsId,
                                               String dsText, int panelType, String panelName) {
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId); ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText(dsText);
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
        ref.setCreatedBy("alice");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("alice");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelName);
        p.setDisplayName(panelName);
        p.setPanelType(panelType);
        p.setDatasetRefId(refId);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }

    private static String readUtf8(IResource resource) {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
