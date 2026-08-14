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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_CONCURRENCY_LIMIT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_EXPORT_MISSING_SOURCE;
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

    /**
     * 测试 seam（D4 方案 A）：经具体类注入以访问 package-private 的
     * {@link NopDatavExportTaskBizModel#setExecutionStartHookForTest}。
     */
    @Inject
    NopDatavExportTaskBizModel exportBizModel;

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
     * #4 Dim09-03：空 sourceType/sourceId 抛 ERR_DATAV_EXPORT_MISSING_SOURCE（非 ERR_DATAV_EXPORT_TASK_NOT_FOUND）。
     * 且空源检查在 validateFormat 之前：传非法 format + 空源 → 期望 missing-source（非 type-not-supported）。
     */
    @Test
    public void testCreateExportTask_emptySource_throwsMissingSource() {
        IServiceContext ctx = ownerContext("alice");

        // 空源 + 合法 format → MISSING_SOURCE
        NopException ex1 = assertThrows(NopException.class, () ->
                exportBiz.createExportTask("", "dash-x", "csv", null, ctx));
        assertEquals(ERR_DATAV_EXPORT_MISSING_SOURCE.getErrorCode(), ex1.getErrorCode(),
                "empty sourceType should throw MISSING_SOURCE, got: " + ex1.getErrorCode());

        NopException ex2 = assertThrows(NopException.class, () ->
                exportBiz.createExportTask("panel", "", "csv", null, ctx));
        assertEquals(ERR_DATAV_EXPORT_MISSING_SOURCE.getErrorCode(), ex2.getErrorCode(),
                "empty sourceId should throw MISSING_SOURCE, got: " + ex2.getErrorCode());

        // 空源 + 非法 format → 仍期望 MISSING_SOURCE（证明空源检查在 format 校验之前）
        NopException ex3 = assertThrows(NopException.class, () ->
                exportBiz.createExportTask("", "", "pdf", null, ctx));
        assertEquals(ERR_DATAV_EXPORT_MISSING_SOURCE.getErrorCode(), ex3.getErrorCode(),
                "empty source with invalid format should still throw MISSING_SOURCE (check precedes format validation), got: "
                        + ex3.getErrorCode());
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
     * cancelled 路径（cancel 请求线程直写）：seed running 任务 → cancelExportTask → 转 cancelled。
     *
     * <p>本测试仅覆盖 cancel 请求线程直写路径（seedTask 直接落 RUNNING，不提交执行体），
     * 与 {@link #testCancelDuringExecutionEndsInCancelled}（覆盖执行体覆盖问题，submitExecution 真实跑）
     * 互补。前者验证 cancelExportTask 的 DB 写入语义，后者验证执行体不覆盖该写入。</p>
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
     * Dim16-01 回归保护：cancel-during-execution E2E（D4 方案 A 确定性窗口）。
     *
     * <p>真实链路：{@code createExportTask}（submitExecution 真实跑执行体，非 seedTask 直写）→
     * 执行体持久化 RUNNING 后在 D4 test seam（{@code executionStartHook}）阻塞 →
     * 测试观察 RUNNING 后调 {@code cancelExportTask}（cancel 线程写 CANCELLED + 置 cancelFlags）→
     * 释放 seam → 执行体恢复后：(a) exporter 内 {@code checkCancelled} 命中抛
     * {@code ERR_DATAV_EXPORT_FAILED}，(b) executeTask catch 分流（在 setStatus(FAILED) 之前判定）
     * 不写 FAILED，(c) 终态保留为 cancel 线程写入的 CANCELLED，fileRecordId=null。</p>
     *
     * <p><b>D4 设计裁定（seam 位置）</b>：seam 放在 BizModel 的「RUNNING 持久化之后、exporter 之前」这一稳定位置，
     * <b>非</b> exporter 内 checkpoint 处。理由：(1) 测试同步点必须独立于被测 checkpoint，否则 mutate-fail 时
     * （移除 exporter checkpoint）seam 一并被移除导致测试无法同步；(2) 此位置之后执行体的下一步必然是 exporter
     * 内首处 checkpoint，cancel 命中确定可见；(3) 避免 seam 放在 exporter 内时「执行体恢复后需获取 JDBC 连接跑
     * queryPanelData」与测试线程轮询产生连接竞争（H2 测试连接池小），导致 mutate-fail 场景下假阳性通过。</p>
     *
     * <p><b>完成等待策略</b>：不使用 {@link #pollUntilTerminal} 轮询（每次 getExportTask 开 session 取连接，
     * 与执行体恢复后跑 exporter 的 JDBC 需求竞争 H2 连接池）。改为等待 {@code cancelFlags[taskId]} 被
     * executeTask 的 finally 块清除（确定性完成信号），再单次直读终态。</p>
     *
     * <p><b>mutate-fail 精确声明</b>：本测试验证 cancel-during-execution 终态为 CANCELLED + fileRecordId=null。
     * Phase 2 之前无 cancelChecker 接线、无 pre-SUCCEEDED 复检，audit Dim07-01 报告执行体 SUCCEEDED 写入覆盖
     * cancel 线程的 CANCELLED（依赖 audit 假设：{@code updateEntityDirectly} 不做版本检查）。实际验证发现：
     * ORM 模型配置了 {@code versionProp="version"}（{@code _app.orm.xml:476}），{@code updateEntityDirectly}
     * 的 UPDATE WHERE 子句包含 {@code version=?}，stale entity 更新命中 0 行 → 抛
     * {@code nop.err.orm.update-entity-not-found}。因此 Phase 2 的两个 checkpoint 与既有 version 锁形成
     * <b>三层防护</b>（defense in depth）：</p>
     * <ul>
     *   <li>Layer 1（Phase 2 exporter checkpoint）：exporter 内 {@code checkCancelled} 命中抛 → catch 分流不写 FAILED</li>
     *   <li>Layer 2（Phase 2 pre-SUCCEEDED 复检）：cancel 命中跳过 SUCCEEDED 写入</li>
     *   <li>Layer 3（既有 version 锁）：stale task 对象的 SUCCEEDED 写入因 version 不匹配抛错 → catch 分流兜底</li>
     * </ul>
     * <p>本测试在当前实现（三层均生效）下通过。mutate-fail 验证（执行时裁定记录）：</p>
     * <ul>
     *   <li>移除 Layer 1（exporter checkpoint）：测试仍通过（Layer 2 或 3 兜底），但日志路径变化（无 cancelled-in-flight），
     *       代码追踪 + 日志差异可证明 Layer 1 独立生效。</li>
     *   <li>移除 Layer 2（pre-SUCCEEDED 复检）：测试仍通过（Layer 1 抛 → catch 分流），日志 cancelled-in-flight 出现，
     *       代码追踪可证明 Layer 2 独立生效。</li>
     *   <li>移除 Layer 1+2+3（同时绕过 version 锁）：测试确定性失败（SUCCEEDED 写入成功 → 终态翻转为 SUCCEEDED）。</li>
     * </ul>
     * <p>closure audit 的 Anti-Hollow Check：经 D4 端到端测试（运行时触发 Layer 1 cancelled-in-flight 日志）+
     * 代码追踪（PanelDataExporter.checkCancelled + executeTask pre-SUCCEEDED 复检 live code 确认）双层证据闭合。</p>
     */
    @Test
    public void testCancelDuringExecutionEndsInCancelled() throws InterruptedException {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-cancel-e2e");
        saveChartPanelWithDataset("panel-cancel-a", dashboardId, "Chart A");
        saveChartPanelWithDataset("panel-cancel-b", dashboardId, "Chart B");

        // D4 test seam：执行体持久化 RUNNING 后立即 countDown 通知测试，并阻塞等测试释放
        CountDownLatch enteredRunning = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        exportBizModel.setExecutionStartHookForTest(() -> {
            enteredRunning.countDown();
            try {
                // 超时保护：避免测试侧异常导致 executor 线程无限阻塞
                if (!releaseExecution.await(15, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            // 提交真实导出任务（执行体真实跑，非 seedTask）
            NopDatavExportTask task = exportBiz.createExportTask(
                    "dashboard", dashboardId, "xlsx", null, ctx);
            assertNotNull(task.getTaskId());

            // 等执行体进入 RUNNING（确定性窗口起点）
            assertTrue(enteredRunning.await(15, TimeUnit.SECONDS),
                    "execution body should reach RUNNING checkpoint within 15s");

            // 在 RUNNING 窗口内调用 cancel（cancelExportTask 写 CANCELLED + 置 cancelFlags[taskId]=true）
            NopDatavExportTask snapshot = exportBiz.getExportTask(task.getTaskId(), ctx);
            assertEquals(NopDatavExportTaskStatus.RUNNING, snapshot.getStatus(),
                    "task must be RUNNING when cancel is invoked (D4 deterministic window), status="
                            + snapshot.getStatus());
            NopDatavExportTask cancelled = exportBiz.cancelExportTask(task.getTaskId(), ctx);
            assertEquals(NopDatavExportTaskStatus.CANCELLED, cancelled.getStatus(),
                    "cancelExportTask immediately writes CANCELLED");

            // 释放执行体：下一步 exporter 进入面板间 checkpoint，cancelFlags 已置 true → 抛 + 跳 SUCCEEDED
            releaseExecution.countDown();

            // 等待执行体完成（cancelFlags 在 executeTask finally 块中清除）— 不轮询以避免 H2 连接竞争
            awaitCancelFlagCleared(task.getTaskId(), 15_000L);

            // 执行体已完成，单次直读终态（无并发连接需求）
            NopDatavExportTask done = daoProvider.daoFor(NopDatavExportTask.class)
                    .getEntityById(task.getTaskId());

            // 核心断言：终态必须为 CANCELLED（非 SUCCEEDED）— Phase 2 两 checkpoint 兜底
            assertEquals(NopDatavExportTaskStatus.CANCELLED, done.getStatus(),
                    "Dim07-01: final status must be CANCELLED (not SUCCEEDED) — cancel was detected "
                            + "during execution and SUCCEEDED write was skipped");
            // fileRecordId 必须 null（cancelled 任务不应记录成功产物）
            assertTrue(done.getFileRecordId() == null || done.getFileRecordId().isEmpty(),
                    "cancelled task must have null fileRecordId, got: " + done.getFileRecordId());
            // errorMsg 由 cancel 线程写入 "cancelled by user"
            assertNotNull(done.getErrorMsg(), "errorMsg should be set by cancel thread");
            assertTrue(done.getErrorMsg().toLowerCase().contains("cancel"),
                    "errorMsg should mention 'cancel', got: " + done.getErrorMsg());
        } finally {
            // 清理 seam，避免影响其他测试
            exportBizModel.setExecutionStartHookForTest(null);
        }
    }

    /**
     * 等待 cancelFlags[taskId] 被清除（执行体完成的确定性信号）。
     */
    private void awaitCancelFlagCleared(String taskId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (exportBizModel.isCancelFlagged(taskId)) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError("cancelFlag for task " + taskId
                        + " was not cleared within " + timeoutMs + "ms (executor did not complete)");
            }
            Thread.sleep(20);
        }
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

    /**
     * Dim14-01 回归保护：在途任务（其他用户 RUNNING）不应被新导出请求误标 FAILED。
     *
     * <p>历史 bug：{@code createExportTask} 请求路径同步调用 {@code recovery.recoverInterruptedTasks()}，
     * 该方法选全部非终态任务（无 owner 谓词）写 status=FAILED，导致并发导出下每个请求都把所有其他用户在途任务误杀。
     * 修复后恢复仅由 {@code @PostConstruct} 启动期执行一次。</p>
     *
     * <p>断言时机：recovery 旧调用在请求线程内同步执行，故 {@code createExportTask} 返回时即可确定性地观察。
     * 该测试在「恢复 per-request 调用」时会失败（bob 的 RUNNING 任务被翻转为 FAILED）。</p>
     */
    @Test
    public void testCreateExportTaskDoesNotKillInFlightTasks() {
        setupSalesData();
        // bob 有一个在途 RUNNING 任务（与 alice 的新请求无关）
        seedTask("bob-inflight-running", "bob", NopDatavExportTaskStatus.RUNNING);
        // 同时 seed 一个 PENDING 任务（覆盖 pending 也不被误杀）
        seedTask("bob-inflight-pending", "bob", NopDatavExportTaskStatus.PENDING);

        IServiceContext aliceCtx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-kill");
        NopDatavPanel panel = saveChartPanelWithDataset("panel-no-kill", dashboardId, "Chart NoKill");

        // alice 发起新导出请求（修复前此处会同步触发 recoverInterruptedTasks 误杀 bob）
        NopDatavExportTask aliceTask = exportBiz.createExportTask(
                "panel", panel.getPanelId(), "csv", Map.of("region", "north"), aliceCtx);
        assertNotNull(aliceTask.getTaskId());

        // 关键断言：bob 的在途任务未被误改为 FAILED（status 维持原值）
        NopDatavExportTask bobRunning = daoProvider.daoFor(NopDatavExportTask.class)
                .getEntityById("bob-inflight-running");
        NopDatavExportTask bobPending = daoProvider.daoFor(NopDatavExportTask.class)
                .getEntityById("bob-inflight-pending");
        assertEquals(NopDatavExportTaskStatus.RUNNING, bobRunning.getStatus(),
                "in-flight RUNNING task must NOT be marked FAILED by another user's createExportTask request");
        assertEquals(NopDatavExportTaskStatus.PENDING, bobPending.getStatus(),
                "in-flight PENDING task must NOT be marked FAILED by another user's createExportTask request");
        // 顺带断言 errorMsg 未被 recovery 写入（restart 字样不应出现）
        assertFalse(bobRunning.getErrorMsg() != null && bobRunning.getErrorMsg().contains("restart"),
                "in-flight task errorMsg must not be touched by request-path recovery");
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
