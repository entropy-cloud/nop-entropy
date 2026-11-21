package io.nop.ai.agent.reliability;

import static io.nop.ai.agent.NopAiAgentErrors.ERR_AGENT_INTERNAL_DETAIL;
import static io.nop.ai.agent.NopAiAgentErrors.ARG_DETAIL;
import io.nop.ai.agent.NopAiAgentException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Serializes a {@link Checkpoint} to a §5.4a markdown section and appends it to
 * a per-session {@code journal.md} file. The journal is the append-only source
 * of truth for persisted checkpoints — {@code saveCheckpoint} only appends a
 * new {@code ## CP-NNN} section, never rewrites existing content.
 *
 * <p><b>Format</b> (per design §5.4a): the file begins with a
 * {@code # Checkpoint Journal - {sessionId}} header, followed by one
 * {@code ## CP-NNN} section per checkpoint. Each section contains key:value
 * lines for type / seq / timestamp (ISO-8601) / sessionId / watermark /
 * toolName / callId / inputSummary / outputSummary / messageCount /
 * tokenEstimate. String values are JSON-quoted (so multi-line summaries are
 * escaped to a single line); {@code null} strings are written as the literal
 * token {@code null}.
 *
 * <p><b>Thread safety</b> (Plan 2519 CL-006/CL-063 javadoc 修订 — declaration
 * matched reality): append writes are serialized by ONE shared {@code ioLock}
 * monitor instance-wide (not a per-file lock) — concurrent
 * {@code saveCheckpoint} calls, same session or cross-session, all serialize
 * on it. The lock protects journal file read/write mutual exclusion and
 * nothing more; it does not span snapshot writes (those serialize in
 * {@code CheckpointSnapshotWriter}'s own lock).
 *
 * <p><b>Plan 2584 Phase 1 (audit nop-ai Major 3)</b>: rotation rewrite now
 * lives in this writer ({@link #rewriteJournal}) and takes the SAME
 * {@code ioLock} as append — the pre-fix dual-lock shape (manager
 * {@code rotateLock} vs writer {@code ioLock}) allowed a concurrent append to
 * be clobbered by the rewrite. The ioLock is a global single monitor, so a
 * rotation briefly blocks appends of OTHER sessions; rotation is low-frequency
 * (retention cap traversal only) and the blocking window is bounded —
 * adjudicated acceptable. The rewrite itself is crash-atomic: content is
 * written to a temp file in the same directory and moved into place with
 * {@link java.nio.file.StandardCopyOption#ATOMIC_MOVE} (falling back to a
 * plain move when the filesystem does not support atomic moves), so a crash
 * mid-write can never leave a truncated journal.
 *
 * <p><b>Append-only semantics</b>: if the file does not exist it is created
 * with the header. Existing content is only overwritten by
 * {@link #rewriteJournal} (rotation), never by append.
 */
public final class CheckpointJournalWriter {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(CheckpointJournalWriter.class);

    private static final String HEADER_PREFIX = "# Checkpoint Journal - ";

    private final Object ioLock = new Object();

    /**
     * Test seam (plan 2584 Phase 1 regression pins): invoked inside the
     * {@code ioLock} critical section of {@link #rewriteJournal} after the
     * lock is held, before content is built. Lets a test deterministically
     * hold the rewrite inside the lock (concurrent-append pin) or inject a
     * mid-write failure (journal-untouched pin). Production code never sets it.
     */
    volatile Runnable rewriteInsideLockHookForTest;

    /**
     * Append a single checkpoint as a {@code ## CP-NNN} section. Creates the
     * file (with the journal header) on first write.
     *
     * @param journalFile the per-session journal.md path; never null
     * @param sessionId   the session id used in the file header; may be null
     *                    (header becomes {@code # Checkpoint Journal - anonymous})
     * @param checkpoint  the checkpoint to serialize; never null
     * @throws NopAiAgentException if an I/O error occurs
     */
    public void appendCheckpoint(Path journalFile, String sessionId, Checkpoint checkpoint) {
        if (journalFile == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointJournalWriter.appendCheckpoint: journalFile must not be null");
        }
        if (checkpoint == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointJournalWriter.appendCheckpoint: checkpoint must not be null");
        }

        String section = serializeSection(checkpoint);

        synchronized (ioLock) {
            try {
                ensureFileWithHeader(journalFile, sessionId);
                Files.write(journalFile, section.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e).param(ARG_DETAIL, "CheckpointJournalWriter.appendCheckpoint: failed to append to " + journalFile
                                + ": " + e.getMessage());
            }
        }
    }

    /**
     * Rewrite the journal file with ONLY the retained checkpoints (rotation
     * truncation). Plan 2584 Phase 1: moved from {@code FileBackedCheckpoint-
     * Manager} into this writer so the rewrite serializes on the SAME
     * {@code ioLock} as {@link #appendCheckpoint} — a concurrent append can
     * never be lost to the rewrite (it blocks until the rewrite completes and
     * is appended afterwards; its checkpoint stays in the manager's retained
     * list anyway).
     *
     * <p>Crash-atomic publication: the content is written to a temp file in
     * the journal's directory, then moved onto the journal with
     * {@code ATOMIC_MOVE} ({@code AtomicMoveNotSupportedException} falls back
     * to a plain replace). A failure at ANY point (serialization, temp write,
     * move) leaves the previous journal file untouched — the file keeps its
     * old (superset) content and stays readable; only a WARN is emitted since
     * rotation is a retention measure that must not break the checkpoint
     * write path (in-memory truncation already bounded the heap).
     *
     * @param journalFile the per-session journal.md path; never null
     * @param sessionId   the session id used in the file header; may be null
     * @param retained    the checkpoints to keep, in order; never null
     */
    public void rewriteJournal(Path journalFile, String sessionId, java.util.List<Checkpoint> retained) {
        if (journalFile == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointJournalWriter.rewriteJournal: journalFile must not be null");
        }
        if (retained == null) {
            throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL).param(ARG_DETAIL, "CheckpointJournalWriter.rewriteJournal: retained must not be null");
        }
        synchronized (ioLock) {
            Path tempFile = null;
            try {
                // test hook inside the guarded block: an injected failure must
                // be treated like any mid-rewrite failure (journal preserved)
                Runnable hook = rewriteInsideLockHookForTest;
                if (hook != null) {
                    hook.run();
                }
                StringBuilder sb = new StringBuilder();
                sb.append(HEADER_PREFIX).append(sessionId != null ? sessionId : "anonymous").append("\n\n");
                synchronized (retained) {
                    for (Checkpoint cp : retained) {
                        sb.append(serializeSection(cp));
                    }
                }
                Path fileName = journalFile.getFileName() != null
                        ? journalFile.getFileName() : journalFile.toAbsolutePath().getFileName();
                tempFile = journalFile.resolveSibling(
                        fileName.toString() + ".rewrite-" + java.util.UUID.randomUUID() + ".tmp");
                Files.write(tempFile, sb.toString().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(tempFile, journalFile,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile = null;
                } catch (java.nio.file.AtomicMoveNotSupportedException fallback) {
                    // filesystem without atomic-move support: plain replace
                    Files.move(tempFile, journalFile,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile = null;
                }
            } catch (Exception e) {
                // Rotation is a retention measure — a failed rewrite must not
                // break the checkpoint write path. The file keeps its old
                // content (superset); the in-memory truncation already bounded
                // the heap.
                LOG.warn("nop.ai.agent.checkpoint-journal-rotate: journal rewrite failed "
                        + "(previous journal preserved): file={}, reason={}", journalFile, e.toString());
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException cleanupEx) {
                        LOG.debug("nop.ai.agent.checkpoint-journal-rotate: temp cleanup failed: {}",
                                cleanupEx.toString());
                    }
                }
            }
        }
    }

    private void ensureFileWithHeader(Path journalFile, String sessionId) throws IOException {
        if (!Files.exists(journalFile)) {
            Path parent = journalFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String header = HEADER_PREFIX + (sessionId != null ? sessionId : "anonymous") + "\n\n";
            Files.write(journalFile, header.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
    }

    static String serializeSection(Checkpoint cp) {
        StringBuilder sb = new StringBuilder();
        sb.append("## CP-").append(formatSeq(cp.getSeq())).append('\n');
        sb.append("type: ").append(cp.getType().name()).append('\n');
        sb.append("seq: ").append(cp.getSeq()).append('\n');
        sb.append("timestamp: ").append(Instant.ofEpochMilli(cp.getTimestamp()).toString()).append('\n');
        sb.append("sessionId: ").append(encodeString(cp.getSessionId())).append('\n');
        sb.append("watermark: ").append(encodeString(cp.getWatermark())).append('\n');
        sb.append("toolName: ").append(encodeString(cp.getToolName())).append('\n');
        sb.append("callId: ").append(encodeString(cp.getCallId())).append('\n');
        sb.append("inputSummary: ").append(encodeString(cp.getInputSummary())).append('\n');
        sb.append("outputSummary: ").append(encodeString(cp.getOutputSummary())).append('\n');
        sb.append("messageCount: ").append(cp.getMessageCount()).append('\n');
        sb.append("tokenEstimate: ").append(cp.getTokenEstimate()).append('\n');
        sb.append("idempotencyKey: ").append(encodeString(cp.getIdempotencyKey())).append('\n');
        sb.append("waitFor: ").append(encodeString(cp.getWaitFor())).append('\n');
        sb.append('\n');
        return sb.toString();
    }

    private static String formatSeq(int seq) {
        return String.format("%03d", seq);
    }

    /**
     * JSON-encode a string value so multi-line content is escaped to a single
     * line. {@code null} is rendered as the literal token {@code null}.
     */
    static String encodeString(String value) {
        if (value == null) {
            return "null";
        }
        return io.nop.core.lang.json.JsonTool.stringify(value);
    }
}
