/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core.model.analyze;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.model.graph.DefaultDirectedGraph;
import io.nop.core.model.graph.DefaultEdge;
import io.nop.wf.core.model.IWorkflowStartModel;
import io.nop.wf.core.model.WfActionModel;
import io.nop.wf.core.model.WfModel;
import io.nop.wf.core.model.WfStepModel;
import io.nop.wf.core.model.WfTransitionModel;
import io.nop.wf.core.model.WfTransitionToModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.nop.wf.core.NopWfCoreErrors.ARG_STEP_NAME;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_TRANSITION_TO_UNKNOWN_STEP;
import static io.nop.wf.core.NopWfCoreErrors.ERR_WF_UNKNOWN_STEP;

public class WfModelAnalyzer {
    static final Logger LOG = LoggerFactory.getLogger(WfModelAnalyzer.class);
    private WfModel wfModel;

    public void analyze(final WfModel model) {
        this.wfModel = model;
        IWorkflowStartModel start = model.getStart();
        String startStepName = start.getStartStepName();

        WfStepModel startStep = model.getStep(startStepName);

        if (startStep == null)
            throw new NopException(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, startStepName);

        DefaultDirectedGraph<WfStepModel, DefaultEdge<WfStepModel>> graph = buildGraph(wfModel);
        Iterator<WfStepModel> it = graph.breadthFirstIterator(startStep);
        int stepIndex = 0;
        while (it.hasNext()) {
            it.next().setStepIndex(stepIndex++);
        }



//        int stepCount = steps.size();
//        List<WfStepModel> allSteps = new ArrayList<>(stepCount);
//        allSteps.addAll(steps.elements());
//        //  allSteps.add(ASSIGNED_STEP);
//        //  allSteps.add(EMPTY_STEP);
//        //  allSteps.add(END_STEP);
//
//        DirectedGraph graph = GraphAlgs.buildDirectedGraph(allSteps, new IDirectedGraphAdapter<WfStepModel>() {
//
//            @Override
//            public Collection<WfStepModel> getNextNodes(WfStepModel o) {
//                return getNext(o, steps);
//            }
//        }, null);
//
//
//        List<WfStepModel> loop = new ArrayList<>();
//        DAG<WfStepModel> dag = GraphAlgs.buildDAG(graph, allSteps, loop);
//        if (!loop.isEmpty() && !model.isAllowStepLoop())
//            throw new EntropyException("wf.err_flow_graph_contains_loop").param("loop", loop);
//
//        for (DAGNode<WfStepModel> stepNode : dag.getAllNodes()) {
//            WfStepModel step = stepNode.getValue();
//
//            //if (step == ASSIGNED_STEP || step == EMPTY_STEP || step == END_STEP)
//            //    continue;
//
//            step.setTopoOrder(stepNode.getTopoOrder());
//
//
//            fixStepModel(stepNode);
//
//        }
//
//        for (WfStepModel step : steps) {
//            if (step == startStep) {
//                if (!step.isFinallyToEnd())
//                    throw new EntropyException("wf.err_startStep_unable_to_reach_end").param("startStep", startStep)
//                            .loc(step.getSourceLocation()).param("descendantStepIds", startStep.getDescendantStepIds());
//            } else {
//                // 普通步骤应该从startStep可达，并且最终要走到endStep
//                if (!step.hasAncestorStep(startStep.getId()))
//                    throw new EntropyException("wf.err_step_not_reachable_from_startStep").param("step", step);
//
//                if (!step.isFinallyToEnd() && !step.isIndependent())
//                    throw new EntropyException("wf.err_step_unable_to_reach_end").param("step", step).param(
//                            "descendantStepIds", step.getDescendantStepIds());
//            }
//
//            Set<String> waitSteps = step.getWaitStepNames();
//            if (waitSteps != null && !waitSteps.isEmpty()) {
//                for (String waitStepId : waitSteps) {
//                    WfStepModel waitStep = steps.get(waitStepId);
//                    if (waitStep == null)
//                        throw new EntropyException("wf.err_unknown_waitStep").param("waitStep", waitStep).param("step",
//                                step);
//
//                    if (step.hasDescendantStep(waitStepId)) {
//                        LOG.warn("wf.warn_waitStep_is_descendant_of_join_step:waitStep={},step={}", waitStep, step);
//                        // throw new EntropyException("wf.err_waitStep_is_descendant").param("waitStep", waitStep)
//                        //         .param("step", step);
//                    }
//
//                    if (!step.hasAncestorStep(waitStepId)) {
//                        LOG.warn("wf.warn_waitStep_is_not_ancestor_of_join_step:waitStep={},step={}", waitStep, step);
//                        //throw new EntropyException("wf.err_waitStep_not_ancestor").param("waitStep", waitStep)
//                        //        .param("step", step);
//                    }
//                }
//            } else if (step.getJoinType() == WfJoinType.and) {
//                // join步骤缺省等待所有父节点完成
//                step.setWaitSteps(step.getAncestorStepIds());
//            }
//
//            if (waitSteps != null && !waitSteps.isEmpty())
//                LOG.debug("wf.use_wait_steps:{},step={}", waitSteps, step);
//        }
    }
//
//    void fixStepModel(DAGNode<WfStepModel> stepNode) {
//        WfStepModel step = stepNode.getValue();
//        step.setAllNextStepIds(getStepNames(stepNode.getAllNextNodeValues()));
//        step.setAllPrevStepIds(getStepNames(stepNode.getAllPrevNodeValues()));
//
//        if (step.isNextToEnd()) {
//            Set<WfStepModel> steps = stepNode.getAllPrevNodeValues();
//            for (WfStepModel prevStep : steps) {
//                prevStep.setFinallyToEnd(true);
//            }
//        }
//
//        if (stepNode.getControlParentNode() != null)
//            step.setControlStep(stepNode.getControlParentNode().getValue());
//
//        List<WfStepModel> prevSteps = stepNode.getPrevNodeValues();
//        step.setPrevSteps(prevSteps);
//
//        List<WfStepModel> nextSteps = stepNode.getNextNodeValues();
//        //nextSteps.remove(EMPTY_STEP);
//        //nextSteps.remove(ASSIGNED_STEP);
//        //nextSteps.remove(END_STEP);
//        step.setNextSteps(nextSteps);
//
//        step.setNextNormalSteps(getNextNormalSteps(nextSteps));
//        step.setPrevNormalSteps(getPrevNormalSteps(prevSteps));
//    }

