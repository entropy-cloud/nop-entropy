package io.nop.batch.dsl.builder;

import io.nop.batch.core.IBatchTask;

public interface IBatchTaskFactory {
    IBatchTask newTask();
}
