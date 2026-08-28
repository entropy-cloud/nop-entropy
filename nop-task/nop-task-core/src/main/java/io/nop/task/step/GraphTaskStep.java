/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepReturn;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_NO_ACTIVE_STEP;

public class GraphTaskStep extends AbstractTaskStep {
    static final Logger LOG = LoggerFactory.getLogger(GraphTaskStep.class);

    private List<GraphStepNode> nodes;

    /**
     * 被 waitError / waitComplete 边引用的步骤名集合（静态图结构，setNodes 时计算）。
     * 这些步骤失败时存在错误边后继可消费该失败，runStep 的错误路径据此决定是否保持 fail-fast。
     */
    private Set<String> errorConsumedSteps = Collections.emptySet();

    public List<GraphStepNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphStepNode> nodes) {
        this.nodes = nodes;
        Set<String> consumed = new HashSet<>();
        for (GraphStepNode node : nodes) {
            consumed.addAll(node.getWaitErrorSteps());
            consumed.addAll(node.getWaitCompleteSteps());
        }
        this.errorConsumedSteps = consumed;
    }

    public static class GraphStepNode {
        private final Set<String> waitSuccessSteps;

        private final Set<String> waitErrorSteps;

        private final Set<String> waitCompleteSteps;
        private final ITaskStepExecution step;

        private final boolean enter;
        private final boolean exit;

        public GraphStepNode(Set<String> waitSteps, Set<String> waitErrorSteps,
                             ITaskStepExecution step, boolean enter, boolean exit) {

            Set<String> successSteps = waitSteps == null ? Collections.emptySet() : waitSteps;
            Set<String> errorSteps = waitErrorSteps == null ? Collections.emptySet() : waitErrorSteps;
            Set<String> completeSteps = new HashSet<>(successSteps);
            completeSteps.retainAll(errorSteps);

            successSteps.removeAll(completeSteps);
            errorSteps.removeAll(completeSteps);

            this.waitSuccessSteps = successSteps;
            this.waitErrorSteps = errorSteps;
            this.waitCompleteSteps = completeSteps;
            this.step = Guard.notNull(step, "step");
            this.enter = enter;
            this.exit = exit;
        }

        public Set<String> getWaitCompleteSteps() {
            return waitCompleteSteps;
        }

        public Set<String> getWaitErrorSteps() {
            return waitErrorSteps;
        }

        public Set<String> getWaitSuccessSteps() {
            return waitSuccessSteps;
        }

        public int getWaitCount() {
            return waitSuccessSteps.size() + waitErrorSteps.size() + waitCompleteSteps.size();
        }

        public ITaskStepExecution getStep() {
            return step;
        }

        public String getStepName() {
            return step.getStepName();
        }

        public boolean isEnter() {
            return enter;
        }

        public boolean isExit() {
            return exit;
        }

        public CompletableFuture<Void> buildWaitFuture(Map<String, CompletableFuture<?>> allFutures) {
            CompletableFuture<Void> ret = new CompletableFuture<>();
            AtomicInteger waitingCount = new AtomicInteger(waitSuccessSteps.size() + this.waitErrorSteps.size() + this.waitCompleteSteps.size());

            for (String waitSuccess : this.waitSuccessSteps) {
                CompletableFuture<?> future = allFutures.get(waitSuccess);
                future.whenComplete((result, err) -> {
                    waitingCount.decrementAndGet();
                    if (err != null) {
                        ret.completeExceptionally(err);
                    } else if (waitingCount.get() <= 0) {
                        ret.complete(null);
                    }
                });
            }

            for (String waitError : this.waitErrorSteps) {
                CompletableFuture<?> future = allFutures.get(waitError);
                future.whenComplete((result, err) -> {
                    waitingCount.decrementAndGet();
                    if (err != null) {
                        if (waitingCount.get() <= 0)
                            ret.complete(null);
                    }
                });
            }

            for (String waitComplete : this.waitCompleteSteps) {
                CompletableFuture<?> future = allFutures.get(waitComplete);
                future.whenComplete((result, err) -> {
                    waitingCount.decrementAndGet();
                    if (waitingCount.get() <= 0)
                        ret.complete(null);
                });
            }

            return ret;
        }
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {

        // STEP_RESULTS 按照stepName保存每个步骤的返回结果
        Map<String, StepResultBean> stepResults = makeResults(stepRt);

        CompletableFuture<TaskStepReturn> future = new CompletableFuture<>();

        Map<String, CompletableFuture<?>> stepFutures = initFutures(stepResults);

        AtomicInteger runningCount = new AtomicInteger();

        // 首个节点错误。错误被错误边后继消费时不立即 fail-fast，若图最终 drain（无可达 exit），
        // 结尾判定用它终结图级 future，避免原图错误被 ERR_TASK_GRAPH_NO_ACTIVE_STEP 掩盖
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        CompletionStage<?> promise = TaskStepHelper.withCancellable(() -> {
            ICancellable cancellable = (ICancellable) stepRt.getCancelToken();

            for (GraphStepNode node : nodes) {
                if (node.getWaitCount() <= 0)
                    continue;

                CompletableFuture<Void> waitFuture = node.buildWaitFuture(stepFutures);

                CompletableFuture<?> stepFuture = stepFutures.get(node.getStepName());

                // 所有前置步骤都结束后执行该步骤
                waitFuture.whenComplete((ret, err) -> {
                    if (err != null) {
                        cancellable.cancel();
                        stepFuture.completeExceptionally(err);
                        future.completeExceptionally(err);
                    } else {
                        runStep(node, stepRt, cancellable, future, stepFutures, runningCount, stepResults, firstError);
                    }
                });
            }

            runningCount.incrementAndGet();
            try {
                for (GraphStepNode node : nodes) {
                    if (!node.isEnter())
                        continue;

                    runStep(node, stepRt, cancellable, future, stepFutures, runningCount, stepResults, firstError);
                }
            } finally {
                runningCount.decrementAndGet();
            }

            if (runningCount.get() == 0 && !future.isDone())
                throw new NopException(ERR_TASK_GRAPH_NO_ACTIVE_STEP)
                        .source(this)
                        .param(ARG_STEP_PATH, stepRt.getStepPath());

            return future;
        }, stepRt, true);

        return TaskStepReturn.ASYNC(null, promise);
    }

    private void runStep(GraphStepNode node, ITaskStepRuntime stepRt, ICancellable cancellable,
                         CompletableFuture<TaskStepReturn> future, Map<String, CompletableFuture<?>> stepFutures,
                         AtomicInteger runningCount, Map<String, StepResultBean> stepResults,
                         AtomicReference<Throwable> firstError) {
        String stepName = node.getStepName();
        CompletableFuture<?> stepFuture = stepFutures.get(stepName);

        if (cancellable.isCancelled()) {
            stepFuture.cancel(false);
            future.cancel(false);
            return;
        }

        runningCount.incrementAndGet();
        node.getStep().executeAsync(stepRt).whenComplete((v, e) -> {
            // decrement 必须后置于完成级联（stepFuture.complete → 同步调度后继 runStep → increment）：
            // 级联期间本节点仍被计数，并发完成线程的 drain 判定不会读到"后继调度中"的瞬时 0
            // （菱形双前驱并发完成时曾误判 ERR_TASK_GRAPH_NO_ACTIVE_STEP，TestGraphDrainRace 复现）
            if (e != null) {
                firstError.compareAndSet(null, e);

                // 失败节点写入 STEP_RESULTS 错误条目，错误边后继经 STEP_RESULTS.x.error 可读取失败信息
                StepResultBean errorResult = new StepResultBean();
                errorResult.setStepName(stepName);
                errorResult.setError(ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), e));
                stepResults.put(stepName, errorResult);

                // 失败节点的 stepFuture 必须以异常完成，waitError/waitComplete 边的后继步骤才能推进
                // （error-join 依赖 stepFuture 的异常完成信号）。完成动作会同步触发已注册的后继调度，
                // 使 runningCount 在下方 fail-fast 判定前被后继的 runStep 递增。
                stepFuture.completeExceptionally(e);
                if (!errorConsumedSteps.contains(stepName)) {
                    // 无错误边后继消费此失败 → 保持 fail-fast：取消全图并以原异常终结
                    cancellable.cancel();
                    future.completeExceptionally(e);
                }
            } else {
                StepResultBean result = StepResultBean.buildFromResult(stepName, stepRt.getLocale(), v);
                stepResults.put(stepName, result);
                stepFuture.complete(null);

                if (node.isExit()) {
                    // 如果是结束步骤
                    LOG.info("nop.task.run-graph-end:stepPath={},outputs={}",stepRt.getStepPath(),v.getOutputs());
                    future.complete(v);
                    cancellable.cancel();
                }
            }

            runningCount.decrementAndGet();

            if (runningCount.get() == 0 && !future.isDone()) {
                // whenComplete回调内的throw进入被丢弃的依赖future（异常静默丢失、图挂死），
                // 必须以completeExceptionally终结图级future。错误被消费但图 drain 时保留首个错误
                future.completeExceptionally(buildDrainError(stepRt, firstError));
            }
        });
    }

    private Throwable buildDrainError(ITaskStepRuntime stepRt, AtomicReference<Throwable> firstError) {
        Throwable err = firstError.get();
        if (err == null) {
            return new NopException(ERR_TASK_GRAPH_NO_ACTIVE_STEP)
                    .source(this)
                    .param(ARG_STEP_PATH, stepRt.getStepPath());
        }
        // 图 drain 时保留首个节点错误（可能已被错误边后继消费但未抵达 exit），不掩盖原始失败
        if (err instanceof NopException)
            ((NopException) err).addXplStack(stepRt.getStepPath() + '@' + getLocation());
        return err;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, StepResultBean> makeResults(ITaskStepRuntime stepRt) {
        String varName = TaskConstants.VAR_STEP_RESULTS;
        Map<String, StepResultBean> results = (Map<String, StepResultBean>)
                stepRt.getLocalValue(varName);
        if (results == null) {
            results = new ConcurrentHashMap<>();
            stepRt.setValue(varName, results);
        }
        return results;
    }

    private Map<String, CompletableFuture<?>> initFutures(Map<String, StepResultBean> results) {
        Map<String, CompletableFuture<?>> futures = new HashMap<>();

        for (GraphStepNode node : nodes) {
            String stepName = node.getStepName();
            if (results.containsKey(stepName)) {
                // 已经结束
                futures.put(stepName, CompletableFuture.completedFuture(null));
            } else {
                futures.put(stepName, new CompletableFuture<>());
            }
        }
        return futures;
    }
}