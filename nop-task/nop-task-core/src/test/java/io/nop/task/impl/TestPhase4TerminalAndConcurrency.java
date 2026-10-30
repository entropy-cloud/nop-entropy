package io.nop.task.impl;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.semaphore.ISemaphore;
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
    // ==================== plan 2514 MA-01（1.5-C1-001/C1-009）：SUSPENDED 判定-写入原子化 ====================

    /**
     * plan 2514 MA-01 红①（1.5-C1-009 复活面）：KILLED 后的迟到 SUSPENDED 写必须被终态前置检查拒绝。
     * 修复前 SUSPENDED 转移游离于 taskState 监视器外（TaskImpl thenCompose 裸写），
     * cancel 驱动 KILLED 后可被裸覆写复活——本测试在修复前代码上必然红。
     */
    @Test
    public void c1_001_c1_009_lateSuspendWrite_mustNotReviveKilledTask() {
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(null, DefaultTaskStateStore.INSTANCE,
                null, io.nop.xlang.api.XLang.newEvalScope(), false);
        ITaskState state = DefaultTaskStateStore.INSTANCE.newTaskState("t", 0, taskRt);
        taskRt.setTaskState(state);
        TaskImpl task = new TaskImpl("t", 0, suspendStep(), false, null, null, null, null);

        // 1. SUSPENDED 转移（SUSPEND 返回为同步 return，thenCompose 判定-写入在同一线程内完成）
        task.execute(taskRt, null);
        assertEquals(TaskConstants.TASK_STATUS_SUSPENDED, state.getTaskStatus());

        // 2. cancel 驱动 KILLED（[05-03] 语义，零回退）
        taskRt.cancel("ops-kill");
        assertEquals(TaskConstants.TASK_STATUS_KILLED, state.getTaskStatus());

        // 3. 迟到的 SUSPENDED 写（修复面：监视器内终态前置检查短路；修复前：KILLED 被 SUSPENDED 裸覆写复活）
        task.execute(taskRt, null);
        assertEquals(TaskConstants.TASK_STATUS_KILLED, state.getTaskStatus(),
                "KILLED terminal must not be revived by a late SUSPENDED write (C1-009 closed)");
        assertTrue(state.isTerminal(), "revival-guarded task must stay terminal");
    }

    /**
     * plan 2514 C10-001（MA-01 收口门）：真并发交错——执行线程 SUSPENDED 转移与 cancel 线程
     * 并发竞速，不变式 = cancel 一旦观察到 SUSPENDED 并驱动 KILLED，终局不得回到 SUSPENDED
     * （修复前裸写可覆写 KILLED → 红；修复后同监视器判定-写入原子 → 绿）。
     */
    @Test
    public void c10_001_suspendCancel_concurrentInterleave_killedStaysTerminal() throws Exception {
        int rounds = 100;
        for (int i = 0; i < rounds; i++) {
            TaskRuntimeImpl taskRt = new TaskRuntimeImpl(null, DefaultTaskStateStore.INSTANCE,
                    null, io.nop.xlang.api.XLang.newEvalScope(), false);
            ITaskState state = DefaultTaskStateStore.INSTANCE.newTaskState("t", 0, taskRt);
            taskRt.setTaskState(state);
            TaskImpl task = new TaskImpl("t", 0, suspendStep(), false, null, null, null, null);

            java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean cancelDroveKilled = new java.util.concurrent.atomic.AtomicBoolean();

            Thread suspender = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                task.execute(taskRt, null); // SUSPENDED 转移（可能先于或晚于 cancel）
            });
            Thread canceller = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Integer before = state.getTaskStatus();
                taskRt.cancel("ops-kill");
                // cancel 观察到 SUSPENDED 且任务转 KILLED → 记录该序（该序下终局必须保持 KILLED）
                if (before != null && before == TaskConstants.TASK_STATUS_SUSPENDED
                        && state.getTaskStatus() == TaskConstants.TASK_STATUS_KILLED) {
                    cancelDroveKilled.set(true);
                }
            });
            suspender.start();
            canceller.start();
            ready.await();
            go.countDown();
            suspender.join(5000);
            canceller.join(5000);

            if (cancelDroveKilled.get()) {
                assertTrue(state.getTaskStatus() == TaskConstants.TASK_STATUS_KILLED,
                        "round " + i + ": once cancel drove KILLED, the SUSPENDED writer must not " +
                                "overwrite it (monitor-atomic transfer, C10-001)");
            }
            Integer finalStatus = state.getTaskStatus();
            assertTrue(finalStatus != null, "round " + i + ": task must have a status after both actions");
        }
    }

    private AbstractTaskStep suspendStep() {
        return new AbstractTaskStep() {
            @Override
            public String getStepType() {
                return "suspend";
            }

            @Override
            public TaskStepReturn execute(ITaskStepRuntime stepRt) {
                return TaskStepReturn.SUSPEND;
            }
        };
    }

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

    // ==================== [05-04]/C10-009 全局闸门注册表：同 key 同实例 + 空闲逐出治理 ====================
    // plan 2514 MA-08（1.5-C5-003，裁定⑤）反向钉改写记录：
    //  - 原钉 `globalGates_strongRefRegistry_sameInstanceAcrossManyKeys`（钉定「永不驱逐」契约）已按
    //    C10-009 契约先改写：保留「同 key 同实例」断言（permit 池不分裂语义不变），新增空闲逐出、
    //    在用 entry 零逐出对照、并发获取×逐出交错 assertSame、registry-size gauge 断言。
    //  - 新增断言在修复前的强引用注册表上必然为红（永不逐出、无 gauge）。

    @Test
    public void globalGates_sameKeySameInstance_andIdleEvictionGovernance() throws Exception {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = FakeStepRt.fakeTaskRuntime();
        var cfgProvider = io.nop.api.core.config.AppConfig.getConfigProvider();
        cfgProvider.updateConfigValue(io.nop.task.TaskConfigs.CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS, 1);
        try {
            // （保留断言）同 key 同实例：permit 池不分裂语义不变（plan 364 [05-04] 核心契约）
            var limiter1 = manager.getRateLimiter(taskRt, "k1", 100, true);
            org.junit.jupiter.api.Assertions.assertSame(limiter1,
                    manager.getRateLimiter(taskRt, "k1", 100, true),
                    "same key within idle window must return same instance (permit pool intact)");
            var sem1 = manager.getSemaphore(taskRt, "s1", 3, true);
            org.junit.jupiter.api.Assertions.assertSame(sem1,
                    manager.getSemaphore(taskRt, "s1", 3, true),
                    "same key within idle window must return same instance (semaphore permit pool intact)");

            // （新增断言，红⑤核心）空闲超阈 + 复访 → 逐出旧实例、新建实例（修复前强引用注册表永不逐出 → 红）
            Thread.sleep(20);
            var limiter1AfterIdle = manager.getRateLimiter(taskRt, "k1", 100, true);
            org.junit.jupiter.api.Assertions.assertNotSame(limiter1, limiter1AfterIdle,
                    "idle entry beyond idle-evict-ms must be evicted on revisit and replaced by a new instance (MA-08)");

            // （新增断言）在用 entry 零逐出对照：信号量 permits 非满额（在途持有者存在）时不得逐出
            var semBusy = manager.getSemaphore(taskRt, "s-busy", 3, true);
            semBusy.tryAcquire(1, 0); // permits 满额被破坏 → 在用
            Thread.sleep(20);
            org.junit.jupiter.api.Assertions.assertSame(semBusy,
                    manager.getSemaphore(taskRt, "s-busy", 3, true),
                    "in-use semaphore (permits not full) must never be evicted (permit pool integrity)");

            // （新增断言，红⑤补断言 Major-3）并发获取 × 逐出交错：同 key compute 内联串行化下，
            // 同一窗口内所有获取者拿到同一实例（permit 池不分裂的结构性证明）。
            // 放宽 idle 到 300ms 使 8 线程同刻获取落在同一逐出窗口内（单次逐出，判据确定性）
            cfgProvider.updateConfigValue(io.nop.task.TaskConfigs.CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS, 300);
            var first = manager.getSemaphore(taskRt, "s-hot", 2, true);
            Thread.sleep(320); // aging: s-hot 空闲超阈
            int threads = 8;
            java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threads);
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
            java.util.List<java.util.concurrent.Future<ISemaphore>> futures = new java.util.ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> {
                        barrier.await();
                        return manager.getSemaphore(taskRt, "s-hot", 2, true);
                    }));
                }
                ISemaphore prev = null;
                for (var f : futures) {
                    ISemaphore cur = f.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (prev != null) {
                        org.junit.jupiter.api.Assertions.assertSame(prev, cur,
                                "concurrent getters racing the eviction re-check must observe one coherent instance (no permit pool split)");
                    }
                    prev = cur;
                }
                org.junit.jupiter.api.Assertions.assertNotSame(first, prev,
                        "aged entry must have been evicted exactly once by the first concurrent getter (MA-08)");
            } finally {
                pool.shutdownNow();
            }

            // （新增断言）registry.size gauge（MA-09 本批内半面）
            org.junit.jupiter.api.Assertions.assertNotNull(
                    io.nop.commons.metrics.GlobalMeterRegistry.instance()
                            .find("nop.task.global-gate.registry-size")
                            .tag("registry", "rate-limiter").gauge(),
                    "registry-size gauge must be registered for rate-limiter registry (MA-08 observation half)");
            org.junit.jupiter.api.Assertions.assertNotNull(
                    io.nop.commons.metrics.GlobalMeterRegistry.instance()
                            .find("nop.task.global-gate.registry-size")
                            .tag("registry", "semaphore").gauge(),
                    "registry-size gauge must be registered for semaphore registry (MA-08 observation half)");
        } finally {
            cfgProvider.updateConfigValue(io.nop.task.TaskConfigs.CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS, 300000);
        }
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
