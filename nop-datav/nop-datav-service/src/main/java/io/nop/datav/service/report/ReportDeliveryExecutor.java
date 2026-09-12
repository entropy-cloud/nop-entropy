package io.nop.datav.service.report;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.file.core.IFileStore;
import io.nop.file.core.UploadRequestBean;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.export.PanelDataExporter;

import jakarta.inject.Inject;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_EXPORT_FILE_MAX_LENGTH;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_MAX_ROWS;
import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ARG_REPORT_TASK_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_DELIVERY_FAILED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_REPORT_TASK_NOT_FOUND;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 定时报告交付执行器（D5-1）。
 *
 * <p>职责：插 pending 交付记录 → 异步执行（{@link GlobalExecutors#globalWorker()}）→
 * <b>session 内</b>（{@link IOrmTemplate#runInNewSession}）：加载任务 → 校验看板已发布 →
 * 复用 {@link PanelDataExporter#exportDashboard} 取数生成文件 → {@link IFileStore#saveFile} 持久化 →
 * 交付记录 SUCCEEDED 提交；<b>session 关闭后</b>：{@link NotificationSender#sendReport}（SMTP）送达 →
 * 成功回写 deliveredChannels / 失败回补 FAILED。Throwable → 交付记录 failed + errorMessage。</p>
 *
 * <p><b>SMTP 移出 session（Dim14-02）</b>：{@link NotificationSender#sendReport}（同步 SMTP，典型超时 30-60s）
 * 不再在持有 JDBC 连接的 ORM session 内执行——SUCCEEDED 先提交，邮件后发，避免并发 cron 下连接池耗尽，
 * 并消除「邮件已发但记录未更新」窗口。邮件失败时经独立新 session 强制回补 SUCCEEDED → FAILED。</p>
 *
 * <p><b>grace 语义（schedule-report-design.md §5）</b>：触发时若距预定时间超过 graceMinutes 则写
 * skipped 记录（显式记录，非静默跳过）。cron 触发时 scheduledFireTime ≈ now，grace 主要兜底
 * 手动/恢复场景的延迟执行。</p>
 *
 * <p><b>取数契约（schedule-report-design.md §7）</b>：报告渲染基于当前已发布看板（复用
 * {@code exportDashboard(dashboardId, params, maxRows)} 查当前 panel 表实时数据），不经 snapshotVersion
 * （固定版本渲染列 follow-up）。</p>
 *
 * <p><b>异步执行模式</b>：镜像 {@code NopDatavExportTaskBizModel.submitExecution} ——
 * {@code GlobalExecutors.globalWorker().submit(...)}；session 内执行体捕获业务异常并落库 failed；
 * 外层仅兜底 session 基础设施故障。</p>
 *
 * <p><b>AR-2 异步提交时序（plan 2026-08-15-2146-2 Phase 2）</b>：事务上下文内（{@code triggerReportNow}
 * 的 @BizMutation 经 GraphQL 事务装饰器）worker 提交经 {@code ITransactionTemplate.afterCommit} 注册，
 * pending 交付行 commit 后才被消费；无事务上下文（cron 路径）保持立即提交（注册前
 * {@code isTransactionOpened} 守护）。worker 首查 null 分支带短退避重查，重查后仍缺行则 ERROR 日志 +
 * {@code markFailedSafe} 显式失败（禁止静默 no-op）。</p>
 */
public class ReportDeliveryExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(ReportDeliveryExecutor.class);

    private static final String BIZ_OBJ_NAME = "nopDatavReportTask";

    /** AR-2 短退避重查参数：最多 3 次 × 200ms（与 NopDatavExportTaskBizModel 对称）。 */
    private static final int DELIVERY_ROW_MAX_RETRIES = 3;
    private static final long DELIVERY_ROW_RETRY_INTERVAL_MS = 200L;

    private final IDaoProvider daoProvider;
    private final IOrmTemplate ormTemplate;
    private final IJdbcTemplate jdbcTemplate;
    private final IFileStore fileStore;
    private final NotificationSender notificationSender;
    private final ITransactionTemplate transactionTemplate;

    /**
     * AR-2 测试 seam：异步执行提交（globalWorker().submit 前置）实际触发次数。
     * 断言「事务内注册未触发 / commit 后触发 / 回滚不触发 / 无事务立即提交」的时序锚点
     * （package-private getter，仅测试可见）。
     */
    private final AtomicInteger submitExecutionInvokedCount = new AtomicInteger();

    @Inject
    public ReportDeliveryExecutor(IDaoProvider daoProvider, IOrmTemplate ormTemplate,
                                  IJdbcTemplate jdbcTemplate, IFileStore fileStore,
                                  NotificationSender notificationSender,
                                  ITransactionTemplate transactionTemplate) {
        this.daoProvider = daoProvider;
        this.ormTemplate = ormTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStore = fileStore;
        this.notificationSender = notificationSender;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 执行一次报告交付（异步）。
     *
     * <p>同步插 pending 交付记录（返回 deliveryId 供调用方追踪），异步执行取数 + 送达 + 更新记录。
     * {@code NopDatavReportScheduler.executeScheduledReport} 调用时传 {@code triggerSource=schedule}；
     * {@code NopDatavReportTaskBizModel.triggerReportNow} 调用时传 {@code triggerSource=manual}。</p>
     *
     * @param reportTaskId       报告任务 ID
     * @param triggerSource      触发来源（{@link NopDatavReportTriggerSource}）
     * @param scheduledFireTime  预定触发时间（用于 grace 检查；cron 触发时取 now）
     * @return 交付记录 ID
     */
    public String execute(String reportTaskId, String triggerSource, long scheduledFireTime) {
        IEntityDao<NopDatavReportTask> taskDao = daoProvider.daoFor(NopDatavReportTask.class);
        NopDatavReportTask task = taskDao.getEntityById(reportTaskId);
        if (task == null) {
            throw new NopException(ERR_DATAV_REPORT_TASK_NOT_FOUND).param(ARG_REPORT_TASK_ID, reportTaskId);
        }

        // 插 pending 交付记录（同步，立即返回 deliveryId）
        Timestamp now = CoreMetrics.currentTimestamp();
        NopDatavReportDelivery delivery = new NopDatavReportDelivery();
        delivery.setDeliveryId(StringHelper.generateUUID());
        delivery.setReportTaskId(reportTaskId);
        delivery.setStatus(NopDatavReportDeliveryStatus.PENDING);
        delivery.setTriggeredBy(triggerSource);
        delivery.setStartTime(now);
        delivery.setDelFlag((byte) 0);
        delivery.setVersion(0L);
        delivery.setCreatedBy(task.getCreatedBy());
        delivery.setCreateTime(now);
        delivery.setUpdatedBy(task.getUpdatedBy());
        delivery.setUpdateTime(now);
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(delivery);

        // 异步执行（AR-2：事务上下文内经 afterCommit 注册，commit 后 worker 才消费 pending 行）
        String deliveryId = delivery.getDeliveryId();
        submitExecutionAfterCommit(reportTaskId, deliveryId, triggerSource, scheduledFireTime);

        return deliveryId;
    }

    /**
     * AR-2 修复：异步提交时序——事务上下文内（{@code triggerReportNow} 为 @BizMutation，经 GraphQL
     * 事务装饰器持有 REQUIRED 事务）经 {@code afterCommit} 注册 worker 提交，pending 交付行 commit 后
     * 才被消费，消除 READ_COMMITTED 下「worker 新 session 首查 null → 静默 return」竞态（交付静默不执行）；
     * 事务回滚时 onAfterCommit 不触发（语义恰好正确：回滚则不提交异步任务）。无事务上下文
     * （cron 路径 {@code NopDatavReportScheduler.executeScheduledReport}）保持立即提交——注册前经
     * {@code isTransactionOpened} 守护，避免 {@code ERR_TXN_NOT_IN_TRANSACTION}。
     */
    private void submitExecutionAfterCommit(String reportTaskId, String deliveryId,
                                            String triggerSource, long scheduledFireTime) {
        if (transactionTemplate != null && transactionTemplate.isTransactionOpened(null)) {
            transactionTemplate.afterCommit(null,
                    () -> submitExecution(reportTaskId, deliveryId, triggerSource, scheduledFireTime));
        } else {
            submitExecution(reportTaskId, deliveryId, triggerSource, scheduledFireTime);
        }
    }

    private void submitExecution(String reportTaskId, String deliveryId, String triggerSource, long scheduledFireTime) {
        submitExecutionInvokedCount.incrementAndGet();
        GlobalExecutors.globalWorker().submit(() -> {
            try {
                // SMTP 送达在 session 关闭后执行（Dim14-02：不在持有 JDBC 连接的 ORM session 内做远程调用）
                runDelivery(reportTaskId, deliveryId, triggerSource, scheduledFireTime);
            } catch (Exception e) {
                LOG.error("nop.datav.report.session-fail:reportTaskId={} deliveryId={}",
                        reportTaskId, deliveryId, e);
                markFailedSafe(deliveryId, "async session error: " + safeMsg(e));
            }
            return null;
        });
    }

    // ============================================================
    // 异步执行体
    // ============================================================

    /**
     * 完整交付执行：session 内取数/落盘 + SUCCEEDED 提交 → session 关闭后 SMTP 送达。
     *
     * <p><b>Dim14-02 修复</b>：{@link NotificationSender#sendReport}（同步 SMTP，典型超时 30-60s）不再在
     * 持有 JDBC 连接的 ORM session 内执行，避免并发 cron 下连接池耗尽。交付记录先提交 SUCCEEDED，
     * 邮件后发；邮件失败回补 FAILED（经独立新 session）。</p>
     *
     * <p>供 {@link #execute}（异步）与 {@link #executeSyncForTest}（同步）共用，确保两条路径行为一致。</p>
     */
    private void runDelivery(String reportTaskId, String deliveryId, String triggerSource, long scheduledFireTime) {
        // Part A：session 内完成取数/落盘/SUCCEEDED 提交（返回送达所需快照，或 null 表示无需/未能送达）
        PreparedDelivery prepared = ormTemplate.runInNewSession(session ->
                runDeliveryInSession(session, reportTaskId, deliveryId, triggerSource, scheduledFireTime));
        // Part B：session 已关闭（JDBC 连接已释放），SMTP 送达不再持有连接
        if (prepared != null) {
            sendNotificationOutOfSession(prepared, reportTaskId, deliveryId);
        }
    }

    /**
     * session 内执行体：加载 → grace → running → 校验 → 取数 → 落盘 → SUCCEEDED 提交。
     * 成功返回送达所需快照（task + delivery，含 generatedFileRecordId/rowCount）；skipped/failed 返回 null。
     */
    private PreparedDelivery runDeliveryInSession(IOrmSession session, String reportTaskId, String deliveryId,
                                                  String triggerSource, long scheduledFireTime) {
        IEntityDao<NopDatavReportDelivery> deliveryDao = daoProvider.daoFor(NopDatavReportDelivery.class);
        IEntityDao<NopDatavReportTask> taskDao = daoProvider.daoFor(NopDatavReportTask.class);
        NopDatavReportDelivery delivery = waitForDeliveryRow(deliveryDao, deliveryId);
        if (delivery == null) {
            // AR-2 可观测性：禁止静默 no-op。正常情况下 afterCommit 时序保证行必然可见；
            // 此分支只剩基础设施异常（如读副本延迟）——短退避重查后仍缺行则显式失败。
            LOG.error("nop.datav.report.delivery-row-not-visible-after-retries:reportTaskId={} deliveryId={}"
                    + " (worker cannot load pending row)", reportTaskId, deliveryId);
            markFailedSafe(deliveryId, "report delivery row not visible to worker after retries");
            return null;
        }
        NopDatavReportTask task = taskDao.getEntityById(reportTaskId);
        if (task == null) {
            markFailed(deliveryDao, delivery, "report task not found: " + reportTaskId);
            return null;
        }

        // grace 检查（misfire 兜底；超期写 skipped 显式记录）
        int graceMinutes = task.getGraceMinutes() == null
                ? CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES.get() : task.getGraceMinutes();
        long nowMs = CoreMetrics.currentTimeMillis();
        long graceMs = (long) graceMinutes * 60L * 1000L;
        if (nowMs - scheduledFireTime > graceMs) {
            markSkipped(deliveryDao, delivery,
                    "skipped due to misfire grace exceeded (scheduledFireTime=" + scheduledFireTime
                            + ", now=" + nowMs + ", graceMinutes=" + graceMinutes + ")");
            touchTask(taskDao, task, NopDatavReportDeliveryStatus.SKIPPED,
                    delivery.getErrorMsg(), null);
            return null;
        }

        // pending → running
        delivery.setStatus(NopDatavReportDeliveryStatus.RUNNING);
        touchDelivery(delivery, task.getUpdatedBy());
        deliveryDao.updateEntityDirectly(delivery);

        try {
            // 校验看板已发布（无发布快照抛错 → failed）
            requirePublishableDashboard(task.getDashboardId());

            // 取数（复用 PanelDataExporter.exportDashboard，查当前 panel 表实时数据）
            PanelDataExporter exporter = new PanelDataExporter(daoProvider, jdbcTemplate);
            int maxRows = CFG_DATAV_REPORT_MAX_ROWS.get();
            @SuppressWarnings("unchecked")
            Map<String, Object> params = StringHelper.isEmpty(task.getParams())
                    ? null : JsonTool.parseMap(task.getParams());
            PanelDataExporter.ExportFile file = exporter.exportDashboard(task.getDashboardId(), params, maxRows);

            // 落盘
            String fileId = saveReportFile(file, reportTaskId);
            delivery.setGeneratedFileRecordId(fileId);
            delivery.setRowCount(file.getRowCount());

            // SUCCEEDED 提交（在 session 内，先于邮件送达）。deliveredChannels 待邮件送达成功后回写。
            delivery.setStatus(NopDatavReportDeliveryStatus.SUCCEEDED);
            delivery.setErrorMsg(null);
            delivery.setEndTime(CoreMetrics.currentTimestamp());
            touchDelivery(delivery, task.getUpdatedBy());
            deliveryDao.updateEntityDirectly(delivery);

            touchTask(taskDao, task, NopDatavReportDeliveryStatus.SUCCEEDED, null, delivery.getEndTime());

            return new PreparedDelivery(task, delivery);
        } catch (Exception e) {
            Throwable reason = NopException.adapt(e);
            String errMsg = safeMsg(reason);
            delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
            delivery.setErrorMsg(errMsg);
            delivery.setEndTime(CoreMetrics.currentTimestamp());
            touchDelivery(delivery, task.getUpdatedBy());
            deliveryDao.updateEntityDirectly(delivery);
            touchTask(taskDao, task, NopDatavReportDeliveryStatus.FAILED, errMsg, delivery.getEndTime());
            LOG.warn("nop.datav.report.delivery-failed:reportTaskId={} deliveryId={} error={}",
                    reportTaskId, deliveryId, errMsg, reason);
            return null;
        }
    }

    /**
     * session 外 SMTP 送达（Dim14-02）：SUCCEEDED 已提交，此处发邮件。
     * 成功 → 回写 deliveredChannels（新 session）；失败 → 回补 FAILED（新 session）。
     */
    private void sendNotificationOutOfSession(PreparedDelivery prepared, String reportTaskId, String deliveryId) {
        List<String> delivered;
        try {
            delivered = notificationSender.sendReport(prepared.task, prepared.delivery, null);
        } catch (Exception e) {
            Throwable reason = NopException.adapt(e);
            String errMsg = safeMsg(reason);
            LOG.warn("nop.datav.report.notify-failed:reportTaskId={} deliveryId={} error={}",
                    reportTaskId, deliveryId, errMsg, reason);
            // 邮件失败回补 FAILED（SUCCEEDED 已提交，故需强制覆盖 SUCCEEDED → FAILED）
            rollbackSucceededToFailed(deliveryId, reportTaskId, "notification failed: " + errMsg);
            return;
        }
        // 成功 → 回写 deliveredChannels（cosmetic，失败仅 warn，不改变 SUCCEEDED 终态）
        if (delivered != null && !delivered.isEmpty()) {
            recordDeliveredChannels(deliveryId, StringHelper.join(delivered, ","));
        }
    }

    /** 新 session 回写 deliveredChannels（仅当记录仍为 SUCCEEDED）。 */
    private void recordDeliveredChannels(String deliveryId, String channels) {
        try {
            ormTemplate.runInNewSession(session -> {
                IEntityDao<NopDatavReportDelivery> dao = daoProvider.daoFor(NopDatavReportDelivery.class);
                NopDatavReportDelivery delivery = dao.getEntityById(deliveryId);
                if (delivery != null && delivery.getStatus() == NopDatavReportDeliveryStatus.SUCCEEDED) {
                    delivery.setDeliveredChannels(channels);
                    touchDelivery(delivery, "system");
                    dao.updateEntityDirectly(delivery);
                }
                return null;
            });
        } catch (Exception e) {
            LOG.warn("nop.datav.report.record-channels-fail:deliveryId={}", deliveryId, e);
        }
    }

    /**
     * 新 session 强制回补 SUCCEEDED → FAILED（邮件送达失败专用，覆盖 SUCCEEDED 终态）。
     * 同时回退 task.lastRunStatus，保持交付记录与任务状态一致。
     */
    private void rollbackSucceededToFailed(String deliveryId, String reportTaskId, String reason) {
        try {
            ormTemplate.runInNewSession(session -> {
                IEntityDao<NopDatavReportDelivery> deliveryDao = daoProvider.daoFor(NopDatavReportDelivery.class);
                IEntityDao<NopDatavReportTask> taskDao = daoProvider.daoFor(NopDatavReportTask.class);
                NopDatavReportDelivery delivery = deliveryDao.getEntityById(deliveryId);
                Timestamp endTime = CoreMetrics.currentTimestamp();
                if (delivery != null && delivery.getStatus() == NopDatavReportDeliveryStatus.SUCCEEDED) {
                    delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
                    delivery.setErrorMsg(reason);
                    delivery.setEndTime(endTime);
                    touchDelivery(delivery, "system");
                    deliveryDao.updateEntityDirectly(delivery);
                }
                NopDatavReportTask task = taskDao.getEntityById(reportTaskId);
                if (task != null) {
                    touchTask(taskDao, task, NopDatavReportDeliveryStatus.FAILED, reason, endTime);
                }
                return null;
            });
        } catch (Exception e) {
            LOG.error("nop.datav.report.rollback-failed-fail:deliveryId={}", deliveryId, e);
        }
    }

    /** session 内 SUCCEEDED 提交后供 session 外 SMTP 送达使用的快照。 */
    private static final class PreparedDelivery {
        final NopDatavReportTask task;
        final NopDatavReportDelivery delivery;

        PreparedDelivery(NopDatavReportTask task, NopDatavReportDelivery delivery) {
            this.task = task;
            this.delivery = delivery;
        }
    }

    // ============================================================
    // 取数 / 文件落盘
    // ============================================================

    private void requirePublishableDashboard(String dashboardId) {
        // 看板记录必须存在
        NopDatavDashboard dashboard = daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardId);
        if (dashboard == null) {
            throw new NopException(ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD)
                    .param(ARG_DASHBOARD_ID, dashboardId);
        }
        // 至少有一个发布快照（证明已发布过）
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        q.setLimit(1);
        NopDatavDashboardSnapshot snapshot = daoProvider.daoFor(NopDatavDashboardSnapshot.class)
                .findFirstByQuery(q);
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD)
                    .param(ARG_DASHBOARD_ID, dashboardId);
        }
    }

    private String saveReportFile(PanelDataExporter.ExportFile file, String reportTaskId) {
        IResource resource = file.getResource();
        long length = resource.length() > 0 ? resource.length() : file.getRowCount();
        InputStream is = null;
        try {
            is = resource.getInputStream();
            UploadRequestBean bean = new UploadRequestBean(
                    is, file.getFileName(), length, file.getMimeType());
            bean.setBizObjName(BIZ_OBJ_NAME);
            bean.setBizObjId(reportTaskId);
            return fileStore.saveFile(bean, CFG_DATAV_EXPORT_FILE_MAX_LENGTH.get());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                    LOG.debug("nop.datav.report.close-stream-fail", e);
                }
            }
            try {
                resource.delete();
            } catch (Exception e) {
                LOG.debug("nop.datav.report.delete-temp-fail", e);
            }
        }
    }

    // ============================================================
    // 状态更新 helpers
    // ============================================================

    private void markSkipped(IEntityDao<NopDatavReportDelivery> dao, NopDatavReportDelivery delivery, String reason) {
        delivery.setStatus(NopDatavReportDeliveryStatus.SKIPPED);
        delivery.setErrorMsg(reason);
        delivery.setEndTime(CoreMetrics.currentTimestamp());
        touchDelivery(delivery, "system");
        dao.updateEntityDirectly(delivery);
    }

    /**
     * AR-2 短退避重查：worker 新 session 首查 miss 时按 {@link #DELIVERY_ROW_RETRY_INTERVAL_MS}
     * 间隔重查至多 {@link #DELIVERY_ROW_MAX_RETRIES} 次（与 {@code NopDatavExportTaskBizModel.waitForTaskRow}
     * 对称）。dao 的 miss 不落 session 缓存，每次重查都直达数据库。
     */
    private static NopDatavReportDelivery waitForDeliveryRow(IEntityDao<NopDatavReportDelivery> dao,
                                                              String deliveryId) {
        NopDatavReportDelivery delivery = dao.getEntityById(deliveryId);
        for (int i = 0; i < DELIVERY_ROW_MAX_RETRIES && delivery == null; i++) {
            try {
                Thread.sleep(DELIVERY_ROW_RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            delivery = dao.getEntityById(deliveryId);
        }
        return delivery;
    }

    private void markFailed(IEntityDao<NopDatavReportDelivery> dao, NopDatavReportDelivery delivery, String reason) {
        delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
        delivery.setErrorMsg(reason);
        delivery.setEndTime(CoreMetrics.currentTimestamp());
        touchDelivery(delivery, "system");
        dao.updateEntityDirectly(delivery);
    }

    private void markFailedSafe(String deliveryId, String reason) {
        try {
            ormTemplate.runInNewSession(session -> {
                IEntityDao<NopDatavReportDelivery> dao = daoProvider.daoFor(NopDatavReportDelivery.class);
                NopDatavReportDelivery delivery = dao.getEntityById(deliveryId);
                if (delivery != null && !NopDatavReportDeliveryStatus.isTerminal(delivery.getStatus())) {
                    delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
                    delivery.setErrorMsg(reason);
                    delivery.setEndTime(CoreMetrics.currentTimestamp());
                    touchDelivery(delivery, "system");
                    dao.updateEntityDirectly(delivery);
                }
                return null;
            });
        } catch (Exception e) {
            LOG.error("nop.datav.report.mark-failed-fail:deliveryId={}", deliveryId, e);
        }
    }

    private void touchDelivery(NopDatavReportDelivery delivery, String operator) {
        delivery.setUpdatedBy(operator);
        delivery.setUpdateTime(CoreMetrics.currentTimestamp());
    }

    private void touchTask(IEntityDao<NopDatavReportTask> dao, NopDatavReportTask task,
                           int deliveryStatus, String errMsg, Timestamp runTime) {
        task.setLastRunStatus(NopDatavReportDeliveryStatus.label(deliveryStatus));
        task.setLastRunError(errMsg);
        task.setLastRunTime(runTime == null ? CoreMetrics.currentTimestamp() : runTime);
        task.setUpdatedBy("system");
        task.setUpdateTime(CoreMetrics.currentTimestamp());
        dao.updateEntityDirectly(task);
    }

    private static String safeMsg(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

        /**
         * 测试辅助：直接同步执行 runDelivery（绕过 GlobalExecutors 异步），供测试断言交付记录状态。
         *
         * <p>契约（session 拆分后）：同步执行 runDelivery（session 内 SUCCEEDED 提交 → session 外 SMTP 送达），
         * 与异步 {@link #execute} 行为一致；返回时邮件已发（或回补 FAILED）。</p>
         */
        public String executeSyncForTest(String reportTaskId, String triggerSource, long scheduledFireTime) {
            IEntityDao<NopDatavReportTask> taskDao = daoProvider.daoFor(NopDatavReportTask.class);
            NopDatavReportTask task = taskDao.getEntityById(reportTaskId);
            if (task == null) {
                throw new NopException(ERR_DATAV_REPORT_TASK_NOT_FOUND).param(ARG_REPORT_TASK_ID, reportTaskId);
            }

            Timestamp now = CoreMetrics.currentTimestamp();
            NopDatavReportDelivery delivery = new NopDatavReportDelivery();
            delivery.setDeliveryId(StringHelper.generateUUID());
            delivery.setReportTaskId(reportTaskId);
            delivery.setStatus(NopDatavReportDeliveryStatus.PENDING);
            delivery.setTriggeredBy(triggerSource);
            delivery.setStartTime(now);
            delivery.setDelFlag((byte) 0);
            delivery.setVersion(0L);
            delivery.setCreatedBy(task.getCreatedBy());
            delivery.setCreateTime(now);
            delivery.setUpdatedBy(task.getUpdatedBy());
            delivery.setUpdateTime(now);
            daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(delivery);

            try {
                runDelivery(reportTaskId, delivery.getDeliveryId(), triggerSource, scheduledFireTime);
            } catch (Exception e) {
                markFailedSafe(delivery.getDeliveryId(), "sync session error: " + safeMsg(e));
            }
            return delivery.getDeliveryId();
        }

    /** 测试辅助：暴露 IOrmTemplate（断言新 session 路径） */
    public IOrmTemplate getOrmTemplate() {
        return ormTemplate;
    }

    /**
     * AR-2 测试 seam 读取：worker 提交（globalWorker().submit 前置）实际触发次数
     * （测试辅助，与 {@link #getOrmTemplate()} 同可见性约定）。
     */
    public int getSubmitExecutionInvokedCountForTest() {
        return submitExecutionInvokedCount.get();
    }
}
