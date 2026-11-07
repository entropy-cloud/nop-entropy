package io.nop.task.state;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.core._NopTaskCoreConstants;
import io.nop.task.impl.FakeStepRt;
import io.nop.task.impl.TaskFlowManagerImpl;
import io.nop.task.step.AbstractTaskStep;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.task.TaskConfigs.CFG_TASK_STEP_MAX_FORK_COUNT;
import static io.nop.task.TaskConfigs.CFG_TASK_STEP_MAX_LOOP_COUNT;
import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.48 R4-08（plan 2541）组 C 状态与锁纪律/边界计数钉族：W-08（MA-15 step 终态守卫
 * 原子化——latch 对齐 check-then-act 窗的确定性交错构造 + 交错混合态断言；守卫语义保留
 * 364 [05-01] 对照腿）、W-10（MA-18 fork-n/loop-n 计数运行期上界 + 预分配改增量）、
 * W-11（MA-20 cleanup 锁纪律——测试内并发 offer×drain 真实交错构造；总册显式标注：
 * 行为红=测试内构造的假想生产者交错，非 live 可达缺陷态——addStepCleanup 生产当前零调用）。
 */
public class TestCraR408StateLockAndCountBounds {

    private static final ErrorCode TEST_PROBE_ERROR =
            define("nop.err.test.r408.probe", "r408 test probe");


    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== W-08（红⑧）：step 终态守卫原子化 ====================

    /**
     * 白盒 seam（检出力载体）：子类覆盖 setResultValue，在守卫的 isDone() 检查通过之后、
     * 写入发生之前闩住——把 check-then-act 窗打开为确定性构造。修复前：另一线程在该窗内
     * 完成 fail+setStepStatus(KILLED) 后，本线程继续写 resultValue → KILLED 行携带
     * resultValue（审计 MA-15 混合态矩阵腿）。修复后：succeed 全程持 this 监视器，
     * setStepStatus(KILLED) 在锁外等待 → 锁内 isDone 复查返回 false → 无混合态。
     */
    static class SeamStepState extends TaskStepStateBean {
        final CyclicBarrier windowBarrier;
        final CountDownLatch windowRelease;

        SeamStepState(CyclicBarrier windowBarrier, CountDownLatch windowRelease) {
            this.windowBarrier = windowBarrier;
            this.windowRelease = windowRelease;
        }

        @Override
        public void setResultValue(Object value) {
            try {
                // 已过 isDone() 检查、未写 resultValue——check-then-act 窗口打开点
                windowBarrier.await();
                windowRelease.await(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new io.nop.api.core.exceptions.NopException(TEST_PROBE_ERROR, e);
            }
            super.setResultValue(value);
        }
    }

    @Test
    public void succeedVsKillInterleaving_noMixedState() throws Exception {
        // 红⑧ 主腿：succeed 与 KILLED driver（fail+setStepStatus）交错 → 终态行无混合状态
        CyclicBarrier windowBarrier = new CyclicBarrier(2);
        CountDownLatch windowRelease = new CountDownLatch(1);
        SeamStepState state = new SeamStepState(windowBarrier, windowRelease);

        CountDownLatch killedDone = new CountDownLatch(1);
        Thread killer = new Thread(() -> {
            try {
                windowBarrier.await(); // 与 succeed 的窗口打开点对齐
                // KILLED driver 序列（对称 TaskStepExecution EXPIRED/KILLED driver）
                state.fail(new io.nop.api.core.exceptions.NopException(TEST_PROBE_ERROR), null);
                state.setStepStatus(_NopTaskCoreConstants.TASK_STEP_STATUS_KILLED);
            } catch (Exception e) {
                // barrier broken（post-fix 监视器串行化后 killer 的首 await 仍在 succeed 前——不受影响）
            } finally {
                windowRelease.countDown();
                killedDone.countDown();
            }
        });
        killer.start();
        state.succeed("r408-result", null, null);
        killedDone.await();

        // 守卫语义保留对照（364 [05-01]/349 零弱化）：first-terminal-wins 逐字保留
        Integer status = state.getStepStatus();
        assertTrue(status != null && (status.intValue() == _NopTaskCoreConstants.TASK_STEP_STATUS_KILLED
                        || status.intValue() == _NopTaskCoreConstants.TASK_STEP_STATUS_COMPLETED),
                "terminal status must be one of the two first-terminal winners, got: " + status);
        // 混合态断言（W-08 本体）：终态行不得携带另一侧载荷
        if (status != null && status.intValue() == _NopTaskCoreConstants.TASK_STEP_STATUS_KILLED) {
            // 修复前交错窗：KILLED 已设而 succeed 继续 setResultValue → KILLED 行携带 resultValue（红）
            assertTrue(state.getResultValue() == null,
                    "KILLED row must not carry resultValue (W-08 MA-15 mixed-state matrix; "
                            + "pre-fix the interleaved succeed wrote resultValue after the KILLED status)");
        } else {
            // COMPLETED 胜出：不得携带 kill 侧 exception（C3-006 混合态另一半）
            assertTrue(state.exception() == null,
                    "COMPLETED row must not carry exception (C3-006 interleaving half)");
        }
    }

