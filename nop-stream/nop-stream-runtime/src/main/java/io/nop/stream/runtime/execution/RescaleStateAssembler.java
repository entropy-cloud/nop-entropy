/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.CheckpointPlan;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.OperatorStateMapping;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.checkpoint.participant.CheckpointParticipant;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.KeyGroupRangeRestoreFilter;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.AbstractUdfStreamOperator;
import io.nop.stream.core.operators.StreamOperator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CHECKPOINT_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CHECKPOINT_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_CURRENT_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EPOCH_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_MISSING_VERTEX_IDS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_NEW_PARALLELISM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_OLD_PARALLELISM;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TASK_INDEX;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TASK_LOCATION;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_VERTEX_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL;

/**
 * Rescale/restore state-assembly collaborator of
 * {@link GraphModelCheckpointExecutor}: the static pure-function family that
 * guards rescale restores (channel-state fail-fast, reverse-vertex
 * differential), rebuilds rescaled task states (keyed-state merge + KeyGroup
 * filtering), materializes key-group ownership, and restores operator state
 * from durable snapshots. All members are static; the host class keeps
 * package-private static delegates so the external call sites
 * ({@code SupervisionLoop}, focused tests) keep their signatures.
 */
class RescaleStateAssembler {

    // Logs under the GraphModelCheckpointExecutor logger name — restore
    // diagnostics keep their original logger.
    private static final Logger LOG = LoggerFactory.getLogger(GraphModelCheckpointExecutor.class);

/**
 * Unaligned checkpoint + rescale interaction: fails fast when a
 * rescale restore would have to redistribute channel state (unaligned
 * checkpoint in-flight data) across a new parallelism. Channel state carries
 * per-channel records with no cross-parallelism redistribution metadata
 * (no {@code InflightDataRescalingDescriptor} in the first version), so
 * silently dropping it — which the prior {@code instanceof TaskEpochSnapshot}
 * guard in {@code restoreChannelStateIfPresent} did, because
 * {@code buildRescaledTaskState} produces a plain {@code TaskStateSnapshot} —
 * breaks exactly-once. See {@code checkpoint-design.md} §2.11.8 D1/D2.
 *
 * <p>Package-private so the focused unit test can exercise the check directly
 * (same pattern as {@link #validateReverseVertexDifferential}).
 *
 * @param vertexId        the rescaling vertex
 * @param oldSubtasks      the checkpoint's old subtask locations for this vertex
 * @param newParallelism   the new parallelism
 * @param oldParallelism   the old parallelism
 * @param stateLookup      lookup over the checkpoint's task states
 * @throws StreamException ({@code ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED})
 *         if any old subtask snapshot carries a non-empty {@link ChannelState}
 */
static void assertNoChannelStateOnRescale(
        String vertexId, List<TaskLocation> oldSubtasks,
        int newParallelism, int oldParallelism, GraphModelCheckpointExecutor.TaskStateLookup stateLookup) throws Exception {
    for (TaskLocation oldLoc : oldSubtasks) {
        TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
        if (!(oldState instanceof TaskEpochSnapshot)) {
            continue;
        }
        ChannelState cs = ((TaskEpochSnapshot) oldState).getChannelState();
        if (cs != null && !cs.isEmpty()) {
            throw new StreamException(ERR_STREAM_CHANNEL_STATE_RESCALE_UNSUPPORTED)
                    .param(ARG_VERTEX_ID, vertexId)
                    .param(ARG_OLD_PARALLELISM, oldParallelism)
                    .param(ARG_NEW_PARALLELISM, newParallelism);
        }
    }
}

/**
 * Build the rescaled TaskStateSnapshot for a new subtask by
 * merging keyed state from <em>all</em> old subtasks of the vertex (the new
 * subtask's KeyGroupRange may intersect several old subtask ranges) and
 * filtering the merged entries to those owned by {@code newRange}. Operator
 * (non-keyed) state is taken 1:1 from the old subtask at the same index
 * when it exists, and left empty for subtasks added by a scale-up (operator
 * state rescale redistribution is out of scope).
 */
@SuppressWarnings("unchecked")
static TaskStateSnapshot buildRescaledTaskState(
        String vertexId, int taskIndex, KeyGroupRange newRange,
        List<TaskLocation> oldSubtasks, int newParallelism, int oldParallelism,
        int maxParallelism, GraphModelCheckpointExecutor.TaskStateLookup stateLookup, CheckpointPlan checkpointPlan) throws Exception {

    TaskLocation newLoc = new TaskLocation(checkpointPlan.getJobId(), checkpointPlan.getPipelineId(), vertexId, taskIndex);
    TaskStateSnapshot merged = new TaskStateSnapshot(newLoc, -1);

    // Operator (non-keyed) state: 1:1 by index where an old subtask exists.
    if (taskIndex < oldParallelism) {
        TaskLocation oldLoc = oldSubtasks.get(taskIndex);
        TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
        if (oldState != null && oldState.getOperatorStates() != null) {
            for (Map.Entry<String, Object> e : oldState.getOperatorStates().entrySet()) {
                merged.putOperatorState(e.getKey(), e.getValue());
            }
        }
    }

    // Keyed state: union of all old subtasks' keyed snapshots, filtered by newRange.
    // Collect each keyed storage key (e.g. "operator-3-keyed") and merge its entries.
    Map<String, List<Map<String, Object>>> mergedKeyedByName = new LinkedHashMap<>();
    for (TaskLocation oldLoc : oldSubtasks) {
        TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
        if (oldState == null || oldState.getKeyedStates() == null) continue;
        for (Map.Entry<String, Object> ke : oldState.getKeyedStates().entrySet()) {
            Map<String, Object> dataMap = toStateDataMap(ke.getValue());
            if (dataMap == null) continue;
            Object statesObj = dataMap.get("states");
            if (!(statesObj instanceof Map)) continue;
            Map<String, Object> statesMap = (Map<String, Object>) statesObj;
            List<Map<String, Object>> bucket = mergedKeyedByName
                    .computeIfAbsent(ke.getKey(), k -> new ArrayList<>());
            bucket.add(statesMap);
        }
    }

    for (Map.Entry<String, List<Map<String, Object>>> entry : mergedKeyedByName.entrySet()) {
        Map<String, Object> mergedStates = mergeAndFilterKeyedStates(entry.getValue(), newRange, maxParallelism);
        if (mergedStates.isEmpty()) continue;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("states", mergedStates);
        merged.putKeyedState(entry.getKey(), new StateSnapshot(data));
    }

    return merged;
}

/**
 * Merge multiple old subtasks' {@code states} sub-maps (per keyed state name)
 * and keep only the entries whose raw key is owned by {@code range}. Entries
 * are appended in old-subtask order; the result preserves each state's info
 * metadata (stateType/valueType/schema...) taken from the first contributor.
 */
@SuppressWarnings("unchecked")
private static Map<String, Object> mergeAndFilterKeyedStates(List<Map<String, Object>> sources,
                                                             KeyGroupRange range, int maxParallelism) {
    Map<String, Object> result = new LinkedHashMap<>();
    // stateName -> merged info map (entries list grows across contributors)
    Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
    for (Map<String, Object> src : sources) {
        Map<String, Object> filtered = KeyGroupRangeRestoreFilter.filterKeyedStates(src, range, maxParallelism);
        for (Map.Entry<String, Object> e : filtered.entrySet()) {
            Map<String, Object> info = (Map<String, Object>) e.getValue();
            Map<String, Object> acc = byName.get(e.getKey());
            if (acc == null) {
                byName.put(e.getKey(), new LinkedHashMap<>(info));
            } else {
                Object entries = info.get("entries");
                if (entries instanceof List) {
                    Object accEntries = acc.computeIfAbsent("entries", k -> new ArrayList<>());
                    if (accEntries instanceof List) {
                        ((List<Object>) accEntries).addAll((List<?>) entries);
                    }
                }
            }
        }
    }
    result.putAll(byName);
    return result;
}

@SuppressWarnings("unchecked")
private static Map<String, Object> toStateDataMap(Object keyedValue) {
    if (keyedValue instanceof StateSnapshot) {
        return ((StateSnapshot) keyedValue).getStateData();
    }
    if (keyedValue instanceof Map) {
        return (Map<String, Object>) keyedValue;
    }
    return null;
}

/**
 * Materialize the key-group ownership of every keyed subtask into
 * a {@link TaskEpochSnapshot} so the production checkpoint path records the
 * KeyGroupRange each subtask owned (the {@code shards} list was never
 * populated in production). The stamped ownership is persisted by
 * {@code CheckpointSerDe} and re-read on restore. Mutates the checkpoint's
 * task-state map in place (it is a mutable {@code HashMap}).
 *
 * <p>Per-vertex {@code parallelism} is derived from the number of subtask
 * locations recorded for that vertex; {@code maxParallelism} is resolved
 * from the execution plan's keyed backends (job-global constant).
 */
static void materializeKeyGroupOwnership(CompletedCheckpoint checkpoint, GraphExecutionPlan execPlan) {
    if (checkpoint == null || checkpoint.getTaskStates() == null || checkpoint.getTaskStates().isEmpty()) {
        return;
    }
    int maxParallelism = GraphModelCheckpointExecutor.resolveMaxParallelism(execPlan, null);
    // Count subtasks per vertex to derive each vertex's parallelism.
    Map<String, Integer> parallelismByVertex = new HashMap<>();
    for (TaskLocation loc : checkpoint.getTaskStates().keySet()) {
        parallelismByVertex.merge(loc.getVertexId(), 1, Integer::sum);
    }
    Map<TaskLocation, TaskStateSnapshot> taskStates = checkpoint.getTaskStates();
    for (Map.Entry<TaskLocation, TaskStateSnapshot> entry : taskStates.entrySet()) {
        TaskLocation loc = entry.getKey();
        int parallelism = parallelismByVertex.getOrDefault(loc.getVertexId(), 1);
        KeyGroupRange range = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                maxParallelism, parallelism, loc.getTaskIndex());
        TaskEpochSnapshot epoch = TaskEpochSnapshot.fromTaskStateSnapshot(entry.getValue());
        epoch.setKeyGroupOwnership(parallelism, maxParallelism, range);
        entry.setValue(epoch);
    }
}

