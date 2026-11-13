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
        // C3-007：release严格限定在acquire成功后的作用域——acquire被中断（未获许可）不补偿；
        // 提交被拒（executor.execute抛出，许可已获得）补偿恰一次；提交成功后释放责任移交任务体
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            executor.execute(() -> {
                try {
                    command.run();
                } finally {
                    semaphore.release();
                }
            });
            acquired = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }
}
