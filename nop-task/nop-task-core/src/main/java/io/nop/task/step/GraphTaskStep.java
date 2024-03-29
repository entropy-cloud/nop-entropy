/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.util.Guard;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.task.IEnhancedTaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskConstants;
import io.nop.task.TaskStepResult;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class GraphTaskStep extends AbstractTaskStep {
    private List<IEnhancedTaskStep> enterSteps;

    private List<GraphStepNode> nodes;

    public List<IEnhancedTaskStep> getEnterSteps() {
        return enterSteps;
    }

    public void setEnterSteps(List<IEnhancedTaskStep> enterSteps) {
        this.enterSteps = enterSteps;
    }

    public List<GraphStepNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphStepNode> nodes) {
        this.nodes = nodes;
    }

    public static class GraphStepNode {
        private final Set<String> waitSteps;
        private final IEnhancedTaskStep step;
        private final boolean end;

        public GraphStepNode(Set<String> waitSteps, IEnhancedTaskStep step, boolean end) {
            this.waitSteps = waitSteps == null ? Collections.emptySet() : waitSteps;
            this.step = Guard.notNull(step, "step");
            this.end = end;
        }

        public Set<String> getWaitSteps() {
            return waitSteps;
        }

        public IEnhancedTaskStep getStep() {
            return step;
        }

        public String getStepName() {
            return step.getStepName();
        }

        public boolean isEnd() {
            return end;
        }
    }

    @Nonnull
    @Override
    public TaskStepResult execute(ITaskStepRuntime stepRt) {

        Map<String, Map<String, Object>> results = makeResults(stepRt);


        CompletableFuture<TaskStepResult> future = new CompletableFuture<>();

        Map<String, CompletableFuture<?>> futures = initFutures(results);

        Cancellable cancellable = new Cancellable();
        Consumer<String> cancel = cancellable::cancel;
        stepRt.getCancelToken().appendOnCancel(cancel);

        for (GraphStepNode node : nodes) {
            if (node.getWaitSteps().isEmpty())
                continue;

            String stepName = node.getStepName();

            CompletableFuture<?>[] waitFutures = new CompletableFuture[node.getWaitSteps().size()];
            int i = 0;
            for (String waitStep : node.getWaitSteps()) {
                waitFutures[i] = futures.get(waitStep);
                i++;
            }

            // 所有前置步骤都结束后执行该步骤
            CompletableFuture.allOf(waitFutures).whenComplete((ret, err) -> {
                if (err != null) {
                    cancellable.cancel();
                    future.completeExceptionally(err);
                } else {
                    if (cancellable.isCancelled())
                        return;

                    CompletableFuture<?> stepFuture = futures.get(stepName);
                    node.getStep().executeWithParentRt(stepRt).getReturnPromise().whenComplete((v, e) -> {
                        if (e != null) {
                            cancellable.cancel();
                            future.completeExceptionally(e);
                        } else {
                            results.put(stepName, v.getReturnValues());

                            if (node.isEnd()) {
                                // 如果是结束步骤
                                future.complete(v);
                                cancellable.cancel();
                            }

                            stepFuture.complete(null);
                        }
                    });
                }
            });
        }

        for (IEnhancedTaskStep step : enterSteps) {
            String stepName = step.getStepName();
            step.executeWithParentRt(stepRt).getReturnPromise().whenComplete((ret, err) -> {
                if (err != null) {
                    cancellable.cancel();
                    future.completeExceptionally(err);
                } else {
                    results.put(stepName, ret.getReturnValues());
                    futures.get(stepName).complete(null);
                }
            });
        }

        return TaskStepResult.ASYNC(null, future.whenComplete((ret, err) -> {
            stepRt.getCancelToken().removeOnCancel(cancel);
        }));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> makeResults(ITaskStepRuntime stepRt) {
        // STEP_RESULTS 按照stepName保存每个步骤的返回结果
        Map<String, Map<String, Object>> results = (Map<String, Map<String, Object>>)
                stepRt.getLocalValue(TaskConstants.VAR_STEP_RESULTS);
        if (results == null) {
            results = new ConcurrentHashMap<>();
            stepRt.setValue(TaskConstants.VAR_STEP_RESULTS, results);
        } else if (!(results instanceof ConcurrentHashMap)) {
            results = new ConcurrentHashMap<>(results);
            stepRt.setValue(TaskConstants.VAR_STEP_RESULTS, results);
        }
        return results;
    }

    private Map<String, CompletableFuture<?>> initFutures(Map<String, Map<String, Object>> results) {
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
