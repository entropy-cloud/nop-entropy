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

import static io.nop.task.TaskErrors.ARG_STEP_PATH;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_NO_ACTIVE_STEP;

public class GraphTaskStep extends AbstractTaskStep {
    static final Logger LOG = LoggerFactory.getLogger(GraphTaskStep.class);

    private List<GraphStepNode> nodes;

    public List<GraphStepNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphStepNode> nodes) {
        this.nodes = nodes;
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
                        runStep(node, stepRt, cancellable, future, stepFutures, runningCount, stepResults);
                    }
                });
            }

            runningCount.incrementAndGet();
            try {
                for (GraphStepNode node : nodes) {
                    if (!node.isEnter())
                        continue;

                    runStep(node, stepRt, cancellable, future, stepFutures, runningCount, stepResults);
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
                         AtomicInteger runningCount, Map<String, StepResultBean> stepResults) {
        String stepName = node.getStepName();
        CompletableFuture<?> stepFuture = stepFutures.get(stepName);

        if (cancellable.isCancelled()) {
            stepFuture.cancel(false);
            future.cancel(false);
            return;
        }

        runningCount.incrementAndGet();
        node.getStep().executeAsync(stepRt).whenComplete((v, e) -> {
            runningCount.decrementAndGet();

            if (e != null) {
                cancellable.cancel();
                future.completeExceptionally(e);
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

            if (runningCount.get() == 0 && !future.isDone())
                throw new NopException(ERR_TASK_GRAPH_NO_ACTIVE_STEP)
                        .source(this)
                        .param(ARG_STEP_PATH, stepRt.getStepPath());
        });
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