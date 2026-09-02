/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.source.CheckpointedSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_STATE_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * S2 bounded file source adapter (composite-scenario-design.md §3.2.2): a
 * {@link SourceFunction} that streams the lines of every file in a directory in
 * stable (sorted) order, checkpointing a per-file byte cursor so a recovered run
 * resumes from the checkpointed position — the same per-split cursor semantics as
 * the FLIP-27 {@code FileSource}/{@code FileSourceReader} pair, exposed in the
 * SourceFunction shape the flow DSL source declaration consumes.
 *
 * <p>Checkpoint contract (operator state under {@value #CURSORS_KEY}):
 * a map of {@code absolute file path -> next byte offset to read}. Because the
 * runtime shares one source-function instance across the subtasks of the source
 * vertex, every subtask snapshots the SAME full cursor map; on restore a subtask
 * only applies non-empty restored state, so whichever subtask wins the read race
 * resumes from the checkpointed cursors (deterministic under parallelism rescale).
 */
public class DirectoryFileSourceFunction implements SourceFunction<String>,
        CheckpointedSourceFunction<String> {

    private static final long serialVersionUID = 1L;

    public static final String CURSORS_KEY = "file-cursors";

    private final String directoryPath;
    private final long lineDelayMs;
    /**
     * Park time after all files are consumed before {@link #run(SourceContext)}
     * returns: gives the engine's periodic checkpoint scheduler time to durably
     * commit the rows fired by the end-of-input watermarks (bounded-run
     * determinism for scenario tests).
     */
    private final long finishLingerMs;

    /**
     * Shared across subtask copies (the runtime shares the source UDF). Guarded by
     * its own monitor because snapshot/restore run on task threads while the reader
     * thread advances cursors.
     */
    private final TreeMap<String, Long> cursors = new TreeMap<>();

    /**
     * Shared across subtask copies (the runtime shares the source UDF).
     *
     * <p>Item 14 (distributed): {@code transient} + re-initialized in
     * {@code readObject} — this source UDF crosses JVM boundaries inside the
     * deployment descriptor's JobGraph (Java serialization), and a deserialized
     * instance with a null guard would NPE in {@code run()}. Non-final because
     * readObject cannot assign final fields.
     */
    private transient java.util.concurrent.atomic.AtomicBoolean runEntered =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private volatile boolean cancelled;

    public DirectoryFileSourceFunction(String directoryPath) {
        this(directoryPath, 0L, 0L);
    }

    public DirectoryFileSourceFunction(String directoryPath, long lineDelayMs, long finishLingerMs) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            throw new StreamException(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG)
                    .param("argName", "directoryPath");
        }
        this.directoryPath = directoryPath;
        this.lineDelayMs = lineDelayMs;
        this.finishLingerMs = finishLingerMs;
    }

    private void readObject(java.io.ObjectInputStream in)
            throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Re-initialize the transient concurrency guard after cross-JVM
        // deserialization (Rule #24 — the un-initialized path must not NPE).
        this.runEntered = new java.util.concurrent.atomic.AtomicBoolean(false);
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    @Override
    public void run(SourceContext<String> ctx) throws Exception {
        // Parallel-subtask guard: the shared UDF must be read by exactly one subtask,
        // otherwise every subtask would re-emit the whole directory (duplicates).
        if (!runEntered.compareAndSet(false, true)) {
            return;
        }
        try {
            for (File file : discoverFiles()) {
                if (cancelled) {
                    return;
                }
                emitRemaining(file, ctx);
            }
            if (finishLingerMs > 0 && !cancelled) {
                Thread.sleep(finishLingerMs);
            }
        } finally {
            runEntered.set(false);
        }
    }

    private List<File> discoverFiles() {
        File dir = new File(directoryPath);
        File[] files = dir.listFiles(File::isFile);
        if (files == null) {
            // Bounded source over a missing directory is a fixture error, not an
            // empty stream: fail fast instead of silently completing.
            throw new IllegalStateException("Transaction log directory does not exist: " + directoryPath);
        }
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
        return sorted;
    }

    /**
     * Reads {@code file} from the checkpointed cursor and emits each line through
     * {@code ctx}, advancing the per-file byte cursor after every line terminator
     * (LF, CRLF and lone CR are all recognized; the fixture line format is ASCII —
     * one byte per character, matching the {@code userId,amount,eventTime} format).
     */
    private void emitRemaining(File file, SourceContext<String> ctx) throws Exception {
        long cursor = currentCursor(file);
        if (cursor >= file.length()) {
            return;
        }

        byte[] buffer = new byte[8192];
        StringBuilder line = new StringBuilder();
        try (InputStream in = new FileInputStream(file)) {
            long skipped = in.skip(cursor);
            if (skipped != cursor) {
                throw new IOException("Failed to seek to cursor " + cursor + " of " + file);
            }
            long offset = cursor;
            boolean lineHasContent = false;
            boolean pendingCr = false;
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled) {
                    return;
                }
                for (int i = 0; i < read; i++) {
                    if (cancelled) {
                        return;
                    }
                    byte b = buffer[i];
                    if (pendingCr) {
                        // the previous '\r' already ended the line and advanced the cursor
                        pendingCr = false;
                        if (b == '\n') {
                            offset++;
                            advanceCursor(file, offset);
                            continue;
                        }
                        // not CRLF: fall through and treat this byte normally
                    }
                    offset++;
                    if (b == '\n' || b == '\r') {
                        emitLine(line, lineHasContent, ctx);
                        lineHasContent = false;
                        advanceCursor(file, offset);
                        pendingCr = b == '\r';
                    } else {
                        line.append((char) b);
                        lineHasContent = true;
                    }
                }
            }
            if (lineHasContent) {
                // final line without terminator
                emitLine(line, true, ctx);
                advanceCursor(file, offset);
            }
        }
    }

    private void emitLine(StringBuilder line, boolean hasContent, SourceContext<String> ctx)
            throws InterruptedException {
        if (hasContent) {
            ctx.collect(line.toString());
            if (lineDelayMs > 0) {
                Thread.sleep(lineDelayMs);
            }
        }
        line.setLength(0);
    }

    private long currentCursor(File file) {
        synchronized (cursors) {
            return cursors.getOrDefault(file.getAbsolutePath(), 0L);
        }
    }

    private void advanceCursor(File file, long offset) {
        synchronized (cursors) {
            cursors.put(file.getAbsolutePath(), offset);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    /**
     * The source is replayable: per-file byte cursors are checkpointed into
     * operator state and a recovered run resumes from the checkpointed position,
     * so {@code STRICT_EXACTLY_ONCE} pipelines may use it.
     */
    @Override
    public io.nop.stream.core.common.functions.source.SourceConsistencyCapability getSourceConsistency() {
        return io.nop.stream.core.common.functions.source.SourceConsistencyCapability.REPLAYABLE;
    }

    // ---- CheckpointedSourceFunction ----

    @Override
    public OperatorSnapshotResult snapshotState(long checkpointId) {
        OperatorSnapshotResult result = new OperatorSnapshotResult();
        result.setCheckpointId(checkpointId);
        TreeMap<String, Long> snapshot;
        synchronized (cursors) {
            snapshot = new TreeMap<>(cursors);
        }
        result.putOperatorState(CURSORS_KEY, snapshot);
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initializeState(TaskStateSnapshot state) {
        if (state == null) {
            return;
        }
        Object raw = state.getOperatorState(CURSORS_KEY);
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map)) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_STATE_NAME, CURSORS_KEY)
                    .param(ARG_DETAIL, "file cursor state is not a Map: " + raw.getClass().getName());
        }
        Map<String, Long> restored = (Map<String, Long>) raw;
        if (restored.isEmpty()) {
            // A subtask whose snapshot carried no cursors must NOT wipe cursors a
            // sibling subtask already restored into the shared map (a scale-up
            // subtask restores empty state).
            return;
        }
        synchronized (cursors) {
            cursors.clear();
            putAllCursors(restored);
        }
    }

    private void putAllCursors(Map<String, Long> restored) {
        for (Map.Entry<String, ?> entry : restored.entrySet()) {
            Object value = entry.getValue();
            long offset;
            if (value instanceof Number) {
                offset = ((Number) value).longValue();
            } else {
                offset = Long.parseLong(String.valueOf(value));
            }
            cursors.put(String.valueOf(entry.getKey()), offset);
        }
    }
}