/**
 * Enforce reverse-direction savepoint/checkpoint vertex differential.
 * Computes the set of stateful vertices (vertexId) referenced by the
 * checkpoint and rejects restore if any of them are absent from the current
 * execution plan — i.e. a stateful vertex was deleted. Aligns with
 * {@code checkpoint-design.md} §8.6 "delete stateful vertex = default
 * reject". Forward direction (current vertex not in checkpoint) is
 * rejected by {@code stateLookup.lookup} in the caller — also hardened
 * here as an explicit forward-differential pre-check so the reject fires
 * independent of invokable installation state.
 *
 * <p>Vertex-level granularity only — operatorId-level differential and the
 * nuanced state-aware §8.6 classification (distinguish stateful vs
 * stateless new vertex, initial-state fallback) are deferred to a
 * roadmap successor.
 */
static void validateReverseVertexDifferential(
        GraphExecutionPlan execPlan,
        CheckpointPlan checkpointPlan,
        Set<TaskLocation> checkpointLocations) {
    if (checkpointLocations == null || checkpointLocations.isEmpty()) {
        return;
    }

    // Current graph's vertex set (only vertices present in the execution
    // plan with installed subtasks count — others are not state-bearing).
    Set<String> currentVertexIds = new TreeSet<>();
    for (String vertexId : execPlan.getSortedVertexIds()) {
        if (!execPlan.getSubtasks(vertexId).isEmpty()) {
            currentVertexIds.add(vertexId);
        }
    }

    // Checkpoint's vertex set (extracted from TaskLocation.vertexId).
    Set<String> checkpointVertexIds = new TreeSet<>();
    for (TaskLocation loc : checkpointLocations) {
        if (loc != null && loc.getVertexId() != null) {
            checkpointVertexIds.add(loc.getVertexId());
        }
    }

    // Forward differential: current vertices absent from checkpoint.
    // Pre-check so the reject fires independent of invokable
    // installation state. Removing this check would lose the contract
    // that a new stateful vertex is rejected on restore.
    Set<String> forwardMissing = new TreeSet<>(currentVertexIds);
    forwardMissing.removeAll(checkpointVertexIds);
    if (!forwardMissing.isEmpty()) {
        throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                .param(ARG_DETAIL, "Current graph contains stateful vertices absent from checkpoint "
                        + "(likely new): missing=" + forwardMissing
                        + "; current-vertices=" + currentVertexIds
                        + "; checkpoint-vertices=" + checkpointVertexIds);
    }

    // Reverse differential: checkpoint vertices not in current graph.
    Set<String> missing = new TreeSet<>(checkpointVertexIds);
    missing.removeAll(currentVertexIds);
    if (!missing.isEmpty()) {
        throw new StreamException(ERR_STREAM_SAVEPOINT_VERTEX_DIFFERENTIAL)
                .param(ARG_MISSING_VERTEX_IDS, missing)
                .param(ARG_CHECKPOINT_VERTEX_IDS, checkpointVertexIds)
                .param(ARG_CURRENT_VERTEX_IDS, currentVertexIds);
    }
}

