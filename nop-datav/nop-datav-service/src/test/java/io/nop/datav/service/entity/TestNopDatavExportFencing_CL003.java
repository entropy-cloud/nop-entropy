package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavExportTask;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.service.export.NopDatavExportTaskStatus;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-003 终态行版本 CAS fencing 红②/红④钉（plan 2526 裁定③⑤，C10 补测-3 与缺保护-4 合流）。
 *
 * <p>覆盖：①scanner 假阳性 FAILED 标记后，worker（真实执行体经 D4 seam 暂停）继续完成导出并
 * 尝试写 SUCCEEDED——条件更新 0 行冲突必须可观测（fencedConflictCount 递增），scanner FAILED
 * 保持（粘滞语义）、stuck 原因不被覆盖；②0 行放弃分支的已落盘 fileId 必须被回收
 * （R1 Major-1：NopFileRecord 无残留行——文件守恒断言）；③face b：已 FAILED 的积压交付行
 * 经执行体加载后必须短路（不复活为 RUNNING/SUCCEEDED）。</p>
 */
public class TestNopDatavExportFencing_CL003 extends AbstractNopDatavTest {

    private static final long POLL_TIMEOUT_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 50L;
    private static final long MINUTE_MS = 60_000L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    io.nop.datav.biz.INopDatavExportTaskBiz exportBiz;

    /** 具体类注入：访问 package-private seam（fencedConflictCount/executionStartHook）。 */
    @Inject
    NopDatavExportTaskBizModel exportBizModel;

    // ==================== 红②+红④：scanner 标记粘滞 + 0 行放弃分支文件回收 ====================

