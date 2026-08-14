package io.nop.datav.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.datav.dao.entity.NopDatavExportTask;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.service.export.NopDatavExportTaskRecovery;
import io.nop.datav.service.export.NopDatavExportTaskStatus;
import io.nop.datav.service.recovery.NopDatavStuckTaskScanner;
import io.nop.datav.service.report.NopDatavReportDeliveryRecovery;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.util.Map;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_STUCK_SCAN_TIMEOUT_MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * stuck-task 周期恢复扫描测试（plan 2026-08-14-1510-1 Phase 2）。
 *
 * <p>覆盖 ①–⑨：①交付 startTime 超阈值标 FAILED；②交付未超阈值不动；③导出 createTime
 * 超阈值标 FAILED；④导出未超阈值不动；⑤幂等（终态重复扫描不动）；⑥扫描异常不崩（WARN 吞）；
 * ⑦表不存在安全跳过；⑧周期触发接线（fireNow 经 beanMethod invoker 真正派发到 scanStuck()，
 * Anti-Hollow rule #22/#23——非 scanStuckForTest 直调）；⑨慢执行竞态（扫描 FAILED 后 worker
 * 仍可写 SUCCEEDED，终态正确不崩）。</p>
 */
public class TestNopDatavStuckTaskScan extends AbstractNopDatavTest {

    private static final long POLL_TIMEOUT_MS = 10_000L;
    private static final long POLL_INTERVAL_MS = 50L;
    private static final long MINUTE_MS = 60_000L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    NopDatavReportDeliveryRecovery deliveryRecovery;

    @Inject
    NopDatavExportTaskRecovery exportRecovery;

    @Inject
    NopDatavStuckTaskScanner stuckScanner;

    // ==================== ①② 交付记录（startTime 基准） ====================

    /**
     * ① 交付记录 startTime 超阈值（61 min > 60 min）→ 标 FAILED + reason="stuck beyond timeout
     * threshold (60m)" + endTime 写入。（updatedBy 由 ORM 审计 updaterProp 自动填充，
     * 显式 setUpdatedBy("system") 与重启恢复一致，不作断言。）
     */
    @Test
    public void testDeliveryBeyondTimeoutMarkedFailedWithReason() {
        long now = System.currentTimeMillis();
        seedDelivery("delivery-stuck-1", "task-stuck-1", NopDatavReportDeliveryStatus.RUNNING,
                now - 61 * MINUTE_MS);

        int marked = deliveryRecovery.scanStuck(60);

        assertEquals(1, marked, "one stuck delivery marked");
        NopDatavReportDelivery d = delivery("delivery-stuck-1");
        assertEquals(NopDatavReportDeliveryStatus.FAILED, d.getStatus(), "beyond threshold -> FAILED");
        assertNotNull(d.getErrorMsg(), "reason recorded");
        assertTrue(d.getErrorMsg().contains(NopDatavReportDeliveryRecovery.STUCK_REASON_PREFIX),
                "reason indicates stuck-by-timeout: " + d.getErrorMsg());
        assertTrue(d.getErrorMsg().contains("(60m)"), "reason carries threshold value: " + d.getErrorMsg());
        assertNotNull(d.getEndTime(), "endTime recorded");
    }

    /**
     * ② 交付记录未超阈值（RUNNING 30 min / PENDING 刚创建）→ 不动（阈值内正常在途，
     * Dim14-01 误杀裁定不回归）。
     */
    @Test
    public void testDeliveryWithinTimeoutUntouched() {
        long now = System.currentTimeMillis();
        seedDelivery("delivery-fresh-run", "task-fresh", NopDatavReportDeliveryStatus.RUNNING,
                now - 30 * MINUTE_MS);
        seedDelivery("delivery-fresh-pend", "task-fresh", NopDatavReportDeliveryStatus.PENDING,
                now);

        int marked = deliveryRecovery.scanStuck(60);

        assertEquals(0, marked, "nothing within threshold marked");
        assertEquals(NopDatavReportDeliveryStatus.RUNNING,
                delivery("delivery-fresh-run").getStatus(), "RUNNING within threshold untouched");
        assertEquals(NopDatavReportDeliveryStatus.PENDING,
                delivery("delivery-fresh-pend").getStatus(), "PENDING within threshold untouched");
        assertNull(delivery("delivery-fresh-run").getErrorMsg(), "no errorMsg written");
    }

