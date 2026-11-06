/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.nop.core.resource.impl.FileResource;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Exactly-once file sink for text-line output. Each checkpoint epoch maps to one output file
 * written via <em>temp file + atomic rename + per-epoch manifest</em>, so that a crash between
 * {@code saveState} and {@code commit} (the durable-but-uncommitted window) recovers with no
 * duplicates and no data loss.
 *
 * <p>Lifecycle per epoch:
 * <ol>
 *   <li>{@code invoke(value)} buffers the record (as a text line) in memory.</li>
 *   <li>{@code saveState(epochId)} writes the buffer to a temp file {@code .{epochId}.tmp},
 *       records a {@link FilePendingCommit} in {@code pendingCommits[epochId]}, clears the buffer,
 *       then delegates to {@code super.saveState} (saveState-first pattern, mirrors the JDBC sink).</li>
 *   <li>{@code commit(epochId)} performs {@code Files.move(ATOMIC_MOVE)} from temp to final file
 *       and atomically updates the manifest. Idempotent: if the manifest already records the epoch,
 *       the rename is skipped (recover-safe re-commit).</li>
 *   <li>{@code abort(epochId)} deletes the temp file (safe no-op if already renamed).</li>
 * </ol>
 *
 * <p><strong>Edge case (final-exists but manifest-missing)</strong>: if a crash happened after the
 * atomic rename but before the manifest write, {@code commit} repairs the manifest (adds the entry,
 * skips the rename) rather than throwing — the data is already durable.
 *
 * <p><strong>Parallel subtasks</strong>: when the sink runs with {@code parallelism > 1},
 * each subtask receives an independent copy via {@link #copyForSubtask(int)} (routed from
 * {@code OperatorChain.deepCopy(subtaskIndex)}). Copies for subtask index &gt; 0 suffix their
 * per-epoch temp/final files ({@code epoch-N.sK.txt}) and manifest keys ({@code N.sK}) so
 * they never overwrite each other's output; subtask 0 keeps the legacy unsuffixed names.
 *
 * <p>See {@code ai-dev/design/nop-stream/connector-design.md} §5.5.
 *
 * @param <IN> the type of input records (rendered via {@code toString()})
 */