    DefaultDirectedGraph<WfStepModel, DefaultEdge<WfStepModel>> buildGraph(WfModel wfModel) {
        final List<WfStepModel> steps = wfModel.getSteps();
        DefaultDirectedGraph<WfStepModel, DefaultEdge<WfStepModel>> graph = DefaultDirectedGraph.create();
        for (WfStepModel step : steps) {
            step.setStepIndex(-1);
            graph.addVertex(step);
            Set<WfStepModel> toSteps = getTransitionToSteps(step, wfModel);
            for (WfStepModel toStep : toSteps) {
                graph.addEdge(step, toStep);
            }
        }
        return graph;
    }

    /**
     * normal step会跳过标记为internal的步骤
     */
    List<WfStepModel> getPrevNormalSteps(List<WfStepModel> prevSteps) {
        if (!hasInternalStep(prevSteps))
            return prevSteps;

        Set<WfStepModel> normalSteps = new HashSet<>(prevSteps.size() + 5);
        collectNormalPrev(prevSteps, normalSteps);

        List<WfStepModel> steps = new ArrayList<>(normalSteps);
        Collections.sort(steps);
        return steps;
    }

    List<WfStepModel> getNextNormalSteps(List<WfStepModel> nextSteps) {
        if (!hasInternalStep(nextSteps))
            return nextSteps;

        Set<WfStepModel> normalSteps = new HashSet<>(nextSteps.size() + 5);
        collectNormalNext(nextSteps, normalSteps);

        List<WfStepModel> steps = new ArrayList<>(normalSteps);
        Collections.sort(steps);
        return steps;
    }

    boolean hasInternalStep(List<WfStepModel> steps) {
        for (WfStepModel step : steps) {
            if (step.isInternal())
                return true;
        }
        return false;
    }

    void collectNormalPrev(Collection<WfStepModel> steps, Collection<WfStepModel> normalSteps) {
        for (WfStepModel step : steps) {
            if (step.isInternal()) {
                collectNormalPrev(step.getPrevSteps(), normalSteps);
            } else {
                normalSteps.add(step);
            }
        }
    }

    void collectNormalNext(Collection<WfStepModel> steps, Collection<WfStepModel> normalSteps) {
        for (WfStepModel step : steps) {
            if (step.isInternal()) {
                collectNormalNext(step.getNextSteps(), normalSteps);
            } else {
                normalSteps.add(step);
            }
        }
    }

    Set<WfStepModel> getTransitionToSteps(WfStepModel step, WfModel wfModel) {
        Set<WfStepModel> ret = new LinkedHashSet<>();
        for (WfActionModel action : step.getActions()) {
            WfTransitionModel transition = action.getTransition();
            addTransitionNext(ret, step, transition, wfModel);
        }

        addTransitionNext(ret, step, step.getTransition(), wfModel);

        return ret;
    }

    Set<WfStepModel> getNext(WfStepModel step, WfModel wfModel) {
        for (String waitStep : step.getWaitStepNames()) {
            if (!wfModel.hasStep(waitStep))
                throw new NopException(ERR_WF_UNKNOWN_STEP).param(ARG_STEP_NAME, waitStep)
                        .source(step);
        }

        Set<WfStepModel> ret = new LinkedHashSet<>();
        for (WfActionModel action : step.getActions()) {
            WfTransitionModel transition = action.getTransition();
            addTransitionNext(ret, step, transition, wfModel);
        }

        addTransitionNext(ret, step, step.getTransition(), wfModel);

        return ret;
    }

    void addTransitionNext(Set<WfStepModel> ret, WfStepModel step, WfTransitionModel transition, WfModel wfModel) {
        if (transition != null) {
            for (WfTransitionToModel to : transition.getTransitionTos()) {
                switch (to.getType()) {
                    case TO_ASSIGNED:
                        step.setNextToAssigned(true);
                        break;
                    case TO_EMPTY:
                        step.setNextToEmpty(true);
                        step.setFinallyToEmpty(true);
                        break;
                    case TO_END:
                        step.setNextToEnd(true);
                        step.setFinallyToEnd(true);
                        break;

                    default:
                        WfStepModel nextStep = wfModel.getStep(to.getStepName());
                        if (nextStep == null)
                            throw new NopException(ERR_WF_TRANSITION_TO_UNKNOWN_STEP)
                                    .source(to)
                                    .param(ARG_STEP_NAME, to.getStepName());
                        ret.add(nextStep);
                }
            }
        }
    }
}
