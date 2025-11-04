package io.nop.commons.concurrent.executor;

import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

public class LimitedExecutor implements Executor {
    private final Executor executor;
    private final Semaphore semaphore;

    public LimitedExecutor(Executor executor, int n) {
        this.executor = executor;
        this.semaphore = new Semaphore(n);
    }

    public static LimitedExecutor limit(Executor executor, int n) {
        return new LimitedExecutor(executor, n);
    }

    @Override
    public void execute(Runnable command) {
        boolean submitted = false;
        try {
            semaphore.acquire();
            executor.execute(() -> {
                try {
                    command.run();
                } finally {
                    semaphore.release();
                }
            });
            submitted = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        } finally {
            if (!submitted) {
                semaphore.release();
            }
        }
    }
}
