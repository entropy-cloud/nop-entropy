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
import java.util.stream.Stream;

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
    private transient SplitEnumeratorContext<FileSplit> context;

    /** WI5 (plan 2330): default policy — discovery-index round-robin (historical behavior). */
    public static final String POLICY_ROUND_ROBIN = "round-robin";
    /** WI5 (plan 2330): size-aware greedy policy, see the class comment on determinism. */
    public static final String POLICY_SIZE_AWARE = "size-aware";

    private final String assignmentPolicy;

    public FileSplitEnumerator(String directoryPath) {
        this(directoryPath, POLICY_ROUND_ROBIN);
    }

    /**
     * WI5: policy-carrying constructor. Unknown policy = typed error, never a silent
     * round-robin fallback (no-silent-no-op).
     */
    public FileSplitEnumerator(String directoryPath, String assignmentPolicy) {
        if (!isValidPolicy(assignmentPolicy)) {
            throw new io.nop.stream.core.exceptions.StreamException(
                    io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG)
                    .param(io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL,
                            "unknown split assignment policy: " + assignmentPolicy
                                    + " (expected " + POLICY_ROUND_ROBIN + " or " + POLICY_SIZE_AWARE + ")");
        }
        this.directoryPath = directoryPath;
        this.assignmentPolicy = assignmentPolicy;
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

    /** WI5: policy whitelist shared with {@code FileSource} construction validation. */
    public static boolean isValidPolicy(String policy) {
        return POLICY_ROUND_ROBIN.equals(policy) || POLICY_SIZE_AWARE.equals(policy);
    }

    /**
     * WI5 size-aware plan (pure function per plan 2330 ruling): greedy over the
     * CURRENT remaining (unassigned, unfinished) set — size desc, then path asc; each
     * split goes to the reader index with the least accumulated bytes (tie: lowest
     * index). Already-assigned splits are NOT attributed (the state carries no reader
     * ownership); delivery results therefore depend on the event sequence while the
     * function itself stays deterministic for a given remaining set.
     */
    private Map<String, Integer> computeSizeAwarePlan() {
        List<String> remaining = new ArrayList<>();
        for (String filePath : discoveredFiles) {
            if (!assignedFiles.contains(filePath) && !finishedFiles.contains(filePath)) {
                remaining.add(filePath);
            }
        }
        remaining.sort((a, b) -> {
            long sa = splitById.get(a) == null ? 0L : splitById.get(a).getEndOffset();
            long sb = splitById.get(b) == null ? 0L : splitById.get(b).getEndOffset();
            if (sa != sb) {
                return sa > sb ? -1 : 1;
            }
            return a.compareTo(b);
        });
        int parallelism = Math.max(1, context == null ? 1 : context.getTotalParallelism());
        long[] accumulated = new long[parallelism];
        Map<String, Integer> plan = new java.util.LinkedHashMap<>();
        for (String filePath : remaining) {
            int target = 0;
            for (int i = 1; i < parallelism; i++) {
                if (accumulated[i] < accumulated[target]) {
                    target = i;
                }
            }
            accumulated[target] += splitById.get(filePath) == null ? 0L : splitById.get(filePath).getEndOffset();
            plan.put(filePath, target);
        }
        return plan;
    }

    private void discoverSplits() throws IOException {
        Path dir = Paths.get(directoryPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IOException("Source directory not found or not a directory: " + directoryPath);
        }
        // CRA 8.44 R4-04 W-09 (C6-011; adjudication C.4): the walk Stream is TWR-owned —
        // a mid-walk failure (unreadable subdirectory → UncheckedIOException) must still
        // execute the walker's registered onClose; UnixDirectoryStream has no
        // finalizer/Cleaner, so the bare shape leaked one directory fd per failed call
        // until process exit. Same discipline as the SnapshotStrategy (:162) walkers.
        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
        }
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
        if (POLICY_SIZE_AWARE.equals(assignmentPolicy)) {
            // WI5: deterministic greedy over the current remaining set (see
            // computeSizeAwarePlan). Only the calling subtask's share is delivered and
            // marked; later callers re-plan over what remains.
            Map<String, Integer> plan = computeSizeAwarePlan();
            List<FileSplit> toAssign = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : plan.entrySet()) {
                if (entry.getValue() != subtaskIndex) {
                    continue;
                }
                FileSplit split = splitById.get(entry.getKey());
                if (split != null) {
                    toAssign.add(split);
                    assignedFiles.add(entry.getKey());
                }
            }
            if (!toAssign.isEmpty()) {
                context.getDeliveryService().assignSplits(subtaskIndex, toAssign);
                LOG.debug("FileSplitEnumerator size-aware assigned {} splits to subtask {}",
                        toAssign.size(), subtaskIndex);
            }
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
        // The constant 0 keeps the VERSION-1 wire format of
        // FileSplitEnumeratorStateSerializer byte-compatible: its second line was
        // reserved for the retired round-robin cursor, which never participated in
        // assignment (stable i % parallelism). The deserializer still parses and
        // ignores that line so old checkpoints keep restoring unchanged.
        return new FileSplitEnumeratorState(
                directoryPath,
                new LinkedHashSet<>(discoveredFiles),
                new LinkedHashSet<>(assignedFiles),
                new LinkedHashSet<>(finishedFiles),
                new LinkedHashMap<>(splitById),
                0);
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
        // state.getNextSubtaskIndex() is deliberately not restored: the legacy cursor
        // is dead state (see snapshotState) and old checkpoints remain readable.
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
