package io.nop.datav.service.export;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.datav.dao.entity.NopDatavExportTask;
import io.nop.orm.IOrmSession;
import io.nop.orm.IOrmTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.sql.Timestamp;
import java.util.List;

/**
 * 进程重启后清理中断的导出任务（D3-3）。
 *
 * <p>JVM 重启后，所有 status ∈ {pending, running} 的任务的执行体（{@code CompletableFuture}）已丢失，
 * 内存 cancel 标志也已消失。本类在 IoC 启动后（{@link #recoverInterruptedTasks()}）将这些任务标记为 failed，
 * 附 reason="interrupted by process restart"，不静默挂起。</p>
 *
 * <p>启动时（{@link PostConstruct}）查询前先经 {@link IJdbcTemplate#existsTable} 判定表是否已建，
 * 避免在测试环境 schema 尚未建表阶段（容器 init 早于建表）误报。方法幂等。全量恢复仅由容器启动期的
 * {@link PostConstruct} 执行一次；请求路径不再调用（audit Dim14-01：per-request 调用会把所有其他
 * 用户/同用户的在途任务误标 FAILED）。周期带阈值的 {@link #scanStuck(int)} 由
 * {@code NopDatavStuckTaskScanner} 调用（时间阈值区分 stuck 与在途，不受 Dim14-01 约束）。
 * {@link io.nop.datav.service.entity.NopDatavExportTaskBizModel}
 * 保留 public {@code recoverInterruptedTasks()} 委托仅供测试/管理直接触发。</p>
 */
public class NopDatavExportTaskRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavExportTaskRecovery.class);

    public static final int STATUS_PENDING = NopDatavExportTaskStatus.PENDING;
    public static final int STATUS_RUNNING = NopDatavExportTaskStatus.RUNNING;
    public static final int STATUS_FAILED = NopDatavExportTaskStatus.FAILED;

    public static final String RESTART_REASON = "interrupted by process restart";

    /** stuck 扫描标记 reason 前缀（完整 reason 为 {@code stuck beyond timeout threshold (Xm)}） */
    public static final String STUCK_REASON_PREFIX = "stuck beyond timeout threshold";

    private final IDaoProvider daoProvider;
    private final IOrmTemplate ormTemplate;
    private final IJdbcTemplate jdbcTemplate;

    @Inject
    public NopDatavExportTaskRecovery(IDaoProvider daoProvider, IOrmTemplate ormTemplate,
                                      IJdbcTemplate jdbcTemplate) {
        this.daoProvider = daoProvider;
        this.ormTemplate = ormTemplate;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            recoverInterruptedTasks();
        } catch (Exception e) {
            LOG.warn("nop.datav.export.recovery-init-fail", e);
        }
    }

    /**
     * 扫描所有 status ∈ {pending, running} 的导出任务，标记为 failed（reason=interrupted by process restart）。
     * 幂等：仅影响非终态任务。
     */
    public void recoverInterruptedTasks() {
        if (!exportTaskTableExists()) {
            return;
        }
        ormTemplate.runInNewSession(this::doRecover);
    }

    private boolean exportTaskTableExists() {
        try {
            return jdbcTemplate.existsTable(null, "nop_datav_export_task");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 周期 stuck 扫描（与重启恢复正交）：仅标记 status ∈ {pending, running} 且
     * {@code createTime < now − timeoutMinutes} 的导出任务为 failed
     * （reason="stuck beyond timeout threshold (Xm)"）。阈值内正常在途任务不动。
     *
     * <p><b>基准字段用 {@code createTime} 而非 {@code updateTime}</b>：导出任务实体无 startTime，
     * createTime 是可用的最早时间戳基准（提交时刻）；updateTime 在每次状态转换时刷新
     * （touchUpdate），任何无关写都会重置时钟，对 stuck 判定不可靠。createTime 含排队等待
     * （提交 → globalWorker 异步执行有延迟），保守高默认阈值（60 min）+ 低并发上限使
     * 「刚出队执行即被误标」场景极罕见（见 schedule-report-design.md §25）。
     * 幂等：仅影响非终态任务。表不存在时安全跳过（返回 0）。</p>
     *
     * @param timeoutMinutes stuck 判定阈值（分钟）
     * @return 本次被标记为 failed 的任务数
     */
    public int scanStuck(int timeoutMinutes) {
        if (!exportTaskTableExists()) {
            LOG.info("nop.datav.export.stuck-scan.table-not-exists: skip scan (table NOP_DATAV_EXPORT_TASK not yet created)");
            return 0;
        }
        Timestamp cutoff = new Timestamp(CoreMetrics.currentTimeMillis() - timeoutMinutes * 60_000L);
        return ormTemplate.runInNewSession(session -> doScanStuck(session, cutoff, timeoutMinutes));
    }

    private int doScanStuck(IOrmSession session, Timestamp cutoff, int timeoutMinutes) {
        IEntityDao<NopDatavExportTask> dao = daoProvider.daoFor(NopDatavExportTask.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("status", java.util.Arrays.asList(STATUS_PENDING, STATUS_RUNNING)));
        query.addFilter(FilterBeans.lt("createTime", cutoff));
        @SuppressWarnings("unchecked")
        List<NopDatavExportTask> stale = (List<NopDatavExportTask>) dao.findAllByQuery(query);
        if (stale.isEmpty()) {
            return 0;
        }
        Timestamp now = CoreMetrics.currentTimestamp();
        String reason = STUCK_REASON_PREFIX + " (" + timeoutMinutes + "m)";
        for (NopDatavExportTask task : stale) {
            task.setStatus(STATUS_FAILED);
            task.setErrorMsg(reason);
            task.setUpdatedBy("system");
            task.setUpdateTime(now);
            dao.updateEntityDirectly(task);
        }
        LOG.info("nop.datav.export.stuck-scan-marked:count={} timeoutMinutes={}", stale.size(), timeoutMinutes);
        return stale.size();
    }

    private Void doRecover(IOrmSession session) {
        IEntityDao<NopDatavExportTask> dao = daoProvider.daoFor(NopDatavExportTask.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("status", java.util.Arrays.asList(STATUS_PENDING, STATUS_RUNNING)));
        @SuppressWarnings("unchecked")
        List<NopDatavExportTask> stale = (List<NopDatavExportTask>) dao.findAllByQuery(query);
        if (stale.isEmpty()) {
            return null;
        }
        Timestamp now = CoreMetrics.currentTimestamp();
        for (NopDatavExportTask task : stale) {
            task.setStatus(STATUS_FAILED);
            task.setErrorMsg(RESTART_REASON);
            task.setUpdatedBy("system");
            task.setUpdateTime(now);
            dao.updateEntityDirectly(task);
        }
        LOG.info("nop.datav.export.recovered-tasks:count={}", stale.size());
        return null;
    }
}
