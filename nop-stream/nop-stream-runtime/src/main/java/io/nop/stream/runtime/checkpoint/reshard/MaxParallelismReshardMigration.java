/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.reshard;

import io.nop.api.core.time.CoreMetrics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.nop.api.core.annotations.core.Internal;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskEpochSnapshot;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.common.state.shard.KeyGroupRange;
import io.nop.stream.core.common.state.shard.KeyGroupReshard;
import io.nop.stream.core.common.state.shard.StateKeyRematerializer;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.runtime.checkpoint.storage.CheckpointSerDe;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Stage 37: offline {@code maxParallelism} reshard migration tool. Reads an old
 * savepoint, recomputes every keyed state's key&rarr;group mapping under a
 * <em>new</em> {@code maxParallelism}, redistributes entries to the new owner
 * subtasks, and writes a new savepoint plus a verification report
 * ({@link ReshardMigrationResult}).
 *
 * <p>This is the explicit migration action referenced by
 * {@code checkpoint-design.md} §8.5/§8.6 for "修改 maxParallelism". It is the
 * counterpart of the Stage 35 restore-time rescale (which only handles
 * {@code parallelism} change with {@code maxParallelism} fixed): a
 * {@code maxParallelism} change moves keys between groups, so the savepoint
 * must be physically rewritten offline rather than re-routed at restore time.
 *
 * <p><b>Scope</b>: operates on full-snapshot savepoints (the {@code .checkpoint}
 * JSON written by {@link io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage}).
 * Operator (non-keyed) state is copied through unchanged; keyed state entries
 * are re-grouped. The original savepoint is never modified (read-only input).
 *
 * <p><b>Failure semantics</b>: any structural anomaly (unknown state type, entry
 * without a key, unreadable savepoint) fails fast — no silent drop. A
 * meaningless migration ({@code old == new maxParallelism}) is rejected.
 */
@Internal
public final class MaxParallelismReshardMigration {

    private MaxParallelismReshardMigration() {
    }

    /**
     * Run a reshard migration from an on-disk savepoint.
     *
     * @param oldSavepointPath  local path to the old savepoint (directory as
     *                          produced by {@code storeSavepoint}, or a raw
     *                          {@code .checkpoint} file)
     * @param oldMaxParallelism maxParallelism in effect when the old savepoint
     *                          was written (consistency check)
     * @param newMaxParallelism target maxParallelism ({@code != oldMaxParallelism})
     * @param outputBaseDir     base directory where the new savepoint directory
     *                          ({@code savepoint-<id>}) is written
     * @return the verification report (also written alongside the new savepoint)
     */
    public static ReshardMigrationResult migrate(
            String oldSavepointPath, int oldMaxParallelism, int newMaxParallelism,
            String outputBaseDir) {
        CompletedCheckpoint oldCheckpoint = readSavepoint(oldSavepointPath);
        Resharded resharded = reshardCheckpoint(oldCheckpoint, oldMaxParallelism, newMaxParallelism);
        String newSavepointPath = writeSavepoint(resharded.checkpoint, outputBaseDir);
        resharded.result.setOldSavepointPath(oldSavepointPath);
        resharded.result.setNewSavepointPath(newSavepointPath);
        return resharded.result;
    }

    /**
     * Pure reshard of an already-deserialized checkpoint (no savepoint read).
     * Exposed for focused unit/E2E tests that build the input in memory. The
     * caller is responsible for persisting {@code result.checkpoint} if needed.
     */
    public static Resharded reshardCheckpoint(CompletedCheckpoint oldCheckpoint,
                                              int oldMaxParallelism, int newMaxParallelism) {
        return reshardCheckpoint(oldCheckpoint, oldMaxParallelism, newMaxParallelism, -1);
    }

