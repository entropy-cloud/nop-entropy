package io.nop.ai.agent.reliability;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.engine.SessionIds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * File-backed {@link ICheckpointManager} — the drop-in persistent sibling of
 * {@link ToolExecutionCheckpoint} (in-memory). Implements the same contract
 * ({@link #saveCheckpoint} / {@link #getLatestCheckpoint} / {@link #getCheckpoint})
 * with the storage backend changed from in-memory maps to the
 * {@code journal.md} + {@code snapshot.json} dual-file format (design §5.4a).
 *
 * <p><b>Drop-in replacement</b>: the executor/engine dispatch-path wiring
 * (L3-4) is unchanged — {@code saveCheckpoint} is called at the same point
 * ({@code ReActAgentExecutor} dispatch loop, after tool execution). Selecting
 * the file-backed manager instead of {@link ToolExecutionCheckpoint} only
 * changes whether checkpoints survive a process restart. The
 * {@link NoOpCheckpoint} default remains the shipped default; this manager is
 * registered explicitly when cross-process persistence is needed.
 *
 * <p><b>File layout</b>: per-session isolation via subdirectories:
 * <pre>
 *   {rootDirectory}/
 *     {sessionId}/
 *       journal.md       (append-only source of truth)
 *       snapshot.json    (derived cache, accelerates recovery)
 * </pre>
 *
 * <p><b>Watermark recovery path</b> (§5.4a): on first access to a session, the
 * manager loads the session's state from disk:
 * <ol>
 *   <li>If {@code snapshot.json} exists, read {@code lastWatermark} and the
 *       recovery-critical context-size snapshot (cached for the
 *       restore-on-restart successor).</li>
 *   <li>Read all {@code journal.md} entries to build a complete in-memory list
 *       + watermark index — this ensures {@link #getCheckpoint} works for any
 *       watermark. The snapshot's {@code lastWatermark} is available as
 *       metadata so the future restore-on-restart successor can skip old
 *       message-history loading; {@link CheckpointJournalReader#readAfter}
 *       (validated in Phase 1 tests) is the incremental-read primitive that
 *       successor will use to replay only the post-snapshot tail.</li>
 * </ol>
 * This makes checkpoint retrieval available immediately after a process
 * restart — the checkpoint list survives across instances (unlike
 * {@link ToolExecutionCheckpoint} which is lost when the process exits).
 *
 * <p><b>Snapshot generation timing</b> (§5.4a Decision): a snapshot is written
 * every {@code snapshotInterval} checkpoints (default 10) or on explicit
 * {@link #flushSnapshot}. compaction-triggered snapshot generation is a
 * Non-Goal. The interval bounds the journal replay range during recovery
 * without the I/O cost of rewriting snapshot.json on every saveCheckpoint.
 *
 * <p><b>§5.4a "only long-running tool" filter</b> (Decision): this manager
 * records a checkpoint for <b>every</b> tool execution by default (consistent
 * with the {@link ToolExecutionCheckpoint} in-memory default). The §5.4a
 * "only long-running tool" qualifier is deployment-specific and cannot be
 * defined at the framework layer. A configurable tool-name filter may be added
 * in a follow-up; the current default matches L3-4 behavior for cognitive
 * consistency.
 *
 * <p><b>Thread safety</b>: per-session state is stored in concurrent data
 * structures. Journal writes are serialized by {@link CheckpointJournalWriter}'s
 * internal lock. Snapshot writes are serialized by
 * {@link CheckpointSnapshotWriter}'s internal lock.
 *
 * <p><b>Retention</b>: the journal grows unbounded (no rotation/cap). At test
 * scale this is safe; a retention/rotation policy is a non-blocking follow-up.
 *
 * <p><b>Fail-closed sessionId validation</b> (P0 path-traversal fix,
 * finding [13-15]): every caller-supplied {@code sessionId} that reaches a
 * filesystem {@code Path.resolve} is validated by
 * {@link SessionIds#requireContainedPath} — only {@code [A-Za-z0-9_-]} ids
 * are accepted and the resolved path must stay inside {@code rootDirectory}.
 * Any invalid id throws {@link NopAiAgentException}; there is no silent
 * sanitization or fall-back.
 */
public class FileBackedCheckpointManager implements ICheckpointManager {

    public static final int DEFAULT_SNAPSHOT_INTERVAL = 10;

    static final String JOURNAL_FILE_NAME = "journal.md";
    static final String SNAPSHOT_FILE_NAME = "snapshot.json";

    private final Path rootDirectory;
    private final int snapshotInterval;

    private final ConcurrentHashMap<String, List<Checkpoint>> bySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Checkpoint> byWatermark = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CheckpointSnapshot> snapshotCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> saveCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> loadedSessions = new ConcurrentHashMap<>();

    private final CheckpointJournalWriter journalWriter = new CheckpointJournalWriter();
    private final CheckpointJournalReader journalReader = new CheckpointJournalReader();
    private final CheckpointSnapshotWriter snapshotWriter = new CheckpointSnapshotWriter();
    private final CheckpointSnapshotReader snapshotReader = new CheckpointSnapshotReader();

    /**
     * Create a file-backed manager with the default snapshot interval (10).
     *
     * @param rootDirectory the checkpoint root directory; per-session
     *                      subdirectories are created under it; never null
     */
    public FileBackedCheckpointManager(Path rootDirectory) {
        this(rootDirectory, DEFAULT_SNAPSHOT_INTERVAL);
    }

    /**
     * Create a file-backed manager with a configurable snapshot interval.
     *
     * @param rootDirectory   the checkpoint root directory; never null
     * @param snapshotInterval write a snapshot.json every N checkpoints;
     *                         must be &gt; 0
     */
    public FileBackedCheckpointManager(Path rootDirectory, int snapshotInterval) {
        if (rootDirectory == null) {
            throw new NopAiAgentException("FileBackedCheckpointManager: rootDirectory must not be null");
        }
        if (snapshotInterval <= 0) {
            throw new NopAiAgentException(
                    "FileBackedCheckpointManager: snapshotInterval must be > 0, got: " + snapshotInterval);
        }
        this.rootDirectory = rootDirectory;
        this.snapshotInterval = snapshotInterval;
    }

    @Override
    public void saveCheckpoint(Checkpoint checkpoint) {
        if (checkpoint == null) {
            throw new NopAiAgentException("FileBackedCheckpointManager.saveCheckpoint: checkpoint must not be null");
        }
        String sid = checkpoint.getSessionId();
        if (sid != null) {
            ensureSessionLoaded(sid);
            Path journalFile = sessionJournalPath(sid);
            journalWriter.appendCheckpoint(journalFile, sid, checkpoint);

            bySession.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(checkpoint);

            int count = saveCounters.computeIfAbsent(sid, k -> new AtomicInteger()).incrementAndGet();
            if (count % snapshotInterval == 0) {
                writeSnapshotForSession(sid, checkpoint);
            }
        }
        byWatermark.put(checkpoint.getWatermark(), checkpoint);
    }

    @Override
    public Checkpoint getLatestCheckpoint(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        ensureSessionLoaded(sessionId);
        List<Checkpoint> list = bySession.get(sessionId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        synchronized (list) {
            return list.get(list.size() - 1);
        }
    }

    @Override
    public Checkpoint getCheckpoint(String watermark) {
        if (watermark == null) {
            return null;
        }
        return byWatermark.get(watermark);
    }

    /**
     * Return an unmodifiable snapshot copy of all checkpoints recorded for a
     * session, in insertion (seq) order. Mirrors
     * {@link ToolExecutionCheckpoint#getCheckpoints(String)} so callers can
     * inspect the full checkpoint history regardless of the manager backend.
     *
     * @param sessionId the session identifier; may be null (returns empty list)
     * @return an unmodifiable list of checkpoints for the session; empty if
     *         none recorded
     */
    public List<Checkpoint> getCheckpoints(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        ensureSessionLoaded(sessionId);
        List<Checkpoint> list = bySession.get(sessionId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    /**
     * Explicitly write a snapshot.json for a session, capturing the
     * recovery-critical state at the latest checkpoint.
     *
     * @param sessionId the session to flush; never null
     */
    public void flushSnapshot(String sessionId) {
        if (sessionId == null) {
            throw new NopAiAgentException("FileBackedCheckpointManager.flushSnapshot: sessionId must not be null");
        }
        ensureSessionLoaded(sessionId);
        Checkpoint latest = getLatestCheckpoint(sessionId);
        if (latest != null) {
            writeSnapshotForSession(sessionId, latest);
        }
    }

    /**
     * @return the root directory under which per-session checkpoint files live
     */
    public Path getRootDirectory() {
        return rootDirectory;
    }

    /**
     * @return the snapshot interval (checkpoints per snapshot write)
     */
    public int getSnapshotInterval() {
        return snapshotInterval;
    }

    // ========================================================================
    // Internal: recovery path + snapshot generation
    // ========================================================================

    private void ensureSessionLoaded(String sessionId) {
        if (loadedSessions.putIfAbsent(sessionId, Boolean.TRUE) == null) {
            loadSessionFromDisk(sessionId);
        }
    }

    private void loadSessionFromDisk(String sessionId) {
        Path sessionDir = sessionDirPath(sessionId);
        Path journalFile = sessionDir.resolve(JOURNAL_FILE_NAME);
        if (!Files.exists(journalFile)) {
            return;
        }

        Path snapshotFile = sessionDir.resolve(SNAPSHOT_FILE_NAME);
        CheckpointSnapshot snapshot = snapshotReader.readIfExists(snapshotFile);
        if (snapshot != null) {
            snapshotCache.put(sessionId, snapshot);
        }

        // Always load all journal entries so the byWatermark index is complete
        // and getCheckpoint(watermark) works for any watermark. The snapshot
        // provides lastWatermark metadata (used by the restore-on-restart
        // successor) and marks the journal as flushed up to that point.
        List<Checkpoint> checkpoints = new ArrayList<>(journalReader.readAll(journalFile));

        if (!checkpoints.isEmpty()) {
            // byWatermark keeps the full mapping (pre + post compaction) so
            // getCheckpoint(oldWatermark) still resolves pre-compaction
            // checkpoints (audit/debug capability preserved).
            for (Checkpoint cp : checkpoints) {
                byWatermark.put(cp.getWatermark(), cp);
            }

            // Compaction-aware truncation (plan 188): the active restore set
            // (bySession) is truncated to start from the most recent
            // CheckpointType.COMPACTION checkpoint (inclusive — COMPACTION
            // marks the post-compaction baseline). Pre-compaction checkpoints
            // reference messageCount values beyond the compacted session's
            // messageCount, violating the documented invariant
            // checkpoint.messageCount <= session.messageCount. The journal.md
            // file on disk is never modified — full audit history persists.
            List<Checkpoint> active = CompactionAwareTruncation.truncateToLatestCompaction(checkpoints);

            List<Checkpoint> syncList = Collections.synchronizedList(active);
            bySession.put(sessionId, syncList);
            saveCounters.put(sessionId, new AtomicInteger(active.size()));
        }
    }

    private void writeSnapshotForSession(String sessionId, Checkpoint latest) {
        String snapId = "snap-" + UUID.randomUUID();
        CheckpointSnapshot snap = CheckpointSnapshot.of(
                snapId,
                sessionId,
                latest.getWatermark(),
                latest.getMessageCount(),
                latest.getTokenEstimate(),
                System.currentTimeMillis());
        Path snapshotFile = sessionDirPath(sessionId).resolve(SNAPSHOT_FILE_NAME);
        snapshotWriter.write(snapshotFile, snap);
        snapshotCache.put(sessionId, snap);
    }

    private Path sessionDirPath(String sessionId) {
        // P0 path-traversal guard (finding [13-15]): validate the caller-
        // supplied sessionId resolves inside rootDirectory before any
        // filesystem access. Defense-in-depth layer — the identifier-level
        // check at DefaultAgentEngine.resolveSessionId only covers
        // execute/sendMessage; raw sessionIds reaching the checkpoint manager
        // via restore/resume paths are caught here.
        return SessionIds.requireContainedPath(sessionId, rootDirectory);
    }

    private Path sessionJournalPath(String sessionId) {
        return sessionDirPath(sessionId).resolve(JOURNAL_FILE_NAME);
    }
}
