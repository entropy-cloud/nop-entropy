/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.task.ITaskStepExecution;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.StepResultBean;
import io.nop.task.TaskConstants;
import io.nop.task.TaskErrors;
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

        /**
         * 本节点声明的 nextOnError 目标（plan 349 Phase 3）：TaskStepExecution 层把失败包装为
         * 携带该跳转名的"成功返回"，图层据此识别 error-handoff 并按失败语义级联
         */
        private final String nextOnErrorStepName;

        public GraphStepNode(Set<String> waitSteps, Set<String> waitErrorSteps,
                             ITaskStepExecution step, boolean enter, boolean exit) {
            this(waitSteps, waitErrorSteps, step, enter, exit, null);
        }

        public GraphStepNode(Set<String> waitSteps, Set<String> waitErrorSteps,
                             ITaskStepExecution step, boolean enter, boolean exit, String nextOnErrorStepName) {

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
            this.nextOnErrorStepName = nextOnErrorStepName;
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

        public String getNextOnErrorStepName() {
            return nextOnErrorStepName;
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
                    // 计数归零即放行（plan 349 Phase 3）：依赖失败 → 触发本错误分支执行；
                    // 依赖成功 → 错误条件不再可能满足，按"跳过"放行（由 runStep 前的
                    // errorTriggerFired 检查决定不执行 body，仅级联后继）。
                    // 修复前仅 err != null 才 complete：被等待步骤成功时 waitFuture 永不完成，图挂死
                    if (waitingCount.get() <= 0)
                        ret.complete(null);
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

        // 错误消费者集合（plan 349 Phase 3）：waitError/waitComplete 依赖某步骤失败的节点。
        // 这些步骤失败时错误可被消费——图不 fail-fast，而是级联错误分支；
        // waitComplete 语义为成功/失败都放行，同样消费错误
        Set<String> errorConsumers = new HashSet<>();
        for (GraphStepNode node : nodes) {
            errorConsumers.addAll(node.getWaitErrorSteps());
            errorConsumers.addAll(node.getWaitCompleteSteps());
        }

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
                        // 上游失败/跳过（plan 349 Phase 3）：本节点不执行，把失败继续传播到本节点的
                        // stepFuture，使下游等待者级联决策（waitSuccess→继续跳过、waitError→触发）。
                        // 图级 future 不在此失败——错误是否终结全图由失败步骤的错误消费者决定；
                        // 全部路径死端时由 runningCount==0 兜底报 NO_ACTIVE_STEP
                        stepFuture.completeExceptionally(err);
                        if (runningCount.get() == 0 && !future.isDone())
                            future.completeExceptionally(noActiveStepError(stepRt));
                    } else {
                        runStep(node, stepRt, cancellable, future, stepFutures, runningCount,
                                stepResults, errorConsumers);
                    }
                });
            }

            runningCount.incrementAndGet();
            try {
                for (GraphStepNode node : nodes) {
                    if (!node.isEnter())
                        continue;

                    runStep(node, stepRt, cancellable, future, stepFutures, runningCount,
                            stepResults, errorConsumers);
                }
            } finally {
                runningCount.decrementAndGet();
            }

            if (runningCount.get() == 0 && !future.isDone())
                throw noActiveStepError(stepRt);

            return future;
        }, stepRt, true);

        return TaskStepReturn.ASYNC(null, promise);
    }

    private void runStep(GraphStepNode node, ITaskStepRuntime stepRt, ICancellable cancellable,
                         CompletableFuture<TaskStepReturn> future, Map<String, CompletableFuture<?>> stepFutures,
                         AtomicInteger runningCount, Map<String, StepResultBean> stepResults,
                         Set<String> errorConsumers) {
        String stepName = node.getStepName();
        CompletableFuture<?> stepFuture = stepFutures.get(stepName);

        if (cancellable.isCancelled()) {
            stepFuture.cancel(false);
            future.cancel(false);
            return;
        }

        // waitError 触发检查（plan 349 Phase 3）：声明的错误触发步骤全部成功完成（无一失败）时，
        // 错误分支前提不成立——跳过本节点 body，但完成 stepFuture 以级联后继
        if (!errorTriggerFired(node, stepResults)) {
            runningCount.incrementAndGet();
            try {
                stepFuture.complete(null);
            } finally {
                runningCount.decrementAndGet();
            }
            if (runningCount.get() == 0 && !future.isDone())
                future.completeExceptionally(noActiveStepError(stepRt));
            return;
        }

        runningCount.incrementAndGet();
        node.getStep().executeAsync(stepRt).whenComplete((v, e) -> {
            if (e != null) {
                runningCount.decrementAndGet();
                if (errorConsumers.contains(stepName)) {
                    // 错误可被消费（plan 349 Phase 3，修复 check2 P1 错误边不可达）：
                    // 先记录错误结果（供下游 errorTriggerFired 判定与 STEP_RESULTS 消费），
                    // 再以异常完成 stepFuture 触发 waitError/waitComplete 等待者——
                    // 错误分支得以执行；成功边等待者按跳过级联。图是否失败由错误分支的
                    // 执行结果决定，全部路径死端时由 runningCount==0 兜底
                    StepResultBean errorResult = new StepResultBean();
                    errorResult.setStepName(stepName);
                    errorResult.setError(ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(), e));
                    stepResults.put(stepName, errorResult);
                    stepFuture.completeExceptionally(e);
                    if (runningCount.get() == 0 && !future.isDone())
                        future.completeExceptionally(noActiveStepError(stepRt));
                } else {
                    // 无错误消费者：维持 fail-fast，取消全图
                    cancellable.cancel();
                    future.completeExceptionally(e);
                }
                return;
            }

            if (v != null && v.isSuspend()) {
                // 挂起传播（plan 349 Phase 1）：不记录结果（resume 后该节点应重新调度）、
                // 不级联后继（挂起期间不应继续调度），图以挂起返回值终结；
                // 兄弟分支经 cancellable.cancel 停止派发
                runningCount.decrementAndGet();
                future.complete(v);
                cancellable.cancel();
                return;
            }

            StepResultBean result = StepResultBean.buildFromResult(stepName, stepRt.getLocale(), v);

            if (isErrorHandoff(node, v)) {
                // 错误分支转交（plan 349 Phase 3，修复 check2 P1 错误边不可达的另一半根因）：
                // TaskStepExecution.buildErrorResult 把失败包装为携带 nextOnError 跳转的成功返回，
                // 图层在此将其还原为"本节点失败"语义——以异常完成 stepFuture，
                // 使 waitSuccess 等待者按跳过级联、waitError（错误分支）触发执行。
                // 修复前 x 的失败被视为成功完成，错误分支被 skip 语义跳过、下游读不到错误分支结果
                runningCount.decrementAndGet();
                ErrorBean errorBean = extractHandoffError(v);
                stepResults.put(stepName, buildErrorResultBean(stepName, stepRt, errorBean, v));
                Throwable err = errorBean == null ? null : NopRebuildException.rebuild(errorBean);
                stepFuture.completeExceptionally(err != null ? err
                        : new NopException(ERR_TASK_GRAPH_NO_ACTIVE_STEP)
                                .source(this)
                                .param(ARG_STEP_PATH, stepRt.getStepPath()));
                if (runningCount.get() == 0 && !future.isDone())
                    future.completeExceptionally(noActiveStepError(stepRt));
                return;
            }

            stepResults.put(stepName, result);
            // 先触发后继级联再减计数：stepFuture.complete(null) 会同步触发 waitFuture
            // 回调并对后继 runStep 执行 incrementAndGet。若先减计数，并发完成窗口内
            // 会出现瞬态 runningCount==0（本节点已减、后继未加），被下方检查误判为
            // ERR_TASK_GRAPH_NO_ACTIVE_STEP（图未结束但无活跃步骤），破坏正常流程
            stepFuture.complete(null);

            if (node.isExit()) {
                // 如果是结束步骤
                LOG.info("nop.task.run-graph-end:stepPath={},outputs={}",stepRt.getStepPath(),v.getOutputs());
                future.complete(v);
                cancellable.cancel();
            }

            runningCount.decrementAndGet();

            if (runningCount.get() == 0 && !future.isDone()) {
                // whenComplete回调内的throw进入被丢弃的依赖future（异常静默丢失、图挂死），
                // 必须以completeExceptionally终结图级future
                future.completeExceptionally(noActiveStepError(stepRt));
            }
        });
    }

    /**
     * waitError 触发判定：节点声明的错误触发步骤中存在失败者（stepResults 记录了错误结果）→ 触发；
     * 全部成功完成或尚未有失败记录 → 未触发（跳过节点 body）。
     */
    private boolean errorTriggerFired(GraphStepNode node, Map<String, StepResultBean> stepResults) {
        if (node.getWaitErrorSteps().isEmpty())
            return true;
        for (String waitError : node.getWaitErrorSteps()) {
            StepResultBean r = stepResults.get(waitError);
            if (r != null && !r.isSuccess())
                return true;
        }
        return false;
    }

    /**
     * 判定成功完成值是否为 error-handoff：节点声明了 nextOnError 且完成值携带该跳转
     * （TaskStepExecution.buildErrorResult 的包装形态）。
     */
    private boolean isErrorHandoff(GraphStepNode node, TaskStepReturn v) {
        String nextOnError = node.getNextOnErrorStepName();
        return nextOnError != null && nextOnError.equals(v.getNextStepName());
    }

    private ErrorBean extractHandoffError(TaskStepReturn v) {
        Object result = v.getResult();
        if (result instanceof ErrorBean)
            return (ErrorBean) result;
        if (result instanceof Map) {
            Object err = ((Map<?, ?>) result).get(TaskConstants.VAR_ERROR);
            if (err instanceof ErrorBean)
                return (ErrorBean) err;
        }
        return null;
    }

    private StepResultBean buildErrorResultBean(String stepName, ITaskStepRuntime stepRt,
                                                ErrorBean errorBean, TaskStepReturn v) {
        StepResultBean errorResult = new StepResultBean();
        errorResult.setStepName(stepName);
        if (errorBean != null) {
            errorResult.setError(errorBean);
        } else {
            // 罕见：nextOnError 跳转被用户步骤显式返回（非 buildErrorResult 包装）——
            // 构造通用错误标记，保证错误分支触发语义不受影响
            errorResult.setError(ErrorMessageManager.instance().buildErrorMessage(stepRt.getLocale(),
                    new NopException(TaskErrors.ERR_TASK_STEP_ALREADY_FAILED)
                            .param(TaskErrors.ARG_STEP_PATH, stepRt.getStepPath())));
        }
        return errorResult;
    }

    private NopException noActiveStepError(ITaskStepRuntime stepRt) {
        return new NopException(ERR_TASK_GRAPH_NO_ACTIVE_STEP)
                .source(this)
                .param(ARG_STEP_PATH, stepRt.getStepPath());
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