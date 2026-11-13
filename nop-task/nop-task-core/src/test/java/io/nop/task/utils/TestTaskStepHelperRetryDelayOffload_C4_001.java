package io.nop.task.utils;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2498 Phase 2, findings 1.5-C4-001 (MA-04) + member face C4-008: the delayed retry
 * branch scheduled the step body itself (and, on the same completion thread, the doRetry
 * bookkeeping chain including the saveState persist write) on the shared platform
 * globalTimer. Per the platform scheduling-slot contract the timer must only wake up; the
 * body is hopped via {@code executeOn} onto the step's explicitly configured executor
 * (executor isolation now covers the delayed path too) or the shared globalWorker pool.
 *
 * <p>Thread-attribution evidence (E2): test ① uses only the pre-existing 4-arg retry API and
 * failed red against the pre-fix baseline (delayed body thread = {@code nop-global-timer-N});
 * tests ② / ③ pin the C4-008 isolation behaviour and the saveState bookkeeping placement.
 */
public class TestTaskStepHelperRetryDelayOffload_C4_001 {

    private static final ErrorCode ERR_TEST_RECOVERABLE =
            ErrorCode.define("nop.err.test.retry-offload.recoverable", "test recoverable");

    private static final List<DefaultThreadPoolExecutor> POOLS = new CopyOnWriteArrayList<>();

    @AfterAll
    public static void destroyPools() {
        POOLS.forEach(DefaultThreadPoolExecutor::destroy);
        POOLS.clear();
    }

    private static RetryPolicy<ITaskStepRuntime> retryPolicy(int maxRetryCount, int delayMillis) {
        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(maxRetryCount);
        policy.setRetryDelay(delayMillis);
        policy.setExponentialDelay(false);
        policy.setJitterRatio(0);
        return policy;
    }

    /**
     * ① Delayed retry body must run on the shared worker pool, never on the globalTimer.
     * Uses the 4-arg retry overload (no explicit executor), so the assertion is
     * compile-compatible with the pre-fix code and ran red on the defect baseline.
     */
    @Test
    public void delayedRetryBodyRunsOnWorkerNotTimerThread_C4_001() {
        TaskStepStateBean state = new TaskStepStateBean();
        RecordingStepRuntime stepRt = new RecordingStepRuntime(state, null);

        AtomicInteger executeCount = new AtomicInteger();
        AtomicReference<String> delayedThread = new AtomicReference<>();
        Callable<TaskStepReturn> action = () -> {
            if (executeCount.incrementAndGet() == 1)
                throw new NopException(ERR_TEST_RECOVERABLE);
            delayedThread.set(Thread.currentThread().getName());
            return TaskStepReturn.RETURN_RESULT("OK");
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, retryPolicy(1, 20), action);
        assertEquals("OK", ret.syncGetOutputs().get(TaskConstants.VAR_RESULT));

        String threadName = delayedThread.get();
        assertTrue(threadName.startsWith("nop-global-worker"),
                "delayed retry body must run on the shared worker pool but ran on: " + threadName
                        + ". Pre-fix (1.5-C4-001) the body was executed on the nop-global-timer thread.");
        assertFalse(threadName.startsWith("nop-global-timer"),
                "delayed retry body must never run on the nop-global-timer thread, got: " + threadName);
    }

    /**
     * ② C4-008 behaviour pin: when the step configures an explicit executor, the delayed
     * retry round runs on THAT pool (executor isolation is effective on the delay path),
     * not on the timer and not on the default worker pool.
     */
    @Test
    public void delayedRetryBodyHonorsStepExecutorIsolation_C4_001() {
        DefaultThreadPoolExecutor pool =
                DefaultThreadPoolExecutor.newExecutor("test-retry-pool", 1, 10, true);
        POOLS.add(pool);

        TaskStepStateBean state = new TaskStepStateBean();
        RecordingStepRuntime stepRt = new RecordingStepRuntime(state, pool);

        AtomicInteger executeCount = new AtomicInteger();
        AtomicReference<String> delayedThread = new AtomicReference<>();
        Callable<TaskStepReturn> action = () -> {
            if (executeCount.incrementAndGet() == 1)
                throw new NopException(ERR_TEST_RECOVERABLE);
            delayedThread.set(Thread.currentThread().getName());
            return TaskStepReturn.RETURN_RESULT("OK");
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, retryPolicy(1, 20), action, "test-retry-pool");
        assertEquals("OK", ret.syncGetOutputs().get(TaskConstants.VAR_RESULT));
        assertEquals(2, executeCount.get(), "1 initial failure + 1 delayed round");

        String threadName = delayedThread.get();
        assertTrue(threadName.startsWith("test-retry-pool"),
                "delayed retry body must run on the step's configured executor pool but ran on: "
                        + threadName + " (C4-008: executor isolation must be effective on the delay path)");
    }

