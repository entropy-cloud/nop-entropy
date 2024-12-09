package io.nop.core.execution;

import io.nop.api.core.util.ICancelToken;

import java.sql.Timestamp;
import java.util.concurrent.CompletableFuture;

public interface ITaskExecutionState extends ICancelToken {
    String getTaskName();

    String getDescription();

    boolean isStarted();

    Timestamp getQueueTime();

    Timestamp getStartTime();

    void cancel(String reason);

    String getProgressMessage();

    long getCurrentProgress();

    long getProgressTotal();

    CompletableFuture<?> getPromise();
}