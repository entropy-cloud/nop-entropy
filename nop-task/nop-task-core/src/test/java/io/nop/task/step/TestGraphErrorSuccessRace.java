package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.state.TaskStepStateBean;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_NO_ACTIVE_STEP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 364 Phase 4 [维度05-07]：graph 收敛判据——错误消费/错误转交路径的级联-减计数顺序
 * 对齐成功路径（级联先于减计数），并以单一判据 completeGraphIfDrained 收敛全部
 * "runningCount==0" 检查。本测试构造"错误分支 × 并发成功分支"交错（audit 指出的
 * TestGraphDrainRace 缺口）：C 失败（错误被 D 消费）与 B 成功并发完成时，全图既不得被
 * 误判 ERR_TASK_GRAPH_NO_ACTIVE_STEP 终结，也必须正常收敛到 exit。
 */
public class TestGraphErrorSuccessRace {

    private static final int ITERATIONS = 300;

    @Test
    public void errorConsumerRaceWithSuccessBranch_noFalseDrain_alwaysConverges() throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            runOnce(i);
        }
    }

    private void runOnce(int round) throws Exception {
        // B、C 由 enter 并发派发；C 失败且错误被 D 消费（waitError）；E(exit) 等待 B,D
        CompletableFuture<TaskStepReturn> futureB = new CompletableFuture<>();
        CompletableFuture<TaskStepReturn> futureC = new CompletableFuture<>();

        GraphTaskStep graph = new GraphTaskStep();
        graph.setNodes(Arrays.asList(
                node("B", null, rt -> TaskStepReturn.ASYNC_RETURN(futureB), true, false),
                node("C", null, rt -> TaskStepReturn.ASYNC_RETURN(futureC), true, false),
                errNode("D", "C", rt -> TaskStepReturn.RETURN_RESULT("recovered")),
                node("E", "B,D", rt -> TaskStepReturn.RETURN_RESULT("done"), false, true)));

        ITaskStepRuntime stepRt = new TestGraphDrainRace.FakeGraphStepRuntime(new TaskStepStateBean());
        TaskStepReturn ret = graph.execute(stepRt);
        assertTrue(ret.isAsync(), "racing graph must return async");

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger barrierErrors = new AtomicInteger();
        Thread tb = new Thread(() -> {
            if (await(barrier, barrierErrors))
                futureB.complete(TaskStepReturn.RETURN_RESULT("b"));
        }, "race-success-" + round);
        Thread tc = new Thread(() -> {
            if (await(barrier, barrierErrors))
                futureC.completeExceptionally(new IllegalStateException("boom-" + round));
        }, "race-error-" + round);
        tb.start();
        tc.start();

        try {
            TaskStepReturn done = ret.getReturnPromise().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals("done", done.getResult(),
                    "exit must surface after success branch and error branch (via D) complete");
        } catch (Exception e) {
            if (containsNoActiveStep(e))
                fail("round " + round + " false-drained: graph terminated with ERR_TASK_GRAPH_NO_ACTIVE_STEP "
                        + "while error branch and success branch were racing; root=" + rootMessage(e));
            throw e;
        } finally {
            tb.join(5000);
            tc.join(5000);
        }
    }

    private boolean containsNoActiveStep(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof NopException
                    && ERR_TASK_GRAPH_NO_ACTIVE_STEP.getErrorCode().equals(((NopException) c).getErrorCode()))
                return true;
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        String msg = t.getMessage();
        while (msg == null && t.getCause() != null) {
            t = t.getCause();
            msg = t.getMessage();
        }
        return t.getClass().getSimpleName() + ": " + msg;
    }

    private static boolean await(CyclicBarrier barrier, AtomicInteger errors) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            errors.incrementAndGet();
            return false;
        }
    }

    private static GraphTaskStep.GraphStepNode node(String name, String waits,
                                                    Function<ITaskStepRuntime, TaskStepReturn> fn,
                                                    boolean enter, boolean exit) {
        Set<String> waitSteps = waits == null ? new HashSet<>() : new HashSet<>(Arrays.asList(waits.split(",")));
        return new GraphTaskStep.GraphStepNode(waitSteps, null, new StubExecution(name, fn), enter, exit);
    }

    /** 错误消费节点：waitError 触发，持有 nextOnErrorStepName 使其被计入 errorConsumers。 */
    private static GraphTaskStep.GraphStepNode errNode(String name, String waitErrors,
                                                       Function<ITaskStepRuntime, TaskStepReturn> fn) {
        Set<String> waitErrorSteps = new HashSet<>(Arrays.asList(waitErrors.split(",")));
        return new GraphTaskStep.GraphStepNode(new HashSet<>(), waitErrorSteps,
                new StubExecution(name, fn), false, false, name);
    }

    private static class StubExecution implements ITaskStepExecution {
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
        public io.nop.api.core.util.SourceLocation getLocation() {
            return null;
        }

        @Override
        public TaskStepReturn executeWithParentRt(ITaskStepRuntime parentRt) {
            return fn.apply(parentRt);
        }
    }
}