    /**
     * Pure reshard with an explicit new parallelism override. When
     * {@code newParallelism < 0} each vertex keeps its old parallelism (pure
     * reshard: subtask count unchanged, only key&rarr;group&rarr;subtask moves).
     */
    public static Resharded reshardCheckpoint(CompletedCheckpoint oldCheckpoint,
                                              int oldMaxParallelism, int newMaxParallelism,
                                              int newParallelismOverride) {
        validateArgs(oldMaxParallelism, newMaxParallelism, newParallelismOverride);
        if (oldCheckpoint == null) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "oldCheckpoint must not be null");
        }

        ReshardMigrationResult result = new ReshardMigrationResult();
        result.setOldMaxParallelism(oldMaxParallelism);
        result.setNewMaxParallelism(newMaxParallelism);

        Map<String, List<TaskLocation>> vertices = groupSubtasksByVertex(oldCheckpoint);

        Map<TaskLocation, TaskStateSnapshot> newTaskStates = new LinkedHashMap<>();

        for (Map.Entry<String, List<TaskLocation>> vertexEntry : vertices.entrySet()) {
            reshardVertexStates(oldCheckpoint, vertexEntry.getKey(), vertexEntry.getValue(),
                    newMaxParallelism, newParallelismOverride, result, newTaskStates);
        }

        // Sanity check: per-state conservation is structural (we only move
        // entries), but assert explicitly to fail-fast on any logic bug.
        for (Map.Entry<String, Integer> stateCount : result.getKeyCountByState().entrySet()) {
            if (stateCount.getValue() == null || stateCount.getValue() < 0) {
                throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL,
                        "Conservation check failed for state " + stateCount.getKey());
            }
        }

        CompletedCheckpoint newCheckpoint = buildReshardedCheckpoint(oldCheckpoint, newTaskStates);

        result.setKeyedStateCount(result.getKeyCountByState().size());
        result.setOperatorStateCount(countOperatorStates(newTaskStates));
        result.setSubtaskCount(newTaskStates.size());

        return new Resharded(newCheckpoint, result);
    }

    // ---- helpers ----

    /**
     * Groups old subtasks by vertexId, preserving a stable subtask order
     * (ascending task index).
     */
    private static Map<String, List<TaskLocation>> groupSubtasksByVertex(CompletedCheckpoint oldCheckpoint) {
        Map<String, List<TaskLocation>> vertices = new TreeMap<>();
        for (TaskLocation loc : oldCheckpoint.getTaskStates().keySet()) {
            vertices.computeIfAbsent(loc.getVertexId(), k -> new ArrayList<>()).add(loc);
        }
        for (List<TaskLocation> subs : vertices.values()) {
            subs.sort((a, b) -> Integer.compare(a.getTaskIndex(), b.getTaskIndex()));
        }
        return vertices;
    }

    /**
     * Reshard one vertex's states under the new maxParallelism. Orchestration
     * of the four phases: merge all old subtasks' keyed pools into per-state
     * global pools, record the per-state key counts (conservation baseline),
     * redistribute the pools to the new owners, and assemble each new
     * subtask's snapshot (operator state copied 1:1 by index, keyed state
     * rebuilt). Updates {@code result}'s statistics and adds the new subtask
     * snapshots to {@code newTaskStates}.
     */
    private static void reshardVertexStates(CompletedCheckpoint oldCheckpoint,
                                            String vertexId,
                                            List<TaskLocation> oldSubtasks,
                                            int newMaxParallelism,
                                            int newParallelismOverride,
                                            ReshardMigrationResult result,
                                            Map<TaskLocation, TaskStateSnapshot> newTaskStates) {
        int oldParallelism = oldSubtasks.size();
        int newParallelism = newParallelismOverride > 0 ? newParallelismOverride : oldParallelism;
        if (newParallelism > newMaxParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "newParallelism (" + newParallelism + ") for vertex " + vertexId
                            + " exceeds newMaxParallelism (" + newMaxParallelism + ")");
        }
        result.setNewParallelism(newParallelism);

        // Phase 1: build the per-state global keyed pool across all old
        // subtasks, tracking each state's outer stateData envelope (keyType).
        Map<String, GlobalKeyedPool> pools = buildGlobalKeyedPools(oldCheckpoint, oldSubtasks);

        // Phase 2: record per-stateName key counts (conservation invariant:
        // this is both the before and after count — redistribution only moves
        // entries).
        recordKeyCounts(vertexId, pools, result);

        // Phase 3: redistribute each keyed pool under the new maxParallelism.
        // newSubtaskIndex -> keyedStorageKey -> rebuilt stateData
        Map<Integer, Map<String, Map<String, Object>>> newKeyedBySubtask =
                redistributePools(pools, newMaxParallelism, newParallelism);

        // Phase 4: assemble each new subtask snapshot.
        assembleNewSubtaskSnapshots(oldCheckpoint, vertexId, oldSubtasks, newMaxParallelism,
                newParallelism, newKeyedBySubtask, result, newTaskStates);
    }

    /**
     * Phase 1: builds the per-state global keyed pool across all old subtasks.
     * keyedStorageKey -&gt; {envelope (stateData map), globalStates}.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, GlobalKeyedPool> buildGlobalKeyedPools(CompletedCheckpoint oldCheckpoint,
                                                                     List<TaskLocation> oldSubtasks) {
        Map<String, GlobalKeyedPool> pools = new LinkedHashMap<>();
        for (TaskLocation oldLoc : oldSubtasks) {
            TaskStateSnapshot oldState = oldCheckpoint.getTaskStates().get(oldLoc);
            if (oldState == null) {
                continue;
            }
            for (Map.Entry<String, Object> ke : oldState.getKeyedStates().entrySet()) {
                Map<String, Object> stateData = toStateDataMap(ke.getValue());
                if (stateData == null) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL,
                            "Keyed state '" + ke.getKey() + "' at " + oldLoc
                                    + " is neither a StateSnapshot nor a state-data map (fail-fast)");
                }
                Object statesObj = stateData.get("states");
                if (!(statesObj instanceof Map)) {
                    throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL,
                            "Keyed state '" + ke.getKey() + "' at " + oldLoc
                                    + " has no 'states' map (fail-fast, no silent drop)");
                }
                GlobalKeyedPool pool = pools.computeIfAbsent(ke.getKey(),
                        k -> new GlobalKeyedPool(new LinkedHashMap<>(stateData), new LinkedHashMap<>()));
                mergeStates((Map<String, Object>) statesObj, pool.globalStates);
            }
        }
        return pools;
    }

    /**
     * Phase 2: records per-stateName key counts. A keyed storage key (backend)
     * may hold multiple named states, so the report is keyed by the inner state
     * name, not the storage key.
     */
    private static void recordKeyCounts(String vertexId, Map<String, GlobalKeyedPool> pools,
                                        ReshardMigrationResult result) {
        for (Map.Entry<String, GlobalKeyedPool> poolEntry : pools.entrySet()) {
            for (String stateName : poolEntry.getValue().globalStates.keySet()) {
                int cnt = countEntriesOfState(poolEntry.getValue().globalStates, stateName);
                result.getKeyCountByState().merge(stateName, cnt, Integer::sum);
            }
        }

        if (pools.isEmpty() && result.getKeyCountByState().isEmpty()) {
            result.addWarning("vertex " + vertexId + " has no keyed state (operator-state-only); "
                    + "reshard is a structural no-op for this vertex and recorded explicitly");
        }
    }

    /**
     * Phase 3: redistributes each keyed pool under the new maxParallelism and
     * groups the rebuilt stateData maps by target subtask.
     */
    private static Map<Integer, Map<String, Map<String, Object>>> redistributePools(
            Map<String, GlobalKeyedPool> pools, int newMaxParallelism, int newParallelism) {
        Map<Integer, Map<String, Map<String, Object>>> newKeyedBySubtask = new TreeMap<>();
        for (int s = 0; s < newParallelism; s++) {
            newKeyedBySubtask.put(s, new LinkedHashMap<>());
        }
        for (Map.Entry<String, GlobalKeyedPool> poolEntry : pools.entrySet()) {
            String keyedStorageKey = poolEntry.getKey();
            GlobalKeyedPool pool = poolEntry.getValue();
            // ST-02: the pool envelope's declared keyType drives key
            // re-materialization, so redistribution hashes the typed key (the
            // live group), not the JSON-native form persisted in the savepoint.
            Map<Integer, Map<String, Object>> redistributed = KeyGroupReshard.redistributeStates(
                    pool.globalStates, newMaxParallelism, newParallelism,
                    StateKeyRematerializer.resolveSnapshotKeyType(pool.envelope, null));
            for (Map.Entry<Integer, Map<String, Object>> re : redistributed.entrySet()) {
                Map<String, Object> newStates = re.getValue();
                Map<String, Object> newStateData = new LinkedHashMap<>(pool.envelope);
                newStateData.put("states", newStates);
                newKeyedBySubtask.get(re.getKey()).put(keyedStorageKey, newStateData);
            }
        }
        return newKeyedBySubtask;
    }

    /**
     * Phase 4: assembles each new subtask's snapshot (operator state copied 1:1
     * by index — scale-up subtasks start empty, operator-state rescale
     * redistribution is out of scope and orthogonal to reshard; keyed state
     * attached from the phase-3 redistribution) and records the per-subtask key
     * distribution.
     */
    @SuppressWarnings("unchecked")
    private static void assembleNewSubtaskSnapshots(CompletedCheckpoint oldCheckpoint,
                                                    String vertexId,
                                                    List<TaskLocation> oldSubtasks,
                                                    int newMaxParallelism,
                                                    int newParallelism,
                                                    Map<Integer, Map<String, Map<String, Object>>> newKeyedBySubtask,
                                                    ReshardMigrationResult result,
                                                    Map<TaskLocation, TaskStateSnapshot> newTaskStates) {
        int oldParallelism = oldSubtasks.size();
        for (int s = 0; s < newParallelism; s++) {
            TaskLocation newLoc = new TaskLocation(oldCheckpoint.getJobId(),
                    oldCheckpoint.getPipelineId(), vertexId, s);
            TaskEpochSnapshot epoch = new TaskEpochSnapshot(newLoc, oldCheckpoint.getCheckpointId());

            // Operator (non-keyed) state: copy 1:1 by index where an old
            // subtask exists; scale-up subtasks start empty (operator-state
            // rescale redistribution is out of scope, orthogonal to reshard).
            if (s < oldParallelism) {
                TaskStateSnapshot oldState = oldCheckpoint.getTaskStates().get(oldSubtasks.get(s));
                if (oldState != null && oldState.getOperatorStates() != null) {
                    for (Map.Entry<String, Object> op : oldState.getOperatorStates().entrySet()) {
                        epoch.putOperatorState(op.getKey(), op.getValue());
                    }
                }
            }

            // Keyed state: attach each rebuilt stateData map.
            Map<String, Map<String, Object>> keyedForSubtask = newKeyedBySubtask.get(s);
            for (Map.Entry<String, Map<String, Object>> ke : keyedForSubtask.entrySet()) {
                epoch.putKeyedState(ke.getKey(), ke.getValue());
            }

            // Materialize new ownership under the new maxParallelism.
            KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                    newMaxParallelism, newParallelism, s);
            epoch.setKeyGroupOwnership(newParallelism, newMaxParallelism, newRange);

            newTaskStates.put(newLoc, epoch);

            // Record per-subtask key distribution.
            int subKeys = 0;
            for (Map<String, Object> sd : keyedForSubtask.values()) {
                Object st = sd.get("states");
                if (st instanceof Map) {
                    subKeys += KeyGroupReshard.countKeyedEntries((Map<String, Object>) st);
                }
            }
            result.getKeyCountBySubtask()
                    .computeIfAbsent(vertexId, k -> new TreeMap<>()).put(s, subKeys);
        }
    }

    /**
     * Builds the resharded checkpoint manifest from the new task states,
     * carrying over the job/pipeline/checkpoint identity of the old checkpoint
     * and marking the result as restored.
     */
    private static CompletedCheckpoint buildReshardedCheckpoint(CompletedCheckpoint oldCheckpoint,
                                                                Map<TaskLocation, TaskStateSnapshot> newTaskStates) {
        CompletedCheckpoint newCheckpoint = CompletedCheckpoint.builder()
                .jobId(oldCheckpoint.getJobId())
                .pipelineId(oldCheckpoint.getPipelineId())
                .checkpointId(oldCheckpoint.getCheckpointId())
                .triggerTimestamp(oldCheckpoint.getTriggerTimestamp())
                .completedTimestamp(CoreMetrics.currentTimeMillis())
                .checkpointType(oldCheckpoint.getCheckpointType())
                .taskStates(newTaskStates)
                .build();
        newCheckpoint.setRestored(true);
        return newCheckpoint;
    }

    private static void validateArgs(int oldMaxParallelism, int newMaxParallelism, int newParallelismOverride) {
        if (oldMaxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "oldMaxParallelism must be >= 1: " + oldMaxParallelism);
        }
        if (newMaxParallelism < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "newMaxParallelism must be >= 1: " + newMaxParallelism);
        }
        if (newMaxParallelism == oldMaxParallelism) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "newMaxParallelism (" + newMaxParallelism
                            + ") == oldMaxParallelism: meaningless reshard rejected (fail-fast). "
                            + "Use a parallelism-only rescale restore instead.");
        }
        if (newParallelismOverride != -1 && newParallelismOverride < 1) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "newParallelism override must be >= 1 or -1 (auto): " + newParallelismOverride);
        }
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
     * Merge a source {@code states} sub-map (stateName -> stateInfo) into an
     * accumulator. Each state's entries are concatenated; the info metadata is
     * taken from the first contributor (consistent with the executor's
     * {@code mergeAndFilterKeyedStates}).
     */
    @SuppressWarnings("unchecked")
    private static void mergeStates(Map<String, Object> source, Map<String, Object> acc) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                throw new StreamException(ERR_STREAM_STATE_ERROR).param(ARG_DETAIL,
                        "State '" + e.getKey() + "' is not a state-info map (fail-fast)");
            }
            Map<String, Object> info = (Map<String, Object>) e.getValue();
            Map<String, Object> dst = (Map<String, Object>) acc.get(e.getKey());
            if (dst == null) {
                acc.put(e.getKey(), new LinkedHashMap<>(info));
            } else {
                Object entries = info.get("entries");
                if (entries instanceof List) {
                    Object accEntries = dst.computeIfAbsent("entries", k -> new ArrayList<>());
                    if (accEntries instanceof List) {
                        ((List<Object>) accEntries).addAll((List<?>) entries);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static int countOperatorStates(Map<TaskLocation, TaskStateSnapshot> taskStates) {
        int count = 0;
        for (TaskStateSnapshot ts : taskStates.values()) {
            if (ts.getOperatorStates() != null) {
                count += ts.getOperatorStates().size();
            }
        }
        return count;
    }

    /** Count the entries of a single named state within a {@code states} sub-map. */
    @SuppressWarnings("unchecked")
    private static int countEntriesOfState(Map<String, Object> globalStates, String stateName) {
        Object infoObj = globalStates.get(stateName);
        if (!(infoObj instanceof Map)) {
            return 0;
        }
        Object entries = ((Map<String, Object>) infoObj).get("entries");
        return entries instanceof List ? ((List<?>) entries).size() : 0;
    }

    private static CompletedCheckpoint readSavepoint(String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR).param(ARG_DETAIL,
                        "Savepoint path does not exist: " + path);
            }
            byte[] data;
            if (Files.isDirectory(p)) {
                Path cpFile = null;
                try (java.util.stream.Stream<Path> s = Files.list(p)) {
                    java.util.Optional<Path> f = s.filter(x -> x.toString().endsWith(".checkpoint")).findFirst();
                    if (f.isPresent()) {
                        cpFile = f.get();
                    }
                }
                if (cpFile == null) {
                    throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR).param(ARG_DETAIL,
                            "No .checkpoint file in savepoint directory: " + path);
                }
                data = Files.readAllBytes(cpFile);
            } else {
                data = Files.readAllBytes(p);
            }
            CompletedCheckpoint cp = CheckpointSerDe.deserializeCheckpoint(data);
            if (cp == null) {
                throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR).param(ARG_DETAIL,
                        "Unrecognized savepoint format (deserialized to null): " + path);
            }
            return cp;
        } catch (StreamException e) {
            throw e;
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL,
                    "Failed to read savepoint: " + path);
        }
    }

    private static String writeSavepoint(CompletedCheckpoint checkpoint, String outputBaseDir) {
        try {
            Path outDir = Paths.get(outputBaseDir);
            Files.createDirectories(outDir);
            String savepointDirName = "savepoint-" + checkpoint.getCheckpointId();
            Path savepointDir = outDir.resolve(savepointDirName);
            Files.createDirectories(savepointDir);

            // Atomic write: .tmp then rename (mirrors LocalFileCheckpointStorage).
            Path checkpointFile = savepointDir.resolve(checkpoint.getCheckpointId() + ".checkpoint");
            Path tmpFile = Paths.get(checkpointFile.toString() + ".tmp");
            byte[] data = CheckpointSerDe.serializeCheckpoint(checkpoint);
            Files.write(tmpFile, data);
            Files.move(tmpFile, checkpointFile,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Persist the verification report alongside the new savepoint so the
            // migration is auditable from disk alone.
            Path reportFile = savepointDir.resolve("reshard-report.json");
            Path tmpReport = Paths.get(reportFile.toString() + ".tmp");
            String reportJson = io.nop.core.lang.json.JsonTool.serialize(
                    reportMap(checkpoint, savepointDir.toString()), true);
            Files.write(tmpReport, reportJson.getBytes(StandardCharsets.UTF_8));
            Files.move(tmpReport, reportFile,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return savepointDir.toString();
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_CHECKPOINT_ERROR, e).param(ARG_DETAIL,
                    "Failed to write resharded savepoint to " + outputBaseDir);
        }
    }

    private static Map<String, Object> reportMap(CompletedCheckpoint cp, String dir) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("newSavepointDir", dir);
        m.put("jobId", cp.getJobId());
        m.put("pipelineId", cp.getPipelineId());
        m.put("checkpointId", cp.getCheckpointId());
        return m;
    }

    /** Holder for the pure-reshard output. */
    public static final class Resharded {
        private final CompletedCheckpoint checkpoint;
        private final ReshardMigrationResult result;

        Resharded(CompletedCheckpoint checkpoint, ReshardMigrationResult result) {
            this.checkpoint = checkpoint;
            this.result = result;
        }

        public CompletedCheckpoint getCheckpoint() {
            return checkpoint;
        }

        public ReshardMigrationResult getResult() {
            return result;
        }
    }

    /** Per keyed-storage-key global pool: envelope stateData + merged global states. */
    private static final class GlobalKeyedPool {
        final Map<String, Object> envelope;
        final Map<String, Object> globalStates;

        GlobalKeyedPool(Map<String, Object> envelope, Map<String, Object> globalStates) {
            this.envelope = envelope;
            this.globalStates = globalStates;
        }
    }
}
