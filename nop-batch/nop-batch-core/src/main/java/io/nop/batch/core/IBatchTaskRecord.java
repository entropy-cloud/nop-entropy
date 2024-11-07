package io.nop.batch.core;

public interface IBatchTaskRecord {
    String getSid();

    String getTaskName();

    String getTaskKey();

    Integer getTaskStatus();
}