public class FileTwoPhaseCommitSink<IN> extends TwoPhaseCommitSinkFunction<IN>
        implements ConnectivityCheckable {

    private static final long serialVersionUID = 1L;

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String MANIFEST_FILE = "manifest.properties";
    private static final String LINE_SEPARATOR = System.lineSeparator();
    /**
     * C3-002: default retention epoch window = SSOT defaults projected through the
     * assembly formula — ceil(30d × 86400000ms ÷ 60000ms) = 43200 epochs. Deployments
     * override via {@link #computeRetentionEpochWindow(long, long)} with their actual
     * retention-days and checkpoint interval.
     */
    public static final long DEFAULT_RETENTION_EPOCH_WINDOW =
            computeRetentionEpochWindow(30L, CheckpointConfig.DEFAULT_CHECKPOINT_INTERVAL);

    /**
     * CRA 8.43 R4-03 W-04 (MA-13=C5-005; adjudication C.1): upper bound of
     * buffered lines in the current epoch's {@code currentBuffer} (same default
     * and meaning as the JDBC sink's {@code maxEpochRecords}). Invoke rejects
     * typed BEFORE buffering once the bound is reached.
     */
    public static final long DEFAULT_MAX_EPOCH_LINES = 100_000L;

    /**
     * CRA 8.43 R4-03 W-04: upper bound of simultaneously pending (uncommitted)
     * epochs in {@code pendingCommits} — each entry pins a tmp file on disk, so
     * the pending stack IS the disk-face bound. 8 pending epochs ≈ 8
     * consecutive unconverged checkpoints before the sink fails typed.
     */
    public static final long DEFAULT_MAX_PENDING_EPOCHS = 8L;

    /**
     * CRA 8.43 R4-03 W-06 (MA-15=C2-005, merged C3-008; adjudication C.4):
     * bounded wait for the cross-JVM manifest lock (both levels — the JVM-local
     * {@link ReentrantLock} AND the OS-level file lock share this one budget).
     * 60s = checkpointInterval-scale: significantly longer than a legal slow
     * commit (fsync + manifest rewrite, seconds), far short of the unbounded
     * block the pre-fix shape had (a peer JVM alive-but-hung froze the commit
     * path forever → checkpoint stall → restart → same hang).
     */
    public static final long DEFAULT_MANIFEST_LOCK_TIMEOUT_MS = 60_000L;
    /**
     * AR-14 (plan 2026-09-04-1326-3): cross-writer manifest lock file in the output
     * directory. Serializes the manifest read-modify-write across parallel subtasks —
     * both threads within one JVM (ReentrantLock) and subtask copies in different
     * JVMs on shared storage (OS-level {@link FileLock}). Without it, two subtasks
     * committing the same epoch interleave load→update→replace and the last writer
     * silently drops the other's manifest entry (lost-update).
     */
    private static final String MANIFEST_LOCK_FILE = ".manifest.lock";
    /** AR-14: intra-JVM manifest locks keyed by canonical output directory. */
    private static final ConcurrentHashMap<String, ReentrantLock> MANIFEST_JVM_LOCKS =
            new ConcurrentHashMap<>();

    private final String outputDir;
    /**
     * Plan 2511 C3-002 (R01 SSOT member row, face {@code epochledger}): manifest
     * retention epoch window = {@code ceil(retention-days × 86400000ms ÷
     * checkpointIntervalMs)}, computed once at assembly ({@link
     * #computeRetentionEpochWindow(long, long)}) and injected here. Manifest pruning
     * (form B, commit-time piggyback) removes keys whose encoded epoch is older than
     * {@code currentEpoch - window} inside the existing AR-14-locked rewrite, so the
     * manifest stops growing O(all epochs). Negative window = assembly fail-fast; a
     * zero window disables pruning explicitly (retention-days = 0 opt-out).
     */
    private final long retentionEpochWindow;

    /** CRA 8.43 R4-03 W-04: current-epoch line-buffer bound ({@code <= 0} fails fast at construction). */
    private final long maxEpochLines;

    /** CRA 8.43 R4-03 W-04: pendingCommits stack bound ({@code <= 0} fails fast at construction). */
    private final long maxPendingEpochs;

    /** CRA 8.43 R4-03 W-06: bounded wait (ms) for BOTH manifest-lock levels ({@code <= 0} fails fast). */
    private final long manifestLockTimeoutMs;
    /**
     * Item 14 (distributed): stored as a NAME (not a {@link Charset} object) so the
     * sink survives the Java serialization of the deployment descriptor's pipeline
     * spec — {@code Charset} implementations are not serializable. Resolved lazily
     * by {@link #charset()}.
     */
    private final String charsetName;
    /**
     * R6 CON-01 (plan 2325): manifest entry keys that were RESTORED via
     * {@link #setPendingCommits} — the recover-safe population. A commit whose
     * manifest key is in this set re-commits a durable-but-uncommitted epoch of
     * a PREVIOUS run (idempotent skip); a commit whose key is NOT in this set
     * was created by THIS run and colliding with an existing manifest entry
     * means a fresh run restarted into a stale output directory — committing
     * would silently drop this run's data, so the guard fails loud instead.
     */
    private final transient java.util.Set<String> recoveredEntryKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private transient volatile Charset charset;
    private transient Path outputDirPath;
    /**
     * Subtask identity of this sink copy (0 for a non-parallel / template instance).
     * Parallel subtask copies suffix their per-epoch temp/final files and manifest keys
     * with this index so they never overwrite each other's output (P0: parallelism&gt;1
     * batch loss via shared pendingCommits / identical temp paths).
     */
    private final int subtaskIndex;

    /**
     * In-memory buffer for the current epoch (not yet in pendingCommits). Transient:
     * re-initialized on demand after cross-JVM deserialization (the deployment
     * descriptor's pipeline spec Java-serializes the sink — field initializers do
     * not run for deserialized instances, so the buffer would be null and the first
     * {@code synchronized (currentBuffer)} would NPE).
     */
    private transient volatile List<String> currentBuffer = new ArrayList<>();

    /**
     * Item 14 (distributed): re-initializes the transient runtime fields after Java
     * deserialization (field initializers do not run on the deserialization path).
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.outputDirPath = Paths.get(outputDir);
        this.charset = Charset.forName(charsetName);
        if (this.currentBuffer == null) {
            this.currentBuffer = new ArrayList<>();
        }
    }

    /**
     * Constructs a file sink writing text lines to {@code outputDir}.
     *
     * @param outputDir the output directory (created if absent)
     * @param charset   the charset for text encoding (null defaults to UTF-8)
     */
    public FileTwoPhaseCommitSink(String outputDir, Charset charset) {
        this(outputDir, charset, 0);
    }

    /**
     * Copy constructor for a parallel subtask (see {@link #copyForSubtask(int)}).
     */
    private FileTwoPhaseCommitSink(String outputDir, Charset charset, int subtaskIndex) {
        this(outputDir, charset, subtaskIndex, DEFAULT_RETENTION_EPOCH_WINDOW);
    }

    /**
     * C3-002: the retention epoch window computed at assembly time — {@code
     * ceil(retentionDays × 86400000ms ÷ checkpointIntervalMs)}, ceiling so the window
     * never UNDER-retains (the window must stay &ge; the re-submittable epoch window).
     * The conversion base is the assembly-time checkpoint interval constant (sink
     * lifetime fixed; interval changes across runs are absorbed by the ceiling and the
     * 30-day default's order of magnitude).
     */
    public static long computeRetentionEpochWindow(long retentionDays, long checkpointIntervalMs) {
        if (retentionDays < 0) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_ARG_NAME, "retentionDays")
                    .param(ARG_DETAIL, "retentionDays must be >= 0 (0 disables pruning), got: "
                            + retentionDays);
        }
        if (checkpointIntervalMs <= 0) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_ARG_NAME, "checkpointIntervalMs")
                    .param(ARG_DETAIL, "checkpointIntervalMs must be positive, got: "
                            + checkpointIntervalMs);
        }
        long retentionMillis = retentionDays * 86_400_000L;
        return (retentionMillis + checkpointIntervalMs - 1) / checkpointIntervalMs;
    }

    /**
     * Convenience constructor defaulting to UTF-8.
     */
    public FileTwoPhaseCommitSink(String outputDir) {
        this(outputDir, StandardCharsets.UTF_8);
    }

    /**
     * Returns an independent copy of this sink for the parallel subtask
     * {@code subtaskIndex}. The copy shares the immutable configuration (output
     * directory, charset) but has a fresh in-memory buffer and an empty
     * {@code pendingCommits} map, and suffixes its per-epoch temp/final files and
     * manifest keys with the subtask index — parallel subtasks therefore never
     * overwrite each other's batches (exactly-once under {@code parallelism > 1}).
     */
    @Override
    public FileTwoPhaseCommitSink<IN> copyForSubtask(int subtaskIndex) {
        return new FileTwoPhaseCommitSink<>(outputDir, charset(), subtaskIndex, retentionEpochWindow,
                maxEpochLines, maxPendingEpochs, manifestLockTimeoutMs);
    }

    /**
     * C3-002: constructs a sink with an explicit manifest retention epoch window
     * (assembly-time projection of the SSOT {@code epochledger} retention parameters
     * via {@link #computeRetentionEpochWindow(long, long)}). Primarily for the
     * deployment assembly and tests; the plain constructors keep the SSOT-default
     * window.
     */
    public FileTwoPhaseCommitSink(String outputDir, Charset charset, int subtaskIndex,
                                  long retentionEpochWindow) {
        this(outputDir, charset, subtaskIndex, retentionEpochWindow,
                DEFAULT_MAX_EPOCH_LINES, DEFAULT_MAX_PENDING_EPOCHS,
                DEFAULT_MANIFEST_LOCK_TIMEOUT_MS);
    }

    /**
     * CRA 8.43 R4-03 W-04/W-06: full constructor with the buffer/pending
     * envelope bounds and the manifest-lock wait budget. Primarily for the
     * deployment assembly and tests; the plain constructors keep the defaults.
     * All three bounds fail fast typed on non-positive values.
     */
    public FileTwoPhaseCommitSink(String outputDir, Charset charset, int subtaskIndex,
                                  long retentionEpochWindow, long maxEpochLines,
                                  long maxPendingEpochs, long manifestLockTimeoutMs) {
        if (outputDir == null || outputDir.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "outputDir");
        }
        // CRA 8.43 R4-03 W-04/W-06: nonsensical envelope declarations fail fast.
        if (maxEpochLines <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "maxEpochLines")
                    .param(ARG_DETAIL, "maxEpochLines must be > 0, got: " + maxEpochLines);
        }
        if (maxPendingEpochs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "maxPendingEpochs")
                    .param(ARG_DETAIL, "maxPendingEpochs must be > 0, got: " + maxPendingEpochs);
        }
        if (manifestLockTimeoutMs <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "manifestLockTimeoutMs")
                    .param(ARG_DETAIL, "manifestLockTimeoutMs must be > 0, got: "
                            + manifestLockTimeoutMs);
        }
        // C3-002: the retention epoch window is an assembly-time constant; negative
        // values would prune not-yet-expired epochs (a recovery-correctness hazard)
        if (retentionEpochWindow < 0) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_ARG_NAME, "retentionEpochWindow")
                    .param(ARG_DETAIL, "manifest retention epoch window must be >= 0 (0 disables"
                            + " pruning), got: " + retentionEpochWindow);
        }
        this.outputDir = outputDir;
        this.charsetName = (charset != null ? charset : StandardCharsets.UTF_8).name();
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
        this.subtaskIndex = subtaskIndex;
        this.retentionEpochWindow = retentionEpochWindow;
        this.maxEpochLines = maxEpochLines;
        this.maxPendingEpochs = maxPendingEpochs;
        this.manifestLockTimeoutMs = manifestLockTimeoutMs;
        this.outputDirPath = Paths.get(outputDir);
        try {
            Files.createDirectories(outputDirPath);
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e)
                    .param(ARG_DETAIL, "Failed to create output directory: " + outputDir);
        }
    }

    /**
     * Item 14: resolves the (transient) charset from the serializable name —
     * double-checked so concurrent first uses after deserialization share one
     * resolution. An unknown charset name fails fast (never silently falls back).
     */
    private Charset charset() {
        Charset local = this.charset;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (this.charset == null) {
                this.charset = Charset.forName(charsetName);
            }
            return this.charset;
        }
    }

    /**
     * Returns the subtask index of this sink copy. Primarily for tests.
     */
    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    @Override
    public SinkConsistencyCapability getSinkConsistency() {
        return SinkConsistencyCapability.TWO_PHASE_COMMIT;
    }

    // ---- Data path ----

    @Override
    public void invoke(IN value) throws Exception {
        if (value == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "value");
        }
        synchronized (currentBuffer) {
            // CRA 8.43 R4-03 W-04 (MA-13=C5-005): declared epoch-line envelope —
            // reject BEFORE buffering (same-monitor atomic check-and-reject) so
            // the "barrier duration × input rate" product fails typed instead of
            // growing the heap without bound.
            if (currentBuffer.size() >= maxEpochLines) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_ARG_NAME, "maxEpochLines")
                        .param(ARG_DETAIL, "FILE 2PC epoch buffer is full: "
                                + currentBuffer.size() + "/" + maxEpochLines
                                + " lines for the current epoch — reduce the checkpoint "
                                + "interval, raise maxEpochLines, or inspect why commits "
                                + "are not draining the buffer");
            }
            currentBuffer.add(value.toString());
        }
    }

    /**
     * Writes the current in-memory buffer to a temp file BEFORE delegating to
     * {@code super.saveState}, so the batch is captured in THIS checkpoint. Mirrors the JDBC sink's
     * saveState-first override.
     */
    @Override
    public TaskStateSnapshot saveState(long epochId) throws Exception {
        synchronized (currentBuffer) {
            int count = currentBuffer.size();
            if (count > 0) {
                Path tempPath = tempPath(epochId);
                writeLines(tempPath, currentBuffer);
                // R5 CON-04: make the epoch data file durable BEFORE the
                // checkpoint is acknowledged — the commit-time fsync in
                // doCommitLocked ran after the checkpoint durable marker, so an
                // OS crash inside that window could commit a truncated file.
                forcePath(tempPath);
                // CRA 8.43 R4-03 W-04 face 2 (C5-010): bound the pendingCommits
                // stack BEFORE the transfer — every pending entry pins a tmp file
                // (disk face) and re-serializes on every later barrier. The
                // retention window / prune chain is NOT touched (2511 adjudication
                // ④ carrier, zero-touch) — the task fails typed and the recovery
                // path takes over.
                if (getPendingCommits().size() >= maxPendingEpochs) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR)
                            .param(ARG_DETAIL, "FILE 2PC pendingCommits stack is full: "
                                    + getPendingCommits().size() + "/" + maxPendingEpochs
                                    + " uncommitted epochs (epoch " + epochId
                                    + " would exceed the bound) — commits are not landing; "
                                    + "inspect the commit-notification chain (finishCommit) "
                                    + "or raise maxPendingEpochs for slow-durable deployments");
                }
                getPendingCommits().put(epochId,
                        new FilePendingCommit(tempPath.toString(), count, subtaskIndex));
                currentBuffer.clear();
            }
        }
        return super.saveState(epochId);
    }

    @Override
    public void preCommit(long checkpointId) throws Exception {
        // saveState already wrote the temp file. No-op.
    }

    @Override
    public void commit(long checkpointId) throws Exception {
        Object raw = getPendingCommits().get(checkpointId);
        if (raw == null) {
            // No pending batch for this epoch — nothing to commit.
            return;
        }
        if (!(raw instanceof FilePendingCommit)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                    .param(ARG_EPOCH_ID, checkpointId)
                    .param(ARG_DETAIL,
                            "pendingCommits value is not a FilePendingCommit: "
                                    + (raw == null ? "null" : raw.getClass().getName()));
        }
        FilePendingCommit pending = (FilePendingCommit) raw;

        // AR-14: the whole manifest read-modify-write (load → idempotence check →
        // rename → update) runs under the output-directory manifest lock, so parallel
        // subtask commits interleave without losing manifest entries.
        withManifestLock(() -> doCommitLocked(checkpointId, pending));
    }

    private void doCommitLocked(long checkpointId, FilePendingCommit pending) throws Exception {
        Path tempPath = Paths.get(pending.getTempPath());
        // Derive the final path and manifest key from the entry's OWNING subtask, not
        // from this copy's index: after recovery a different subtask copy may re-commit
        // a durable-but-uncommitted entry, and the recorded paths must stay identical.
        int ownerSubtask = pending.getSubtaskIndex();
        Path finalPath = finalPath(ownerSubtask, checkpointId);
        String manifestEntryKey = manifestKey(ownerSubtask, checkpointId);
        Properties manifest = loadManifest();

        // C3-002 form B (R01 SSOT member row, face epochledger): commit-time piggyback
        // pruning — drop manifest keys whose encoded epoch fell out of the retention
        // window, folding the prune into the SAME locked rewrite the commit already
        // performs, so manifest growth converges from O(all epochs) to O(retention set).
        // Never touches the key being committed; the window guarantees pruning only
        // strictly older-than (currentEpoch - window) keys.
        pruneExpiredEntries(manifest, checkpointId);

        // Idempotent guard: manifest already records this epoch.
        // R6 CON-01 (plan 2325): discriminate by pending-entry PROVENANCE — an
        // entry restored via setPendingCommits belongs to the previous run and
        // its re-commit is recover-safe (skip); an entry created by THIS run
        // colliding with an existing manifest entry means a fresh run restarted
        // into a stale output directory. The old WARN-and-drop swallowed this
        // run's data (temp never renamed, consumer never sees it) — fail loud.
        if (manifest.containsKey(manifestEntryKey)) {
            if (recoveredEntryKeys.contains(manifestEntryKey)) {
                LOG.warn("File 2PC commit skipped: manifest already records epoch {} at key {} (recover-safe re-commit of a restored epoch)",
                        checkpointId, manifestEntryKey);
                getPendingCommits().remove(checkpointId);
                return;
            }
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_EPOCH_ID, checkpointId)
                    .param(ARG_DETAIL, "File 2PC fresh-run manifest collision: this run's pending epoch "
                            + checkpointId + " collides with manifest key " + manifestEntryKey
                            + " written by a previous run in the same output directory. "
                            + "The uncommitted temp data is preserved at " + tempPath
                            + ". Use a fresh output directory or restore from the checkpoint "
                            + "instead of restarting a new run into a used directory (R6 CON-01)");
        }

        if (Files.exists(finalPath)) {
            // Edge case: rename succeeded on a prior attempt but manifest write did not.
            // Repair the manifest (add entry, skip rename) — data is already durable.
            // R6 CON-01 (plan 2325): the repair may only adopt the file when the
            // pending entry was RESTORED (it references the previous run's own
            // durable output); a this-run entry colliding with an existing final
            // file is the same fresh-run reuse hazard as the manifest guard.
            if (!recoveredEntryKeys.contains(manifestEntryKey)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR)
                        .param(ARG_EPOCH_ID, checkpointId)
                        .param(ARG_DETAIL, "File 2PC fresh-run final-file collision: this run's pending epoch "
                                + checkpointId + " collides with existing final file " + finalPath
                                + " from a previous run in the same output directory (R6 CON-01)");
            }
            LOG.warn("File 2PC final file already exists for epoch {} at {} — repairing manifest entry {} (recover-safe re-commit)",
                    checkpointId, finalPath, manifestEntryKey);
            manifest.setProperty(manifestEntryKey, finalPath.toString());
            updateManifestAtomically(manifest);
            getPendingCommits().remove(checkpointId);
            return;
        }

        // Flush the temp data file to stable storage BEFORE the
        // rename. Without it the exactly-once promise stopped at the rename:
        // after an OS crash the manifest could record a committed epoch whose
        // data blocks never reached the disk. Cost is one fsync per commit
        // (per checkpoint interval), not per record.
        forcePath(tempPath);

        // Atomic rename: temp → final. ST-7 adjudication: this stays on
        // Files.move(ATOMIC_MOVE) — FileResource.renameTo delegates to
        // FileHelper.moveFile, which omits ATOMIC_MOVE and swallows
        // FileAlreadyExistsException (returns false); wrapping it would break the
        // exactly-once commit contract.
        try {
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e)
                    .param(ARG_EPOCH_ID, checkpointId)
                    .param(ARG_DETAIL, "Atomic move failed: " + tempPath + " -> " + finalPath);
        }

        // Atomic manifest update (the manifest temp file is fsynced inside
        // updateManifestAtomically before its own rename)
        manifest.setProperty(manifestEntryKey, finalPath.toString());
        updateManifestAtomically(manifest);

        // Best-effort directory entry flush so the renames above survive an OS
        // crash on POSIX filesystems; unsupported platforms skip quietly.
        forceDirectoryQuietly(outputDirPath);

        getPendingCommits().remove(checkpointId);
    }

    /**
     * C3-002 form B: removes manifest entries whose epoch-key age exceeds the
     * retention window — {@code epoch(key) < currentEpoch - retentionEpochWindow}.
     * Idempotent (a second pass over the same manifest removes nothing) and guarded
     * against over-deletion by the ceiling-rounded window (retention window &ge;
     * the re-submittable epoch window, so restoredKeys-style recoverable epochs are
     * never pruned).
     */
    private void pruneExpiredEntries(Properties manifest, long currentEpoch) {
        if (retentionEpochWindow <= 0) {
            return; // retention-days=0 opt-out: pruning disabled
        }
        long keepAbove = currentEpoch - retentionEpochWindow;
        List<String> expired = null;
        for (String key : manifest.stringPropertyNames()) {
            Long epoch = epochOfKey(key);
            if (epoch != null && epoch < keepAbove) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(key);
            }
        }
        if (expired != null) {
            for (String key : expired) {
                manifest.remove(key);
            }
            LOG.info("file-2pc: pruned {} manifest key(s) below epoch {} (retention window {})",
                    expired.size(), keepAbove, retentionEpochWindow);
        }
    }

    /**
     * C3-002: decodes the epoch number encoded in a manifest key ({@code N} for
     * subtask 0, {@code N.sK} for subtask K). Returns null for keys without an
     * epoch-shaped prefix — those are never pruned by the epoch-age predicate.
     */
    static Long epochOfKey(String key) {
        int dot = key.indexOf('.');
        String digits = dot >= 0 ? key.substring(0, dot) : key;
        if (digits.isEmpty()) {
            return null;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** C3-002 test/ops visibility: the effective retention epoch window. */
    long getRetentionEpochWindow() {
        return retentionEpochWindow;
    }

    /**
     * AR-14: runs {@code action} under the output-directory manifest lock — a JVM-local
     * {@link ReentrantLock} for same-process subtask threads, plus an OS-level
     * {@link FileLock} on {@code .manifest.lock} for subtask copies in different JVMs
     * on shared storage. Lock ordering is always jvmLock → fileLock (no inversion).
     */
    private void withManifestLock(FileTwoPhaseCommitAction action) throws Exception {
        ReentrantLock jvmLock = MANIFEST_JVM_LOCKS.computeIfAbsent(
                outputDirPath.toAbsolutePath().normalize().toString(), k -> new ReentrantLock());
        // CRA 8.43 R4-03 W-06 (MA-15=C2-005; adjudication C.4): BOTH lock levels
        // now share ONE bounded budget ({@code manifestLockTimeoutMs}) —
        // jvmLock.tryLock(time, unit) is bounded AND interruptible (cancel
        // propagates), replacing the unbounded lock() that froze the commit
        // path when a peer JVM hung. Lock ORDER is unchanged: the OS-level
        // tryLock polling loop below still runs while HOLDING the jvmLock
        // (JL→FL, no inversion), and every timeout path unlocks symmetrically.
        boolean jvmAcquired;
        try {
            jvmAcquired = jvmLock.tryLock(manifestLockTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                    .param(ARG_ARG_NAME, "manifestLock")
                    .param(ARG_DETAIL, "Interrupted while waiting for the JVM-local manifest "
                            + "lock of " + outputDirPath.toAbsolutePath().normalize()
                            + " (waited up to " + manifestLockTimeoutMs + "ms) — check the "
                            + "lock-holding thread dump of the peer JVM");
        }
        if (!jvmAcquired) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                    .param(ARG_ARG_NAME, "manifestLock")
                    .param(ARG_DETAIL, "Timed out after " + manifestLockTimeoutMs
                            + "ms waiting for the JVM-local manifest lock of "
                            + outputDirPath.toAbsolutePath().normalize() + " — a peer writer "
                            + "holds it; check the lock-holding thread dump");
        }
        try (FileChannel channel = FileChannel.open(outputDirPath.resolve(MANIFEST_LOCK_FILE),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            // OS level: FileLock has no timed variant — tryLock() polling with a
            // fixed 200ms step until the shared budget runs out (audit C2-005
            // sanctioned shape). Lock order (JL→FL) and the critical section are
            // unchanged.
            FileLock fileLock = null;
            long deadline = System.currentTimeMillis() + manifestLockTimeoutMs;
            try {
                while (true) {
                    try {
                        fileLock = channel.tryLock();
                    } catch (java.nio.channels.OverlappingFileLockException e) {
                        // The JVM (or a foreign holder inside this JVM) already
                        // holds an overlapping lock — treat exactly like a
                        // cross-JVM contender: keep polling until the shared
                        // budget runs out (fail-loud, never a thrown raw
                        // exception from inside the bounded-wait loop).
                        fileLock = null;
                    }
                    if (fileLock != null) {
                        break;
                    }
                    if (System.currentTimeMillis() >= deadline) {
                        throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                                .param(ARG_ARG_NAME, "manifestLock")
                                .param(ARG_DETAIL, "Timed out after " + manifestLockTimeoutMs
                                        + "ms waiting for the OS-level manifest lock file "
                                        + outputDirPath.resolve(MANIFEST_LOCK_FILE)
                                        + " — a peer JVM writer holds it; check whether that "
                                        + "JVM is alive and dump its lock-holding threads");
                    }
                    Thread.sleep(200L);
                }
                action.run();
            } finally {
                if (fileLock != null) {
                    fileLock.release();
                }
            }
        } finally {
            jvmLock.unlock();
        }
    }

    /** AR-14: manifest critical-section body (checked-exception action). */
    @FunctionalInterface
    private interface FileTwoPhaseCommitAction {
        void run() throws Exception;
    }

    @Override
    public void rollback() throws Exception {
        synchronized (currentBuffer) {
            currentBuffer.clear();
        }
    }

    /**
     * Item 14 (composite-scenario distributed): normalizes restored
     * {@code pendingCommits} VALUES back to {@link FilePendingCommit}. The
     * local-storage JSON checkpoint round-trip turns each value into a
     * {@code LinkedHashMap} (the same degradation the base class already
     * normalizes for the Long keys) — without this conversion every re-commit
     * of a durable-but-uncommitted epoch after recovery failed with
     * "pendingCommits value is not a FilePendingCommit", so the recovered
     * output never converged.
     */
    @Override
    public void setPendingCommits(Map<Long, Object> pending) {
        Map<Long, Object> converted = new LinkedHashMap<>(pending.size());
        for (Map.Entry<?, ?> entry : pending.entrySet()) {
            Object key = entry.getKey();
            Long epochId;
            if (key instanceof Number) {
                epochId = ((Number) key).longValue();
            } else {
                epochId = Long.parseLong(String.valueOf(key));
            }
            Object convertedEntry = toFilePendingCommit(entry.getValue());
            converted.put(epochId, convertedEntry);
            if (convertedEntry instanceof FilePendingCommit) {
                recoveredEntryKeys.add(manifestKey(
                        ((FilePendingCommit) convertedEntry).getSubtaskIndex(), epochId));
            }
        }
        super.setPendingCommits(converted);
    }

    private static Object toFilePendingCommit(Object raw) {
        if (raw == null || raw instanceof FilePendingCommit) {
            return raw;
        }
        if (raw instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) raw;
            FilePendingCommit commit = new FilePendingCommit();
            Object tempPath = m.get("tempPath");
            if (tempPath != null) {
                commit.setTempPath(String.valueOf(tempPath));
            }
            Object recordCount = m.get("recordCount");
            if (recordCount instanceof Number) {
                commit.setRecordCount(((Number) recordCount).intValue());
            }
            Object subtaskIndex = m.get("subtaskIndex");
            if (subtaskIndex instanceof Number) {
                commit.setSubtaskIndex(((Number) subtaskIndex).intValue());
            }
            return commit;
        }
        return raw;
    }

    @Override
    public void abort(long epochId) throws Exception {
        Object raw = getPendingCommits().remove(epochId);
        if (raw instanceof FilePendingCommit) {
            Path tempPath = Paths.get(((FilePendingCommit) raw).getTempPath());
            deleteIfExistsQuiet(tempPath);
        }
    }

    @Override
    public void beginTransaction() throws Exception {
        // No per-epoch transaction resource to initialize (NIO file handles are per-call).
    }

    /**
     * Item 20 (P-REQ-13, D3 file-2pc row): pre-submit connectivity probe —
     * {@code beginTransaction() + rollback()}; construction has already verified and
     * created the output directory (the job's own write target — an idempotent
     * expected object, explicitly exempt from the residue red line). The probe writes
     * no data and no epoch final file (D3-⑥).
     */
    @Override
    public void checkConnection() throws Exception {
        if (outputDirPath == null || !java.nio.file.Files.isDirectory(outputDirPath)) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR)
                    .param(ARG_DETAIL, "Output directory missing after construction: " + outputDir);
        }
        beginTransaction();
        rollback();
    }

    // ---- Manifest management ----

    /**
     * Escapes a manifest VALUE for the Properties.load read path: backslash,
     * newline, CR, tab and a leading space. Keys are internal constants and are
     * not escaped.
     */
    private static String escapeManifestValue(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 0 && c == ' ') {
                sb.append("\\ ");
                continue;
            }
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private Properties loadManifest() throws IOException {
        Properties props = new Properties();
        Path manifestPath = outputDirPath.resolve(MANIFEST_FILE);
        if (Files.exists(manifestPath)) {
            // ST-7: the manifest read entry goes through the IResource abstraction
            // (FileResource over the local file) — same local-file contract.
            try (InputStream in = new FileResource(manifestPath.toString(), manifestPath.toFile()).getInputStream()) {
                props.load(in);
            }
        }
        return props;
    }

    /**
     * Writes the manifest atomically: serialize to a per-subtask temp file
     * ({@code manifest.properties[.sK].tmp}, AR-14 — parallel subtasks never share a
     * tmp name, so interleaved commits cannot hit each other's half-written temp),
     * then {@code Files.move(ATOMIC_MOVE)} to {@code manifest.properties}. The
     * read-modify-write around this call is serialized by {@link #withManifestLock}.
     */
    private void updateManifestAtomically(Properties manifest) throws IOException {
        Path tempManifest = outputDirPath.resolve(
                MANIFEST_FILE + subtaskSuffix(subtaskIndex) + TEMP_SUFFIX);
        Path finalManifest = outputDirPath.resolve(MANIFEST_FILE);
        // Sort keys for deterministic output
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String name : manifest.stringPropertyNames()) {
            sorted.put(name, manifest.getProperty(name));
        }
        // ST-7: the temp-manifest write entry goes through the IResource abstraction
        // (FileResource.getOutputStream = create-or-truncate, same contract as
        // Files.newOutputStream).
        try (OutputStream out = new FileResource(tempManifest.toString(), tempManifest.toFile()).getOutputStream()) {
            // Properties.store is non-deterministic; use sorted manual write instead.
            // Values are escaped because the manifest is read back via
            // Properties.load, which treats '\' as an escape — unescaped Windows
            // paths (C:\out\epoch-1.txt) would silently corrupt on round-trip.
            StringBuilder sb = new StringBuilder();
            sb.append("# file-sink manifest").append(LINE_SEPARATOR);
            for (TreeMap.Entry<String, String> entry : sorted.entrySet()) {
                sb.append(entry.getKey()).append('=')
                        .append(escapeManifestValue(entry.getValue())).append(LINE_SEPARATOR);
            }
            out.write(sb.toString().getBytes(charset()));
        }
        // The manifest is the commit record — flush it to
        // stable storage before publishing the rename, otherwise an OS crash
        // could leave a "committed" manifest whose content was never durable.
        forcePath(tempManifest);
        // ST-7 adjudication: the final publish stays on Files.move(ATOMIC_MOVE +
        // REPLACE_EXISTING) — FileResource.renameTo omits ATOMIC_MOVE and swallows
        // FileAlreadyExistsException; wrapping it would break the atomic manifest
        // update contract.
        Files.move(tempManifest, finalManifest,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Flushes a regular file's content to stable storage
     * (fsync). Closes the channel immediately; metadata-only flush
     * ({@code force(false)}) is sufficient because the payload was written
     * through the same channel-visible file state.
     */
    private static void forcePath(Path path) throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE)) {
            ch.force(false);
        }
    }

    /**
     * Best-effort fsync of a directory (makes renames durable
     * on POSIX). Platforms that cannot open a directory as a channel (e.g.
     * Windows) skip quietly — process-crash safety is unaffected there, and the
     * OS-crash window reverts to the pre-fix behavior instead of failing.
     */
    private static void forceDirectoryQuietly(Path dir) {
        try (FileChannel ch = FileChannel.open(dir, StandardOpenOption.READ)) {
            ch.force(false);
        } catch (Exception e) {
            LOG.debug("Directory fsync unavailable for {} (platform-dependent, best-effort)", dir, e);
        }
    }

    String manifestKey(long epochId) {
        return manifestKey(subtaskIndex, epochId);
    }

    static String manifestKey(int subtaskIndex, long epochId) {
        return epochId + subtaskSuffix(subtaskIndex);
    }

    /**
     * Path suffix that disambiguates parallel subtask copies. Subtask 0 keeps the
     * legacy unsuffixed names so existing single-subtask deployments (and their
     * on-disk manifests) stay compatible.
     */
    static String subtaskSuffix(int subtaskIndex) {
        return subtaskIndex > 0 ? ".s" + subtaskIndex : "";
    }

    /**
     * Returns whether the manifest records the given epoch as committed. Primarily for tests.
     */
    public boolean isEpochCommitted(long epochId) throws IOException {
        Properties manifest = loadManifest();
        return manifest.containsKey(manifestKey(epochId));
    }

    /**
     * Returns the final path for an epoch's output file. Primarily for tests.
     */
    Path finalPath(long epochId) {
        return finalPath(subtaskIndex, epochId);
    }

    static Path finalPath(int subtaskIndex, long epochId, Path outputDirPath) {
        return outputDirPath.resolve("epoch-" + epochId + subtaskSuffix(subtaskIndex) + ".txt");
    }

    final Path finalPath(int subtaskIndex, long epochId) {
        return finalPath(subtaskIndex, epochId, outputDirPath);
    }

    Path tempPath(long epochId) {
        return outputDirPath.resolve(".epoch-" + epochId + subtaskSuffix(subtaskIndex) + TEMP_SUFFIX);
    }

    private void writeLines(Path path, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        // ST-7: the epoch temp-file write entry goes through the IResource abstraction
        // (FileResource.getOutputStream = create-or-truncate, same contract as
        // Files.newOutputStream). The temp→final atomic rename in doCommitLocked stays
        // on Files.move (see the ST-7 adjudication comment there).
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileResource(path.toString(), path.toFile()).getOutputStream(), charset()))) {
            for (String line : lines) {
                writer.write(line);
                writer.write(LINE_SEPARATOR);
            }
        }
    }

    private void deleteIfExistsQuiet(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // best-effort cleanup
        }
    }

    /**
     * Returns an unmodifiable snapshot of the current in-memory buffer. Primarily for tests.
     */
    List<String> getCurrentBufferSnapshot() {
        synchronized (currentBuffer) {
            return Collections.unmodifiableList(new ArrayList<>(currentBuffer));
        }
    }
}
