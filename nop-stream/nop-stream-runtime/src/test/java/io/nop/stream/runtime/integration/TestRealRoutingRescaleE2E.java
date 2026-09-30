/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.TaskLocation;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroupAssignment;
import io.nop.stream.core.datastream.DataStreamImpl.KeySelectorPartitioner;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import io.nop.stream.runtime.execution.GraphModelCheckpointExecutor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-01 nail test (plan 368 Phase 1): the record-routing formula used by the
 * production keyBy partitioner ({@link KeySelectorPartitioner}) must agree
 * with the keyed-state ownership formula (stableHash &#8594; key-group &#8594;
 * range owner), so that after a rescale restore every record keeps arriving at
 * the subtask that owns its state.
 *
 * <p>The harness attaches the exact production partitioner to a JobEdge
 * (source vertex &#8594; keyed vertex) and runs the real local dispatch +
 * real restore-dispatch machinery:
 * <ul>
 *   <li>{@link #realDispatchRoutesEachKeyToItsOwnershipSubtask()} observes
 *       which subtask each record lands on and asserts single-ownership per
 *       subtask.</li>
 *   <li>{@link #rescaleRestoreKeepsStateOnTheSubtaskRecordsRouteTo()} stages a
 *       savepoint whose per-subtask keyed placement is taken from the real
 *       routing observation at p=2, restores at p=4, and asserts every key
 *       reads back its value on the subtask its record is routed to.</li>
 * </ul>
 * Anti-hollow: before the routing/ownership unification this loses state for
 * misrouted keys (each phase-B record lands on a subtask that does not own the
 * key-group its state was restored to); after the fix every key reads back
 * exactly the staged value.
 */
public class TestRealRoutingRescaleE2E {

    private static final int MAX_PARALLELISM = 128;
    private static final String SOURCE_VERTEX = "route-src";
    private static final String KEYED_VERTEX = "route-keyed";
    private static final String KEYED_STORAGE_KEY = "operator-0-keyed";
    private static final String JOB_ID = "route-job";
    private static final String PIPELINE_ID = "route-pipe";

    /** Thread-grouped key reception (one entry per keyed subtask thread). */
    static final Map<Long, Set<String>> RECEIVED_BY_THREAD = new ConcurrentHashMap<>();

    /** Collected phase-B read results: "key=1" on hit, "key=MISSING" on a routing/ownership mismatch. */
    static final List<String> READ_RESULTS = Collections.synchronizedList(new ArrayList<>());

    /** When true the keyed operator reads state instead of writing it. */
    static volatile boolean readMode = false;

    @TempDir
    Path tempDir;

    /** Keyed operator that owns its backend, records reception, and writes or reads per-key state. */
    public static final class RoutingStateOperator extends AbstractStreamOperator<String>
            implements io.nop.stream.core.operators.OneInputStreamOperator<String, String> {
        private static final long serialVersionUID = 1L;

        private transient ValueState<Long> state;

        public RoutingStateOperator() {
            setKeyedStateBackend(new MemoryKeyedStateBackend<>(String.class, MAX_PARALLELISM));
        }

        /**
         * Constructor-based per-subtask copy (required by the copyForSubtask
         * contract): the keyed state backend field is not transient, so the
         * serialization-based default would share ONE backend across all
         * subtasks and each subtask's restore would clear the previous one.
         */
        @Override
        public io.nop.stream.core.operators.StreamOperator<String> copyForSubtask() {
            return new RoutingStateOperator();
        }

        @Override
        public void processElement(io.nop.stream.core.streamrecord.StreamRecord<String> record) throws Exception {
            String key = record.getValue();
            RECEIVED_BY_THREAD.computeIfAbsent(Thread.currentThread().getId(),
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(key);

            @SuppressWarnings("unchecked")
            MemoryKeyedStateBackend<String> backend =
                    (MemoryKeyedStateBackend<String>) (Object) getKeyedStateBackend();
            backend.setCurrentKey(key);
            if (state == null) {
                state = backend.getState(new ValueStateDescriptor<>("v", Long.class, 0L));
            }
            if (readMode) {
                Long v = state.value();
                READ_RESULTS.add(key + "=" + (v == null ? "MISSING" : v));
            } else {
                state.update(1L);
            }
        }
    }

    private static List<String> buildKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            keys.add("route-key-" + i);
        }
        return keys;
    }

    /** Build the two-vertex graph with the production keyBy partitioner on the edge. */
    private static JobGraph buildJobGraph(int keyedParallelism, List<String> keys) {
        SourceFunction<String> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                for (String key : keys) {
                    ctx.collect(key);
                }
            }

            @Override
            public void cancel() {
            }
        };

        StreamSourceOperator<String> srcOp = new StreamSourceOperator<>(source);
        OperatorChain srcChain = new OperatorChain(Collections.singletonList(srcOp));
        StreamTaskInvokable srcInvokable = new StreamTaskInvokable(srcChain);
        JobVertex srcVertex = new JobVertex(SOURCE_VERTEX, "route-source", 1,
                Collections.singletonList(srcChain), srcInvokable);

        RoutingStateOperator keyedOp = new RoutingStateOperator();
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(v -> {
        });
        OperatorChain keyedChain = new OperatorChain(Arrays.asList(keyedOp, sinkOp));
        StreamTaskInvokable keyedInvokable = new StreamTaskInvokable(keyedChain);
        JobVertex keyedVertex = new JobVertex(KEYED_VERTEX, "route-keyed-vertex", keyedParallelism,
                Collections.singletonList(keyedChain), keyedInvokable);

        KeySelector<String, String> keySelector = k -> k;
        JobEdge edge = new JobEdge(SOURCE_VERTEX, KEYED_VERTEX,
                io.nop.stream.core.jobgraph.ResultPartitionType.PIPELINED_BOUNDED,
                new KeySelectorPartitioner<>(keySelector, MAX_PARALLELISM));

        JobGraph jobGraph = new JobGraph("real-routing-e2e");
        jobGraph.addVertex(srcVertex);
        jobGraph.addVertex(keyedVertex);
        jobGraph.addEdge(edge);
        return jobGraph;
    }

    private static CheckpointConfig baseConfig(Path storageDir) {
        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(JOB_ID);
        config.setPipelineId(PIPELINE_ID);
        config.setCheckpointEnabled(true);
        config.setCheckpointInterval(600_000L);
        config.setStorageProperty("path", storageDir.toString());
        return config;
    }

    @Test
    @Timeout(60)
    void keyByBakesDefaultMaxParallelismIntoRoutedPartitioner() throws Exception {
        // Without a configured state backend, keyBy must bake the
        // KeyGroup.DEFAULT_MAX_PARALLELISM routing bound into the edge
        // partitioner (AR-01 fix wiring), not the legacy raw-hash formula.
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4);
        env.fromCollection(buildKeys()).keyBy(k -> k).sink(v -> {
        });
        JobGraph jobGraph = env.buildJobGraph("keyby-default-mp");

        io.nop.commons.partition.IPartitioner<?> partitioner = null;
        for (JobEdge edge : jobGraph.getEdges()) {
            if (edge.getPartitioner() != null) {
                partitioner = edge.getPartitioner();
            }
        }
        assertNotNull(partitioner, "keyBy must attach the KeySelectorPartitioner to the edge");
        @SuppressWarnings("rawtypes")
        io.nop.commons.partition.IPartitioner rawPartitioner = partitioner;
        int mismatchesAgainstOwnership = 0;
        for (int i = 0; i < 40; i++) {
            String key = "route-key-" + i;
            assertEquals(KeyGroupAssignment.assignToSubtask(key, 128, 4), rawPartitioner.partition(key, 4),
                    "keyBy routing must equal the ownership formula with the default maxParallelism");
            if ((key.hashCode() & Integer.MAX_VALUE) % 4 != rawPartitioner.partition(key, 4)) {
                mismatchesAgainstOwnership++;
            }
        }
        assertTrue(mismatchesAgainstOwnership > 0,
                "test data must distinguish the ownership formula from the legacy raw-hash formula");
    }

    @Test
    @Timeout(180)
    void realDispatchRoutesEachKeyToItsOwnershipSubtask() throws Exception {
        RECEIVED_BY_THREAD.clear();
        JobGraph jobGraph = buildJobGraph(2, buildKeys());
        GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, JOB_ID, baseConfig(tempDir));

        assertEquals(2, RECEIVED_BY_THREAD.size(), "records must be dispatched to exactly 2 keyed subtasks");
        Set<Integer> ownerSubtasks = new java.util.HashSet<>();
        for (Set<String> received : RECEIVED_BY_THREAD.values()) {
            assertTrue(!received.isEmpty(), "no subtask may observe an empty share of a keyed stream");
            int owner = -1;
            for (String key : received) {
                int subtask = KeyGroupAssignment.assignToSubtask(key, MAX_PARALLELISM, 2);
                if (owner < 0) {
                    owner = subtask;
                } else {
                    assertEquals(owner, subtask,
                            "one subtask received keys owned by different subtasks: " + received);
                }
            }
            ownerSubtasks.add(owner);
        }
        assertEquals(2, ownerSubtasks.size(), "the dispatched subtasks must cover distinct ownership slots");
    }

    @Test
    @Timeout(180)
    void rescaleRestoreKeepsStateOnTheSubtaskRecordsRouteTo() throws Exception {
        RECEIVED_BY_THREAD.clear();
        READ_RESULTS.clear();
        readMode = false;

        // Phase A: observe real routing placement at p=2 and write per-key state.
        GraphModelCheckpointExecutor.executeWithCheckpoint(buildJobGraph(2, buildKeys()), JOB_ID,
                baseConfig(tempDir.resolve("ckpt-a")));
        assertEquals(2, RECEIVED_BY_THREAD.size(), "phase A must observe two keyed subtasks");

        // Stage a savepoint whose per-subtask keyed placement mirrors the real
        // phase-A routing (one staged subtask per observed receiving subtask).
        Path spDir = tempDir.resolve("sp");
        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(spDir.toString());
        Map<TaskLocation, TaskStateSnapshot> taskStates = new LinkedHashMap<>();
        int stagedSubtask = 0;
        for (Map.Entry<Long, Set<String>> e : RECEIVED_BY_THREAD.entrySet()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (String key : e.getValue()) {
                Map<String, Object> en = new LinkedHashMap<>();
                en.put("namespace", "_default_");
                en.put("key", key);
                en.put("value", 1L);
                entries.add(en);
            }
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("stateType", "ValueState");
            info.put("valueType", "java.lang.Long");
            info.put("entries", entries);
            Map<String, Object> states = new LinkedHashMap<>();
            states.put("v", info);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("keyType", "java.lang.String");
            data.put("states", states);

            TaskLocation loc = new TaskLocation(JOB_ID, PIPELINE_ID, KEYED_VERTEX, stagedSubtask++);
            TaskStateSnapshot ts = new TaskStateSnapshot(loc, 1L);
            ts.putKeyedState(KEYED_STORAGE_KEY, new StateSnapshot(data));
            taskStates.put(loc, ts);
        }
        // The reverse vertex differential requires every current vertex to be
        // present in the checkpoint, including the (stateless) source vertex.
        taskStates.put(new TaskLocation(JOB_ID, PIPELINE_ID, SOURCE_VERTEX, 0),
                new TaskStateSnapshot(new TaskLocation(JOB_ID, PIPELINE_ID, SOURCE_VERTEX, 0), 1L));
        CompletedCheckpoint checkpoint = CompletedCheckpoint.builder()
                .jobId(JOB_ID).pipelineId(PIPELINE_ID).checkpointId(1L)
                .triggerTimestamp(System.currentTimeMillis())
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(taskStates)
                .build();
        storage.storeCheckPoint(checkpoint);

        // Phase B: restore at p=4 and read per-key state under real routing.
        readMode = true;
        GraphModelCheckpointExecutor.executeWithSavepoint(buildJobGraph(4, buildKeys()), JOB_ID,
                baseConfig(tempDir), spDir.toString());

        assertEquals(40, READ_RESULTS.size(), "every key must be read back exactly once");
        for (String result : READ_RESULTS) {
            assertTrue(result.endsWith("=1"),
                    "key " + result + " lost its restored state (record routed away from its state owner)");
        }
    }
}