    @Test
    public void succeedThenLateFail_firstTerminalWins_semanticsPreserved() {
        // 守卫语义保留对照腿：先 succeed 后 fail → COMPLETED 行不被后到 fail 覆写 exception
        //（364 [05-01] 零弱化）
        TaskStepStateBean state = new TaskStepStateBean();
        state.succeed("ok", null, null);
        state.fail(new io.nop.api.core.exceptions.NopException(TEST_PROBE_ERROR), null);
        assertTrue(state.isSuccess());
        assertEquals("ok", state.getResultValue());
        assertTrue(state.exception() == null,
                "364 [05-01] semantics preserved: late fail must not overwrite COMPLETED row");
    }

    // ==================== W-10（红⑩）：fork-n/loop-n 计数运行期上界 ====================

    @Test
    public void forkNCountOverLimit_rejectedAtRuntime() {
        // 动态配置 max-fork-count=5；模型 countExpr=6 → 运行期 typed 拒绝（修复前正常 fork）
        AppConfig.getConfigProvider().updateConfigValue(CFG_TASK_STEP_MAX_FORK_COUNT, 5);
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITask task = manager.getTask("test/r408-forkn-over-limit", 0);
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);

        NopException err = assertThrows(NopException.class,
                () -> task.execute(taskRt).syncGetOutputs(),
                "forkN count exceeding max-fork-count must be rejected at runtime (W-10 MA-18)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
        assertTrue(String.valueOf(err.getParam("reason")).contains("max-fork-count"));
    }

    @Test
    public void forkNCountZero_keepsLegacyEmptyForkSemantics() {
        // count<=0 维持既有空 fork no-op 语义（裁定 D：校验仅承载超上界单语义）
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITask task = manager.getTask("test/fork-n-zero", 0);
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);
        var ret = task.execute(taskRt).syncGetOutputs();
        // fork-n-zero 既有模型语义：空 fork 后置步骤置 RESULT=done（空 fork no-op 零回归）
        assertEquals("done", ret.get("RESULT"));
    }

