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
