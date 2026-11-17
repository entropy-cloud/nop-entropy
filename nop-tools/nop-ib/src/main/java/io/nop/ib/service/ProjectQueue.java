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
    /** cmd of the job currently executing on the lane (wave 11 observability). */
    private final Map<Path, String> runningCmd = new ConcurrentHashMap<>();

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
                runningCmd.put(job.repoRoot, job.cmd);
                execution.run();
            } finally {
                runningCmd.remove(job.repoRoot, job.cmd);
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

    /** Queued-only depth for one root: pending minus the job currently running (0 when idle). */
    public int queuedDepth(Path root) {
        int p = pending(root);
        return runningCmd.containsKey(root) ? Math.max(0, p - 1) : p;
    }

    /** @return cmd of the job executing on this lane, null when idle. */
    public String runningCmd(Path root) {
        return runningCmd.get(root);
    }

    /** All lane roots ever submitted (lanes are never removed). */
    public java.util.Set<Path> laneRoots() {
        java.util.Set<Path> out = new java.util.LinkedHashSet<>(lanes.keySet());
        out.addAll(depth.keySet());
        return out;
    }

    public int pendingTotal() {
        int sum = 0;
        for (Integer d : depth.values())
            sum += d;
        return sum;
    }
}
