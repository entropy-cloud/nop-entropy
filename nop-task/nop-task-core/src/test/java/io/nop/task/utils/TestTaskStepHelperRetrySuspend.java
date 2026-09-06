package io.nop.task.utils;

import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P2-5 回归测试：{@link TaskStepHelper#retry} 对 SUSPEND 返回不得调用 {@code state.succeed}。
 *
 * <p>修复前：同步非异步返回一律 {@code state.succeed(...)}，把 stepStatus 置 COMPLETED——
 * 挂起≠完成，步骤状态机错误；后续任何 saveState 会把错误终态落库，恢复时被 continuation-skip 误跳过。
 *
 * <p>覆盖同步与异步（doRetry 成功分支）两条路径。
 */
public class TestTaskStepHelperRetrySuspend {

    @Test
    public void syncSuspend_notMarkedCompleted_returnedImmediately() {
        TaskStepStateBean state = new TaskStepStateBean();
        FakeTaskStepRuntime stepRt = new FakeTaskStepRuntime(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        AtomicInteger executeCount = new AtomicInteger();
        Callable<TaskStepReturn> action = () -> {
            executeCount.incrementAndGet();
            return TaskStepReturn.SUSPEND;
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, action);

        assertTrue(ret.isSuspend(), "retry-wrapped suspend step must return the SUSPEND signal");
        assertFalse(state.isDone(),
                "suspend must not mark step state COMPLETED. Pre-fix: state.succeed() set stepStatus=COMPLETED "
                        + "for the suspending step and any later saveState persisted the wrong terminal state.");
        assertEquals(1, executeCount.get(), "suspend must return immediately without retrying");
    }

    @Test
    public void asyncSuspend_notMarkedCompleted() {
        TaskStepStateBean state = new TaskStepStateBean();
        FakeTaskStepRuntime stepRt = new FakeTaskStepRuntime(state);

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(2);
        policy.setRetryDelay(0);

        CompletableFuture<TaskStepReturn> future = new CompletableFuture<>();
        AtomicInteger executeCount = new AtomicInteger();
        Callable<TaskStepReturn> action = () -> {
            executeCount.incrementAndGet();
            return TaskStepReturn.ASYNC_RETURN(future);
        };

        TaskStepReturn ret = TaskStepHelper.retry(null, stepRt, policy, action);
        future.complete(TaskStepReturn.SUSPEND);
        TaskStepReturn done = ret.sync();

        assertTrue(done.isSuspend(), "async completion value SUSPEND must be returned as-is");
        assertFalse(state.isDone(),
                "doRetry success branch must not mark a suspending step COMPLETED (pre-fix state.succeed).");
        assertEquals(1, executeCount.get(), "suspend must not trigger retry");
    }

    /**
     * 最小 fake {@link ITaskStepRuntime}：仅实现 {@link TaskStepHelper#retry} 路径所需方法。
     */
    static class FakeTaskStepRuntime implements ITaskStepRuntime {
        private final ITaskStepState state;

        FakeTaskStepRuntime(ITaskStepState state) {
            this.state = state;
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            return null;
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
        }

        @Override
        public boolean isRecoverMode() {
            return false;
        }

        @Override
        public ITaskStepRuntime newStepRuntime(String stepName, String stepType,
                                               Set<String> persistVars, boolean useParentScope, boolean concurrent) {
            throw new UnsupportedOperationException("not used by retry suspend test");
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
            throw new UnsupportedOperationException("not used by retry suspend test");
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
}
