package io.nop.ib.service;

import io.nop.ib.json.JsonIn;
import io.nop.ib.json.JsonOut;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Durable per-project job history (2026-11-22: "recent 100 job statuses are
 * always queryable"). Every terminal job appends one JSON line to
 * {@code <root>/.nop-ib/history.jsonl}; {@link #readLast(Path, int)} answers
 * from the file tail, so history survives service restarts (the in-memory
 * registry deliberately does not — it stays crash-recoverable by resubmit).
 *
 * <p>Bound: callers keep the file effectively bounded by reading tails only;
 * the append is a single-line write, never a rewrite (crash-safe tail semantics:
 * a torn last line is skipped by the reader).
 */
public final class JobHistory {
    public static final int RETAINED = 100;

    /** service-boot discriminator: job ids recycle per boot, so history lines
     * from different boots would be ambiguous without it (2026-11-22 audit). */
    public static final long BOOT_ID = System.currentTimeMillis();

    private JobHistory() {
    }

    static Path historyFile(Path repoRoot) {
        return repoRoot.resolve(".nop-ib").resolve("history.jsonl");
    }

    /** Append one terminal-job summary line (best-effort: history must never break a build). */
    public static void append(Path repoRoot, Map<String, Object> summary) {
        try {
            Path file = historyFile(repoRoot);
            Files.createDirectories(file.getParent());
            Files.writeString(file, JsonOut.write(summary) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // history is advisory; a full disk must not fail the build result
        }
    }

    /** Terminal summaries for one root, newest first, at most {@code limit} entries. */
    public static List<Map<String, Object>> readLast(Path repoRoot, int limit) {
        Path file = historyFile(repoRoot);
        if (!Files.isRegularFile(file))
            return List.of();
        Deque<Map<String, Object>> tail = new ArrayDeque<>(limit);
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                Object parsed;
                try {
                    parsed = JsonIn.parse(line);
                } catch (Exception e) {
                    continue; // torn/corrupt line: skip
                }
                if (!(parsed instanceof Map))
                    continue;
                if (tail.size() >= limit)
                    tail.pollFirst();
                tail.addLast(JsonOut.asMap(parsed));
            }
        } catch (IOException e) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(tail);
        java.util.Collections.reverse(out);
        return out;
    }

    /** Compact terminal summary persisted per job (also what /v1/jobs returns). */
    public static Map<String, Object> terminalSummary(BuildJob job) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", job.id);
        m.put("bootId", BOOT_ID);
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
}
