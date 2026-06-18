package io.nop.task.ext.reliability;

import java.util.concurrent.atomic.AtomicInteger;

public class ExecutionCounterBean {
    public static final String BEAN_NAME = "testExecutionCounter";

    private final AtomicInteger counter = new AtomicInteger(0);

    public int incrementAndGet() {
        return counter.incrementAndGet();
    }

    public int get() {
        return counter.get();
    }

    public void reset() {
        counter.set(0);
    }
}
