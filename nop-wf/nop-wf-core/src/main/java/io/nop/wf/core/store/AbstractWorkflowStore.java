package io.nop.wf.core.store;

import io.nop.core.model.tree.TreeVisitors;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.core.NopWfCoreConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AbstractWorkflowStore implements IWorkflowStore {

    protected abstract IWorkflowRecord getWfRecord(IWorkflowStepRecord stepRecord);

    protected abstract Collection<? extends IWorkflowStepRecord> getSteps(IWorkflowRecord wfRecord);

    @Override
    public IWorkflowStepRecord getLatestStepRecordByName(IWorkflowRecord wfRecord, String stepName) {
        return getSteps(wfRecord).stream().filter(step -> step.getStepName().equals(stepName))
                .sorted(Comparator.comparing(IWorkflowStepRecord::getCreateTime).reversed())
                .findFirst().orElse(null);
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getStepRecords(IWorkflowRecord wfRecord, boolean includeHistory,
                                                                    Predicate<? super IWorkflowStepRecord> filter) {
        if (includeHistory && filter == null) {
            return getSteps(wfRecord);
        }
        return getSteps(wfRecord).stream().filter(step -> {
            if (!includeHistory && step.isHistory())
                return false;
            if (filter != null)
                return filter.test(step);
            return true;
        }).collect(Collectors.toList());
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getStepRecordsByName(IWorkflowRecord wfRecord, String stepName,
                                                                          boolean includeHistory) {
        return getStepRecords(wfRecord, includeHistory, step -> step.getStepName().equals(stepName));
    }

    @Override
    public IWorkflowStepRecord getPrevStepRecordByName(IWorkflowStepRecord stepRecord, String stepName) {
        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getPrevStepRecords,
                stepRecord, false, k -> k.getStepName().equals(stepName));
        if (it.hasNext())
            return it.next();
        return null;
    }

    @Override
    public IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord, String stepName) {
        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getNextStepRecords,
                stepRecord, false, k -> k.getStepName().equals(stepName));
        if (it.hasNext())
            return it.next();
        return null;
    }

    @Override
    public IWorkflowStepRecord getNextStepRecordByName(IWorkflowStepRecord stepRecord,
                                                       String stepName, IWfActor actor) {
        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getNextStepRecords,
                stepRecord, false, k -> k.getStepName().equals(stepName)
                        && actor.isActor(k.getActorType(), k.getActorId(), k.getActorDeptId()));
        if (it.hasNext())
            return it.next();
        return null;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getJoinWaitStepRecords(
            IWorkflowStepRecord stepRecord, Function<IWorkflowStepRecord, String> joinGroupGetter,
            Set<String> stepNames) {

        List<IWorkflowStepRecord> ret = new ArrayList<>();

        IWorkflowRecord wfBean = getWfRecord(stepRecord);
        String joinGroup = stepRecord.getJoinGroup();

        for (IWorkflowStepRecord step : getSteps(wfBean)) {
            if (step == stepRecord)
                continue;

            if (step.getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND)
                continue;

            if (!stepNames.contains(step.getStepName()))
                continue;

            String stepJoinGroup = joinGroupGetter.apply(step);
            if (Objects.equals(joinGroup, stepJoinGroup)) {
                ret.add(step);
            }
        }
        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getPrevStepRecordsByName(IWorkflowStepRecord stepRecord,
                                                                              Collection<String> stepNames) {
        final Set<IWorkflowStepRecord> ret = new LinkedHashSet<>();

        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getPrevStepRecords,
                stepRecord, false, step -> {
                    if (stepNames.contains(step.getStepName())) {
                        ret.add(step);
                        // 不再继续查找这一分支
                        return false;
                    }
                    return true;
                });

        while (it.hasNext()) {
            ret.add(it.next());
        }
        return ret;
    }

    @Override
    public Collection<? extends IWorkflowStepRecord> getNextStepRecordsByName(IWorkflowStepRecord stepRecord,
                                                                              Collection<String> stepNames) {
        final Set<IWorkflowStepRecord> ret = new LinkedHashSet<>();

        Iterator<IWorkflowStepRecord> it = TreeVisitors.widthFirstIterator(this::getNextStepRecords,
                stepRecord, false, step -> {
                    if (stepNames.contains(step.getStepName())) {
                        ret.add(step);
                        // 不再继续查找这一分支
                        return false;
                    }
                    return true;
                });

        while (it.hasNext()) {
            ret.add(it.next());
        }
        return ret;
    }

    @Override
    public boolean isAllStepsHistory(IWorkflowRecord wfRecord) {
        return getSteps(wfRecord).stream().allMatch(IWorkflowStepRecord::isHistory);
    }

    @Override
    public IWorkflowStepRecord getNextJoinStepRecord(IWorkflowStepRecord stepRecord, String joinGroup,
                                                     String stepName, IWfActor actor) {
        IWorkflowRecord wfRecord = getWfRecord(stepRecord);

        for (IWorkflowStepRecord prev : getSteps(wfRecord)) {
            if (prev.getStatus() >= NopWfCoreConstants.WF_STEP_STATUS_HISTORY_BOUND)
                continue;

            if (prev.getStepName().equals(stepName)) {
                if (Objects.equals(joinGroup, prev.getJoinGroup()))
                    return prev;
            }
        }
        return null;
    }

    @Override
    public boolean isAllSignalOn(IWorkflowRecord wfRecord, Set<String> signals) {
        return wfRecord.isAllSignalOn(signals);
    }

    @Override
    public void addSignals(IWorkflowRecord wfRecord, Set<String> signals) {
        wfRecord.addSignals(signals);
    }

    @Override
    public boolean removeSignals(IWorkflowRecord wfRecord, Set<String> signals) {
        return wfRecord.removeSignals(signals);
    }

    @Override
    public Set<String> getOnSignals(IWorkflowRecord wfRecord) {
        return wfRecord.getOnSignals();
    }

    @Override
    public boolean isSignalOn(IWorkflowRecord wfRecord, String signal) {
        return wfRecord.isSignalOn(signal);
    }
}
