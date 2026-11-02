package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CL-019 调度注销副作用 afterCommit 后置红⑫钉（plan 2526 裁定⑫，C10 缺保护）。
 *
 * <p>缺陷机制：级联删除在事务体内先 unregister（进程内调度状态不随 DB 事务回滚）、
 * 后续步骤失败回滚 → DB 行恢复 ENABLED 而 cron job 已消失 → 定时报告静默停摆。
 * 本钉以真调度器注册表（getRegisteredJobNames）为观察面：</p>
 *
 * <ul>
 *   <li><b>回滚钉（红⑫ 主断言，构造性当前即红）</b>：事务内走完整级联删除后强制回滚 →
 *       断言 job 仍在注册表（afterCommit 未触发）+ 行恢复 ENABLED——修复前注销在事务体内
 *       同步发生，回滚后 job 丢失，本断言确定性失败。</li>
 *   <li><b>提交钉（接线正向）</b>：同路径正常提交 → afterCommit 触发，job 注销、行删除。</li>
 * </ul>
 *
 * <p>TestNopDatavDeleteLifecycle 既有即时注销钉（无事务直行路径）零回退——本钉与之互补，
 * 分别覆盖「有事务 afterCommit」与「无事务直接执行」两分支。</p>
 */
public class TestNopDatavSchedulerAfterCommit_CL019 extends AbstractNopDatavTest {

    private static final ErrorCode ERR_FORCED_ROLLBACK =
            ErrorCode.define("nop.err.datav.test-cl019-forced-rollback", "test forced rollback (CL-019)");

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    NopDatavReportScheduler reportScheduler;

    /**
     * 回滚恢复钉（红⑫ 主断言）：级联删除排入 afterCommit 的注销在事务回滚后不发生——
     * job 保留、行恢复 ENABLED（修复前：注销同步发生 → 回滚后 job 丢失 → 静默停摆，本断言红）。
     */
    @Test
    public void testRollbackAfterCascadeKeepsCronJobRegistered() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-cl019-rollback", "cl019-rollback", "alice");
        NopDatavReportTask task = seedReportTask("task-cl019-rollback", dash.getDashboardId());
        reportScheduler.registerTask(task.getReportTaskId());
        String jobName = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName),
                "precondition: report cron job registered");

        assertThrows(NopException.class, () -> transactionTemplate.runInTransaction(txn -> {
            ormTemplate.runInSession(session -> {
                assertTrue(dashboardBiz.delete(dash.getDashboardId(), ownerCtx),
                        "cascade delete inside transaction");
                return null;
            });
            throw new NopException(ERR_FORCED_ROLLBACK);
        }));

        // 回滚终态：afterCommit 不触发 → job 保留（与恢复后的 ENABLED 行一致）
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName),
                "CL-019: rollback must NOT unregister the cron job (deferred side effect), registry="
                        + reportScheduler.getRegisteredJobNames());
        assertNotNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dash.getDashboardId()),
                "dashboard row restored by rollback");
        NopDatavReportTask restored = daoProvider.daoFor(NopDatavReportTask.class)
                .getEntityById(task.getReportTaskId());
        assertNotNull(restored, "report task row restored by rollback");
        assertEquals((int) NopDatavReportTaskStatus.ENABLED, restored.getStatus(),
                "restored row stays ENABLED and the job is still there (no silent stall divergence)");
    }

    /**
     * 提交钉（接线正向，rule 23）：同路径正常提交 → afterCommit 触发 → job 注销 + 行删除。
     * 证明注销没有被无限期推迟（不是「只 defer 不 fire」的空壳）。
     */
    @Test
    public void testCommitAfterCascadeUnregistersCronJob() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-cl019-commit", "cl019-commit", "alice");
        NopDatavReportTask task = seedReportTask("task-cl019-commit", dash.getDashboardId());
        reportScheduler.registerTask(task.getReportTaskId());
        String jobName = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName),
                "precondition: report cron job registered");

        transactionTemplate.runInTransaction(txn -> {
            ormTemplate.runInSession(session -> {
                assertTrue(dashboardBiz.delete(dash.getDashboardId(), ownerCtx));
                return null;
            });
            return null;
        });

        assertFalse(reportScheduler.getRegisteredJobNames().contains(jobName),
                "CL-019: commit fires afterCommit → cron job unregistered, registry="
                        + reportScheduler.getRegisteredJobNames());
        assertNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dash.getDashboardId()),
                "dashboard row deleted on commit");
        // 级联语义=置DISABLED（非物理删除）：删除生命周期级联把关联 ReportTask 置 DISABLED
        NopDatavReportTask after = daoProvider.daoFor(NopDatavReportTask.class)
                .getEntityById(task.getReportTaskId());
        assertNotNull(after, "report task row survives delete (cascade disables, not deletes)");
        assertEquals(io.nop.datav.service.report.NopDatavReportTaskStatus.DISABLED, after.getStatus(),
                "report task row disabled on commit");
    }

    // ==================== Helpers（沿 TestNopDatavDeleteLifecycle 形态） ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavDashboard saveDashboard(String id, String name, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private NopDatavReportTask seedReportTask(String taskId, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavReportTask t = new NopDatavReportTask();
        t.setReportTaskId(taskId);
        t.setTaskName(taskId + "-name");
        t.setDisplayName(taskId + "-display");
        t.setDashboardId(dashboardId);
        t.setCronExpr("0 0 8 * * ?");
        t.setFormat("csv");
        t.setRecipients("[\"a@example.com\"]");
        t.setNotifyChannels("[\"email\"]");
        t.setStatus(NopDatavReportTaskStatus.ENABLED);
        t.setGraceMinutes(60);
        t.setVersion(0L);
        t.setCreatedBy("alice");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("alice");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportTask.class).saveEntityDirectly(t);
        return t;
    }
}
