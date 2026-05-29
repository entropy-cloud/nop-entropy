/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.nop.stream.core.checkpoint.SourceEnumeratorState;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Manages source split discovery and assignment to subtasks.
 *
 * <p>The SourceEnumerator is the single point of control for split management in a
 * distributed streaming job. It:
 * <ul>
 *   <li>Discovers available splits (e.g., Kafka partitions, database shards)</li>
 *   <li>Assigns splits to specific subtasks in a round-robin fashion</li>
 *   <li>Tracks which splits have been assigned, finished, or are pending acknowledgement</li>
 *   <li>Snapshots and restores its state for checkpoint/recovery</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is designed to be used from a single
 * coordinator thread. Concurrent access from multiple threads requires external synchronization.
 */
public class SourceEnumerator {

    /** All splits that have been discovered so far */
    private final Set<String> discoveredSplits;

    /** Splits that have been discovered but not yet assigned to any subtask */
    private final Queue<String> unassignedSplits;

    /** Mapping of splitId → subtaskIndex for currently assigned splits */
    private final Map<String, Integer> assignedSplits;

    /** Splits that have been fully consumed by their assigned subtask */
    private final Set<String> finishedSplits;

    /** Splits that have been assigned but not yet acknowledged by the subtask */
    private final Set<String> pendingAcknowledgements;

    /** Total number of subtasks (parallelism) */
    private final int totalParallelism;

    /** Cursor for discovery, allowing incremental split discovery */
    private Object discoveryCursor;

    /** Index used for round-robin assignment */
    private int nextSubtaskIndex;

    /** All known split metadata (splitId → SourceSplit) */
    private final Map<String, SourceSplit> splitMetadata;

    public SourceEnumerator(int totalParallelism) {
        if (totalParallelism <= 0) {
            throw new StreamException("totalParallelism must be positive, got: " + totalParallelism);
        }
        this.totalParallelism = totalParallelism;
        this.discoveredSplits = new LinkedHashSet<>();
        this.unassignedSplits = new ConcurrentLinkedQueue<>();
        this.assignedSplits = new ConcurrentHashMap<>();
        this.finishedSplits = ConcurrentHashMap.newKeySet();
        this.pendingAcknowledgements = ConcurrentHashMap.newKeySet();
        this.splitMetadata = new ConcurrentHashMap<>();
        this.nextSubtaskIndex = 0;
    }

    // ==================== Discovery ====================

    /**
     * Registers newly discovered splits. Splits that have already been discovered
     * or are already finished are silently ignored.
     *
     * @param splits the splits to register
     */
    public void discoverSplits(List<SourceSplit> splits) {
        if (splits == null) return;
        for (SourceSplit split : splits) {
            String splitId = split.getSplitId();
            if (!discoveredSplits.contains(splitId) && !finishedSplits.contains(splitId)) {
                discoveredSplits.add(splitId);
                unassignedSplits.add(splitId);
                splitMetadata.put(splitId, split);
            }
        }
    }

    /**
     * Returns all currently discovered split IDs.
     */
    public Set<String> getDiscoveredSplits() {
        return Collections.unmodifiableSet(discoveredSplits);
    }

    /**
     * Returns splits that are not yet assigned to any subtask.
     */
    public List<String> getUnassignedSplits() {
        return Collections.unmodifiableList(new ArrayList<>(unassignedSplits));
    }

    // ==================== Assignment ====================

    /**
     * Assigns available splits to a specific subtask using round-robin distribution.
     *
     * <p>If there are fewer unassigned splits than subtasks, only the splits available
     * at the time of calling will be assigned. The method returns the list of split IDs
     * assigned to the given subtask.
     *
     * @param subtaskIndex the index of the subtask to assign splits to
     * @return list of split IDs assigned to this subtask
     */
    public List<String> assignSplits(int subtaskIndex) {
        if (subtaskIndex < 0 || subtaskIndex >= totalParallelism) {
            throw new StreamException(
                    "subtaskIndex must be in [0, " + totalParallelism + "), got: " + subtaskIndex);
        }

        List<String> assigned = new ArrayList<>();
        Iterator<String> it = unassignedSplits.iterator();
        while (it.hasNext()) {
            String splitId = it.next();
            // Only assign to the target subtask in round-robin
            int target = nextSubtaskIndex % totalParallelism;
            if (target == subtaskIndex) {
                it.remove();
                assignedSplits.put(splitId, subtaskIndex);
                pendingAcknowledgements.add(splitId);
                assigned.add(splitId);
                nextSubtaskIndex++;
            } else {
                continue;
            }
        }

        return assigned;
    }

