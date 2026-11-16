package io.nop.ib.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Per-project serialization (design 07 IB-S3): one single-threaded executor per
 * canonical repo root. Concurrent submissions to the same project line up FIFO
 * and run one at a time — concurrent writes to one worktree's target/ are the
 * footgun this exists to prevent, so the caller never needs its own locking.
 * Depth is bounded: enqueue beyond {@link #MAX_QUEUE} is an explicit rejection,
 * never a silent drop.
 */
public final class ProjectQueue {
    public static final int MAX_QUEUE = 32;

    private final Map<Path, ExecutorService> lanes = new ConcurrentHashMap<>();
    private final Map<Path, Integer> depth = new ConcurrentHashMap<>();

    /** @return false when the lane is full (caller must reject explicitly). */
    public boolean submit(BuildJob job, Runnable execution) {
        Integer d = depth.compute(job.repoRoot, (k, v) -> v == null ? 1 : v + 1);
        if (d > MAX_QUEUE) {
            depth.compute(job.repoRoot, (k, v) -> v - 1);
            return false;
        }
        ExecutorService lane = lanes.computeIfAbsent(job.repoRoot,
                root -> Executors.newSingleThreadExecutor(task -> {
                    Thread t = new Thread(task, "nop-ib-build-" + root);
                    t.setDaemon(true);
                    return t;
                }));
        lane.execute(() -> {
            try {
                if (job.state == BuildJob.State.CANCELLED)
                    return;
                execution.run();
            } finally {
                depth.compute(job.repoRoot, (k, v) -> v == null ? 0 : v - 1);
            }
        });
        return true;
    }

    /** Active + queued count for one root (queue observability). */
    public int pending(Path root) {
        Integer d = depth.get(root);
        return d == null ? 0 : d;
    }

    public int pendingTotal() {
        int sum = 0;
        for (Integer d : depth.values())
            sum += d;
        return sum;
    }
}
