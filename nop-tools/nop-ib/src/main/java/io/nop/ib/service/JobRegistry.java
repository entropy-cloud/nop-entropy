package io.nop.ib.service;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory job table (design 07 IB-S5). Jobs are NOT persisted: losing the
 * registry to a crash costs the client a resubmit, and the disk state makes
 * that replay idempotent. plan 2588: cancelling works while QUEUED (no-op
 * wake) and while RUNNING (collaborative process-tree kill by the caller,
 * see {@link #markCancelled(BuildJob, int)}); a terminal job answers with an
 * explicit {@code already-terminal} instead of a silent no-op.
 */
public final class JobRegistry {
    /** plan 2588: DELETE on a terminal job (replaces the old cannot-cancel-running). */
    public static final String ALREADY_TERMINAL = "already-terminal";

    /**
     * 2026-11-22: terminal job statuses stay queryable — the newest
     * {@link #RETAINED} terminal summaries are kept in memory (no disk state:
     * the service stays stateless-by-restart, user adjudication). Compact
     * summaries only, so the retained window costs a few KB.
     */
    public static final int RETAINED = 100;

    private final java.util.Deque<Map<String, Object>> terminalHistory = new java.util.ArrayDeque<>();

    private final Map<String, BuildJob> jobs = new ConcurrentHashMap<>();
    /**
     * plan 2588 idempotent submit (design 09 §三): key is
     * {@code repoRoot + "\u0000" + clientRequestId} — bound to the root so the
     * same clientRequestId in two different projects never dedupes against each
     * other. Cleaned together with the job at prune time.
     */
    private final Map<String, BuildJob> idempotent = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public BuildJob create(String projectDir, String cmd, java.nio.file.Path repoRoot,
                           java.util.List<String> args, boolean wait) {
        return create(projectDir, cmd, repoRoot, args, wait, null);
    }

    /** plan 2588 overload with the optional idempotency key (old signature kept). */
    public BuildJob create(String projectDir, String cmd, java.nio.file.Path repoRoot,
                           java.util.List<String> args, boolean wait, String clientRequestId) {
        String id = "job-" + seq.incrementAndGet();
        BuildJob job = new BuildJob(id, projectDir, cmd, repoRoot, args, wait);
        if (clientRequestId != null) {
            job.idemKey = idemKey(repoRoot, clientRequestId);
            idempotent.put(job.idemKey, job);
        }
        jobs.put(id, job);
        return job;
    }

    private static String idemKey(java.nio.file.Path repoRoot, String clientRequestId) {
        return repoRoot + "\u0000" + clientRequestId;
    }

    /** plan 2588: the existing job for (root, clientRequestId), or null. */
    public BuildJob findIdempotent(java.nio.file.Path repoRoot, String clientRequestId) {
        return idempotent.get(idemKey(repoRoot, clientRequestId));
    }

    /**
     * 2026-11-22 multi-agent audit: a QUEUED (not yet started) job with the
     * same (root, cmd, args) absorbs identical submissions — the queued job
     * executes against the latest source when it starts, so coalescing is
     * result-equivalent to a second run and saves the duplicate build. A
     * RUNNING job must NOT coalesce: its inputs were already snapshotted, and
     * a changed source must re-run (the serial re-run then hits the test cache
     * on the fast path when nothing actually changed). Args are compared
     * order-sensitively (they are the literal CLI argument list).
     */
    public BuildJob findActive(java.nio.file.Path repoRoot, String cmd, java.util.List<String> args) {
        for (BuildJob j : jobs.values()) {
            if (j.state != BuildJob.State.QUEUED || !j.repoRoot.equals(repoRoot) || !j.cmd.equals(cmd))
                continue;
            if (j.args.equals(args))
                return j;
        }
        return null;
    }

    public BuildJob get(String id) {
        return jobs.get(id);
    }

    /**
     * QUEUED-only wake-cancel. RUNNING jobs go through
     * {@link ServiceCore#cancel(String)} (process-tree kill first); terminal
     * jobs answer {@link #ALREADY_TERMINAL}.
     * @return null on success, ALREADY_TERMINAL otherwise.
     */
    public String cancel(BuildJob job) {
        synchronized (job) {
            if (job.state != BuildJob.State.QUEUED)
                return ALREADY_TERMINAL;
            job.state = BuildJob.State.CANCELLED;
            job.exit = -1;
            job.finishedAt = System.currentTimeMillis();
            recordTerminal(job);
            // plan 2583: wake await() waiters — a cancelled QUEUED job used to
            // leave them parked until the 60s poll timeout
            job.notifyAll();
            return null;
        }
    }

    /**
     * plan 2588 running-cancel bookkeeping (the caller already killed the
     * process tree and interrupted the job thread): CANCELLED with exit=-1,
     * a fail-loud line in the job log, and a notify so await/long-poll wake.
     */
    public void markCancelled(BuildJob job, int killedProcesses) {
        synchronized (job) {
            if (!job.isTerminal()) {
                job.state = BuildJob.State.CANCELLED;
                job.exit = -1;
                job.finishedAt = System.currentTimeMillis();
                JobLog.appendLine(job.logFile, "[ctrl] cancelled at phase=running (processes killed: "
                        + killedProcesses + ")");
                job.notifyAll();
            }
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

    /**
     * Forget terminal jobs to bound memory (called from the submit path AND the
     * periodic reaper — plan 2588: cleanup never waits for the next submit).
     * Pruning also deletes the job's log file and its idempotency entry.
     */
    /** Compact terminal summary (also what /v1/jobs returns for history entries). */
    static Map<String, Object> terminalSummary(BuildJob job) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", job.id);
        m.put("cmd", job.cmd);
        m.put("args", job.args);
        m.put("state", job.state.name().toLowerCase(java.util.Locale.ROOT));
        m.put("exit", job.exit);
        m.put("queuedAt", job.queuedAt);
        m.put("startedAt", job.startedAt);
        m.put("finishedAt", job.finishedAt);
        // never-started (cancelled while queued): startedAt==0 — duration is the
        // queue wait, not finishedAt-0 (live audit: durationMs came out as the
        // raw finishedAt epoch)
        m.put("durationMs", job.startedAt > 0 ? job.finishedAt - job.startedAt : 0);
        m.put("queueWaitMs", job.finishedAt - job.queuedAt);
        if (job.logFile != null)
            m.put("logPath", job.logFile.toString());
        if (job.error != null)
            m.put("error", job.error);
        return m;
    }

    /** Record one terminal job into the bounded in-memory history (newest last). */
    public synchronized void recordTerminal(BuildJob job) {
        terminalHistory.addLast(terminalSummary(job));
        while (terminalHistory.size() > RETAINED)
            terminalHistory.pollFirst();
    }

    /** Retained terminal summaries, newest first (bounded by {@link #RETAINED}). */
    public synchronized List<Map<String, Object>> terminalHistory() {
        List<Map<String, Object>> out = new ArrayList<>(terminalHistory);
        java.util.Collections.reverse(out);
        return out;
    }

    public void prune() {
        // 2026-11-22: the newest RETAINED terminal jobs stay in memory forever
        // ("recent 100 statuses always queryable"); older ones fall out (log
        // file deleted, idempotency entry dropped). No disk state (user
        // adjudication): memory-only retention.
        List<BuildJob> terminal = new ArrayList<>();
        for (BuildJob j : jobs.values())
            if (j.isTerminal())
                terminal.add(j);
        terminal.sort((a, b) -> Long.compare(b.finishedAt, a.finishedAt));
        java.util.Set<String> keepIds = new java.util.HashSet<>();
        for (int i = 0; i < Math.min(RETAINED, terminal.size()); i++)
            keepIds.add(terminal.get(i).id);
        for (BuildJob j : terminal) {
            if (keepIds.contains(j.id))
                continue;
            jobs.remove(j.id);
            if (j.idemKey != null)
                idempotent.remove(j.idemKey, j);
            if (j.logFile != null) {
                try {
                    Files.deleteIfExists(j.logFile);
                } catch (Exception ignored) {
                    // best-effort: a stuck log file must not break pruning
                }
            }
        }
    }
}
