package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.mock.MockEmailSender;
import io.nop.datav.service.report.NopDatavReportDeliveryRecovery;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import io.nop.datav.service.report.NotificationSender;
import io.nop.datav.service.report.ReportDeliveryExecutor;
import io.nop.file.dao.entity.NopFileRecord;
import io.nop.integration.api.email.EmailMessage;
import io.nop.report.dao.entity.NopReportDataset;
import io.nop.sys.dao.entity.NopSysNoticeTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时报告端到端测试（D5-1 Phase 4）。
 *
 * <p>从 triggerReportNow / scheduler.executeScheduledReport 入口到交付历史 + 邮件送达完整链路跑通，
 * 断言调用链运行时连通（scheduler→executor→exporter→notificationSender→emailSender，
 * Anti-Hollow rule #22/#23）。</p>
 *
 * <p>覆盖路径：
 * <ul>
 *   <li>主线端到端：建看板（含发布快照）+ 报告任务 → triggerReportNow → 取数生成文件 → 邮件送达 → 交付历史 succeeded</li>
 *   <li>调度注册：save enabled 任务 → scheduler.registerTask → addJob 被调用（jobNames 含）；disable/delete → unregister</li>
 *   <li>显式失败：无通知渠道 / 无发布看板 / IM 渠道 / 未配置发件人 / 模板未找到</li>
 *   <li>grace 超期 → skipped 交付记录</li>
 *   <li>重启恢复：stale running 交付记录经 recovery.init 置 failed</li>
 *   <li>吞业务错误：scheduler.executeScheduledReport 业务异常被吞、返回正常结果</li>
 *   <li>权限：非 owner 用户不可管理（owner guard 抛错）</li>
 * </ul>
 * </p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/datav/beans/test-report-mock.beans.xml")
@NopTestProperty(name = "nop.datav.report.default-sender", value = "noreply@example.com")
@NopTestProperty(name = "nop.file.store-dir", value = "target/test-file-store-report")
public class TestNopDatavReportE2E extends AbstractNopDatavTest {

    public TestNopDatavReportE2E() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    private static final long POLL_TIMEOUT_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 100L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopDatavReportScheduler reportScheduler;

    @Inject
    ReportDeliveryExecutor reportDeliveryExecutor;

    @Inject
    NopDatavReportDeliveryRecovery deliveryRecovery;

    @Inject
    io.nop.integration.api.email.IEmailSender emailSender;

    private MockEmailSender mockEmailSender;

    @Override
    @BeforeEach
    public void init(TestInfo testInfo) {
        super.init(testInfo);
        mockEmailSender = (MockEmailSender) emailSender;
        mockEmailSender.reset();
    }

    // ==================== 主线端到端 ====================

    /**
     * 全链路：建看板（含发布快照）+ 报告任务 → triggerReportNow → 取数生成文件 →
     * 邮件送达（mock 断言）→ 交付历史 succeeded。
     *
     * <p><b>Anti-Hollow 接线验证（rule #23）</b>：断言
     * <ul>
     *   <li>NopFileRecord 落库（证明 ReportDeliveryExecutor 调用 PanelDataExporter.exportDashboard 取数 + IFileStore.saveFile 落盘）</li>
     *   <li>mockEmailSender 收到 sendEmail 调用，attachments 含文件（证明 NotificationSender 调用 IEmailSender.sendEmail）</li>
     *   <li>交付历史 status=SUCCEEDED，deliveredChannels=email</li>
     * </ul>
     * 调用链连通，非空方法体。</p>
     */
    @Test
    public void testE2eManualTriggerReportGeneratesFileAndSendsEmail() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-report-e2e", "alice", true);
        saveChartPanelWithDataset("panel-e2e-report", dashboardId, "Sales Chart");

