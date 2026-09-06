package io.nop.task.impl;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.ioc.StaticBeanContainer;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.FullSnapshotTaskStateStore;
import io.nop.task.utils.TaskStepHelper;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 349 Phase 1：SUSPEND 契约化回归。
 *
 * <p>覆盖分析报告确认的 8 处挂起破坏点中可端到端验证的部分：
 * <ul>
 *   <li>isSuspend 值判断契约（BuildOutput 包装层重建返回值不再丢失挂起语义）；</li>
 *   <li>TaskImpl 挂起驱动 SUSPENDED（修复 check2 P0-2：曾被置 COMPLETED 且永不可恢复）；</li>
 *   <li>suspend 步骤缺省 metrics 不 NPE（check2 P0-1）；</li>
 *   <li>声明 outputs 的挂起步骤不被 BuildOutput 吞掉；</li>
 *   <li>Sequential/Selector 异步挂起透传；</li>
 *   <li>fork/parallel/graph 分支挂起传播；</li>
 *   <li>retry 包装的挂起步骤不被 succeed 标成 COMPLETED；</li>
 *   <li>挂起→resume 端到端续跑（经 FullSnapshotTaskStateStore 模拟 stateBean 持久化）。</li>
 * </ul>
 */
public class TestSuspendContract extends AbstractTaskTestCase {

    private FullSnapshotTaskStateStore store;

    @BeforeEach
    public void setUpStore() {
        store = new FullSnapshotTaskStateStore();
        TaskFlowManagerImpl manager = (TaskFlowManagerImpl) taskFlowManager;
        // fresh execute 与 resume 共用同一 snapshot store，模拟持久化 round-trip
        manager.setNonPersistStateStore(store);
        manager.setTaskStateStore(store);

        StaticBeanContainer beans = new StaticBeanContainer();
        beans.registerBean("myExecutor", GlobalExecutors.globalWorker());
        BeanContainer.registerInstance(beans);
    }

    private ExecResult execute(String taskName) throws Exception {
        ITask task = taskFlowManager.getTask(taskName, 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        TaskStepReturn ret = task.execute(taskRt);
        // executor 包装的异步挂起：execute 返回时 promise 可能尚未完成（suspend 步骤在
        // globalWorker 线程排队），同步等待其完成值（SUSPEND）再断言。此前未等待就断言
        // isSuspend()，负载下 promise 未完成时 nextStepName=null 判 false——时序 flaky。
        if (ret.isAsync()) {
            ret = ret.sync();
        }
        return new ExecResult(taskRt, ret);
    }

    private static class ExecResult {
        final ITaskRuntime taskRt;
        final TaskStepReturn ret;

        ExecResult(ITaskRuntime taskRt, TaskStepReturn ret) {
            this.taskRt = taskRt;
            this.ret = ret;
        }
    }

    /**
     * 裸 suspend 步骤：任务挂起（SUSPENDED 非终态），resume 后从挂起点续跑至 COMPLETED。
     * 修复前：TaskImpl 把 SUSPEND 驱动为 COMPLETED（check2 P0-2），resume 被 isTerminal 短路；
     * 裸 suspend 步骤同时命中 check2 P0-1（recordMetrics 缺省时 meter null NPE）。
     */
    @Test
    public void suspendPlain_suspendsAndResumes() throws Exception {
        ExecResult r = execute("test/suspend-plain");
        assertTrue(r.ret.isSuspend(), "task must return SUSPEND");

        String taskInstanceId = r.taskRt.getTaskInstanceId();
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_SUSPENDED),
                store.getLatestTaskSnapshot(taskInstanceId).getTaskStatus(),
                "suspended task must be SUSPENDED, not COMPLETED (check2 P0-2)");

        // resume：挂起步骤经持久化 stateBean（first=true）判定为已挂起过 → resumeWhen null → CONTINUE
        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        TaskStepReturn resumed = taskFlowManager.getTask("test/suspend-plain", 0).execute(resumeRt);
        assertEquals(42, resumed.syncGetResult(), "resume must continue from the suspension point");

        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_COMPLETED),
                store.getLatestTaskSnapshot(taskInstanceId).getTaskStatus(),
                "resumed task must reach COMPLETED");
    }

    /**
     * 声明 outputs 的挂起步骤：不被 BuildOutputTaskStepWrapper 重建为普通返回。
     * 修复前：RETURN("@suspend", outputs) 因 isSuspend 身份判断失配，
     * 步骤被标 COMPLETED 且顺序流抛 ERR_TASK_UNKNOWN_NEXT_STEP。
     */
    @Test
    public void suspendWithOutput_notSwallowedByBuildOutput() throws Exception {
        ExecResult r = execute("test/suspend-with-output");
        assertTrue(r.ret.isSuspend(),
                "suspend step with declared outputs must still suspend");
    }

    /**
     * Sequential 中异步完成的挂起（executor 包装）：必须透传挂起，
     * 修复前落入 getNextIndex("@suspend") 抛 ERR_TASK_UNKNOWN_NEXT_STEP。
     */
    @Test
    public void asyncSuspendInSequential_propagates() throws Exception {
        ExecResult r = execute("test/suspend-sequential-async");
        assertTrue(r.ret.isSuspend(), "async-completed suspend must propagate through SequentialTaskStep");
    }

    /**
     * Selector 中异步完成的挂起：必须透传挂起，
     * 修复前被当作 falsy 静默跳到下一候选（返回 'fallback'）。
     */
    @Test
    public void asyncSuspendInSelector_propagates() throws Exception {
        ExecResult r = execute("test/suspend-selector-async");
        assertTrue(r.ret.isSuspend(),
                "async-completed suspend must propagate through SelectorTaskStep (not skip to next candidate)");
    }

    /**
     * fork 分支挂起：向上传播 SUSPEND，不作为成功聚合项继续执行后继步骤。
     */
    @Test
    public void suspendInFork_propagates() throws Exception {
        ExecResult r = execute("test/suspend-fork");
        assertTrue(r.ret.isSuspend(), "suspended fork branch must propagate SUSPEND (not aggregate as success)");
    }

    /**
     * graph 节点挂起：图以挂起返回值终结，不级联后继 exit 节点。
     */
    @Test
    public void suspendInGraph_propagates() throws Exception {
        ExecResult r = execute("test/suspend-graph");
        assertTrue(r.ret.isSuspend(), "suspended graph node must propagate SUSPEND (not cascade to exit)");
    }

    /**
     * parallel 子步骤挂起：向上传播 SUSPEND，不作为成功聚合项继续执行后继步骤
     * （closure audit 补充：与 fork 对偶的专属用例）。
     */
    @Test
    public void suspendInParallel_propagates() throws Exception {
        ExecResult r = execute("test/suspend-parallel");
        assertTrue(r.ret.isSuspend(), "suspended parallel sub-step must propagate SUSPEND (not aggregate as success)");
    }

    /**
     * retry 包装的挂起步骤：state 不被 succeed 标成 COMPLETED
     * （修复后 resume 时 continuation-skip 不会永久跳过挂起的子流程）。
     */
    @Test
    public void retryWrappedSuspend_notMarkedCompleted() {
        TaskStepStateBean state = new TaskStepStateBean();
        ITaskStepRuntime stepRt = FakeStepRt.of(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, () -> TaskStepReturn.of(null, TaskStepReturn.SUSPEND));

        assertTrue(ret.isSuspend(), "suspend must propagate through retry wrapper");
        assertFalse(state.isDone(),
                "retry-wrapped suspend must not be driven to COMPLETED terminal state "
                        + "(pre-fix: state.succeed made resume continuation-skip skip the suspended subflow forever)");
    }
}