    @Test
    public void loopNIterationsOverLimit_rejectedAtRuntime() {
        // 动态配置 max-loop-count=5；begin=0,end=6,step=1 → floor(6/1)+1=7 次迭代 > 5 → 拒绝
        //（含端点公式 floor(|end-begin|/|step|)+1——R1-D-2 勘正形态）
        AppConfig.getConfigProvider().updateConfigValue(CFG_TASK_STEP_MAX_LOOP_COUNT, 5);
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITask task = manager.getTask("test/r408-loopn-over-limit", 0);
        ITaskRuntime taskRt = manager.newTaskRuntime(task, false, null);

        NopException err = assertThrows(NopException.class,
                () -> task.execute(taskRt).syncGetOutputs(),
                "loopN iteration count exceeding max-loop-count must be rejected at runtime (W-10 MA-18)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
        assertTrue(String.valueOf(err.getParam("reason")).contains("max-loop-count"));
    }

    @Test
    public void loopNBeginEqualsEnd_singleIteration_allowed() {
        // begin=end 实跑 1 次迭代（floor(0/step)+1=1 ≤ 上界）——天然放行，非零迭代误判回归面
        AppConfig.getConfigProvider().updateConfigValue(CFG_TASK_STEP_MAX_LOOP_COUNT, 5);
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        // 直接以 LoopNTaskStep 构造 begin=end=3：单迭代后 shouldContinue false
        io.nop.task.step.LoopNTaskStep loopStep = new io.nop.task.step.LoopNTaskStep();
        loopStep.setVarName("a");
        loopStep.setBeginExpr((IEvalAction) scope -> 3);
        loopStep.setEndExpr((IEvalAction) scope -> 3);
        AtomicInteger bodyRuns = new AtomicInteger();
        loopStep.setBody(new AbstractTaskStep() {
            @Override
            public TaskStepReturn execute(ITaskStepRuntime rt) {
                bodyRuns.incrementAndGet();
                return TaskStepReturn.CONTINUE;
            }
        });
        ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-loop-single"), false, null);
        ITaskStepRuntime rt = taskRt.newMainStepRuntime();
        loopStep.execute(rt);
        assertEquals(1, bodyRuns.get(),
                "begin==end runs exactly one iteration (floor(0/step)+1) — legal, below any cap");
    }

    // ==================== W-11（红⑪）：cleanup 并发 offer×drain 真实交错 ====================

    /**
     * 总册显式标注：行为红=测试内构造的假想生产者交错，非 live 可达缺陷态
     * （addStepCleanup 生产当前零调用、消费侧 live——公开扩展点生产者出现即触达，
     * R2 Minor-7 防 closure audit 误读为接线验证证据）。
     */
    @Test
    public void concurrentAddAndDrain_noLostCleanup() throws Exception {
        TaskStepRuntimeImplProbe rt = new TaskStepRuntimeImplProbe();

        int producers = 4;
        int perProducer = 2000;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        AtomicInteger executed = new AtomicInteger();

        Thread[] producerThreads = new Thread[producers];
        for (int i = 0; i < producers; i++) {
            producerThreads[i] = new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < perProducer; j++) {
                        rt.addStepCleanup(executed::incrementAndGet);
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
            producerThreads[i].start();
        }

        Thread drainer = new Thread(() -> {
            try {
                startLatch.await();
                // 持续 drain 与 offer 并发交错（drain 期间生产者持续 offer——修复前
                // ArrayList forEach 快照后 clear 的丢更新窗）
                while (!Thread.currentThread().isInterrupted()) {
                    rt.drainAll();
                    if (executed.get() >= producers * perProducer)
                        break;
                    Thread.yield();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        });
        drainer.start();

        startLatch.countDown();
        for (Thread t : producerThreads)
            t.join(10_000);
        drainer.join(10_000);

        assertTrue(errors.isEmpty(), "no unexpected errors: " + errors);
        assertEquals(producers * perProducer, executed.get(),
                "every cleanup offered concurrently must be executed exactly once (W-11 MA-20; "
                        + "pre-fix synchronized-add × non-sync-drain lost updates)");
        rt.drainAll();
        assertEquals(producers * perProducer, executed.get(), "queue fully drained, no residue");
    }

    /**
     * W-11 探针：以真实 TaskStepRuntimeImpl 承载（addStepCleanup/runStepCleanups 为被测
     * 修复面）。经包内可见的直接构造（test 树同包不可达——public 构造器跨包使用）。
     */
    static class TaskStepRuntimeImplProbe {
        final io.nop.task.impl.TaskStepRuntimeImpl delegate =
                new io.nop.task.impl.TaskStepRuntimeImpl(FakeStepRt.fakeTaskRuntime(),
                        null, io.nop.xlang.api.XLang.newEvalScope(new java.util.concurrent.ConcurrentHashMap<>()));

        void addStepCleanup(Runnable r) {
            delegate.addStepCleanup(r);
        }

        void drainAll() {
            delegate.runStepCleanups();
        }
    }
}