    /**
     * worker 在 RUNNING 窗口被 scanner fenced 标 FAILED（假阳性粘滞）→ 释放执行体 → worker 完成
     * 导出落盘后写 SUCCEEDED → 0 行冲突（可观测）→ 放弃 + 回收 fileId。
     * 断言：终态保持 FAILED + stuck 原因保留 + NopFileRecord 零残留（文件守恒）。
     */
    @Test
    public void testScannerFailedMarkerStickyAndWorkerConflictReclaimsFile() throws Exception {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-fence-cl003");
        saveChartPanelWithDataset("panel-fence-a", dashboardId, "Chart A");

        // D4 seam：执行体进入 RUNNING 后阻塞，测试窗口内模拟 scanner 标记
        CountDownLatch enteredRunning = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        exportBizModel.setExecutionStartHookForTest(() -> {
            enteredRunning.countDown();
            try {
                if (!releaseExecution.await(20, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            NopDatavExportTask task = exportBiz.createExportTask(
                    "dashboard", dashboardId, "xlsx", null, ctx);
            assertNotNull(task.getTaskId());
            String taskId = task.getTaskId();

            assertTrue(enteredRunning.await(20, TimeUnit.SECONDS),
                    "worker must reach RUNNING checkpoint");
            assertEquals(NopDatavExportTaskStatus.RUNNING, currentTask(taskId).getStatus(),
                    "task RUNNING at window start");

            // 模拟 stuck scanner：条件更新标 FAILED（fenced，fresh 加载版本推进）
            NopDatavExportTask scanned = daoProvider.daoFor(NopDatavExportTask.class).getEntityById(taskId);
            scanned.setStatus(NopDatavExportTaskStatus.FAILED);
            scanned.setErrorMsg("stuck beyond timeout threshold (60m)");
            scanned.setUpdatedBy("system");
            scanned.setUpdateTime(new Timestamp(System.currentTimeMillis()));
            scanned.orm_disableVersionCheckError(true);
            daoProvider.daoFor(NopDatavExportTask.class).updateEntityDirectly(scanned);
            assertEquals(NopDatavExportTaskStatus.FAILED, currentTask(taskId).getStatus(),
                    "scanner marker in place before worker resumes");

            int conflictsBefore = exportBizModel.getFencedConflictCountForTest();
            releaseExecution.countDown();

            // 确定性完成信号：worker 的 SUCCEEDED fenced 写 0 行冲突（接线验证 rule 23）
            awaitFencedConflict(conflictsBefore);

            // 终态保持：scanner FAILED 粘滞（不被 worker 覆盖）
            NopDatavExportTask done = currentTask(taskId);
            assertEquals(NopDatavExportTaskStatus.FAILED, done.getStatus(),
                    "scanner FAILED must stay sticky (worker 0-row abandoned)");
            assertTrue(done.getErrorMsg() != null
                            && done.getErrorMsg().contains("stuck beyond timeout threshold"),
                    "stuck reason preserved, got: " + done.getErrorMsg());
            assertNull(done.getFileRecordId(), "fenced-abandoned worker must not link fileRecordId");

            // 红④文件守恒：0 行放弃分支 fileId 已回收——NopFileRecord 零残留行
            assertEquals(0L, fileRowCountForTask(taskId),
                    "0-row abandon branch must reclaim the saved export file (no NopFileRecord residue)");
        } finally {
            exportBizModel.setExecutionStartHookForTest(null);
        }
    }

    // ==================== 红② face b：积压 FAILED 交付不复活 ====================

    /**
     * 积压交付已被 scanner 标 FAILED → 执行体（runDeliveryInSessionForTest 同步驱动既有行）
     * 加载后终态短路：不写 RUNNING、不取数、不写 SUCCEEDED——复活窗口闭合。
     */
    @Test
    public void testBackloggedFailedDeliveryNotResurrected() {
        long now = System.currentTimeMillis();
        String deliveryId = "delivery-faceb-cl003";
        String reportTaskId = "task-faceb-cl003";
        NopDatavReportDelivery d = new NopDatavReportDelivery();
        d.setDeliveryId(deliveryId);
        d.setReportTaskId(reportTaskId);
        d.setStatus(NopDatavReportDeliveryStatus.FAILED);
        d.setErrorMsg("stuck beyond timeout threshold (60m)");
        d.setTriggeredBy("schedule");
        d.setStartTime(new Timestamp(now - 90 * MINUTE_MS));
        d.setDelFlag((byte) 0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now - 90 * MINUTE_MS));
        d.setUpdatedBy("system");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(d);

        reportDeliveryExecutor.runDeliveryInSessionForTest(reportTaskId, deliveryId, "schedule", now);

        NopDatavReportDelivery after = daoProvider.daoFor(NopDatavReportDelivery.class).getEntityById(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, after.getStatus(),
                "backlogged FAILED delivery must NOT be resurrected (terminal short-circuit)");
        assertTrue(after.getErrorMsg() != null && after.getErrorMsg().contains("stuck beyond timeout"),
                "scanner reason preserved on short-circuited delivery");
    }

    @Inject
    io.nop.datav.service.report.ReportDeliveryExecutor reportDeliveryExecutor;

    // ==================== Helpers ====================

    private NopDatavExportTask currentTask(String taskId) {
        return daoProvider.daoFor(NopDatavExportTask.class).getEntityById(taskId);
    }

    private long fileRowCountForTask(String taskId) {
        var q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq("bizObjId", taskId));
        return daoProvider.daoFor(NopFileRecord.class).countByQuery(q);
    }

    private void awaitFencedConflict(int before) throws InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (exportBizModel.getFencedConflictCountForTest() <= before) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("worker fenced conflict not observed within "
                        + POLL_TIMEOUT_MS + "ms (0-row abandon branch not reached)");
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
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
                    .name("drop:TEST_DATAV_SALES").sql("drop table TEST_DATAV_SALES").end());
        } catch (Exception ignored) {
            // 表不存在（首跑）时 drop 失败属预期——建表语句紧随其后，忽略即语义
        }
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
    }

    private void insertSalesRow(String region, String product, String amount) {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private void insertSalesRow(String region, String product, int amount) {
        insertSalesRow(region, product, String.valueOf(amount));
    }

    private void saveChartPanelWithDataset(String panelId, String dashboardId, String panelName) {
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(panelId + "-ds");
        ds.setDsName(panelId + "-ds");
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES order by AMOUNT desc");
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
        ref.setDatasetRefId(panelId + "-ref");
        ref.setDashboardId(dashboardId);
        ref.setRefDatasetId(panelId + "-ds");
        ref.setRefDatasetName(panelName);
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "north"))));
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
        p.setPanelType(TYPE_CHART);
        p.setDatasetRefId(panelId + "-ref");
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
    }
}