    // ==================== ③④ 导出任务（createTime 基准） ====================

    /**
     * ③ 导出任务 createTime 超阈值 → 标 FAILED + reason（createTime 基准：实体无 startTime）。
     */
    @Test
    public void testExportTaskBeyondTimeoutMarkedFailed() {
        long now = System.currentTimeMillis();
        seedExportTask("export-stuck-1", "alice", NopDatavExportTaskStatus.RUNNING,
                now - 61 * MINUTE_MS);

        int marked = exportRecovery.scanStuck(60);

        assertEquals(1, marked, "one stuck export task marked");
        NopDatavExportTask t = exportTask("export-stuck-1");
        assertEquals(NopDatavExportTaskStatus.FAILED, t.getStatus(), "beyond threshold -> FAILED");
        assertNotNull(t.getErrorMsg(), "reason recorded");
        assertTrue(t.getErrorMsg().contains(NopDatavExportTaskRecovery.STUCK_REASON_PREFIX),
                "reason indicates stuck-by-timeout: " + t.getErrorMsg());
        assertTrue(t.getErrorMsg().contains("(60m)"), "reason carries threshold value: " + t.getErrorMsg());
    }

    /**
     * ④ 导出任务未超阈值（RUNNING 30 min / PENDING 刚提交）→ 不动。
     */
    @Test
    public void testExportTaskWithinTimeoutUntouched() {
        long now = System.currentTimeMillis();
        seedExportTask("export-fresh-run", "alice", NopDatavExportTaskStatus.RUNNING,
                now - 30 * MINUTE_MS);
        seedExportTask("export-fresh-pend", "alice", NopDatavExportTaskStatus.PENDING, now);

        int marked = exportRecovery.scanStuck(60);

        assertEquals(0, marked, "nothing within threshold marked");
        assertEquals(NopDatavExportTaskStatus.RUNNING,
                exportTask("export-fresh-run").getStatus(), "RUNNING within threshold untouched");
        assertEquals(NopDatavExportTaskStatus.PENDING,
                exportTask("export-fresh-pend").getStatus(), "PENDING within threshold untouched");
    }

    // ==================== ⑤ 幂等 ====================