static void restoreTaskStatesFromCheckpoint(
        GraphExecutionPlan execPlan,
        CheckpointPlan checkpointPlan,
        CompletedCheckpoint checkpoint) throws Exception {
    // Pass the checkpoint's TaskLocation set so the shared restore
    // path can perform the reverse-direction vertex differential check.
    Set<TaskLocation> checkpointLocations = checkpoint.getTaskStates().keySet();
    GraphModelCheckpointExecutor.restoreTaskStatesFromSource(execPlan, checkpointPlan, checkpoint.getCheckpointId(),
            checkpointLocations,
            (taskLocation) -> {
                TaskStateSnapshot state = checkpoint.getTaskState(taskLocation);
                if (state == null) {
                    throw new StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED)
                            .param(ARG_VERTEX_ID, taskLocation.getVertexId())
                            .param(ARG_TASK_INDEX, taskLocation.getTaskIndex())
                            .param(ARG_TASK_LOCATION, taskLocation)
                            .param(ARG_CHECKPOINT_ID, checkpoint.getCheckpointId())
                            .param(ARG_EPOCH_ID, checkpoint.getCheckpointId())
                            .param(ARG_DETAIL, "Available keys: " + checkpoint.getTaskStates().keySet());
                }
                return state;
            });
}

/**
 * Restores operator state for a single {@link OperatorChain} from a
 * {@link TaskStateSnapshot} captured at the given epoch.
 *
 * <p>Exposed package-private so
 * {@link SupervisionLoop#rebuildTask} can reuse the exact same restore path
 * as the initial {@code GraphModelCheckpointExecutor.restoreFromCheckpoint} on region-scoped restart.
 * With consistent-cut epoch alignment (replay from
 * epoch N &gt; 0), operator state must be restored from the checkpoint at
 * epoch N — replaying from epoch 0 (empty initial state) would lose
 * stateful operators' (window/CEP/aggregate) pre-checkpoint accumulated
 * state and silently produce wrong results.
 *
 * @param chain     the operator chain to restore into (must not be null)
 * @param epochId   the checkpoint id (consistent-cut epoch) of the snapshot
 * @param taskState the per-task state snapshot (must not be null)
 * @param mappings  operator-state mappings for this task (may be empty)
 * @throws Exception if any operator's restore fails (fail-fast)
 */
