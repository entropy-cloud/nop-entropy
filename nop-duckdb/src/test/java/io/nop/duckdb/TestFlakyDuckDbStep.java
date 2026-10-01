package io.nop.duckdb;

import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only wrapper: fails the first attempt with a retryable NopDuckDbException, then
 * delegates to the real step. Registered as bean testFlakyDuckDbStep in test resources.
 */
public class TestFlakyDuckDbStep extends DuckDbSqlTaskStep {
    public static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        if (ATTEMPTS.incrementAndGet() == 1) {
            throw new NopDuckDbException("simulated transient failure for retry test");
        }
        return super.execute(stepRt);
    }
}
