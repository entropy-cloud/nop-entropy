/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.coordinator.LocalSourceCoordinator;

/**
 * Stage 49 Phase 3: {@link SourceReader} for {@link FileSplit}. Reads a text file line by
 * line within the split's byte range, emitting each line as a {@code String} record.
 *
 * <p>Multiple splits may be assigned to one reader (parallelism < number of files). The
 * reader processes them in FIFO order; per-split cursor is tracked on the {@link FileSplit}
 * instance itself, which is what gets snapshot/restored through operator state.
 *
 * <p>The reader reports each split as finished once its cursor reaches the end offset, and
 * then requests more splits via the {@link io.nop.stream.core.source.SplitAssignmentProxy}
 * (pull model, Stage 49 D3/D4). When no more splits are available and all held splits are
 * finished, {@link #pollNext()} keeps returning empty (the operator main loop will exit on
 * its own once the bounded source signals exhaustion — for v1 we rely on the enumerator
 * marking all splits finished before signaling end-of-input via the empty-poll contract).
 */
public final class FileSourceReader implements SourceReader<String, FileSplit> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(FileSourceReader.class);

    private final SourceReaderContext context;
    private final Deque<FileSplit> assignedSplits = new ArrayDeque<>();
    private final List<FileSplit> finishedSplits = new ArrayList<>();

    /** Currently-open split (lazy-open on first poll). */
    private transient FileSplit activeSplit;
    private transient BufferedReader activeReader;
    private transient long activeBytesConsumed;

    /** Whether the reader has ever received at least one split (avoids premature finish). */
    private transient boolean everAssigned;

    /** Whether the reader has asked the enumerator for more splits (avoid re-request storms). */
    private transient boolean requestedMoreSplits;

    public FileSourceReader(SourceReaderContext context) {
        this.context = context;
    }

    @Override
    public void start() {
        requestMoreSplitsIfNeeded();
    }

    @Override
    public void addSplits(List<FileSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        synchronized (this) {
            assignedSplits.addAll(splits);
            everAssigned = true;
            requestedMoreSplits = false;
        }
        LOG.debug("FileSourceReader subtask={} assigned {} splits (total pending={})",
                context == null ? "?" : context.getSubtaskIndex(), splits.size(), assignedSplits.size());
    }

    @Override
    public boolean isFinished() {
        synchronized (this) {
            boolean consumedAllAssigned = everAssigned
                    && activeSplit == null
                    && assignedSplits.isEmpty();
            // Empty-source early-exit: if the reader has been started and asked the
            // coordinator for splits, and the coordinator confirms the enumerator is
            // exhausted (no splits were ever discovered for this source), terminate.
            // Without this, an empty-directory source would loop forever.
            if (!consumedAllAssigned && !everAssigned && context != null
                    && context.getAssignmentProxy() instanceof LocalSourceCoordinator.ReaderChannel) {
                LocalSourceCoordinator.ReaderChannel<?> channel =
                        (LocalSourceCoordinator.ReaderChannel<?>) context.getAssignmentProxy();
                if (requestedMoreSplits && channel.isEnumeratorExhausted()) {
                    return true;
                }
            }
            return consumedAllAssigned;
        }
    }

    @Override
    public Optional<String> pollNext() throws Exception {
        for (;;) {
            String line;
            synchronized (this) {
                if (activeReader == null) {
                    FileSplit next = assignedSplits.poll();
                    if (next == null) {
                        // No active split and no pending splits. Request more once; if
                        // still none, the operator main loop exits on the empty-poll signal.
                        requestMoreSplitsIfNeeded();
                        return Optional.empty();
                    }
                    openSplit(next);
                }
                line = activeReader.readLine();
            }

            if (line != null) {
                activeBytesConsumed += line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline
                // Update the cursor on the active split so checkpoint reflects progress
                if (activeSplit != null) {
                    long newOffset = activeSplit.getStartOffset() + activeBytesConsumed;
                    activeSplit = activeSplit.withCurrentOffset(newOffset);
                }
                return Optional.of(line);
            }

            // Reached EOF on the active reader
            synchronized (this) {
                closeActiveReader();
                if (activeSplit != null) {
                    finishedSplits.add(activeSplit.withCurrentOffset(activeSplit.getEndOffset()));
                    reportFinishedToCoordinator(activeSplit.splitId());
                    activeSplit = null;
                }
                // Loop back to open the next split (if any)
            }
        }
    }

    private void openSplit(FileSplit split) throws IOException {
        Path path = Paths.get(split.getFilePath());
        if (!Files.exists(path)) {
            throw new IOException("File not found for split: " + split);
        }
        FileInputStream fis = new FileInputStream(path.toFile());
        // Seek to the cursor (for restored splits, resume from checkpointed offset)
        long skip = split.getCurrentOffset();
        while (skip > 0) {
            long skipped = fis.skip(skip);
            if (skipped <= 0) break;
            skip -= skipped;
        }
        activeReader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        activeSplit = split;
        activeBytesConsumed = 0;
        LOG.debug("FileSourceReader opened split {} at offset {}",
                split.getFilePath(), split.getCurrentOffset());
    }

    private void closeActiveReader() {
        if (activeReader != null) {
            try {
                activeReader.close();
            } catch (IOException e) {
                LOG.warn("Failed to close reader for split {}", activeSplit, e);
            }
            activeReader = null;
        }
    }

    private void requestMoreSplitsIfNeeded() {
        if (requestedMoreSplits) {
            return;
        }
        if (context == null || context.getAssignmentProxy() == null) {
            return;
        }
        if (assignedSplits.isEmpty() && activeSplit == null) {
            context.getAssignmentProxy().requestSplits(
                    context.getSubtaskIndex(), Optional.empty());
            requestedMoreSplits = true;
        }
    }

    private void reportFinishedToCoordinator(String splitId) {
        if (context == null || context.getAssignmentProxy() == null) {
            return;
        }
        List<String> finished = new ArrayList<>();
        finished.add(splitId);
        context.getAssignmentProxy().reportFinishedSplits(context.getSubtaskIndex(), finished);
    }

    @Override
    public List<FileSplit> snapshotState(long checkpointId) {
        synchronized (this) {
            List<FileSplit> snapshot = new ArrayList<>();
            // Active split (with current cursor) + all pending assigned splits.
            if (activeSplit != null) {
                snapshot.add(activeSplit);
            }
            snapshot.addAll(assignedSplits);
            return snapshot;
        }
    }

    @Override
    public void restoreState(List<FileSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return;
        }
        synchronized (this) {
            closeActiveReader();
            activeSplit = null;
            assignedSplits.clear();
            finishedSplits.clear();
            assignedSplits.addAll(splits);
        }
        LOG.debug("FileSourceReader restored {} splits", splits.size());
    }

    @Override
    public void notifyCheckpointComplete(long checkpointId) {
        // v1: no external-side acks needed for file reads (file offset is naturally idempotent).
    }

    @Override
    public void close() {
        synchronized (this) {
            closeActiveReader();
        }
    }

    // ====================== Test accessors ======================

    int getPendingSplitCount() {
        synchronized (this) {
            return assignedSplits.size();
        }
    }

    int getFinishedSplitCount() {
        synchronized (this) {
            return finishedSplits.size();
        }
    }

    /** Test helper: returns a defensive copy of currently-held split ids (active + pending). */
    List<String> getHeldSplitIds() {
        synchronized (this) {
            List<String> ids = new ArrayList<>();
            if (activeSplit != null) ids.add(activeSplit.splitId());
            for (FileSplit s : assignedSplits) ids.add(s.splitId());
            return ids;
        }
    }
}