static void restoreOperatorsFromState(
        OperatorChain chain,
        long epochId,
        TaskStateSnapshot taskState,
        List<OperatorStateMapping> mappings) throws Exception {

    if (chain == null) return;

    List<StreamOperator<?>> operators = chain.getOperators();
    for (int i = 0; i < operators.size(); i++) {
        StreamOperator<?> op = operators.get(i);
        if (op instanceof AbstractStreamOperator) {
            OperatorSnapshotResult opResult = buildSnapshotFromTaskState(taskState, i, mappings);
            if (opResult != null && !opResult.isEmpty()) {
                try {
                    ((AbstractStreamOperator<?>) op).restoreState(opResult);
                    LOG.debug("Restored state for operator index {}", i);
                } catch (Exception e) {
                    LOG.error("Failed to restore state for operator index {}", i, e);
                    throw e;
                }
            }
        }

        if (op instanceof CheckpointParticipant) {
            try {
                ((CheckpointParticipant) op).restoreFromEpoch(epochId, taskState);
                LOG.debug("Restored from epoch {} for CheckpointParticipant operator index {}", epochId, i);
            } catch (Exception e) {
                LOG.error("Failed to restoreFromEpoch for operator index {}", i, e);
                throw e;
            }
        } else if (op instanceof AbstractUdfStreamOperator) {
            Object udf = ((AbstractUdfStreamOperator<?, ?>) op).getUserFunction();
            if (udf instanceof CheckpointParticipant && udf != op) {
                try {
                    ((CheckpointParticipant) udf).restoreFromEpoch(epochId, taskState);
                    LOG.debug("Restored from epoch {} for CheckpointParticipant UDF operator index {}", epochId, i);
                } catch (Exception e) {
                    LOG.error("Failed to restoreFromEpoch for UDF operator index {}", i, e);
                    throw e;
                }
            }
        }
    }
}

