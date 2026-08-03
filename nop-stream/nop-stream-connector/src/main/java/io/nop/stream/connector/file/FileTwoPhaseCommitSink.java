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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
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
 * <p>See {@code ai-dev/design/nop-stream/connector-design.md} §5.5.
 *
 * @param <IN> the type of input records (rendered via {@code toString()})
 */
public class FileTwoPhaseCommitSink<IN> extends TwoPhaseCommitSinkFunction<IN> {

    private static final long serialVersionUID = 1L;

    private static final String TEMP_SUFFIX = ".tmp";
    private static final String MANIFEST_FILE = "manifest.properties";
    private static final String MANIFEST_TEMP = "manifest.properties.tmp";
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final String outputDir;
    private final Charset charset;
    private final transient Path outputDirPath;

    // In-memory buffer for the current epoch (not yet in pendingCommits)
    private final transient List<String> currentBuffer = new ArrayList<>();

    /**
     * Constructs a file sink writing text lines to {@code outputDir}.
     *
     * @param outputDir the output directory (created if absent)
     * @param charset   the charset for text encoding (null defaults to UTF-8)
     */
    public FileTwoPhaseCommitSink(String outputDir, Charset charset) {
        if (outputDir == null || outputDir.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "outputDir");
        }
        this.outputDir = outputDir;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
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
                getPendingCommits().put(epochId, new FilePendingCommit(tempPath.toString(), count));
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
        Path tempPath = Paths.get(pending.getTempPath());
        Path finalPath = finalPath(checkpointId);
        Properties manifest = loadManifest();

        // Idempotent guard: manifest already records this epoch → skip (recover-safe re-commit)
        if (manifest.containsKey(manifestKey(checkpointId))) {
            getPendingCommits().remove(checkpointId);
            return;
        }

        if (Files.exists(finalPath)) {
            // Edge case: rename succeeded on a prior attempt but manifest write did not.
            // Repair the manifest (add entry, skip rename) — data is already durable.
            manifest.setProperty(manifestKey(checkpointId), finalPath.toString());
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
        manifest.setProperty(manifestKey(checkpointId), finalPath.toString());
        updateManifestAtomically(manifest);

        getPendingCommits().remove(checkpointId);
    }

    @Override
    public void rollback() throws Exception {
        synchronized (currentBuffer) {
            currentBuffer.clear();
        }
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
     * Writes the manifest atomically: serialize to {@code manifest.properties.tmp}, then
     * {@code Files.move(ATOMIC_MOVE)} to {@code manifest.properties}.
     */
    private void updateManifestAtomically(Properties manifest) throws IOException {
        Path tempManifest = outputDirPath.resolve(MANIFEST_TEMP);
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
            out.write(sb.toString().getBytes(charset));
        }
        Files.move(tempManifest, finalManifest,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    String manifestKey(long epochId) {
        return String.valueOf(epochId);
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
        return outputDirPath.resolve("epoch-" + epochId + ".txt");
    }

    Path tempPath(long epochId) {
        return outputDirPath.resolve(".epoch-" + epochId + TEMP_SUFFIX);
    }

    private void writeLines(Path path, List<String> lines) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(path), charset))) {
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