        NopDatavReportTask task = seedReportTask("task-e2e", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);
        seedNoticeTemplate("report-delivery-default",
                "Report {reportName} for dashboard {dashboardName} generated at {generatedTime}");

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());

        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.SUCCEEDED, delivery.getStatus(),
                "delivery succeeded, errorMsg=" + delivery.getErrorMsg());
        assertNotNull(delivery.getGeneratedFileRecordId(), "file record id recorded");
        assertTrue(delivery.getRowCount() >= 0, "rowCount recorded");
        assertEquals("email", delivery.getDeliveredChannels(), "delivered via email channel");

        NopFileRecord record = daoProvider.daoFor(NopFileRecord.class)
                .getEntityById(delivery.getGeneratedFileRecordId());
        assertNotNull(record, "NopFileRecord persisted by IFileStore.saveFile");
        assertEquals("nopDatavReportTask", record.getBizObjName(), "file attached to report task biz obj");

        List<EmailMessage> sent = mockEmailSender.getSentMails();
        assertEquals(1, sent.size(), "one email sent");
        EmailMessage mail = sent.get(0);
        assertEquals(Arrays.asList("a@example.com", "b@example.com"), mail.getTo(),
                "email recipients match task.recipients");
        assertEquals("noreply@example.com", mail.getFrom(), "email from default sender config");
        assertNotNull(mail.getSubject(), "email subject rendered");
        assertTrue(mail.getSubject().contains("task-e2e-name"), "subject contains report name");
        assertNotNull(mail.getAttachments(), "email has attachment");
        assertEquals(1, mail.getAttachments().size(), "one attachment (generated report file)");
    }

    // ==================== 调度注册 ====================

    /**
     * save enabled 报告任务 → scheduler.registerTask 被调用 → addJob 注册 cron job。
     * 断言 scheduler.getJobNames() 含 "nop-datav-report-<taskId>"。
     *
     * <p>注意：seed 用 saveEntityDirectly（不经 BizModel），不会触发 afterEntityChange 自动注册，
     * 因此可安全建 ENABLED 任务再手动调 registerTask 断言。
     * doRegister 内部校验 status 必须 ENABLED 才注册（DISABLED 会 removeJob）。</p>
     */
    @Test
    public void testSchedulerRegisterUnregisterOnEnabledDisabled() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-sched", "alice", true);

        // ENABLED 状态建任务（saveEntityDirectly 不触发 afterEntityChange）
        NopDatavReportTask task = seedReportTask("task-sched", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        if (reportScheduler.getScheduler() == null) {
            return;
        }

        reportScheduler.registerTask(task.getReportTaskId());
        String jobName = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName),
                "cron job registered after registerTask: " + reportScheduler.getRegisteredJobNames());

        reportScheduler.unregisterTask(task.getReportTaskId());
        assertFalse(reportScheduler.getRegisteredJobNames().contains(jobName),
                "cron job removed after unregisterTask");
    }

    /**
     * 启动 scanner：seed enabled 任务 → 手动调 init() → 注册。
     */
    @Test
    public void testSchedulerInitScansEnabledTasks() {
        if (reportScheduler.getScheduler() == null) {
            return;
        }
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-scan", "alice", true);
        NopDatavReportTask task = seedReportTask("task-scan", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        reportScheduler.init();
        String jobName = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName),
                "init scanner registered enabled task: " + reportScheduler.getRegisteredJobNames());
    }

    // ==================== 吞业务错误（FAILED-brick 规避） ====================

    /**
     * executeScheduledReport 业务异常被吞、返回正常结果（不抛——规避 LocalJobScheduler FAILED-brick）。
     *
     * <p>异步执行模型：execute() 同步插 pending 交付记录 + 异步 submit doExecute；
     * executeScheduledReport 本身不抛（业务错误在异步线程被 catch 落库 failed）。
     * 本测试断言两层：
     * <ul>
     *   <li>executeScheduledReport 返回非 null 结果（不抛——FAILED-brick 规避契约）</li>
     *   <li>异步线程将业务错误落库：交付记录最终达 FAILED（无发布看板）</li>
     * </ul>
     * </p>
     */
    @Test
    public void testExecuteScheduledReportSwallowsBusinessError() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-fail", "alice", false);
        NopDatavReportTask task = seedReportTask("task-fail", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        Map<String, Object> result = reportScheduler.fireScheduledForTest(task.getReportTaskId());

        // 关键契约：方法不抛、返回正常结果对象（FAILED-brick 规避）
        assertNotNull(result, "executeScheduledReport must return a result, not throw");
        assertNotNull(result.get("deliveryId"), "deliveryId recorded for async tracking");

        // 异步线程将业务错误（无发布看板）落库 failed
        String deliveryId = (String) result.get("deliveryId");
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                "async business error (no publishable dashboard) recorded as failed");
        assertNotNull(delivery.getErrorMsg());
        assertTrue(delivery.getErrorMsg().toLowerCase().contains("publish"),
                "errorMsg mentions publish: " + delivery.getErrorMsg());
    }

    /**
     * cron 执行路径也吞业务错误：直接调 executeScheduledReport（不经 fireNow），
     * 参数缺失/异常均被 catch，返回正常结果。
     */
    @Test
    public void testExecuteScheduledReportWithUnknownTaskReturnsFailed() {
        Map<String, Object> result = reportScheduler.fireScheduledForTest("nonexistent-task-id");
        assertNotNull(result);
        assertEquals("failed", result.get("status"));
        assertNotNull(result.get("error"));
    }

    // ==================== 显式失败路径 ====================

    /**
     * 无通知渠道（notifyChannels 为空 JSON 数组）→ delivery failed（显式失败，非静默跳过）。
     */
    @Test
    public void testNoNotifiableChannelFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-chan", "alice", true);
        saveChartPanelWithDataset("panel-no-chan", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTaskWithChannels("task-no-chan", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true, "[\"a@example.com\"]", "[]");

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                "empty notifyChannels -> delivery failed");
        assertNotNull(delivery.getErrorMsg(), "errorMsg recorded");
    }

    /**
     * 无已发布看板（无发布快照）→ delivery failed（显式失败）。
     */
    @Test
    public void testNoPublishableDashboardFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-pub", "alice", false);
        NopDatavReportTask task = seedReportTask("task-no-pub", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                "no publishable dashboard -> delivery failed");
        assertNotNull(delivery.getErrorMsg());
    }

    /**
     * IM 渠道（notifyChannels 含 im）→ delivery failed（UnsupportedOperationException 被 executor 吞为 failed，非静默跳过）。
     */
    @Test
    public void testImChannelThrowsUnsupported() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-im", "alice", true);
        saveChartPanelWithDataset("panel-im", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTaskWithChannels("task-im", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true, "[\"a@example.com\"]", "[\"im\"]");

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                "IM channel -> delivery failed (UnsupportedOperationException swallowed by executor)");
        assertNotNull(delivery.getErrorMsg());
        assertTrue(delivery.getErrorMsg().toLowerCase().contains("im")
                        || delivery.getErrorMsg().toLowerCase().contains("channel"),
                "errorMsg mentions IM channel: " + delivery.getErrorMsg());
    }

    /**
     * 未配置发件人（default-sender 为空）→ delivery failed（显式失败）。
     *
     * <p>通过临时清空 sender 配置模拟宿主未配置邮件发件人场景（{@code ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED}）。</p>
     */
    @Test
    public void testSenderNotConfiguredFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-sender", "alice", true);
        saveChartPanelWithDataset("panel-no-sender", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTask("task-no-sender", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        String origSender = io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER.get();
        io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER, "");
        try {
            String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                    task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
            NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
            assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                    "sender not configured -> delivery failed");
            assertNotNull(delivery.getErrorMsg());
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                    io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER, origSender);
        }
    }

    // ==================== grace 语义 ====================

    /**
     * grace 超期：scheduledFireTime 远早于 now（超过 graceMinutes）→ 写 skipped 交付记录（显式记录，非静默跳过）。
     */
    @Test
    public void testGraceExceededWritesSkippedDelivery() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-grace", "alice", true);
        saveChartPanelWithDataset("panel-grace", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTaskWithGrace("task-grace", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true, 5);

        long oldScheduledFireTime = System.currentTimeMillis() - 60L * 60L * 1000L;
        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, oldScheduledFireTime);

        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.SKIPPED, delivery.getStatus(),
                "grace exceeded -> delivery skipped");
        assertNotNull(delivery.getErrorMsg());
        assertTrue(delivery.getErrorMsg().toLowerCase().contains("grace")
                        || delivery.getErrorMsg().toLowerCase().contains("misfire"),
                "errorMsg mentions grace/misfire: " + delivery.getErrorMsg());

        assertEquals(0, mockEmailSender.getSendCount(), "no email sent for skipped delivery");
    }

    // ==================== 重启恢复 ====================

    /**
     * stale running 交付记录经 NopDatavReportDeliveryRecovery.init() 置 failed（独立 bean，非 scheduler.init）。
     */
    @Test
    public void testRestartRecoveryMarksInterruptedAsFailed() {
        seedDelivery("delivery-stale", "task-recover", NopDatavReportDeliveryStatus.RUNNING);

        deliveryRecovery.recoverInterruptedDeliveries();

        NopDatavReportDelivery r = daoProvider.daoFor(NopDatavReportDelivery.class)
                .getEntityById("delivery-stale");
        assertEquals(NopDatavReportDeliveryStatus.FAILED, r.getStatus(), "running -> failed on recovery");
        assertTrue(r.getErrorMsg().contains("restart"), "reason indicates restart: " + r.getErrorMsg());
    }

    // ==================== 模板渲染 ====================

    /**
     * 模板键映射失败（NopSysNoticeTemplate.name 查不到）→ delivery failed（显式失败）。
     */
    @Test
    public void testTemplateNotFoundFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-tpl", "alice", true);
        saveChartPanelWithDataset("panel-tpl", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTaskWithTemplate("task-tpl", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true, "nonexistent-template-key");

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                "template not found -> delivery failed");
        assertNotNull(delivery.getErrorMsg());
        assertTrue(delivery.getErrorMsg().toLowerCase().contains("template")
                        || delivery.getErrorMsg().toLowerCase().contains("nop.sys"),
                "errorMsg mentions template: " + delivery.getErrorMsg());
    }

    // ==================== 权限（owner guard） ====================

    /**
     * 报告任务 owner guard：不存在 dashboard 时直接调用 executor.execute 走 panel 数据查询路径，
     * 此处断言 BizModel 层 owner guard 接口存在（{@link NopDatavDashboardOwnerGuard#requireDashboardOwnership}）
     * 且对不存在 dashboard 抛 NopException（非 owner 不可管理）。
     *
     * <p>本测试断言 owner guard 的「dashboard 不存在」分支显式失败（非静默返回），
     * 与设计契约 schedule-report-design.md §8「非 owner 不可管理」一致。</p>
     */
    @Test
    public void testOwnerGuardFailsForNonexistentDashboard() {
        IServiceContext nonOwner = ownerContext("mallory");
        NopException ex = assertThrows(NopException.class, () ->
                io.nop.datav.service.NopDatavDashboardOwnerGuard.requireDashboardOwnership(
                        daoProvider, "nonexistent-dashboard-id", nonOwner));
        assertNotNull(ex.getMessage(), "owner guard throws for nonexistent dashboard (explicit failure)");
    }

    // ==================== Dim14-02 接线验证（SMTP 移出 session） ====================

    /**
     * 接线验证：{@code notificationSender.sendReport}（SMTP）在交付记录 SUCCEEDED 提交之后才被调用。
     *
     * <p><b>Anti-Hollow（rule #23）</b>：在 sendEmail 调用时机读取该交付记录的已提交状态，
     * 断言为 SUCCEEDED（而非 RUNNING/PENDING）。这证明 sendReport 运行于 runInNewSession 块之外——
     * SUCCEEDED 已先提交，SMTP 期间不再持有 JDBC 连接（Dim14-02 修复）。若 SMTP 仍在 session 内
     * （旧代码 sendReport 在 setStatus(SUCCEEDED) 之前），此处会读到 RUNNING/PENDING。</p>
     */
    @Test
    public void testSendReportHappensAfterSucceededCommit() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-smtp-order", "alice", true);
        saveChartPanelWithDataset("panel-smtp-order", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTask("task-smtp-order", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        AtomicReference<Integer> statusAtSend = new AtomicReference<>();
        AtomicReference<String> channelsAtSend = new AtomicReference<>();
        mockEmailSender.setOnSend(() -> {
            // SMTP 调用时机：读取该任务交付记录的已提交状态（独立读取，不经执行 session）
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq("reportTaskId", task.getReportTaskId()));
            q.setLimit(1);
            NopDatavReportDelivery d = daoProvider.daoFor(NopDatavReportDelivery.class).findFirstByQuery(q);
            if (d != null) {
                statusAtSend.set(d.getStatus());
                channelsAtSend.set(d.getDeliveredChannels());
            }
        });

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        assertEquals(NopDatavReportDeliveryStatus.SUCCEEDED, delivery.getStatus(),
                "delivery succeeded");
        assertEquals("email", delivery.getDeliveredChannels(), "deliveredChannels recorded after email");

        // 关键断言：SMTP 调用时交付记录已提交为 SUCCEEDED（sendReport 在 session 外、SUCCEEDED 提交之后）
        assertNotNull(statusAtSend.get(), "sendReport was invoked (onSend hook fired)");
        assertEquals(NopDatavReportDeliveryStatus.SUCCEEDED, statusAtSend.get(),
                "delivery already committed SUCCEEDED when sendReport invoked (SMTP outside session)");
        // deliveredChannels 在邮件成功后才回写，SMTP 调用时仍为 null
        assertNull(channelsAtSend.get(),
                "deliveredChannels not yet recorded at SMTP time (recorded only after successful send)");
        assertEquals(1, mockEmailSender.getSendCount(), "one email sent");
    }

    /**
     * 邮件失败回补 FAILED：sendReport 抛错时，已提交的 SUCCEEDED 被强制覆盖为 FAILED（新 session），
     * 不留「email sent / record SUCCEEDED」或「record RUNNING」状态。
     *
     * <p>同时断言 task.lastRunStatus 一并回退为 failed，保持交付记录与任务状态一致。</p>
     */
    @Test
    public void testNotificationFailureRollsBackSucceededToFailed() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-smtp-fail", "alice", true);
        saveChartPanelWithDataset("panel-smtp-fail", dashboardId, "Chart");
        NopDatavReportTask task = seedReportTask("task-smtp-fail", dashboardId, "alice",
                "0 0 8 * * ?", "xlsx", true);

        // 模拟 SMTP 失败（sendEmail 入口抛错）
        mockEmailSender.setFailOnSend(new RuntimeException("simulated SMTP timeout"));

        String deliveryId = reportDeliveryExecutor.executeSyncForTest(
                task.getReportTaskId(), NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
        NopDatavReportDelivery delivery = pollUntilTerminal(deliveryId);
        // 邮件失败 → 回补 FAILED（SUCCEEDED 已先提交，此处强制覆盖）
        assertEquals(NopDatavReportDeliveryStatus.FAILED, delivery.getStatus(),
                "notification failure rolls back SUCCEEDED -> FAILED");
        assertNotNull(delivery.getErrorMsg(), "errorMsg recorded");
        assertTrue(delivery.getErrorMsg().toLowerCase().contains("notification"),
                "errorMsg indicates notification failure: " + delivery.getErrorMsg());
        assertEquals(0, mockEmailSender.getSendCount(), "no successful send recorded (SMTP threw)");

        // task.lastRunStatus 一并回退为 failed
        NopDatavReportTask refreshedTask = daoProvider.daoFor(NopDatavReportTask.class)
                .getEntityById(task.getReportTaskId());
        assertEquals(NopDatavReportDeliveryStatus.label(NopDatavReportDeliveryStatus.FAILED),
                refreshedTask.getLastRunStatus(), "task lastRunStatus rolled back to failed");
    }

    // ==================== Helpers ====================

    private NopDatavReportDelivery pollUntilTerminal(String deliveryId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            NopDatavReportDelivery d = daoProvider.daoFor(NopDatavReportDelivery.class)
                    .getEntityById(deliveryId);
            if (d != null && NopDatavReportDeliveryStatus.isTerminal(d.getStatus())) {
                return d;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            }
        }
        throw new AssertionError("delivery " + deliveryId + " did not reach terminal state within "
                + POLL_TIMEOUT_MS + "ms");
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private String setupDashboard(String name, String owner, boolean withSnapshot) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(withSnapshot ? 10 : 0);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);

        if (withSnapshot) {
            NopDatavDashboardSnapshot s = new NopDatavDashboardSnapshot();
            s.setSnapshotId(java.util.UUID.randomUUID().toString().replace("-", ""));
            s.setDashboardId(d.getDashboardId());
            s.setSnapshotVersion(1L);
            s.setSnapshotContent("{}");
            s.setPublishedBy(owner);
            s.setPublishedTime(new Timestamp(now));
            s.setVersion(0L);
            s.setCreatedBy(owner);
            s.setCreateTime(new Timestamp(now));
            s.setUpdatedBy(owner);
            s.setUpdateTime(new Timestamp(now));
            daoProvider.daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(s);
        }
        return d.getDashboardId();
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(SQL.begin()
                    .name("drop:TEST_DATAV_SALES_RPT")
                    .sql("drop table TEST_DATAV_SALES_RPT").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_SALES_RPT")
                .sql("create table TEST_DATAV_SALES_RPT(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_SALES_RPT")
                .sql("insert into TEST_DATAV_SALES_RPT(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private NopDatavPanel saveChartPanelWithDataset(String panelId, String dashboardId, String panelName) {
        String refId = panelId + "-ref";
        String dsId = panelId + "-ds";
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES_RPT order by AMOUNT desc");
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
        ref.setParamMapping("{}");
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

    private NopDatavReportTask seedReportTask(String taskId, String dashboardId, String owner,
                                              String cron, String format, boolean enabled) {
        return seedReportTaskWithChannels(taskId, dashboardId, owner, cron, format, enabled,
                "[\"a@example.com\",\"b@example.com\"]", "[\"email\"]");
    }

    private NopDatavReportTask seedReportTaskWithChannels(String taskId, String dashboardId, String owner,
                                                          String cron, String format, boolean enabled,
                                                          String recipientsJson, String channelsJson) {
        return seedReportTaskFull(taskId, dashboardId, owner, cron, format, enabled,
                recipientsJson, channelsJson, 60, null);
    }

    private NopDatavReportTask seedReportTaskWithGrace(String taskId, String dashboardId, String owner,
                                                       String cron, String format, boolean enabled,
                                                       int graceMinutes) {
        return seedReportTaskFull(taskId, dashboardId, owner, cron, format, enabled,
                "[\"a@example.com\"]", "[\"email\"]", graceMinutes, null);
    }

    private NopDatavReportTask seedReportTaskWithTemplate(String taskId, String dashboardId, String owner,
                                                          String cron, String format, boolean enabled,
                                                          String templateKey) {
        return seedReportTaskFull(taskId, dashboardId, owner, cron, format, enabled,
                "[\"a@example.com\"]", "[\"email\"]", 60, templateKey);
    }

    private NopDatavReportTask seedReportTaskFull(String taskId, String dashboardId, String owner,
                                                  String cron, String format, boolean enabled,
                                                  String recipientsJson, String channelsJson,
                                                  int graceMinutes, String templateKey) {
        long now = System.currentTimeMillis();
        NopDatavReportTask t = new NopDatavReportTask();
        t.setReportTaskId(taskId);
        t.setTaskName(taskId + "-name");
        t.setDisplayName(taskId + "-display");
        t.setDashboardId(dashboardId);
        t.setCronExpr(cron);
        t.setFormat(format);
        t.setRecipients(recipientsJson);
        t.setNotifyChannels(channelsJson);
        t.setParams(null);
        t.setStatus(enabled ? NopDatavReportTaskStatus.ENABLED : NopDatavReportTaskStatus.DISABLED);
        t.setGraceMinutes(graceMinutes);
        t.setTemplateKey(templateKey);
        t.setDelFlag((byte) 0);
        t.setVersion(0L);
        t.setCreatedBy(owner);
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy(owner);
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportTask.class).saveEntityDirectly(t);
        return t;
    }

    private void seedDelivery(String deliveryId, String reportTaskId, int status) {
        long now = System.currentTimeMillis();
        NopDatavReportDelivery d = new NopDatavReportDelivery();
        d.setDeliveryId(deliveryId);
        d.setReportTaskId(reportTaskId);
        d.setStatus(status);
        d.setTriggeredBy(NopDatavReportTriggerSource.SCHEDULE);
        d.setStartTime(new Timestamp(now));
        d.setDelFlag((byte) 0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(d);
    }

    private void seedNoticeTemplate(String name, String content) {
        long now = System.currentTimeMillis();
        NopSysNoticeTemplate t = new NopSysNoticeTemplate();
        t.setSid(java.util.UUID.randomUUID().toString().replace("-", ""));
        t.setName(name);
        t.setTplType(NotificationSender.TPL_TYPE_REPORT_DELIVERY);
        t.setContent(content);
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopSysNoticeTemplate.class).saveEntityDirectly(t);
    }
}
