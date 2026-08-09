package io.nop.datav.service.export;

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
 * 避免在测试环境 schema 尚未建表阶段（容器 init 早于建表）误报。方法幂等，BizModel 在每次发起导出前
 * 也会调用一次以覆盖「重启后未触发 init 即有导出请求」的场景。</p>
 */
public class NopDatavExportTaskRecovery {

    private static final Logger LOG = LoggerFactory.getLogger(NopDatavExportTaskRecovery.class);

    public static final int STATUS_PENDING = NopDatavExportTaskStatus.PENDING;
    public static final int STATUS_RUNNING = NopDatavExportTaskStatus.RUNNING;
    public static final int STATUS_FAILED = NopDatavExportTaskStatus.FAILED;

    public static final String RESTART_REASON = "interrupted by process restart";

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

    private Void doRecover(IOrmSession session) {
        IEntityDao<NopDatavExportTask> dao = daoProvider.daoFor(NopDatavExportTask.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("status", java.util.Arrays.asList(STATUS_PENDING, STATUS_RUNNING)));
        @SuppressWarnings("unchecked")
        List<NopDatavExportTask> stale = (List<NopDatavExportTask>) dao.findAllByQuery(query);
        if (stale.isEmpty()) {
            return null;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
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
