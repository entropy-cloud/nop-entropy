package io.nop.ib.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One queued/running/finished build request (design 07 §2.2). Registry state is
 * in-memory only — the build facts themselves stay on disk in the project's
 * .nop-ib directory, so a restarted service re-derives everything and a client
 * that lost its job id simply resubmits (idempotent).
 */
public final class BuildJob {

    public enum State { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    public final String id;
    public final String projectDir;
    public final String cmd;
    public final java.nio.file.Path repoRoot;
    public final java.util.List<String> args;
    public final boolean wait;

    public volatile State state = State.QUEUED;
    public volatile int exit = -1;
    public volatile String payload;      // CLI --json stdout (may be truncated)
    public volatile String stderr;       // diagnostics stream, kept out of the payload
    public volatile boolean truncated;
    public volatile String error;
    public volatile long queuedAt = System.currentTimeMillis();
    public volatile long startedAt;
    public volatile long finishedAt;
    /** plan 2588: durable per-job log ({repoRoot}/.nop-ib/jobs/{id}.log). */
    public volatile java.nio.file.Path logFile;
    /** plan 2588: the lane thread running this job (running-cancel interrupt). */
    volatile Thread thread;
    /** plan 2588: idempotency key "repoRoot\u0000clientRequestId" (null when absent). */
    String idemKey;
    /** plan 2588: submit-response marker — true when an existing job was returned. */
    public volatile boolean replayed;

    BuildJob(String id, String projectDir, String cmd, java.nio.file.Path repoRoot,
             java.util.List<String> args, boolean wait) {
        this.id = id;
        this.projectDir = projectDir;
        this.cmd = cmd;
        this.repoRoot = repoRoot;
        this.args = args;
        this.wait = wait;
    }

    /** JSON snapshot for GET /v1/job/{id} — same field names as design 07 §2.1. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
        m.put("cmd", cmd);
        m.put("projectDir", projectDir);
        if (logFile != null)
            m.put("logPath", logFile.toString());
        Map<String, Object> progress = ProgressRegistry.get(repoRoot);
        if (progress != null && !isTerminal())
            m.put("progress", progress);
        if (error != null)
            m.put("error", error);
        if (state == State.DONE || state == State.FAILED) {
            m.put("exit", exit);
            if (payload != null)
                m.put("payload", payload);
            if (truncated)
                m.put("truncated", true);
            m.put("queuedAt", queuedAt);
            m.put("startedAt", startedAt);
            m.put("finishedAt", finishedAt);
        }
        return m;
    }

    /** plan 2588: DONE / FAILED / CANCELLED jobs carry their full result. */
    public boolean isTerminal() {
        State s = state;
        return s == State.DONE || s == State.FAILED || s == State.CANCELLED;
    }

    /**
     * plan 2588: intermediate state for the long-poll path (design 09 §2) —
     * explicit queued/running phase, position in the lane, waited time; never
     * an error and never a reused {@code failed} state. progress facts ride
     * along when the root has published any.
     */
    public Map<String, Object> pendingSnapshot(int queueAhead, long waitedMs) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean running = state == State.RUNNING;
        m.put("id", id);
        m.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
        m.put("phase", running ? "running" : "queued");
        m.put("queueAhead", queueAhead);
        m.put("elapsedMs", System.currentTimeMillis() - queuedAt);
        Map<String, Object> progress = ProgressRegistry.get(repoRoot);
        if (progress != null)
            m.put("progress", progress);
        if (logFile != null)
            m.put("logPath", logFile.toString());
        m.put("waitedMs", waitedMs);
        return m;
    }

    /**
     * Compact summary for GET /v1/jobs (wave 11 observability): facts only, no
     * payload. projectDir is the canonical repo root (the queue lane identity).
     */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("cmd", cmd);
        m.put("projectDir", repoRoot.toString());
        m.put("state", state.name().toLowerCase(java.util.Locale.ROOT));
        m.put("exit", exit);
        m.put("queuedAt", queuedAt);
        m.put("startedAt", startedAt > 0 ? startedAt : null);
        m.put("finishedAt", finishedAt > 0 ? finishedAt : null);
        long now = System.currentTimeMillis();
        if (finishedAt > 0)
            m.put("durationMs", finishedAt - startedAt);
        else if (startedAt > 0)
            m.put("durationMs", now - startedAt);
        else
            m.put("durationMs", null);
        return m;
    }
}
