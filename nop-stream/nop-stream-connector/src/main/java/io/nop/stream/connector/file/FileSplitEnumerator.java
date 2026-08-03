/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;

/**
 * Stage 49 Phase 3: {@link SplitEnumerator} for {@link FileSource}. Scans a directory at
 * deploy/start time and assigns each file as a single {@link FileSplit} to readers in
 * round-robin order across subtasks (Stage 49 D3 — initial split delivery on first reader
 * registration; subsequent readers receive their share via pull request).
 *
 * <p>Enumerator state (checkpoint/restore):
 * <ul>
 *   <li>{@code discoveredFiles} — every file path discovered at start</li>
 *   <li>{@code assignedFiles} — set of files already assigned to a reader</li>
 *   <li>{@code finishedFiles} — set of files reported finished by readers</li>
 *   <li>{@code nextSubtaskIndex} — round-robin cursor for the next assignment</li>
 * </ul>
 *
 * <p>Stage 49 D4: deploy-time discovery only (bounded source). Continuous polling for
 * newly-arriving files is deferred (successor scope: unbounded source plan).
 */
public final class FileSplitEnumerator implements SplitEnumerator<FileSplit, FileSplitEnumeratorState> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(FileSplitEnumerator.class);

    private final String directoryPath;

    /** Set in start(); reused for snapshotState. */
    private transient Set<String> discoveredFiles = ConcurrentHashMap.newKeySet();
    private transient Set<String> assignedFiles = ConcurrentHashMap.newKeySet();
    private transient Set<String> finishedFiles = ConcurrentHashMap.newKeySet();
    private transient Map<String, FileSplit> splitById = new ConcurrentHashMap<>();
    private transient int nextSubtaskIndex;
    private transient SplitEnumeratorContext<FileSplit> context;

    public FileSplitEnumerator(String directoryPath) {
        this.directoryPath = directoryPath;
    }

    @Override
    public void start(SplitEnumeratorContext<FileSplit> context) throws Exception {
        this.context = context;
        if (discoveredFiles.isEmpty()) {
            discoverSplits();
        }
        LOG.info("FileSplitEnumerator started: {} files discovered under {}",
                discoveredFiles.size(), directoryPath);
    }

    private void discoverSplits() throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IOException("Source directory not found or not a directory: " + directoryPath);
        }
        List<Path> files = Files.walk(dir)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
        for (Path file : files) {
            long size = Files.size(file);
            FileSplit split = new FileSplit(file.toString(), size);
            discoveredFiles.add(file.toString());
            splitById.put(file.toString(), split);
        }
    }

    @Override
    public void addReader(int subtaskIndex) {
        // On reader registration, hand out any unassigned splits (round-robin).
        // For bounded sources with static discovery, we proactively push initial splits
        // so readers can start immediately.
        assignAvailableSplitsTo(subtaskIndex);
    }

    @Override
    public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {
        // Pull model: a reader that finished its initial share asks for more.
        assignAvailableSplitsTo(subtaskIndex);
    }

    private void assignAvailableSplitsTo(int subtaskIndex) {
        if (context == null || context.getDeliveryService() == null) {
            return;
        }
        // Stable per-split round-robin: split at discovery-index i is assigned to
        // subtask (i mod totalParallelism). This is independent of registration order so
        // late-registering subtasks still get their fair share without losing splits.
        int parallelism = Math.max(1, context.getTotalParallelism());
        List<String> discoveredOrder = new ArrayList<>(discoveredFiles);
        List<FileSplit> toAssign = new ArrayList<>();
        for (int i = 0; i < discoveredOrder.size(); i++) {
            String filePath = discoveredOrder.get(i);
            if (assignedFiles.contains(filePath) || finishedFiles.contains(filePath)) {
                continue;
            }
            int target = i % parallelism;
            if (target == subtaskIndex) {
                FileSplit split = splitById.get(filePath);
                if (split != null) {
                    toAssign.add(split);
                    assignedFiles.add(filePath);
                }
            }
        }

        if (!toAssign.isEmpty()) {
            context.getDeliveryService().assignSplits(subtaskIndex, toAssign);
            LOG.debug("FileSplitEnumerator assigned {} splits to subtask {}",
                    toAssign.size(), subtaskIndex);
        }
    }

    /** Called by tests / coordinator to record finished splits for restore round-trip. */
    public void markSplitFinished(String filePath) {
        finishedFiles.add(filePath);
    }

    @Override
    public FileSplitEnumeratorState snapshotState(long checkpointId) {
        // Defensive copies of the current bookkeeping
        return new FileSplitEnumeratorState(
                directoryPath,
                new LinkedHashSet<>(discoveredFiles),
                new LinkedHashSet<>(assignedFiles),
                new LinkedHashSet<>(finishedFiles),
                new LinkedHashMap<>(splitById),
                nextSubtaskIndex);
    }

    @Override
    public void restoreState(FileSplitEnumeratorState state) {
        if (state == null) {
            return;
        }
        this.discoveredFiles = ConcurrentHashMap.newKeySet();
        this.discoveredFiles.addAll(state.getDiscoveredFiles());
        this.assignedFiles = ConcurrentHashMap.newKeySet();
        this.assignedFiles.addAll(state.getAssignedFiles());
        this.finishedFiles = ConcurrentHashMap.newKeySet();
        this.finishedFiles.addAll(state.getFinishedFiles());
        this.splitById = new ConcurrentHashMap<>(state.getSplitById());
        this.nextSubtaskIndex = state.getNextSubtaskIndex();
    }

    @Override
    public void close() {
    }

    // ====================== Test accessors ======================

    int getDiscoveredCount() {
        return discoveredFiles.size();
    }

    int getAssignedCount() {
        return assignedFiles.size();
    }

    int getFinishedCount() {
        return finishedFiles.size();
    }

    Set<String> getDiscoveredFiles() {
        return new LinkedHashSet<>(discoveredFiles);
    }
}
