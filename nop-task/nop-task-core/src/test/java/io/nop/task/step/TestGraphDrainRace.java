package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * check2 后续回归（2026-08-28 全量测试暴露）：GraphTaskStep 的 drain 判定
 * （runningCount==0 && !future.isDone() → completeExceptionally）在两前驱并发完成时
 * 存在瞬时零误判——后完成者的 decrement 与后继 runStep 的 increment 之间，
 * 另一线程的 drain 判定读到 0，图级 future 被误判"无活跃步骤"异常终结。
 *
 * <p>plan344 把该判定从 throw（异常被丢弃、无害）改为 completeExceptionally 后，
 * 竞态假阳性真正生效：菱形 join 图随机性整体失败（nop-ai-agent 的 fan-out
 * 测试在全模块运行时可稳定复现 success=false + failedTaskId=null）。
 *
 * <p>复现方式：菱形 A→{B,C}→D，B/C 的步骤 future 由两个线程经 CyclicBarrier
 * 对齐后同时完成，使两个 whenComplete 回调并发进入 runStep 的完成路径。
 * 300 轮迭代，修复前以 ERR_TASK_GRAPH_NO_ACTIVE_STEP 随机失败（红），
 * 修复后全部成功。
 */
public class TestGraphDrainRace {

    private static final int ITERATIONS = 300;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void concurrentPredecessorCompletion_noFalseDrain() throws Exception {
        int falseDrain = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            try {
                runDiamondOnce();
            } catch (AssertionError e) {
                throw e;
            } catch (Throwable t) {
                falseDrain++;
                if (falseDrain == 1) {
                    // 首次失败即报详细形态，便于确认是误判 drain 而非其他缺陷
                    t.printStackTrace();
                }
            }
        }
        assertEquals(0, falseDrain,
                "graph must never false-drain when join successors are being scheduled; "
                        + falseDrain + "/" + ITERATIONS + " iterations failed");
    }

    private void runDiamondOnce() throws Exception {
        CompletableFuture<TaskStepReturn> futureB = new CompletableFuture<>();
        CompletableFuture<TaskStepReturn> futureC = new CompletableFuture<>();

        GraphTaskStep graph = new GraphTaskStep();
        graph.setNodes(Arrays.asList(
                node("A", null, new StubExecution("A", rt -> TaskStepReturn.RETURN_RESULT("a")), true, false),
                node("B", "A", new StubExecution("B", rt -> TaskStepReturn.ASYNC_RETURN(futureB)), false, false),
                node("C", "A", new StubExecution("C", rt -> TaskStepReturn.ASYNC_RETURN(futureC)), false, false),
                node("D", "B,C", new StubExecution("D", rt -> TaskStepReturn.RETURN_RESULT("done")), false, true)));

        ITaskStepRuntime stepRt = new FakeGraphStepRuntime(new TaskStepStateBean());
        TaskStepReturn ret = graph.execute(stepRt);
        assertTrue(ret.isAsync(), "diamond with pending B/C futures must return async");

        CyclicBarrier barrier = new CyclicBarrier(2);
        Thread tb = new Thread(() -> {
            await(barrier);
            futureB.complete(TaskStepReturn.RETURN_RESULT("b"));
        }, "drain-race-b");
        Thread tc = new Thread(() -> {
            await(barrier);
            futureC.complete(TaskStepReturn.RETURN_RESULT("c"));
        }, "drain-race-c");
        tb.start();
        tc.start();

        try {
            TaskStepReturn done = ret.getReturnPromise().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals("done", done.getResult(),
                    "exit step result must surface after both predecessors complete");
        } catch (Exception e) {
            fail("diamond failed spuriously: " + rootMessage(e));
        } finally {
            tb.join(5000);
            tc.join(5000);
        }
    }

    private static String rootMessage(Throwable t) {
        String msg = t.getMessage();
        while (msg == null && t.getCause() != null) {
            t = t.getCause();
            msg = t.getMessage();
        }
        return t.getClass().getSimpleName() + ": " + msg;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static GraphTaskStep.GraphStepNode node(String name, String waits, ITaskStepExecution step,
                                            boolean enter, boolean exit) {
        Set<String> waitSteps = waits == null
                ? new HashSet<>() : new HashSet<>(Arrays.asList(waits.split(",")));
        return new GraphTaskStep.GraphStepNode(waitSteps, null, step, enter, exit);
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
     * 最小 fake runtime：cancelToken 可读写（GraphTaskStep 的 withCancellable 依赖）、
     * evalScope 提供真实 scope（STEP_RESULTS 存取），其余 no-op。
     */
    static class FakeGraphStepRuntime implements ITaskStepRuntime {
        private final ITaskStepState state;
        private final IEvalScope evalScope = XLang.newEvalScope();
        private ICancelToken cancelToken;

        FakeGraphStepRuntime(ITaskStepState state) {
            this.state = state;
        }

        @Override
        public ITaskRuntime getTaskRuntime() {
            throw new UnsupportedOperationException("not used by drain race test");
        }

        @Override
        public String getLocale() {
            return "zh-CN";
        }

        @Override
        public String getStepPath() {
            return "test-drain-race";
        }

        @Override
        public ICancelToken getCancelToken() {
            return cancelToken;
        }

        @Override
        public void setCancelToken(ICancelToken cancelToken) {
            this.cancelToken = cancelToken;
        }

        @Override
        public IEvalScope getEvalScope() {
            return evalScope;
        }

        @Override
        public Set<String> getOutputNames() {
            return Collections.emptySet();
        }

        @Override
        public void setOutputNames(Set<String> outputNames) {
        }

        @Override
        public ITaskStepState getState() {
            return state;
        }

        @Override
        public Set<String> getTagSet() {
            return Collections.emptySet();
        }

        @Override
        public void setTagSet(Set<String> enabledFlags) {
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
            return this;
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
    }
}
