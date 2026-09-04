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

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.connector.ConnectivityCheckable;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;
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
     * Item 14 (distributed): stored as a NAME (not a {@link Charset} object) so the
     * sink survives the Java serialization of the deployment descriptor's pipeline
     * spec — {@code Charset} implementations are not serializable. Resolved lazily
     * by {@link #charset()}.
     */
    private final String charsetName;
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
        if (outputDir == null || outputDir.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "outputDir");
        }
        this.outputDir = outputDir;
        this.charsetName = (charset != null ? charset : StandardCharsets.UTF_8).name();
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
        this.subtaskIndex = subtaskIndex;
        this.outputDirPath = Paths.get(outputDir);
        try {
            Files.createDirectories(outputDirPath);
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e)
                    .param(ARG_DETAIL, "Failed to create output directory: " + outputDir);
        }
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
        return new FileTwoPhaseCommitSink<>(outputDir, charset(), subtaskIndex);
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

        // Idempotent guard: manifest already records this epoch → skip (recover-safe re-commit)
        if (manifest.containsKey(manifestEntryKey)) {
            getPendingCommits().remove(checkpointId);
            return;
        }

        if (Files.exists(finalPath)) {
            // Edge case: rename succeeded on a prior attempt but manifest write did not.
            // Repair the manifest (add entry, skip rename) — data is already durable.
            manifest.setProperty(manifestEntryKey, finalPath.toString());
            updateManifestAtomically(manifest);
            getPendingCommits().remove(checkpointId);
            return;
        }

        // Atomic rename: temp → final
        try {
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e)
                    .param(ARG_EPOCH_ID, checkpointId)
                    .param(ARG_DETAIL, "Atomic move failed: " + tempPath + " -> " + finalPath);
        }

        // Atomic manifest update
        manifest.setProperty(manifestEntryKey, finalPath.toString());
        updateManifestAtomically(manifest);

        getPendingCommits().remove(checkpointId);
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
        jvmLock.lock();
        try (FileChannel channel = FileChannel.open(outputDirPath.resolve(MANIFEST_LOCK_FILE),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock fileLock = channel.lock();
            try {
                action.run();
            } finally {
                fileLock.release();
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
            converted.put(epochId, toFilePendingCommit(entry.getValue()));
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

    private Properties loadManifest() throws IOException {
        Properties props = new Properties();
        Path manifestPath = outputDirPath.resolve(MANIFEST_FILE);
        if (Files.exists(manifestPath)) {
            try (InputStream in = Files.newInputStream(manifestPath)) {
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
        try (OutputStream out = Files.newOutputStream(tempManifest)) {
            // Properties.store is non-deterministic; use sorted manual write instead.
            StringBuilder sb = new StringBuilder();
            sb.append("# file-sink manifest").append(LINE_SEPARATOR);
            for (TreeMap.Entry<String, String> entry : sorted.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append(LINE_SEPARATOR);
            }
            out.write(sb.toString().getBytes(charset()));
        }
        Files.move(tempManifest, finalManifest,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
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
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(path), charset()))) {
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
