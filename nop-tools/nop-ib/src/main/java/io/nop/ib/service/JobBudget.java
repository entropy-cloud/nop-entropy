package io.nop.ib.service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two-layer machine budget (design 07 IB-S4): a semaphore caps concurrent jobs
 * at {@code max(2, cores/2)}, and the per-job test-worker budget is compressed
 * to {@code max(2, permits / activeJobs)} via the TestCommand override seam —
 * job-count capping alone would still let N single-job periods each run a
 * full worker budget and oversubscribe the machine. Single project (active=1)
 * keeps its full budget.
 */
public final class JobBudget {
    public final int permits;
    private final Semaphore sem;
    private final AtomicInteger active = new AtomicInteger();

    public JobBudget(int permits) {
        this.permits = permits;
        this.sem = new Semaphore(permits);
    }

    public static JobBudget fromSystem(int cores) {
        int byProp = Integer.getInteger("nop.ib.serviceWorkerBudget", 0);
        int p = byProp > 0 ? byProp : Math.max(2, cores / 2);
        return new JobBudget(p);
    }

    /** Blocks until a permit is free; returns the per-job worker budget. */
    public int acquire() throws InterruptedException {
        sem.acquire();
        int n = active.incrementAndGet();
        return Math.max(2, permits / n);
    }

    public void release() {
        active.decrementAndGet();
        sem.release();
    }

    public int activeJobs() {
        return active.get();
    }
}
