package io.nop.task.step;

import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P2-3/P2-4 回归测试：SequentialTaskStep / SelectorTaskStep 的异步回调
 * 必须对异步完成值 SUSPEND 透传挂起信号（与各自同步路径的 isSuspend 判定对齐）。
 *
 * <p>用可手动完成的 future 构造确定性的异步挂起（不经 executor，避免时序抖动）：
 * <ul>
 *   <li>P2-3 sequential：修复前异步 SUSPEND 进入 getNextIndex，查不到 "@suspend"
 *       抛 ERR_TASK_UNKNOWN_NEXT_STEP。</li>
 *   <li>P2-4 selector：修复前异步 SUSPEND 的 isResultTruthy()==false，被当作
 *       "候选返回 falsy" 静默跳过并推进下一候选。</li>
 * </ul>
 */
public class TestAsyncSuspendPropagation {

    @Test
    public void sequentialAsyncSuspend_propagatedAsSuspend() {
        CompletableFuture<TaskStepReturn> future = new CompletableFuture<>();
        SequentialTaskStep seq = new SequentialTaskStep();
        seq.setSteps(Collections.singletonList(new StubExecution("s1", rt -> TaskStepReturn.ASYNC_RETURN(future))));

        TaskStepReturn ret = seq.execute(newStepRt());
        assertTrue(ret.isAsync(), "step must return an async result before the future is completed");

        future.complete(TaskStepReturn.SUSPEND);
        TaskStepReturn done = ret.sync();

        assertTrue(done.isSuspend(),
                "sequential must propagate async SUSPEND instead of resolving '@suspend' as next step name. "
                        + "Pre-fix: getNextIndex threw ERR_TASK_UNKNOWN_NEXT_STEP for nextStepName='@suspend'.");
    }

    @Test
    public void selectorAsyncSuspend_propagatedWithoutAdvancingToNextCandidate() {
        CompletableFuture<TaskStepReturn> future = new CompletableFuture<>();
        AtomicInteger secondCandidateExecutions = new AtomicInteger();

        SelectorTaskStep selector = new SelectorTaskStep();
        selector.setSteps(Arrays.asList(
                new StubExecution("cand1", rt -> TaskStepReturn.ASYNC_RETURN(future)),
                new StubExecution("cand2", rt -> {
                    secondCandidateExecutions.incrementAndGet();
                    return TaskStepReturn.RETURN_RESULT("SECOND_RAN");
                })));

        TaskStepReturn ret = selector.execute(newStepRt());
        assertTrue(ret.isAsync(), "first candidate must return an async result before the future is completed");

        future.complete(TaskStepReturn.SUSPEND);
        TaskStepReturn done = ret.sync();

        assertTrue(done.isSuspend(),
                "selector must propagate async SUSPEND. Pre-fix: SUSPEND was treated as a falsy candidate "
                        + "result and the selector silently advanced to the next candidate.");
        assertEquals(0, secondCandidateExecutions.get(),
                "suspending candidate must stop selector execution, the next candidate must not run");
    }

    /**
     * 顺序步骤同步挂起的既有行为守卫（同步路径 isSuspend 判定已存在，修复未改变）。
     */
    @Test
    public void sequentialSyncSuspend_propagatedAsSuspend() {
        SequentialTaskStep seq = new SequentialTaskStep();
        seq.setSteps(Collections.singletonList(
                new StubExecution("s1", rt -> TaskStepReturn.SUSPEND)));

        TaskStepReturn ret = seq.execute(newStepRt());
        assertTrue(ret.isSuspend(), "sync SUSPEND must propagate (existing behavior guard)");
        assertFalse(ret.isAsync());
    }

    private static ITaskStepRuntime newStepRt() {
        return new FakeStepRuntime(new TaskStepStateBean());
    }

    static class StubExecution implements ITaskStepExecution {
        private final String stepName;
        private final Function<ITaskStepRuntime, TaskStepReturn> fn;

        StubExecution(String stepName, Function<ITaskStepRuntime, TaskStepReturn> fn) {
            this.stepName = stepName;
            this.fn = fn;
        }

        @Override
        public String getStepName() {
            return stepName;
        }

        @Override
        public SourceLocation getLocation() {
            return null;
        }

        @Nonnull
        @Override
        public TaskStepReturn executeWithParentRt(ITaskStepRuntime parentRt) {
            return fn.apply(parentRt);
        }
    }

    /**
     * 最小 fake {@link ITaskStepRuntime}：state 支持 bodyStepIndex 读写（Sequential/Selector 推进依赖），
     * evalScope 提供空 scope，其余未用到的方法为 no-op / 抛 UnsupportedOperationException。
     */
    static class FakeStepRuntime implements ITaskStepRuntime {
        private final ITaskStepState state;
        private final IEvalScope evalScope = XLang.newEvalScope();

        FakeStepRuntime(ITaskStepState state) {
            this.state = state;
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            throw new UnsupportedOperationException("not used by async suspend propagation test");
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
            throw new UnsupportedOperationException("not used by async suspend propagation test");
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
            return evalScope;
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