    /**
     * ③ The doRetry bookkeeping chain (setRetryAttempt + saveState persist write) that runs
     * after a failed delayed round must execute on the carrier thread, not on the timer.
     * saveState is called once on the caller thread (after the initial sync failure) and
     * once on the delayed round's carrier thread (after the delayed failure).
     */
    @Test
    public void delayedRetrySaveStateBookkeepingNotOnTimerThread_C4_001() {
        TaskStepStateBean state = new TaskStepStateBean();
        RecordingStepRuntime stepRt = new RecordingStepRuntime(state, null);

        AtomicInteger executeCount = new AtomicInteger();
        Callable<TaskStepReturn> action = () -> {
            if (executeCount.incrementAndGet() <= 2)
                throw new NopException(ERR_TEST_RECOVERABLE);
            return TaskStepReturn.RETURN_RESULT("OK");
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, retryPolicy(2, 20), action);
        assertEquals("OK", ret.syncGetOutputs().get(TaskConstants.VAR_RESULT));

        List<String> saveStateThreads = stepRt.saveStateThreads;
        assertEquals(2, saveStateThreads.size(),
                "one saveState per retry-attempt increment: initial failure + delayed failure");
        String delayedSaveStateThread = saveStateThreads.get(1);
        assertTrue(delayedSaveStateThread.startsWith("nop-global-worker"),
                "delayed round's saveState bookkeeping must run on the carrier (worker) thread but ran on: "
                        + delayedSaveStateThread);
        assertFalse(delayedSaveStateThread.startsWith("nop-global-timer"),
                "delayed round's saveState bookkeeping must never run on the nop-global-timer thread");
    }

    /**
     * Minimal {@link ITaskStepRuntime} fake: {@code getScheduledExecutor} returns the real
     * globalTimer (the uninjected TaskFlowManagerImpl fallback chain — rule 23 wiring
     * evidence), {@code getThreadPoolExecutor} returns the configurable explicit pool, and
     * {@code saveState} records the calling thread.
     */
    static class RecordingStepRuntime implements ITaskStepRuntime {
        private final ITaskStepState state;
        private final IThreadPoolExecutor executorPool;
        final List<String> saveStateThreads = new CopyOnWriteArrayList<>();
        private final ITaskRuntime taskRt;

        RecordingStepRuntime(ITaskStepState state, IThreadPoolExecutor executorPool) {
            this.state = state;
            this.executorPool = executorPool;
            this.taskRt = newFakeTaskRuntime(executorPool);
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            return taskRt;
        }

        @Override
        public ICancelToken getCancelToken() {
            return null;
        }

        @Override
        public void setCancelToken(ICancelToken cancelToken) {
        }

        @Override
        public Set<String> getOutputNames() {
            return null;
        }

        @Override
        public void setOutputNames(Set<String> outputNames) {
        }

        @Override
        public ITaskStepState getState() {
            return state;
        }

        @Override
        public boolean isSupportPersist() {
            return false;
        }

        @Override
        public void saveState() {
            saveStateThreads.add(Thread.currentThread().getName());
        }

        @Override
        public boolean isRecoverMode() {
            return false;
        }

        @Override
        public ITaskStepRuntime newStepRuntime(String stepName, String stepType,
                                               Set<String> persistVars, boolean useParentScope, boolean concurrent) {
            throw new UnsupportedOperationException("not used by TaskStepHelper.retry");
        }

        @Override
        public void addStepCleanup(Runnable cleanup) {
        }

        @Override
        public void runStepCleanups() {
        }

        @Override
        public Throwable getException() {
            return null;
        }

        @Override
        public void setException(Throwable exception) {
        }

        @Override
        public IEvalScope getEvalScope() {
            throw new UnsupportedOperationException("not used by TaskStepHelper.retry");
        }

        @Nonnull
        @Override
        public Set<String> getTagSet() {
            return Collections.emptySet();
        }

        @Override
        public void setTagSet(Set<String> enabledFlags) {
        }
    }

    static ITaskRuntime newFakeTaskRuntime(IThreadPoolExecutor executorPool) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                switch (method.getName()) {
                    case "getScheduledExecutor":
                        // real platform fallback: TaskFlowManagerImpl without an injected
                        // scheduled executor falls back to GlobalExecutors.globalTimer()
                        return GlobalExecutors.globalTimer();
                    case "getThreadPoolExecutor":
                        return executorPool;
                    case "getTaskName":
                        return "fake-task";
                    case "getTaskVersion":
                        return 0L;
                    case "getTaskInstanceId":
                        return "fake-instance";
                    case "isRecoverMode":
                        return Boolean.FALSE;
                    case "toString":
                        return "FakeTaskRuntime";
                    default:
                        throw new UnsupportedOperationException(
                                "not used by TaskStepHelper.retry: " + method.getName());
                }
            }
        };
        return (ITaskRuntime) Proxy.newProxyInstance(
                TestTaskStepHelperRetryDelayOffload_C4_001.class.getClassLoader(),
                new Class<?>[]{ITaskRuntime.class}, handler);
    }
}
