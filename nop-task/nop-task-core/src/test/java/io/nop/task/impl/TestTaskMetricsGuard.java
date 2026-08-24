package io.nop.task.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.TaskConstants;
import io.nop.task.state.TaskLevelSnapshotTaskStateStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * check2 P1-1 回归测试：任务级 recordMetrics=false 且任务失败时，
 * 同步 catch 出口的 {@code metrics.endTask} 必须判空。
 *
 * <p>修复前：catch 块内 {@code metrics.endTask(meter, false)} 在 metrics==null 时抛 NPE——
 * <ul>
 *   <li>传播的是 NPE 而非原始业务异常（异常信息丢失）；</li>
 *   <li>NPE 发生在 driveTaskTerminal 之前，任务终态 FAILED 不落库，task 实例停留 ACTIVE。</li>
 * </ul>
 */
public class TestTaskMetricsGuard {

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
    }

    @Test
    public void recordMetricsOff_failurePropagatesOriginalError_persistsFailed() {
        ITask task = taskFlowManager.getTask("test/metrics-off-failed", 0);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, null);
        String taskInstanceId = taskRt.getTaskInstanceId();

        try {
            task.execute(taskRt).syncGetOutputs();
            fail("failing task must propagate the exception");
        } catch (Exception e) {
            assertTrue(containsErrorCode(e, "nop.err.test.metrics-off-failed"),
                    "propagated exception must preserve the original business error code. Pre-fix: "
                            + "metrics.endTask NPE replaced the original exception. Chain: " + dumpChain(e));
        }

        ITaskState snapshot = snapshotStore.getLatestTaskSnapshot(taskInstanceId);
        assertNotNull(snapshot,
                "task must reach the FAILED terminal driver and save state. Pre-fix: the NPE was thrown "
                        + "before driveTaskTerminal and nothing was persisted.");
        assertEquals(Integer.valueOf(TaskConstants.TASK_STATUS_FAILED), snapshot.getTaskStatus(),
                "task-level recordMetrics=false must not break FAILED persistence");
    }

    private static boolean containsErrorCode(Throwable e, String errorCode) {
        while (e != null) {
            if (e instanceof NopException && errorCode.equals(((NopException) e).getErrorCode()))
                return true;
            if (e.getMessage() != null && e.getMessage().contains(errorCode))
                return true;
            e = e.getCause();
        }
        return false;
    }

    private static String dumpChain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 10) {
            sb.append("\n  [").append(depth).append("] ").append(cur.getClass().getName())
                    .append(": code=").append(cur instanceof NopException ? ((NopException) cur).getErrorCode() : "n/a")
                    .append(", msg=").append(cur.getMessage());
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }
}