    /**
     * Assigns all unassigned splits in a batch across all subtasks using round-robin.
     *
     * @return map of subtaskIndex → list of assigned split IDs
     */
    public Map<Integer, List<String>> assignAllSplits() {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        while (!unassignedSplits.isEmpty()) {
            String splitId = unassignedSplits.poll();
            if (splitId == null) break;

            int target = nextSubtaskIndex % totalParallelism;
            assignedSplits.put(splitId, target);
            pendingAcknowledgements.add(splitId);
            result.computeIfAbsent(target, k -> new ArrayList<>()).add(splitId);
            nextSubtaskIndex++;
        }
        return result;
    }

    /**
     * Acknowledges that a subtask has started processing a split.
     * Removes the split from pending acknowledgements.
     *
     * @param splitId the split ID to acknowledge
     */
    public void acknowledgeSplit(String splitId) {
        pendingAcknowledgements.remove(splitId);
    }

    /**
     * Marks a split as finished (fully consumed by the assigned subtask).
     * The split is moved from assigned to finished, but its assignment
     * information is retained (getAssignedSubtask still returns the subtask).
     *
     * @param splitId the split ID that has been finished
     */
    public void markSplitFinished(String splitId) {
        if (assignedSplits.containsKey(splitId) || pendingAcknowledgements.contains(splitId)) {
            finishedSplits.add(splitId);
            pendingAcknowledgements.remove(splitId);
            // Note: we do NOT remove from assignedSplits so that getAssignedSubtask still works
        }
    }

    /**
     * Returns the subtask index that a split is assigned to, or -1 if not assigned.
     */
    public int getAssignedSubtask(String splitId) {
        Integer idx = assignedSplits.get(splitId);
        return idx != null ? idx : -1;
    }

    // ==================== State ====================

    /**
     * Snapshots the current state of the enumerator for checkpointing.
     *
     * @return a serializable snapshot of the current state
     */
    public SourceEnumeratorState snapshotState() {
        Map<String, String> assignedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : assignedSplits.entrySet()) {
            assignedMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new SourceEnumeratorState(
                new ArrayList<>(discoveredSplits),
                new ArrayList<>(unassignedSplits),
                assignedMap,
                new LinkedHashSet<>(finishedSplits),
                new LinkedHashSet<>(pendingAcknowledgements),
                discoveryCursor
        );
    }

    /**
     * Restores the enumerator state from a checkpoint snapshot.
     *
     * @param state the state to restore from
     */
    public void restoreState(SourceEnumeratorState state) {
        if (state == null) return;

        discoveredSplits.clear();
        unassignedSplits.clear();
        assignedSplits.clear();
        finishedSplits.clear();
        pendingAcknowledgements.clear();

        if (state.getDiscoveredSplits() != null) {
            discoveredSplits.addAll(state.getDiscoveredSplits());
        }
        if (state.getUnassignedSplits() != null) {
            unassignedSplits.addAll(state.getUnassignedSplits());
        }
        if (state.getAssignedSplits() != null) {
            for (Map.Entry<String, String> entry : state.getAssignedSplits().entrySet()) {
                assignedSplits.put(entry.getKey(), Integer.parseInt(entry.getValue()));
            }
        }
        if (state.getFinishedSplits() != null) {
            finishedSplits.addAll(state.getFinishedSplits());
        }
        if (state.getPendingAcknowledgements() != null) {
            pendingAcknowledgements.addAll(state.getPendingAcknowledgements());
        }
        this.discoveryCursor = state.getDiscoveryCursor();

        // Recalculate nextSubtaskIndex based on assigned splits
        this.nextSubtaskIndex = discoveredSplits.size() - unassignedSplits.size();
    }

    // ==================== Accessors ====================

    public int getTotalParallelism() {
        return totalParallelism;
    }

    public int getAssignedSplitCount() {
        return assignedSplits.size();
    }

    public int getUnassignedSplitCount() {
        return unassignedSplits.size();
    }

    public int getFinishedSplitCount() {
        return finishedSplits.size();
    }

    public Set<String> getFinishedSplits() {
        return Collections.unmodifiableSet(finishedSplits);
    }

    public Map<String, Integer> getAssignedSplits() {
        return Collections.unmodifiableMap(assignedSplits);
    }

    public Object getDiscoveryCursor() {
        return discoveryCursor;
    }

    public void setDiscoveryCursor(Object cursor) {
        this.discoveryCursor = cursor;
    }

    /**
     * Returns true if all discovered splits have been finished.
     */
    public boolean allSplitsFinished() {
        return discoveredSplits.isEmpty() || discoveredSplits.equals(finishedSplits);
    }
}
