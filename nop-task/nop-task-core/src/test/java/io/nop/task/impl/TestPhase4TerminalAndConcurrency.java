package io.nop.task.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.TaskStepExecution;
import io.nop.task.state.TaskStateBean;
import io.nop.task.state.TaskStepStateBean;
import io.nop.task.utils.TaskStepHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static io.nop.task.TaskErrors.ERR_TASK_MANDATORY_INPUT_NOT_ALLOW_EMPTY;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_STEP_IN_LIB;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 364 Phase 4 终态与并发正确性回归。
 *
 * <p>[维度02-02] sync/async 两出口失败驱动统一为 driveStepFailure 后，nextOnError 配置下
 * 两条路径的 XPL stack 可观测性一致（修复前 async 出口的 addXplStack 位于 nextOnError 分支
 * 之后永不可达——同步失败带栈、异步失败不带）。
 *
 * <p>[维度05-01①] task 级驱动"判定+写入"原子化后，COMPLETED 与 KILLED 双驱动交替调用
 * （不必起真线程竞速——按 plan 建议直接驱动函数交替），终态与 exception 恒同源、后到者不覆写。
 *
 * <p>[维度05-03③] SUSPENDED 任务被 cancel 直接驱动 KILLED 终态并落盘（修复前 kill 调用
 * 立即返回成功、状态要等任意久之后的 resume 才转移）。
 *
 * <p>[维度05-04] 全局限流器/信号量注册表为强引用——同一 key 重复获取恒返回同一实例
 * （permit 池不再可能因缓存驱逐分裂）。
 */
public class TestPhase4TerminalAndConcurrency {

    static final ErrorCode ERR_TEST_PHASE4_FAIL =
            ErrorCode.define("nop.err.test.phase4.fail", "phase4 driver test failure");

    // ==================== [02-02] 失败驱动统一：XPL stack 两路径一致 ====================

    private ITaskStepRuntime newParentWithChild(TaskStepStateBean childState) {
        // executeWithParentRt 经 parentRt.newStepRuntime 取子 runtime——FakeStepRt 从 holders 取
        java.util.Map<String, Object> holders = new java.util.HashMap<>();
        holders.put("childStepRt", FakeStepRt.of(childState));
        return FakeStepRt.of(new TaskStepStateBean(), holders);
    }

    @Test
    public void syncFailure_withNextOnError_xplStackObservable() {
        TaskStepStateBean state = new TaskStepStateBean();
        ITaskStepRuntime stepRt = newParentWithChild(state);
        NopException boom = new NopException(ERR_TEST_PHASE4_FAIL);
        AbstractTaskStep failing = new AbstractTaskStep() {
            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                throw boom;
            }
        };

        TaskStepExecution exec = new TaskStepExecution(null, "syncFail",
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptySet(), null, null, failing, null, "handler",
                false, null, false);
        TaskStepReturn ret = exec.executeWithParentRt(stepRt);

