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
import static io.nop.task.TaskErrors.ARG_STEP_NAME;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_STEP_CONTAINS_LOOP;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_STEP_NO_ENTER_STEPS;
import static io.nop.task.TaskErrors.ERR_TASK_GRAPH_STEP_NO_EXIT_STEPS;
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
                        .param(ARG_STEP_NAME, stepModel.getName());
            dag.addNextNode(START_NAME, enterStep);
        }

        for (String enterStep : stepModel.getExitSteps()) {
            if (!stepModel.hasStep(enterStep))
                throw new NopException(ERR_TASK_UNKNOWN_STEP_IN_GRAPH)
                        .source(stepModel)
                        .param(ARG_STEP_NAME, stepModel.getName());
        }

        normalizeWaitSteps(stepModel);
        addDataDepends(stepModel);

        addNextStep(stepModel);
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
