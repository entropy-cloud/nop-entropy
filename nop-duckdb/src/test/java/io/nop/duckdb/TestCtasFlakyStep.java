package io.nop.duckdb;

import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only wrapper for the 5.6-W-03 replay regression (plan 2529 组 A / 裁定②.5,
 * TestFlakyDuckDbStep precedent): the first attempt runs the real step to completion
 * (the CTAS for the result table commits and the step's connection closes), then injects
 * a non-bizFatal recoverable failure - the {@code <retry>} decorator replays the same
 * unfinished step against the persistent dbPath and must hit the table-exists guard.
 */
public class TestCtasFlakyStep extends DuckDbSqlTaskStep {
    public static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        if (ATTEMPTS.incrementAndGet() == 1) {
            TaskStepReturn ret = super.execute(stepRt);
            throw new NopDuckDbException(
                    "simulated transient failure after the first successful CTAS execution");
        }
        return super.execute(stepRt);
    }
}
