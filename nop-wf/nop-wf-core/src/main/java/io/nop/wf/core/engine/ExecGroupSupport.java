package io.nop.wf.core.engine;

import io.nop.wf.core.IWorkflowStep;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.impl.IWorkflowStepImplementor;
import io.nop.wf.core.model.WfExecGroupType;

import java.util.List;

public class ExecGroupSupport {
    public static boolean isSameExecGroup(IWorkflowStepImplementor stepA, IWorkflowStepImplementor stepB) {
        if (stepA.getRecord().getExecGroup() != null)
            return stepA.getRecord().getExecGroup().equals(stepB.getRecord().getExecGroup());
        return false;
    }

    /**
     * 当步骤结束时判断是否整个execGroup也应该结束
     */
    public static boolean shouldExecGroupComplete(IWorkflowStepImplementor step) {
        WfExecGroupType execGroupType = step.getExecGroupType();
        if (execGroupType == WfExecGroupType.OR_GROUP)
            return true;

        if (execGroupType == WfExecGroupType.VOTE_GROUP) {
            return isVoteGroupComplete(step);
        }

        // 其他执行分组类型需要所有组内步骤都结束
        return step.getStepsInSameExecGroup(false, false).isEmpty();
    }

    private static boolean isVoteGroupComplete(IWorkflowStepImplementor step) {
        List<? extends IWorkflowStep> steps = step.getStepsInSameExecGroup(true, true);
        int totalWeight = 0;
        int completeWeight = 0;

        for (IWorkflowStep member : steps) {
            if (step.isExcludeInExecGroup())
                continue;

            Integer weight = member.getRecord().getVoteWeight();
            if (weight == null)
                weight = 1;

            totalWeight += weight;

            // 仅考虑成功完成的步骤
            if (member.isCompleted())
                completeWeight += weight;
        }

        Integer passWeight = step.getModel().getPassWeight();
        if (passWeight != null) {
            if (completeWeight >= passWeight)
                return true;
        }

        Double passPercent = step.getModel().getPassPercent();
        // 缺省要求半数以上通过
        if (passPercent == null && passWeight == null)
            passPercent = 0.5;

        if (passPercent != null) {
            return completeWeight * 1.0 / totalWeight >= passWeight;
        }

        return false;
    }

    /**
     * 当本步骤拒绝的时候判断是否整个execGroup也应该拒绝
     */
    public static boolean shouldExecGroupReject(IWorkflowStepImplementor step) {
        WfExecGroupType execGroupType = step.getExecGroupType();
        if (execGroupType == WfExecGroupType.VOTE_GROUP) {
            return isVoteGroupReject(step);
        }

        return true;
    }

    private static boolean isVoteGroupReject(IWorkflowStepImplementor step) {
        List<? extends IWorkflowStep> steps = step.getStepsInSameExecGroup(true, true);
        int totalWeight = 0;
        int rejectWeight = 0;

        for (IWorkflowStep member : steps) {
            if (step.isExcludeInExecGroup())
                continue;

            Integer weight = member.getRecord().getVoteWeight();
            if (weight == null)
                weight = 1;

            totalWeight += weight;

            if (member.isCompleted() || member.isActivated() || member.isWaiting())
                continue;

            // 已经完成，且没有成功的步骤
            rejectWeight += weight;
        }

        Integer passWeight = step.getModel().getPassWeight();
        if (passWeight != null) {
            // 剩下的步骤已经不足以通过
            if (totalWeight - rejectWeight < passWeight)
                return true;
        }

        Double passPercent = step.getModel().getPassPercent();
        // 缺省要求半数以上通过
        if (passPercent == null && passWeight == null)
            passPercent = 0.5;

        if (passPercent != null) {
            return (1.0 - rejectWeight * 1.0 / totalWeight) < passWeight;
        }

        return false;
    }

    public static void skipExecGroupMembers(WorkflowEngineImpl engine, IWorkflowStepImplementor step, WfRuntime wfRt) {
        List<? extends IWorkflowStep> members = step.getStepsInSameExecGroup(false, false);
        for (IWorkflowStep member : members) {
            engine.doExitStep((IWorkflowStepImplementor) member, NopWfCoreConstants.WF_STEP_STATUS_SKIPPED, wfRt);
        }
    }
}
