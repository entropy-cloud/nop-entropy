package io.nop.batch.core;

public interface IBatchTaskBuilder {
    IBatchTask buildTask(IBatchTaskContext context);
}
