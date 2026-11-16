package io.nop.ib.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory job table (design 07 IB-S5). Jobs are NOT persisted: losing the
 * registry to a crash costs the client a resubmit, and the disk state makes
 * that replay idempotent. Cancelling is only meaningful while queued — a
 * running build cannot be safely half-killed (target/ writes), so DELETE on a
 * running job returns an explicit {@code cannot-cancel-running} instead of a
 * silent no-op.
 */
public final class JobRegistry {
    public static final String CANNOT_CANCEL_RUNNING = "cannot-cancel-running";

    private final Map<String, BuildJob> jobs = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public BuildJob create(String projectDir, String cmd, java.nio.file.Path repoRoot,
                           java.util.List<String> args, boolean wait) {
        String id = "job-" + seq.incrementAndGet();
        BuildJob job = new BuildJob(id, projectDir, cmd, repoRoot, args, wait);
        jobs.put(id, job);
        return job;
    }

    public BuildJob get(String id) {
        return jobs.get(id);
    }

    /** @return null on success, CANNOT_CANCEL_RUNNING if already running/done. */
    public String cancel(BuildJob job) {
        synchronized (job) {
            if (job.state != BuildJob.State.QUEUED)
                return CANNOT_CANCEL_RUNNING;
            job.state = BuildJob.State.CANCELLED;
            job.finishedAt = System.currentTimeMillis();
            return null;
        }
    }

    /** Jobs for one root (queue observability), oldest first. */
    public List<BuildJob> ofRoot(java.nio.file.Path root) {
        List<BuildJob> out = new ArrayList<>();
        for (BuildJob j : jobs.values())
            if (j.repoRoot.equals(root))
                out.add(j);
        out.sort((a, b) -> Long.compare(a.queuedAt, b.queuedAt));
        return out;
    }

    public Collection<BuildJob> all() {
        return jobs.values();
    }

    /** Forget terminal jobs to bound memory (called after snapshots). */
    public void prune() {
        long now = System.currentTimeMillis();
        for (BuildJob j : jobs.values()) {
            BuildJob.State s = j.state;
            boolean terminal = s == BuildJob.State.DONE || s == BuildJob.State.FAILED
                    || s == BuildJob.State.CANCELLED;
            if (terminal && now - j.finishedAt > 10 * 60_000L)
                jobs.remove(j.id);
        }
    }
}