    /**
     * ⑤ 幂等：①标记 FAILED 后重复扫描返回 0 且不覆盖 errorMsg；②既有终态记录
     * （FAILED/SUCCEEDED）即使时间超阈值也不动（查询过滤非终态）。
     */
    @Test
    public void testRescanIdempotentAndTerminalRecordsUntouched() {
        long now = System.currentTimeMillis();
        // 非终态超阈值 → 首扫标记
        seedDelivery("delivery-idem", "task-idem", NopDatavReportDeliveryStatus.RUNNING,
                now - 61 * MINUTE_MS);
        seedExportTask("export-idem", "alice", NopDatavExportTaskStatus.PENDING,
                now - 61 * MINUTE_MS);
        assertEquals(1, deliveryRecovery.scanStuck(60));
        assertEquals(1, exportRecovery.scanStuck(60));

        // 终态记录：即使时间超阈值也不动（errorMsg 不被覆盖）
        seedDelivery("delivery-terminal-failed", "task-idem", NopDatavReportDeliveryStatus.FAILED,
                now - 120 * MINUTE_MS, "manual stop");
        seedExportTask("export-terminal-succeeded", "alice", NopDatavExportTaskStatus.SUCCEEDED,
                now - 120 * MINUTE_MS);

        // 重复扫描：已标记的非终态变终态后查询不再命中；终态记录不动
        assertEquals(0, deliveryRecovery.scanStuck(60), "rescan finds nothing (idempotent)");
        assertEquals(0, exportRecovery.scanStuck(60), "rescan finds nothing (idempotent)");

        assertEquals(NopDatavReportDeliveryStatus.FAILED,
                delivery("delivery-idem").getStatus(), "already-marked stays FAILED");
        assertTrue(delivery("delivery-idem").getErrorMsg()
                        .contains(NopDatavReportDeliveryRecovery.STUCK_REASON_PREFIX),
                "stuck reason not overwritten on rescan");
        assertEquals("manual stop", delivery("delivery-terminal-failed").getErrorMsg(),
                "pre-existing terminal FAILED untouched (errorMsg preserved)");
        assertEquals(NopDatavReportDeliveryStatus.FAILED,
                delivery("delivery-terminal-failed").getStatus(), "terminal FAILED stays");
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED,
                exportTask("export-terminal-succeeded").getStatus(), "terminal SUCCEEDED stays");
        assertEquals(NopDatavExportTaskStatus.FAILED,
                exportTask("export-idem").getStatus(), "already-marked stays FAILED");
    }

    // ==================== ⑥ 扫描异常不崩 ====================

    /**
     * ⑥ scanner 吞单类扫描异常（WARN 日志、继续另一类、返回正常结果对象、不抛——
     * FAILED-brick 规避契约），scanCount 仍递增。
     */
    @Test
    public void testScannerSwallowsRecoveryException() {
        NopDatavReportDeliveryRecovery throwingDelivery =
                new NopDatavReportDeliveryRecovery(null, null, null) {
                    @Override
                    public int scanStuck(int timeoutMinutes) {
                        throw NopException.adapt(new IllegalStateException("simulated delivery scan failure"));
                    }
                };
        NopDatavExportTaskRecovery throwingExport =
                new NopDatavExportTaskRecovery(null, null, null) {
                    @Override
                    public int scanStuck(int timeoutMinutes) {
                        throw NopException.adapt(new IllegalStateException("simulated export scan failure"));
                    }
                };
        NopDatavStuckTaskScanner scanner = new NopDatavStuckTaskScanner();
        scanner.setDeliveryRecovery(throwingDelivery);
        scanner.setExportTaskRecovery(throwingExport);

        long before = scanner.getScanCountForTest();
        Map<String, Object> result = scanner.scanStuckForTest();

        assertNotNull(result, "scanStuck returns a result object, not throw");
        assertEquals(0, result.get("markedDeliveries"), "failed scan reports 0");
        assertEquals(0, result.get("markedTasks"), "failed scan reports 0");
        assertEquals(before + 1, scanner.getScanCountForTest(), "scanCount still incremented");
    }

    // ==================== ⑦ 表不存在安全跳过 ====================

    /**
     * ⑦ existsTable=false（schema 未建表阶段）→ scanStuck 安全返回 0，不查询不标记不抛
     * （镜像重启恢复的启动期守卫语义）。
     */
    @Test
    public void testScanStuckSkipsWhenTableNotExists() {
        long now = System.currentTimeMillis();
        // 超阈值的 stuck 记录确实存在，但守卫判定表不存在 → 必须跳过而非标记
        seedDelivery("delivery-notable", "task-notable", NopDatavReportDeliveryStatus.RUNNING,
                now - 120 * MINUTE_MS);
        seedExportTask("export-notable", "alice", NopDatavExportTaskStatus.RUNNING,
                now - 120 * MINUTE_MS);

        IJdbcTemplate noTableJdbc = newNoTableJdbcTemplate();
        NopDatavReportDeliveryRecovery noTableDelivery =
                new NopDatavReportDeliveryRecovery(daoProvider, ormTemplate, noTableJdbc);
        NopDatavExportTaskRecovery noTableExport =
                new NopDatavExportTaskRecovery(daoProvider, ormTemplate, noTableJdbc);

        assertEquals(0, noTableDelivery.scanStuck(60), "delivery scan skipped when table not exists");
        assertEquals(0, noTableExport.scanStuck(60), "export scan skipped when table not exists");
        assertEquals(NopDatavReportDeliveryStatus.RUNNING,
                delivery("delivery-notable").getStatus(), "records untouched (skip, not full-scan)");
        assertEquals(NopDatavExportTaskStatus.RUNNING,
                exportTask("export-notable").getStatus(), "records untouched (skip, not full-scan)");
    }

    // ==================== ⑧ 周期触发接线（Anti-Hollow #22/#23，无条件） ====================

    /**
     * ⑧ 周期触发接线 + 端到端：cron job 由 {@code @PostConstruct} 注册（getJobNames 含），
     * 经 {@code scheduler.fireNow(JOB_NAME)} 走 <b>beanMethod invoker</b> 真正派发到
     * {@code scanStuck()}（scanCount 递增证明运行时调用，非 scanStuckForTest 直调），
     * 超阈值记录在完整路径「cron 触发 → 扫描 → 标 FAILED」下被标记。
     *
     * <p>断言链：job 注册存在 → fireNow 成功 → scanCount +1（invoker 派发到达）→
     * 超阈值交付与导出记录均 FAILED（timeout-minutes 配置被 scanStuck() 读取并生效）。</p>
     */
    @Test
    public void testPeriodicWiringFireNowInvokesScanStuck() {
        assertNotNull(stuckScanner.getScheduler(),
                "test env must register IJobScheduler (nop-job-local test scope)");
        assertTrue(stuckScanner.getScheduler().getJobNames().contains(NopDatavStuckTaskScanner.JOB_NAME),
                "periodic job registered by @PostConstruct: "
                        + stuckScanner.getScheduler().getJobNames());

        int timeoutMinutes = CFG_DATAV_STUCK_SCAN_TIMEOUT_MINUTES.get();
        long now = System.currentTimeMillis();
        String deliveryId = "delivery-wire";
        String taskId = "export-wire";
        seedDelivery(deliveryId, "task-wire", NopDatavReportDeliveryStatus.RUNNING,
                now - (timeoutMinutes + 1) * MINUTE_MS);
        seedExportTask(taskId, "alice", NopDatavExportTaskStatus.RUNNING,
                now - (timeoutMinutes + 1) * MINUTE_MS);

        long scansBefore = stuckScanner.getScanCountForTest();
        assertTrue(stuckScanner.getScheduler().fireNow(NopDatavStuckTaskScanner.JOB_NAME),
                "fireNow dispatches the registered periodic job");

        NopDatavReportDelivery d = pollDeliveryFailed(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, d.getStatus(),
                "cron-triggered scan marked stuck delivery FAILED (end-to-end)");
        assertTrue(d.getErrorMsg().contains(NopDatavReportDeliveryRecovery.STUCK_REASON_PREFIX),
                "stuck reason written by dispatched scan: " + d.getErrorMsg());

        NopDatavExportTask t = pollExportTaskFailed(taskId);
        assertEquals(NopDatavExportTaskStatus.FAILED, t.getStatus(),
                "cron-triggered scan marked stuck export task FAILED (end-to-end)");

        assertTrue(stuckScanner.getScanCountForTest() > scansBefore,
                "beanMethod invoker dispatched runtime call to scanStuck() (wiring verified)");
    }

    // ==================== ⑨ 慢执行竞态 ====================

    /**
     * ⑨ 慢执行竞态（已裁定良性）：超阈值记录被扫描标 FAILED（假阳性）后，仍在运行的
     * worker 最终写 SUCCEEDED 覆盖之——终态正确（报告确实送达）、不崩。镜像
     * ReportDeliveryExecutor / NopDatavExportTaskBizModel 成功路径的终态写字段。
     */
    @Test
    public void testSlowExecutionRaceWorkerSuccessOverwritesScanFailed() {
        long now = System.currentTimeMillis();
        String deliveryId = "delivery-race";
        String taskId = "export-race";
        seedDelivery(deliveryId, "task-race", NopDatavReportDeliveryStatus.RUNNING,
                now - 90 * MINUTE_MS);
        seedExportTask(taskId, "alice", NopDatavExportTaskStatus.RUNNING, now - 90 * MINUTE_MS);

        // 扫描先把慢执行记录标 FAILED（假阳性）
        assertEquals(1, deliveryRecovery.scanStuck(60));
        assertEquals(1, exportRecovery.scanStuck(60));
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery(deliveryId).getStatus(),
                "scanner marked slow delivery FAILED (false positive)");

        // worker 最终完成、写 SUCCEEDED（镜像 ReportDeliveryExecutor 成功路径终态写）
        IEntityDao<NopDatavReportDelivery> deliveryDao =
                daoProvider.daoFor(NopDatavReportDelivery.class);
        NopDatavReportDelivery finished = deliveryDao.getEntityById(deliveryId);
        finished.setStatus(NopDatavReportDeliveryStatus.SUCCEEDED);
        finished.setEndTime(new Timestamp(System.currentTimeMillis()));
        finished.setDeliveredChannels("email");
        finished.setUpdatedBy("alice");
        finished.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        deliveryDao.updateEntityDirectly(finished);

        // 导出 worker 同构（镜像 NopDatavExportTaskBizModel.executeTask 成功路径终态写）
        IEntityDao<NopDatavExportTask> taskDao = daoProvider.daoFor(NopDatavExportTask.class);
        NopDatavExportTask finishedTask = taskDao.getEntityById(taskId);
        finishedTask.setStatus(NopDatavExportTaskStatus.SUCCEEDED);
        finishedTask.setFileRecordId("simulated-file-record");
        finishedTask.setRowCount(3L);
        finishedTask.setErrorMsg(null);
        finishedTask.setUpdatedBy("alice");
        finishedTask.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        taskDao.updateEntityDirectly(finishedTask);

        // 终态正确：worker 的 SUCCEEDED 修正扫描的假阳性 FAILED，不崩
        assertEquals(NopDatavReportDeliveryStatus.SUCCEEDED, delivery(deliveryId).getStatus(),
                "worker SUCCEEDED overwrites scanner false-positive FAILED (final state correct)");
        assertEquals("email", delivery(deliveryId).getDeliveredChannels(), "worker fields win");
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED, exportTask(taskId).getStatus(),
                "export worker SUCCEEDED overwrites scanner false-positive FAILED");
        assertEquals(3L, exportTask(taskId).getRowCount(), "worker fields win");
        assertNull(exportTask(taskId).getErrorMsg(), "scanner reason cleared by worker");
    }

    // ==================== Helpers ====================

    /** IJdbcTemplate 动态代理：existsTable 恒 false（其余方法不可达——守卫短路）。 */
    private static IJdbcTemplate newNoTableJdbcTemplate() {
        return (IJdbcTemplate) Proxy.newProxyInstance(
                IJdbcTemplate.class.getClassLoader(),
                new Class<?>[]{IJdbcTemplate.class},
                (proxy, method, args) -> {
                    if ("existsTable".equals(method.getName())) {
                        return Boolean.FALSE;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return Boolean.FALSE;
                    }
                    if (rt.isPrimitive() && rt != void.class) {
                        return 0;
                    }
                    return null;
                });
    }

    private NopDatavReportDelivery delivery(String deliveryId) {
        return daoProvider.daoFor(NopDatavReportDelivery.class).getEntityById(deliveryId);
    }

    private NopDatavExportTask exportTask(String taskId) {
        return daoProvider.daoFor(NopDatavExportTask.class).getEntityById(taskId);
    }

    private NopDatavReportDelivery pollDeliveryFailed(String deliveryId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        NopDatavReportDelivery d = null;
        while (System.currentTimeMillis() < deadline) {
            d = delivery(deliveryId);
            if (d != null && NopDatavReportDeliveryStatus.isTerminal(d.getStatus())) {
                return d;
            }
            sleepQuietly();
        }
        return d;
    }

    private NopDatavExportTask pollExportTaskFailed(String taskId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        NopDatavExportTask t = null;
        while (System.currentTimeMillis() < deadline) {
            t = exportTask(taskId);
            if (t != null && NopDatavExportTaskStatus.isTerminal(t.getStatus())) {
                return t;
            }
            sleepQuietly();
        }
        return t;
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while polling", e);
        }
    }

    private void seedDelivery(String deliveryId, String reportTaskId, int status,
                              long startTimeMillis) {
        seedDelivery(deliveryId, reportTaskId, status, startTimeMillis, null);
    }

    private void seedDelivery(String deliveryId, String reportTaskId, int status,
                              long startTimeMillis, String errorMsg) {
        long now = System.currentTimeMillis();
        NopDatavReportDelivery d = new NopDatavReportDelivery();
        d.setDeliveryId(deliveryId);
        d.setReportTaskId(reportTaskId);
        d.setStatus(status);
        d.setTriggeredBy(NopDatavReportTriggerSource.SCHEDULE);
        d.setStartTime(new Timestamp(startTimeMillis));
        d.setErrorMsg(errorMsg);
        d.setDelFlag((byte) 0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(startTimeMillis));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(d);
    }

    private void seedExportTask(String taskId, String owner, int status, long createTimeMillis) {
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
        IEntityDao<NopDatavExportTask> dao = daoProvider.daoFor(NopDatavExportTask.class);
        dao.saveEntityDirectly(t);
        // ORM 审计在 insert 时强制 createTimeProp=now（覆盖 seeded 值）；经 update 路径回拨到目标时刻
        // （update 不触碰 createTime，与生产 stuck 记录「insert 于过去」等效）
        NopDatavExportTask saved = dao.getEntityById(taskId);
        saved.setCreateTime(new Timestamp(createTimeMillis));
        dao.updateEntityDirectly(saved);
    }
}
