package io.nop.duckdb;

import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only wrapper: throws the permanent file-locked error on the first attempt, then runs
 * the real step. With bizFatal adjudication the retry decorator must NOT retry it.
 */
public class TestFileLockedStep extends DuckDbSqlTaskStep {
    public static final AtomicInteger ATTEMPTS = new AtomicInteger();

    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        if (ATTEMPTS.incrementAndGet() == 1) {
            throw (io.nop.api.core.exceptions.NopException)
                    new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_FILE_LOCKED)
                            .param(NopDuckDbErrors.ARG_FILE_PATH, "/fake/locked.duckdb")
                            .param(NopDuckDbErrors.ARG_REASON, "simulated lock holder")
                            .bizFatal(true);
        }
        return super.execute(stepRt);
    }
}
