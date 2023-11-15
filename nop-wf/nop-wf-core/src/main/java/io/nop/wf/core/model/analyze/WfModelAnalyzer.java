/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model.analyze;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.KeyedList;
import io.nop.core.model.graph.GraphDepthFirstIterator;
import io.nop.core.model.graph.dag.Dag;
import io.nop.wf.core.model.IWorkflowStartModel;
import io.nop.wf.core.model.WfActionModel;
import io.nop.wf.core.model.WfJoinStepModel;
import io.nop.wf.core.model.WfJoinType;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfRefActionModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.model.WfTransitionModel;
import io.nop.wf.core.model.WfTransitionToModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ARG_LOOP_EDGES;
import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_GRAPH_CONTAINS_LOOP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_STEP_NOT_ENDABLE;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TO_UNKNOWN_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;

public class WfModelAnalyzer {
    static final Logger LOG = LoggerFactory.getLogger(WfModelAnalyzer.class);

    public void analyze(final WfModel wfModel) {
        IWorkflowStartModel start = wfModel.getStart();
        String startStepName = start.getStartStepName();

        WfStepModel startStep = wfModel.getStep(startStepName);

        if (startStep == null)
            throw new NopException(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, startStepName);

        initStepActions(wfModel);

        Dag dag = buildDag(wfModel);

        wfModel.getSteps().forEach(step -> {
            step.setStepIndex(dag.requireNode(step.getName()).getNodeIndex());
        });

        if (wfModel.isAllowStepLoop()) {
            if (dag.containsLoop())
                throw new NopException(ERR_WF_GRAPH_CONTAINS_LOOP)
                        .source(wfModel)
                        .param(ARG_LOOP_EDGES, dag.getLoopEdges());
        }

        wfModel.setDag(dag);

        initTransitionFromSteps(wfModel);

        checkEnd(wfModel);

        checkWaitSteps(wfModel, dag);
    }

    private void initStepActions(WfModel wfModel) {
        wfModel.getSteps().forEach(step -> {
            List<WfRefActionModel> refActions = step.getRefActions();
            if (refActions != null) {
                List<WfActionModel> actions = new KeyedList<>(refActions.size(), WfActionModel::getName);
                refActions.forEach(refAction -> {
                    WfActionModel action = (WfActionModel) wfModel.requireAction(refAction.getName());
                    actions.add(action);
                });
                step.setActions(actions);
            }
        });
    }

    private void initTransitionFromSteps(WfModel wfModel) {
        Map<String, Set<WfStepModel>> fromMap = new HashMap<>();

        for (WfStepModel stepModel : wfModel.getSteps()) {
            if (stepModel.getTransitionToSteps() != null) {
                for (WfStepModel toStep : stepModel.getTransitionToSteps()) {
                    fromMap.computeIfAbsent(toStep.getName(), k -> new LinkedHashSet<>()).add(stepModel);
                }
            }
        }

        fromMap.forEach((name, list) -> {
            wfModel.getStep(name).setTransitionFromSteps(new ArrayList<>(list));
        });

        wfModel.getSteps().forEach(step -> {
            if (step.getTransitionToSteps() == null) {
                step.setTransitionToSteps(Collections.emptyList());
            }
            if (step.getTransitionFromSteps() == null) {
                step.setTransitionToSteps(Collections.emptyList());
            }
        });
    }

    private void checkEnd(WfModel wfModel) {
        for (WfStepModel step : wfModel.getSteps()) {
            if (step.isNextToEnd()) {
                Iterator<WfStepModel> it = new GraphDepthFirstIterator<>(s -> {
                    // 如果已经遍历过，则可以跳过，避免重复处理
                    if (s.isEventuallyToEnd())
                        return null;
                    return s.getTransitionFromSteps();
                }, step);

                while (it.hasNext()) {
                    it.next().setEventuallyToEnd(true);
                }
            }

            if (step.isNextToEmpty()) {
                Iterator<WfStepModel> it = new GraphDepthFirstIterator<>(s -> {
                    if (s.isEventuallyToEmpty())
                        return null;
                    return s.getTransitionFromSteps();
                }, step);

                while (it.hasNext()) {
                    it.next().setEventuallyToEmpty(true);
                }
            }

            Iterator<WfStepModel> it = new GraphDepthFirstIterator<>(s -> {
                if (s.isEventuallyToAssigned())
                    return null;
                return s.getTransitionFromSteps();
            }, step);

            while (it.hasNext()) {
                it.next().setEventuallyToAssigned(true);
            }
        }

        wfModel.getSteps().forEach(step -> {
            if (!step.isEventuallyToEnd() && !step.isEventuallyToEmpty() && !step.isEventuallyToAssigned())
                throw new NopException(ERR_WF_STEP_NOT_ENDABLE)
                        .source(step)
                        .param(ARG_STEP_NAME, step.getName());
        });
    }

    private void checkWaitSteps(WfModel wfModel, Dag dag) {
        for (WfStepModel step : wfModel.getSteps()) {
            if (step.getWaitStepNames() != null && !step.getWaitStepNames().isEmpty()) {
                for (String waitStep : step.getWaitStepNames()) {
                    if (!wfModel.hasStep(waitStep))
                        throw new NopException(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, waitStep)
                                .source(step);
                }
            } else if (step.getJoinType() == WfJoinType.and) {
                // join步骤缺省等待所有父节点完成
                WfJoinStepModel join = (WfJoinStepModel) step;
                join.setWaitStepNames(dag.getAncestorNodeNames(step.getName()));
                LOG.debug("nop.wf.join-wait-steps:step={},wait={}", join.getName(), join.getWaitStepNames());
            }
        }
    }

    Dag buildDag(WfModel wfModel) {
        final List<WfStepModel> steps = wfModel.getSteps();
        Dag dag = new Dag(wfModel.getStart().getStartStepName());

        for (WfStepModel step : steps) {
            dag.addNode(step.getName()).setInternal(step.isInternal());
            Set<WfStepModel> toSteps = collectTransitionToSteps(dag, step, wfModel);
            step.setTransitionToSteps(new ArrayList<>(toSteps));
        }

        dag.analyze();
        return dag;
    }

    Set<WfStepModel> collectTransitionToSteps(Dag dag, WfStepModel step, WfModel wfModel) {
        Set<WfStepModel> ret = new LinkedHashSet<>();
        for (WfActionModel action : step.getActions()) {
            WfTransitionModel transition = action.getTransition();
            addTransitionNext(ret, dag, step, transition, wfModel);
        }

        addTransitionNext(ret, dag, step, step.getTransition(), wfModel);

        return ret;
    }

    void addTransitionNext(Set<WfStepModel> ret, Dag dag, WfStepModel step, WfTransitionModel transition, WfModel wfModel) {
        if (transition != null) {
            for (WfTransitionToModel to : transition.getTransitionTos()) {
                switch (to.getType()) {
                    case TO_ASSIGNED:
                        step.setNextToAssigned(true);
                        break;
                    case TO_EMPTY:
                        step.setNextToEmpty(true);
                        break;
                    case TO_END:
                        step.setNextToEnd(true);
                        break;

                    default:
                        WfStepModel nextStep = wfModel.getStep(to.getStepName());
                        if (nextStep == null)
                            throw new NopException(ERR_WF_TRANSITION_TO_UNKNOWN_STEP)
                                    .source(to)
                                    .param(ARG_STEP_NAME, to.getStepName());
                        ret.add(nextStep);

                        // DAG中忽略所有的回退连接，从而区分父子关系
                        if (!to.isBackLink())
                            dag.addNextNode(step.getName(), nextStep.getName());

                }
            }
        }
    }
}