static OperatorSnapshotResult buildSnapshotFromTaskState(
        TaskStateSnapshot taskState,
        int operatorIndex,
        List<OperatorStateMapping> mappings) {

    OperatorSnapshotResult.Builder builder = OperatorSnapshotResult.builder();
    boolean found = false;

    if (mappings != null) {
        for (OperatorStateMapping mapping : mappings) {
            if (mapping.getOperatorIndex() == operatorIndex) {
                String opStateKey = mapping.getOperatorStateKey();
                String prefix = opStateKey + "-";
                for (Map.Entry<String, Object> entry : taskState.getOperatorStates().entrySet()) {
                    if (entry.getKey().equals(opStateKey) || entry.getKey().startsWith(prefix)) {
                        String stateKey = entry.getKey().equals(opStateKey)
                                ? entry.getKey()
                                : entry.getKey().substring(prefix.length());
                        builder.putOperatorState(stateKey, entry.getValue());
                        found = true;
                    }
                }

                if (mapping.hasKeyedState()) {
                    String keyedPrefix = mapping.getKeyedStateStorageKey();
                    for (Map.Entry<String, Object> entry : taskState.getKeyedStates().entrySet()) {
                        if (entry.getKey().startsWith(keyedPrefix)) {
                            builder.putKeyedState(entry.getKey(), entry.getValue());
                            found = true;
                        }
                    }
                } else {
                    // CheckpointPlanBuilder marks keyed state only when the
                    // operator's keyedStateBackend exists AT PLAN-BUILD TIME
                    // (pre-open → always null), so the tracker ACK writes the
                    // operator's keyed snapshot under the RAW key
                    // ("keyed-state", the key AbstractStreamOperator.
                    // snapshotState uses) instead of a per-operator prefix.
                    // This fallback reads that raw key; without it keyed
                    // state would be silently dropped
                    // on restore (opResult empty → restore skipped) and every
                    // keyed operator would resume from empty state. A chain has at
                    // most one keyed-state-bearing operator by construction
                    // (the raw key would otherwise overwrite on the ACK side).
                    Object rawKeyed = taskState.getKeyedState("keyed-state");
                    if (rawKeyed != null) {
                        builder.putKeyedState("keyed-state", rawKeyed);
                        found = true;
                    }
                }
                break;
            }
        }
    }

    if (!found) {
        String opStateKey = "operator-" + operatorIndex;
        Object opState = taskState.getOperatorState(opStateKey);
        if (opState != null) {
            builder.putOperatorState(opStateKey, opState);
        }

        LOG.warn("No mapping found for operator index {}, skipping keyed state", operatorIndex);
    }

    return builder.build();
}
}
