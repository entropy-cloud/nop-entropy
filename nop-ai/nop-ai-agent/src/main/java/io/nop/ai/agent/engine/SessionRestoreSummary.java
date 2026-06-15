package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable summary returned by
 * {@link IAgentEngine#restorePendingSessions(String, String)} — the
 * auto-restore-on-startup batch orchestrator (plan 184, design §1.1 recovery
 * model). Records the outcome of every session the orchestrator considered
 * so an operator (or an unattended deployment log) can tell exactly what was
 * restored, what was deliberately skipped, and what failed.
 *
 * <p>Every session discovered by the session store's
 * {@code listAllSessions()} falls into exactly one of three buckets:
 * <ul>
 *   <li><b>{@link #getRestored()}</b> — the session was a restore candidate
 *       (status {@code running} or {@code pending}) and {@code restoreSession}
 *       completed without throwing. Each entry carries the sessionId and the
 *       post-restore {@link AgentExecStatus} (typically {@code completed} if
 *       the ReAct loop finished, or another terminal status).</li>
 *   <li><b>{@link #getSkipped()}</b> — the session was deliberately not
 *       restored. {@code paused} sessions are skipped because sticky-pause is
 *       a governance state that requires an explicit human
 *       {@code resumeSession} (plan 180); terminal sessions (completed /
 *       failed / cancelled / forced_stopped / escalated) are skipped because
 *       they already reached a final outcome. Each entry carries the
 *       sessionId, the pre-restore status, and a human-readable skip reason.</li>
 *   <li><b>{@link #getFailed()}</b> — the session was a restore candidate but
 *       {@code restoreSession} threw (e.g. agent model not found, checkpoint
 *       inconsistency that surfaced as an exception). Each entry carries the
 *       sessionId and the failure message. A failure on one session never
 *       aborts the batch — per-session failure isolation is part of the
 *       contract.</li>
 * </ul>
 *
 * <p>An empty summary (all three lists empty) is a legitimate state: it
 * means the session store had no persisted sessions at all (fresh root) or
 * the store is in-memory and the cache is empty after a restart.
 */
public final class SessionRestoreSummary {

    private final List<Entry> restored;
    private final List<SkipEntry> skipped;
    private final List<Entry> failed;

    public SessionRestoreSummary(List<Entry> restored, List<SkipEntry> skipped, List<Entry> failed) {
        this.restored = frozen(restored);
        this.skipped = frozen(skipped);
        this.failed = frozen(failed);
    }

    public List<Entry> getRestored() {
        return restored;
    }

    public List<SkipEntry> getSkipped() {
        return skipped;
    }

    public List<Entry> getFailed() {
        return failed;
    }

    public int getRestoredCount() {
        return restored.size();
    }

    public int getSkippedCount() {
        return skipped.size();
    }

    public int getFailedCount() {
        return failed.size();
    }

    @Override
    public String toString() {
        return "SessionRestoreSummary{restored=" + restored.size()
                + ", skipped=" + skipped.size()
                + ", failed=" + failed.size() + '}';
    }

    private static <T> List<T> frozen(List<T> source) {
        return source == null || source.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }

    /**
     * A restored or failed session entry. For a restored session,
     * {@code detail} holds the resulting {@link AgentExecStatus} name; for a
     * failed session, {@code detail} holds the failure message.
     */
    public static final class Entry {
        private final String sessionId;
        private final String detail;

        public Entry(String sessionId, String detail) {
            this.sessionId = sessionId;
            this.detail = detail;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getDetail() {
            return detail;
        }

        @Override
        public String toString() {
            return sessionId + "=" + detail;
        }
    }

    /**
     * A skipped session entry: records the pre-restore status and a
     * human-readable reason (e.g. "paused: sticky-pause requires resumeSession",
     * "terminal: completed").
     */
    public static final class SkipEntry {
        private final String sessionId;
        private final AgentExecStatus preRestoreStatus;
        private final String reason;

        public SkipEntry(String sessionId, AgentExecStatus preRestoreStatus, String reason) {
            this.sessionId = sessionId;
            this.preRestoreStatus = preRestoreStatus;
            this.reason = reason;
        }

        public String getSessionId() {
            return sessionId;
        }

        public AgentExecStatus getPreRestoreStatus() {
            return preRestoreStatus;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return sessionId + "[" + preRestoreStatus + "]: " + reason;
        }
    }
}
