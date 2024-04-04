package io.nop.task.model;

import io.nop.task.ITaskStepFlagOperation;
import io.nop.task.model._gen._TaskFlagsModel;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class TaskFlagsModel extends _TaskFlagsModel implements ITaskStepFlagOperation {
    public TaskFlagsModel() {

    }

    @Override
    public boolean checkMatchFlag(Set<String> enabledFlags) {
        Predicate<Set<String>> match = getMatch();
        return match == null || match.test(enabledFlags);
    }

    @Override
    public Set<String> buildChildFlags(Set<String> enabledFlags) {
        Set<String> enable = getEnable();
        Set<String> disable = getDisable();
        Map<String, String> rename = getRename();

        if (enable == null && disable == null && rename == null)
            return enabledFlags;

        Set<String> ret = new LinkedHashSet<>();
        if (rename != null) {
            for (String flag : enabledFlags) {
                String renamed = rename.get(flag);
                if (renamed != null) {
                    ret.add(renamed);
                } else {
                    ret.add(flag);
                }
            }
        } else {
            ret.addAll(enabledFlags);
        }

        if (enable != null) {
            ret.addAll(enable);
        }

        if (disable != null) {
            ret.removeAll(disable);
        }
        return ret;
    }
}
