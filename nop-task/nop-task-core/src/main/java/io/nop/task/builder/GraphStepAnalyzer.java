package io.nop.task.builder;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.model.graph.dag.Dag;
import io.nop.task.TaskConstants;
import io.nop.task.model.IGraphTaskStepModel;
import io.nop.task.model.TaskInputModel;
import io.nop.task.model.TaskStepModel;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.exec.GetPropertyExecutable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.task.TaskErrors.ARG_GRAPH_STEP_NAME;
import static io.nop.task.TaskErrors.ARG_LOOP_EDGES;
import static io.nop.task.TaskErrors.ARG_OVERLAP_STEP_NAMES;
import static io.nop.task.TaskErrors.ARG_STEP_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_DUPLICATE_STEP_IN_GRAPH;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_STEP_CONTAINS_LOOP;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_STEP_NO_ENTER_STEPS;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_STEP_NO_EXIT_STEPS;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_ENTER_WAIT_OVERLAP;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_STEP_IN_GRAPH;

public class GraphStepAnalyzer {
    static final String START_NAME = "_start";
    private final Dag dag = new Dag(START_NAME);

    public void analyze(IGraphTaskStepModel stepModel) {
        initDag(stepModel);

        dag.analyze();

        if (dag.containsLoop())
            throw new NopException(ERR_TASK_GRAPH_STEP_CONTAINS_LOOP)
                    .source(stepModel)
                    .param(ARG_STEP_NAME, stepModel.getName())
                    .param(ARG_LOOP_EDGES, dag.getLoopEdges());
    }

    void initDag(IGraphTaskStepModel stepModel) {
        // 重复步骤名校验（plan 349 Phase 3）：GraphTaskStep 的 initFutures/stepFutures 按 stepName
        // 建 map，重名互相覆盖导致执行计数错乱；XML 解析经 xdef key-attr 已去重，
        // 此处防御程序化构造的模型
        Set<String> seenNames = new HashSet<>();
        for (TaskStepModel subStep : stepModel.getSteps()) {
            if (!seenNames.add(subStep.getName()))
                throw new NopException(ERR_TASK_DUPLICATE_STEP_IN_GRAPH)
                        .source(stepModel)
                        .param(ARG_GRAPH_STEP_NAME, stepModel.getName())
                        .param(ARG_STEP_NAME, subStep.getName());
        }

        if (stepModel.getEnterSteps() == null || stepModel.getEnterSteps().isEmpty())
            throw new NopException(ERR_TASK_GRAPH_STEP_NO_ENTER_STEPS)
                    .source(stepModel)
                    .param(ARG_STEP_NAME, stepModel.getName());

        if (stepModel.getExitSteps() == null || stepModel.getExitSteps().isEmpty())
            throw new NopException(ERR_TASK_GRAPH_STEP_NO_EXIT_STEPS)
                    .source(stepModel)
                    .param(ARG_STEP_NAME, stepModel.getName());


        for (String enterStep : stepModel.getEnterSteps()) {
            if (!stepModel.hasStep(enterStep))
                throw new NopException(ERR_TASK_UNKNOWN_STEP_IN_GRAPH)
                        .source(stepModel)
                        .param(ARG_GRAPH_STEP_NAME, stepModel.getName())
                        .param(ARG_STEP_NAME, enterStep);
            dag.addNextNode(START_NAME, enterStep);
        }

        for (String exitStep : stepModel.getExitSteps()) {
            if (!stepModel.hasStep(exitStep))
                throw new NopException(ERR_TASK_UNKNOWN_STEP_IN_GRAPH)
                        .source(stepModel)
                        .param(ARG_GRAPH_STEP_NAME, stepModel.getName())
                        .param(ARG_STEP_NAME, exitStep);
        }

        normalizeWaitSteps(stepModel);
        addDataDepends(stepModel);

        // W-02(a)（CRA 8.49 R4-09，1.5-C3-003 裁定 C）：enter∩wait 双调度构建期硬失败。
        // 位次在 normalizeWaitSteps（next 自动派生 waitStep）与 addDataDepends（数据依赖派生
        // wait 注入）之后，两类注入通道均被覆盖——同列 enterSteps 又被 next/waitSteps 依赖时
        // GraphTaskStep 的 enter 直跑循环与 wait 调度循环会双调度同一节点（body 副作用双跑、
        // 结果覆写）。存量模型如存在此误配，升级后装配即 fail-fast（须先修模型再升级）
        validateEnterWaitOverlap(stepModel);

        addNextStep(stepModel);
    }