        assertEquals("handler", ret.getNextStepName(), "nextOnError must hand off to error branch");
        assertSameException(state, boom);
        assertNotNull(boom.getXplStack(), "sync failure must carry XPL stack");
    }

    @Test
    public void asyncFailure_withNextOnError_xplStackObservable() {
        TaskStepStateBean state = new TaskStepStateBean();
        ITaskStepRuntime stepRt = newParentWithChild(state);
        NopException boom = new NopException(ERR_TEST_PHASE4_FAIL);
        CompletableFuture<TaskStepReturn> failed = new CompletableFuture<>();
        failed.completeExceptionally(boom);
        AbstractTaskStep asyncFailing = new AbstractTaskStep() {
            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                return TaskStepReturn.ASYNC(null, failed);
            }
        };

        TaskStepExecution exec = new TaskStepExecution(null, "asyncFail",
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptySet(), null, null, asyncFailing, null, "handler",
                false, null, false);
        TaskStepReturn ret = exec.executeWithParentRt(stepRt);

        assertEquals("handler", ret.getNextStepName(), "nextOnError must hand off to error branch (async too)");
        assertSameException(state, boom);
        // 修复前（02-02 漂移）：async 出口的 addXplStack 位于 nextOnError 分支之后，永不可达
        assertNotNull(boom.getXplStack(),
                "async failure must carry XPL stack exactly like sync failure (02-02 parity)");
    }

    private void assertSameException(TaskStepStateBean state, NopException boom) {
        assertEquals(_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED, state.getStepStatus(),
                "step must be driven to FAILED terminal");
        assertTrue(state.isDone(), "step must be terminal after failure driver");
        org.junit.jupiter.api.Assertions.assertSame(boom, state.exception(),
                "failed driver must persist the original exception");
    }

    // ==================== [05-01①] 终态驱动交替：终态与 exception 同源 ====================

    @Test
    public void terminalDrivers_alternatingOrder_terminalAndExceptionStaySameSource() throws Exception {
        TaskImpl task = new TaskImpl("t", 0, noopStep(), false, null, null, null, null);
        Method driveCompleted = TaskImpl.class.getDeclaredMethod("driveTaskCompleted",
                ITaskRuntime.class, ITaskState.class, TaskStepReturn.class);
        Method driveKilled = TaskImpl.class.getDeclaredMethod("driveTaskKilled",
                ITaskRuntime.class, ITaskState.class, Throwable.class);
        driveCompleted.setAccessible(true);
        driveKilled.setAccessible(true);

        ITaskRuntime taskRt = FakeStepRt.fakeTaskRuntime();
        for (int i = 0; i < 20; i++) {
            TaskStateBean state = new TaskStateBean();
            boolean completedFirst = (i % 2 == 0);

            if (completedFirst) {
                driveCompleted.invoke(task, taskRt, state, TaskStepReturn.RETURN_RESULT("ok"));
                driveKilled.invoke(task, taskRt, state, new NopException(ERR_TEST_PHASE4_FAIL));
                assertEquals(TaskConstants.TASK_STATUS_COMPLETED, state.getTaskStatus(),
                        "first-terminal-wins: COMPLETED arrived first and must not be overwritten");
                assertNull(state.exception(), "COMPLETED terminal must not carry a kill exception");
            } else {
                NopException kill = new NopException(ERR_TEST_PHASE4_FAIL);
                driveKilled.invoke(task, taskRt, state, kill);
                driveCompleted.invoke(task, taskRt, state, TaskStepReturn.RETURN_RESULT("ok"));
                assertEquals(TaskConstants.TASK_STATUS_KILLED, state.getTaskStatus(),
                        "first-terminal-wins: KILLED arrived first and must not be overwritten");
                org.junit.jupiter.api.Assertions.assertSame(kill, state.exception(),
                        "KILLED terminal exception must stay same-source with the status");
            }
        }
    }

    // ==================== [05-03③] 挂起任务 cancel 直接驱动 KILLED ====================

    @Test
    public void suspendedTask_cancel_drivesKilledWithoutResume() {
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(null, DefaultTaskStateStore.INSTANCE,
                null, io.nop.xlang.api.XLang.newEvalScope(), false);
        TaskStateBean state = new TaskStateBean();
        state.setTaskName("suspended-task");
        state.setTaskInstanceId("inst-suspend-cancel");
        state.setTaskStatus(TaskConstants.TASK_STATUS_SUSPENDED);
        taskRt.setTaskState(state);

        taskRt.cancel("ops-kill");

        assertEquals(TaskConstants.TASK_STATUS_KILLED, state.getTaskStatus(),
                "cancel on SUSPENDED task must drive KILLED immediately (05-03)");
        assertNotNull(state.exception(), "KILLED terminal must record the cancellation exception");
        assertTrue(state.isTerminal(), "suspended-then-cancelled task must be terminal");
    }

    // ==================== [05-04] 全局闸门注册表不驱逐 ====================

    @Test
    public void globalGates_strongRefRegistry_sameInstanceAcrossManyKeys() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = FakeStepRt.fakeTaskRuntime();

        var limiter1 = manager.getRateLimiter(taskRt, "k1", 100, true);
        // 跨越默认容量配置（10000）之外的多次获取后，首个实例必须仍然同源（permit 池不分裂）
        for (int i = 0; i < 50; i++)
            manager.getRateLimiter(taskRt, "other-key-" + i, 1, true);
        org.junit.jupiter.api.Assertions.assertSame(limiter1,
                manager.getRateLimiter(taskRt, "k1", 100, true),
                "global rate limiter instances must never be evicted (05-04)");

        var sem1 = manager.getSemaphore(taskRt, "s1", 3, true);
        for (int i = 0; i < 50; i++)
            manager.getSemaphore(taskRt, "other-sem-" + i, 1, true);
        org.junit.jupiter.api.Assertions.assertSame(sem1,
                manager.getSemaphore(taskRt, "s1", 3, true),
                "global semaphore instances must never be evicted (05-04)");
    }

    private AbstractTaskStep noopStep() {
        return new AbstractTaskStep() {
            @Override
            public String getStepType() {
                return "noop";
            }

            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                return TaskStepReturn.CONTINUE;
            }
        };
    }
}
