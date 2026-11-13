package io.nop.task.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.AbstractTaskStep;
import io.nop.task.step.ThrottleTaskStepWrapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.task.TaskErrors.ERR_TASK_STEP_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.48 R4-08（plan 2541）组 A throttle 闸门域钉族：W-03（MA-10 配置固化告警对称+周期化）、
 * W-04（MA-11 重入检测——C10-002 宿主：显式共享 key 嵌套/call-task 自递归/call-step 同 stepRt
 * 三缺省形态 + 合法并发兄弟零误报腿）、W-05（MA-12 参数构建期校验）。
 *
 * <p>检测时点契约（勘误 10 R2 Major-4 / 设计文档裁定 A）：检测=tryAcquire 成功之后、移除点=
 * release 同点、合法并发兄弟钉随本类承载。maxConcurrency≥2 前置（maxConcurrency=1 时内层在
 * tryAcquire 处以既有 ERR_TASK_THROTTLE_TIMEOUT 超时失败，重入检测点不可达——既有信号量语义）。
 */
public class TestCraR408ThrottleGate extends AbstractTaskTestCase {

    // ==================== W-05（红⑤）：throttle 参数构建期校验 ====================

    @Test
    public void throttleMaxConcurrencyNonPositive_failsAtBuildTime() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/fc-throttle-maxconcurrency-nonpositive", 0),
                "throttle.maxConcurrency<=0 must hard-fail at build time like timeout/rateLimit (W-05 MA-12)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
        assertEquals("throttle.maxConcurrency", err.getParam("attrName"));
    }

    @Test
    public void throttleMaxWaitNegative_failsAtBuildTime() {
        NopException err = assertThrows(NopException.class,
                () -> taskFlowManager.getTask("test/fc-throttle-maxwait-negative", 0),
                "throttle.maxWait<0 must hard-fail at build time (W-05 MA-12)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
        assertEquals("throttle.maxWait", err.getParam("attrName"));
    }

    // ==================== W-04 / C10-002（红④）：重入检测 ====================

    @Test
    public void sharedKeyExprNestedAcquisition_failsAsReentry() {
        // 腿1：显式共享 keyExpr 递归嵌套——外层 sequential 持有 global key 'r408-k'，
        // 内层 xpl 同 key 嵌套获取（同任务祖先链命中）→ 确定性硬失败
        NopException err = assertThrows(NopException.class,
                () -> runTask("test/r408-throttle-shared-key-nested"),
                "nested acquisition of the same global key in one execution chain must hard-fail (W-04 MA-11)");
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), err.getErrorCode());
    }

    @Test
    public void callTaskSelfRecursionDefaultKey_failsAsReentry() {
        // 腿2：global×自递归 call-task（C2-011 路径 A）——同名任务注册表键同为
        // taskName:main；子任务经 inherited-keys 传播命中重入检测。
        // pre-fix 缺陷形态：子任务 tryAcquire 消耗第二枚 permit 深度递归，permit 池在深度 2
        // 耗尽后以 ERR_TASK_THROTTLE_TIMEOUT 失败（≠CONFIG_INVALID 即红）
        Throwable err = assertThrows(Throwable.class,
                () -> runTask("test/r408-throttle-selfcall"),
                "call-task self-recursion with default key must be detected as re-entry via inherited keys (W-04 path A)");
        NopException nopErr = assertThrows(NopException.class, () -> {
            throw err;
        });
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), nopErr.getErrorCode(),
                "expected re-entry hard failure, got: " + err);
    }

    @Test
    public void forkSiblingBranchesSameKey_noFalseReentry() {
        // 合法并发兄弟腿（检测时点契约③）：fork 两分支并发同 key 均成功——held 集分支隔离
        // 的结构性证明；重入检测不得把合法并发误报为重入（一刀切注册表级拒绝防线）
        var ret = runTask("test/r408-throttle-fork-siblings");
        assertEquals("OK", ret.get("RESULT"));
    }

    /**
     * 腿3：call-step 同 stepRt 形态（C2-011 路径 B 的载体等价构造）——同一步骤运行时二次
     * 进入同名 key（外层 wrapper 尚未移除 held 键）。经真实 TaskStepRuntimeImpl 链承载
     * （无显式 keyExpr 的 lib-step 缺省 key=stepPath 语义以 const keyExpr 等价）。
     * pre-fix 缺陷形态：第二层 tryAcquire 消耗 permit（池 2 耗尽后第三层超时）→
     * ERR_TASK_THROTTLE_TIMEOUT（≠CONFIG_INVALID 即红）
     */
    @Test
    public void sameStepRtNestedAcquisition_failsAsReentry() {
        TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-leg3"), false, null);
        ITaskStepRuntime mainRt = taskRt.newMainStepRuntime();

        IEvalAction constKey = scope -> "r408-leg3-key";
        AtomicInteger invocations = new AtomicInteger();
        ThrottleTaskStepWrapper[] holder = new ThrottleTaskStepWrapper[1];
        ITaskStep selfBody = new AbstractTaskStep() {
            @Override
            public TaskStepReturn execute(ITaskStepRuntime rt) {
                invocations.incrementAndGet();
                return holder[0].execute(rt);
            }
        };
        holder[0] = new ThrottleTaskStepWrapper(selfBody, true, 2, 100, constKey);

        Throwable err = assertThrows(Throwable.class, () -> holder[0].execute(mainRt),
                "same-stepRt nested acquisition must be detected as re-entry (W-04 path B)");
        NopException nopErr = assertThrows(NopException.class, () -> {
            throw err;
        });
        assertEquals(ERR_TASK_STEP_CONFIG_INVALID.getErrorCode(), nopErr.getErrorCode(),
                "expected re-entry hard failure, got: " + err);
        // 重入硬失败沿链传播出外层 wrapper——两层 permit 均经 release 成对路径归还（不泄漏：
        // 修复前同步抛出形态泄漏 permit；本断言锁「rejection 后池归满」）
        ISemaphore sem = taskRt.getSemaphore("r408-leg3-key", 2, true);
        assertEquals(2, sem.availablePermits(),
                "both permits returned through the paired release paths after re-entry rejection");
        assertEquals(1, invocations.get(),
                "re-entry is rejected at the throttle layer before the inner body executes again "
                        + "(outer body entered exactly once)");
    }

    // ==================== W-03（红③）：配置固化告警对称 + 周期化 ====================

    @Test
    public void rateLimiterFrozenWarn_isPeriodicallyThrottled() {
        Logger logger = (Logger) LoggerFactory.getLogger(TaskFlowManagerImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
            ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-w03"), false, null);

            manager.getRateLimiter(taskRt, "r408-w03-key", 10.0, true);
            manager.getRateLimiter(taskRt, "r408-w03-key", 99.0, true);
            manager.getRateLimiter(taskRt, "r408-w03-key", 98.0, true);

            long frozenWarns = appender.list.stream()
                    .filter(ev -> ev.getFormattedMessage() != null
                            && ev.getFormattedMessage().contains("config-frozen")
                            && ev.getFormattedMessage().contains("rate-limiter"))
                    .count();
            assertTrue(frozenWarns <= 1,
                    "rate-limiter frozen-config WARN must be throttled to once per window (W-03 MA-10), got "
                            + frozenWarns + " events. Pre-fix every call printed one WARN.");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    public void semaphoreFrozenConfig_emitsSymmetricWarn() {
        Logger logger = (Logger) LoggerFactory.getLogger(TaskFlowManagerImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TaskFlowManagerImpl manager = new TaskFlowManagerImpl();
            ITaskRuntime taskRt = manager.newTaskRuntime(FakeStepRt.fakeTask("r408-w03s"), false, null);

            manager.getSemaphore(taskRt, "r408-w03-sem", 3, true);
            manager.getSemaphore(taskRt, "r408-w03-sem", 5, true);

            long frozenWarns = appender.list.stream()
                    .filter(ev -> ev.getFormattedMessage() != null
                            && ev.getFormattedMessage().contains("config-frozen")
                            && ev.getFormattedMessage().contains("semaphore"))
                    .count();
            assertEquals(1, frozenWarns,
                    "semaphore side must emit the symmetric frozen-config WARN exactly once per window "
                            + "(W-03 MA-10; pre-fix the semaphore side had zero signal)");
            assertEquals(3, manager.getSemaphore(taskRt, "r408-w03-sem", 5, true).maxPermits(),
                    "existing pooled instance's permits remain active (frozen semantics preserved)");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