    /**
     * W-02(a)（裁定 C）：enterSteps ∩ (waitSteps ∪ waitErrorSteps) 非空即硬失败。
     * 在 addDataDepends 之后调用使 next 派生/data 依赖派生两类 wait 注入均被覆盖。
     */
    private void validateEnterWaitOverlap(IGraphTaskStepModel stepModel) {
        Set<String> enterSteps = new HashSet<>(stepModel.getEnterSteps());
        Set<String> overlap = new java.util.LinkedHashSet<>();
        for (TaskStepModel subStep : stepModel.getSteps()) {
            if (!enterSteps.contains(subStep.getName()))
                continue;
            if (containsAny(subStep.getWaitSteps(), enterSteps)
                    || containsAny(subStep.getWaitErrorSteps(), enterSteps))
                overlap.add(subStep.getName());
        }
        if (!overlap.isEmpty())
            throw new NopException(ERR_TASK_GRAPH_ENTER_WAIT_OVERLAP)
                    .source(stepModel)
                    .param(ARG_GRAPH_STEP_NAME, stepModel.getName())
                    .param(ARG_OVERLAP_STEP_NAMES, overlap);
    }

    private static boolean containsAny(Set<String> steps, Set<String> enterSteps) {
        if (steps == null)
            return false;
        for (String step : steps) {
            if (enterSteps.contains(step))
                return true;
        }
        return false;
    }

    private void normalizeWaitSteps(IGraphTaskStepModel stepModel) {
        for (TaskStepModel subStep : stepModel.getSteps()) {
            if (!StringHelper.isEmpty(subStep.getNext())) {
                stepModel.getStep(subStep.getNext()).addWaitStep(subStep.getName());
            }

            if (!StringHelper.isEmpty(subStep.getNextOnError())) {
                stepModel.getStep(subStep.getNextOnError()).addWaitErrorStep(subStep.getName());
            }
        }
    }

    private void addNextStep(IGraphTaskStepModel stepModel) {
        for (TaskStepModel subStep : stepModel.getSteps()) {
            String name = subStep.getName();
            dag.addNode(name);

            if (subStep.getWaitSteps() != null) {
                subStep.getWaitSteps().forEach(waitStep -> {
                    dag.addNextNode(waitStep, name);
                });
            }

            if (subStep.getWaitErrorSteps() != null) {
                subStep.getWaitErrorSteps().forEach(waitStep -> {
                    dag.addNextNode(waitStep, name);
                });
            }
        }
    }

    private void addDataDepends(IGraphTaskStepModel stepModel) {
        for (TaskStepModel subStep : stepModel.getSteps()) {
            for (TaskInputModel inputModel : subStep.getInputs()) {
                addInputDepend(stepModel, subStep, inputModel);
            }
        }
    }

    /**
     * 步骤依赖于输入变量
     */
    private void addInputDepend(IGraphTaskStepModel stepModel, TaskStepModel subStep, TaskInputModel inputModel) {
        ScopeStepCollector collector = getScopeStepInfo(inputModel.getSource());
        if (collector == null)
            return;

        for (String varStepName : collector.stepNames) {
            if (!stepModel.hasStep(varStepName))
                throw new NopException(ERR_TASK_UNKNOWN_STEP_IN_GRAPH)
                        .source(stepModel)
                        .param(ARG_GRAPH_STEP_NAME, stepModel.getName())
                        .param(ARG_STEP_NAME, varStepName);

            subStep.addWaitStep(varStepName);
        }

        for (String varStepName : collector.errorStepNames) {
            if (!stepModel.hasStep(varStepName))
                throw new NopException(ERR_TASK_UNKNOWN_STEP_IN_GRAPH)
                        .source(stepModel)
                        .param(ARG_GRAPH_STEP_NAME, stepModel.getName())
                        .param(ARG_STEP_NAME, varStepName);

            subStep.addWaitErrorStep(varStepName);
        }
    }

    private ScopeStepCollector getScopeStepInfo(IEvalAction action) {
        if (action instanceof ExprEvalAction) {
            ScopeStepCollector collector = new ScopeStepCollector();
            ((ExprEvalAction) action).getExpr().visit(collector);
            return collector;
        }
        return null;
    }

    static class ScopeStepCollector implements IExecutableExpressionVisitor {
        private final Set<IExecutableExpression> visited = CollectionHelper.newIdentityHashSet();

        final Set<String> stepNames = new HashSet<>();
        final Set<String> errorStepNames = new HashSet<>();

        @Override
        public boolean onVisitExpr(IExecutableExpression expr) {
            if (visited.add(expr)) {
                if (expr instanceof GetPropertyExecutable) {
                    GetPropertyExecutable getExpr = (GetPropertyExecutable) expr;
                    String scopeVar = getExpr.getRootScopeVar();
                    if (TaskConstants.VAR_STEP_RESULTS.equals(scopeVar)) {
                        List<String> propPath = getExpr.collectScopePropPath();
                        String stepName = propPath.get(propPath.size() - 2);
                        if (propPath.size() == 2) {
                            // 例如STEP_RESULTS.step1
                            stepNames.add(stepName);
                        } else {
                            String propName = propPath.get(propPath.size() - 3);
                            if (propName.equals(TaskConstants.PROP_ERROR)) {
                                errorStepNames.add(stepName);
                            } else {
                                stepNames.add(stepName);
                            }
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        public Set<String> getStepNames() {
            return stepNames;
        }
    }
}
