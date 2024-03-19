package io.nop.task;

public interface ITaskStepLib {
    String getLibName();

    long getLibVersion();

    ITaskStep getStep(String stepName);
}
