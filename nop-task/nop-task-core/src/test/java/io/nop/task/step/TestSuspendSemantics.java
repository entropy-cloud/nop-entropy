package io.nop.task.step;

import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.metrics.EmptyTaskFlowMetrics;
import io.nop.task.state.TaskLevelSnapshotTaskStateStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P0-1/P0-2/P1-2/P2-6 回归测试：任务挂起（SUSPEND）语义。
 *
 * <p>覆盖：
 * <ul>
 *   <li>P0-1：步骤级 recordMetrics 缺省 false 时 suspend 出口不因 {@code metrics.endStep(null)} 抛 NPE。</li>
 *   <li>P0-2：mainStep 返回 SUSPEND 时任务进入 SUSPENDED(20)（非 COMPLETED）并持久化，
 *       不 runCleanup、不 endTask；恢复执行不被终态短路。</li>
 *   <li>P1-2：声明了输出的 suspend 步骤不被 BuildOutputTaskStepWrapper 转换为普通返回。</li>
 *   <li>P2-6：恢复路径 runtime（getTaskRuntime）初始化 metrics。</li>
 * </ul>
 */
public class TestSuspendSemantics {

    private TaskFlowManagerImpl taskFlowManager;
    private TaskLevelSnapshotTaskStateStore snapshotStore;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        snapshotStore = new TaskLevelSnapshotTaskStateStore();
        taskFlowManager = new TaskFlowManagerImpl();
        taskFlowManager.setNonPersistStateStore(snapshotStore);
        taskFlowManager.setTaskStateStore(snapshotStore);
    }

    /**
     * P0-1 + P0-2：默认配置（步骤级 recordMetrics=false）下 suspend 步骤返回挂起信号，
     * 任务状态为 SUSPENDED 而非 COMPLETED，且持久化快照反映 SUSPENDED。
     *
     * <p>修复前：TaskStepExecution suspend 出口 {@code metrics.endStep(null, false)} 抛 NPE
     * （fresh 路径 metrics 恒为 TaskFlowMetricsImpl），挂起变成任务失败。
     */
    @Test
    public void suspendStep_returnsSuspendSignal_taskMarkedSuspended() {
        ITask task = taskFlowManager.getTask("test/suspend-01", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        TaskStepReturn ret = task.execute(taskRt);

        assertTrue(ret.isSuspend(),
                "suspend step must return the SUSPEND signal. Pre-fix: metrics.endStep(null) threw NPE "
                        + "under default step-level recordMetrics=false and the task failed instead of suspending.");

        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_SUSPENDED), taskRt.getTaskState().getTaskStatus(),
                "suspended task must be marked SUSPENDED(20), not COMPLETED. "
                        + "Pre-fix: suspend was driven through the COMPLETED driver and persisted as terminal.");
        assertFalse(taskRt.getTaskState().isTerminal(),
                "SUSPENDED must not be terminal, otherwise resume would be short-circuited");

        ITaskState snapshot = snapshotStore.getLatestTaskSnapshot(taskRt.getTaskInstanceId());
        assertNotNull(snapshot, "suspend must save task state");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_SUSPENDED), snapshot.getTaskStatus(),
                "persisted snapshot must reflect SUSPENDED, not COMPLETED");
        assertFalse(snapshot.isTerminal(), "persisted SUSPENDED snapshot must not be terminal");
    }

    /**
     * P0-2 端到端：挂起后经 getTaskRuntime 恢复（recoverMode）——
     * 不被终态短路返回缓存结果；已完成步骤被 continuation-skip 跳过；
     * suspend 步骤 stateBean 未持久化（check2 P1-6 暂缓项）故再次挂起。
     *
     * <p>修复前：任务被持久化为 COMPLETED，恢复命中终态短路返回缓存 null 结果，挂起恢复语义断裂。
     */
    @Test
    public void resume_suspendedTask_notShortCircuited_suspendsAgain() {
        ITask task = taskFlowManager.getTask("test/suspend-01", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        String taskInstanceId = taskRt.getTaskInstanceId();
        assertTrue(task.execute(taskRt).isSuspend(), "first execution must suspend");

        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertNotNull(resumeRt, "resume runtime must load the suspended task state");
        assertFalse(resumeRt.getTaskState().isTerminal(),
                "loaded SUSPENDED state must not be terminal, mainStep must be re-executed");

        // P2-6：恢复路径 runtime 初始化 metrics（与 fresh 路径对称），不再保持 EmptyTaskFlowMetrics
        assertFalse(resumeRt.getMetrics() instanceof EmptyTaskFlowMetrics,
                "resume runtime must initialize metrics. Pre-fix: getTaskRuntime never called setMetrics "
                        + "and metrics stayed EmptyTaskFlowMetrics.INSTANCE.");

        TaskStepReturn ret2 = task.execute(resumeRt);
        assertTrue(ret2.isSuspend(),
                "resume of a suspended task must reach the suspend step again (not short-circuit as COMPLETED). "
                        + "Pre-fix: task was persisted COMPLETED and resume returned the cached terminal result.");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_SUSPENDED), resumeRt.getTaskState().getTaskStatus(),
                "resumed execution that hits the suspend step again must stay SUSPENDED");
    }

    /**
     * P2-6（独立用例）：getTaskRuntime 恢复路径初始化 metrics。
     * 用正常完成的任务避免与 suspend 流程耦合，修复前 runtime metrics 保持 EmptyTaskFlowMetrics.INSTANCE。
     */
    @Test
    public void getTaskRuntime_initializesMetrics() {
        ITask task = taskFlowManager.getTask("test/terminal-state-persist-success", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        String taskInstanceId = taskRt.getTaskInstanceId();
        task.execute(taskRt).syncGetOutputs();

        ITaskRuntime resumeRt = taskFlowManager.getTaskRuntime(taskInstanceId, null, null);
        assertNotNull(resumeRt, "completed task instance must be loadable");
        assertFalse(resumeRt.getMetrics() instanceof EmptyTaskFlowMetrics,
                "resume runtime must initialize metrics (symmetric with newTaskRuntime/prepareTaskRuntime). "
                        + "Pre-fix: getTaskRuntime never called setMetrics and metrics stayed "
                        + "EmptyTaskFlowMetrics.INSTANCE, so resumed executions recorded no metrics.");
    }

    /**
     * P1-2：声明输出的 suspend 步骤保持挂起信号。
     *
     * <p>修复前：BuildOutputTaskStepWrapper 的 thenApply 对 SUSPEND 立即求值输出表达式，
     * 返回 RETURN("@suspend", result)，sequential 的 getNextIndex 查不到 "@suspend"
     * 抛 ERR_TASK_UNKNOWN_NEXT_STEP，任务失败。
     */
    @Test
    public void suspendWithDeclaredOutput_staysSuspend() {
        ITask task = taskFlowManager.getTask("test/suspend-with-output", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);

        TaskStepReturn ret = task.execute(taskRt);

        assertTrue(ret.isSuspend(),
                "suspend step with declared outputs must stay suspended. Pre-fix: BuildOutputTaskStepWrapper "
                        + "converted SUSPEND into a plain RETURN with nextStepName='@suspend' and the task "
                        + "failed with ERR_TASK_UNKNOWN_NEXT_STEP.");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_SUSPENDED), taskRt.getTaskState().getTaskStatus(),
                "task with output-declaring suspend step must be SUSPENDED");
    }
}
