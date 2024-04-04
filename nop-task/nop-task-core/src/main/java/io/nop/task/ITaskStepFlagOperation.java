package io.nop.task;

import java.util.Set;

public interface ITaskStepFlagOperation {
    boolean checkMatchFlag(Set<String> enabledFlags);

    Set<String> buildChildFlags(Set<String> enabledFlags);
}
