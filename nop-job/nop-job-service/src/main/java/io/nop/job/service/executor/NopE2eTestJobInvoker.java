package io.nop.job.service.executor;

import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class NopE2eTestJobInvoker implements IJobInvoker {
    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        return CompletableFuture.completedFuture(JobFireResult.CONTINUE(0L));
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }
}
