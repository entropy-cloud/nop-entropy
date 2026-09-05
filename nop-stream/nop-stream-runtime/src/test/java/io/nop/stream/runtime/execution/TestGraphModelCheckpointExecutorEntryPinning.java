/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.environment.StreamExecutionResult;
import io.nop.stream.core.execution.GraphExecutionPlan;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.plan.DeploymentPlan;
import io.nop.stream.core.execution.plan.PartitionedPlan;
import io.nop.stream.core.graph.PartitionedPlanGenerator;
import io.nop.stream.core.graph.StreamGraphGenerator;
import io.nop.stream.core.jobgraph.Invokable;
import io.nop.stream.core.jobgraph.JobEdge;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobGraphGenerator;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.jobgraph.ResultPartitionType;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.model.StreamModel;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.StreamOperatorFactory;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.transformation.SinkTransformation;
import io.nop.stream.core.transformation.SourceTransformation;
import io.nop.stream.core.transformation.Transformation;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-09-03-1951-1 Phase 3「先钉后拆」(GMCE): pins the CURRENT differences among
 * the three {@code GraphModelCheckpointExecutor.executeWithCheckpoint} overloads before
 * the convergence refactor, per the plan's difference dimensions ①—⑤:
 * <ul>
 *   <li><b>③ unaligned passthrough asymmetry</b> — the JobGraph entry builds its
 *       execution plan via the 4-arg {@code GraphExecutionPlan.build} which NEVER
 *       threads unaligned-checkpoint config (gates stay unaligned-disabled even when
 *       the config enables it), while the StreamModel entries use the 6-arg build that
 *       threads {@code isUnalignedCheckpointEnabled()}/{@code getUnalignedThreshold()}.
 *       The unaligned flag lives inside the InputGate (no public GMCE surface), so the
 *       pin is asserted at the plan level (per the plan's observation note).</li>
 *   <li><b>② fingerprint</b> — the JobGraph entry never sets a coordinator fingerprint
 *       (manifest carries none); the StreamModel entries set
 *       {@code streamModel.computeFingerprint()} (manifest carries one).</li>
 *   <li><b>④ restore parameter</b> — the JobGraph entry restores with a null
 *       StreamModel; the StreamModel entries pass the model (observable through the
 *       fingerprint source used by the compatibility check on a subsequent restore).</li>
 *   <li><b>overload 2 e2e regression path</b> — the StreamModel/default-config entry
 *       has no existing direct coverage (Phase 1 D3 finding); this suite covers it.</li>
 * </ul>
 * The refactor must keep this suite green unchanged (behavior-zero-change evidence).
 */
class TestGraphModelCheckpointExecutorEntryPinning {

    @TempDir
    Path tempDir;

    // ==================================================================
    // ③ plan-level pin: 4-arg build never threads unaligned; 6-arg does
    // ==================================================================

    private static OperatorChain passthroughChain() {
        return new OperatorChain(Collections.singletonList(new NoOpOperator()));
    }

    private static JobVertex vertex(String id) {
        return new JobVertex(id, id, 1,
                Collections.singletonList(passthroughChain()),
                (Invokable<Void>) () -> {
                });
    }

    private static JobGraph linearGraph() {
        JobGraph graph = new JobGraph("unaligned-pin");
        graph.addVertex(vertex("A"));
        graph.addVertex(vertex("B"));
        graph.addEdge(new JobEdge("A", "B", ResultPartitionType.PIPELINED));
        return graph;
    }

    private static InputGate inputGateOf(GraphExecutionPlan plan, String vertexId) {
        // The InputGate is reached via the subtask's invokable (same route the
        // production abort/restore paths use).
        return plan.getSubtasks(vertexId).get(0).getInvokable().getInputGate();
    }

    @Test
    void fourArgBuildLeavesGatesUnalignedDisabledEvenWhenConfigWouldEnableIt() {
        JobGraph graph = linearGraph();

        // JobGraph-entry equivalent: 4-arg build — unaligned config is NOT threaded
        // (the internal delegation pins unalignedCheckpointEnabled=false).
        GraphExecutionPlan plan4 = GraphExecutionPlan.build(graph, null, true, 10_000L);
        InputGate gate4 = inputGateOf(plan4, "B");
        assertNotNull(gate4, "vertex B must have an InputGate (it has an input edge)");
        assertFalse(gate4.isUnalignedCheckpointEnabled(),
                "4-arg build must leave the gate unaligned-disabled (JobGraph entry "
                        + "does not pass unaligned config through — pinned asymmetry ③)");

        // StreamModel-entry equivalent: 6-arg build with unaligned enabled.
        GraphExecutionPlan plan6 = GraphExecutionPlan.build(graph, null, true, 10_000L, true, 5_000L);
        InputGate gate6 = inputGateOf(plan6, "B");
        assertTrue(gate6.isUnalignedCheckpointEnabled(),
                "6-arg build with unalignedCheckpointEnabled=true must enable it on the gate");
    }

    // ==================================================================
    // ② + ④ overload-1 (JobGraph) e2e: manifest carries NO fingerprint
    // ==================================================================

    @Test
    void jobGraphEntryPersistsManifestWithoutFingerprint() throws Exception {
        String jobId = "pin-overload1";
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());

        JobGraph jobGraph = integersJobGraph(sinkResults);

        CheckpointConfig config = new CheckpointConfig();
        config.setJobId(jobId);
        config.setPipelineId("1");
        config.setStorageProperty("path", tempDir.toString());

        StreamExecutionResult result =
                GraphModelCheckpointExecutor.executeWithCheckpoint(jobGraph, "pin-overload1", config);

        assertNotNull(result);
        assertEquals("pin-overload1", result.getJobName());
        assertEquals(Arrays.asList(10, 20, 30, 40, 50), sortedCopy(sinkResults),
                "overload-1 e2e output");

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        EpochManifest manifest = storage.loadLatestEpochManifest(jobId, "1");
        assertNotNull(manifest, "overload-1 run must persist an EpochManifest");
        assertNull(manifest.getStreamModelFingerprint(),
                "② pinned asymmetry: the JobGraph entry never sets a coordinator "
                        + "fingerprint, so the manifest carries none");
        storage.deleteAllCheckpoints(jobId);
    }

    // ==================================================================
    // ② + ④ overload-3 (StreamModel + userConfig) e2e: manifest HAS fingerprint
    // ==================================================================

    @Test
    void streamModelEntryWithUserConfigPersistsManifestWithFingerprint() throws Exception {
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());
        StreamModel model = integersStreamModel(sinkResults);
        PartitionedPlan pp = partitionedPlanOf(model);
        DeploymentPlan dp = deploymentPlanOf(pp);
        String jobId = pp.getJobId();

        CheckpointConfig config = new CheckpointConfig();
        config.setStorageProperty("path", tempDir.toString());

        StreamExecutionResult result = GraphModelCheckpointExecutor.executeWithCheckpoint(
                model, pp, dp, config);

        assertNotNull(result);
        assertEquals(jobId, result.getJobName(), "jobName resolves from partitionedPlan");
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), sortedCopy(sinkResults),
                "overload-3 e2e output");

        LocalFileCheckpointStorage storage = new LocalFileCheckpointStorage(tempDir.toString());
        EpochManifest manifest = storage.loadLatestEpochManifest(jobId, "pipeline-0");
        assertNotNull(manifest, "overload-3 run must persist an EpochManifest");
        assertNotNull(manifest.getStreamModelFingerprint(),
                "② pinned behavior: the StreamModel entry sets the computed fingerprint, "
                        + "so the manifest carries one");
        assertNotNull(model.computeFingerprint());
        storage.deleteAllCheckpoints(jobId);
    }

    // ==================================================================
    // overload-2 (StreamModel, default config) e2e regression path
    // ==================================================================

    @Test
    void streamModelDefaultConfigEntryRunsEndToEnd() throws Exception {
        List<Integer> sinkResults = Collections.synchronizedList(new ArrayList<>());
        StreamModel model = integersStreamModel(sinkResults);
        PartitionedPlan pp = partitionedPlanOf(model);
        DeploymentPlan dp = deploymentPlanOf(pp);
        String jobId = pp.getJobId();

        // Overload 2 builds its config internally (defaults) and cannot take a storage
        // path — it uses the global default checkpoint dir. Clean the job's tree before
        // and after so the run is isolated and the manifest pin is observable.
        LocalFileCheckpointStorage defaultStorage = new LocalFileCheckpointStorage(
                System.getProperty("java.io.tmpdir") + "/nop-stream-checkpoints");
        defaultStorage.deleteAllCheckpoints(jobId);

        try {
            StreamExecutionResult result = GraphModelCheckpointExecutor.executeWithCheckpoint(
                    model, pp, dp);

            assertNotNull(result, "overload-2 must return a result");
            assertEquals(Arrays.asList(1, 2, 3, 4, 5), sortedCopy(sinkResults),
                    "overload-2 e2e output");

            EpochManifest manifest = defaultStorage.loadLatestEpochManifest(jobId, "pipeline-0");
            assertNotNull(manifest,
                    "overload-2 run must persist an EpochManifest to the default storage");
            assertNotNull(manifest.getStreamModelFingerprint(),
                    "② pinned behavior: overload-2 sets the computed fingerprint");
        } finally {
            defaultStorage.deleteAllCheckpoints(jobId);
        }
    }

    // ==================================================================
    // shared scaffolding
    // ==================================================================

    private static List<Integer> sortedCopy(List<Integer> list) {
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    private static SourceFunction<Integer> boundedIntSource() {
        return new SourceFunction<>() {
            @Override
            public void run(SourceContext<Integer> ctx) {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                }
            }

            @Override
            public void cancel() {
            }
        };
    }

    /** Single self-contained chain source→map→sink (mirrors existing e2e scaffolding). */
    private static JobGraph integersJobGraph(List<Integer> sinkResults) {
        StreamSourceOperator<Integer> sourceOp = new StreamSourceOperator<>(boundedIntSource());
        io.nop.stream.core.operators.StreamMap<Integer, Integer> mapOp =
                new io.nop.stream.core.operators.StreamMap<>(x -> x * 10);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(new SinkFunction<>() {
            @Override
            public void consume(Integer value) {
                sinkResults.add(value);
            }
        });

        OperatorChain chain = new OperatorChain(Arrays.asList(sourceOp, mapOp, sinkOp));
        io.nop.stream.core.execution.task.StreamTaskInvokable invokable =
                new io.nop.stream.core.execution.task.StreamTaskInvokable(chain);
        JobVertex vertex = new JobVertex("v1", "Chain", 1,
                Collections.singletonList(chain), invokable);

        JobGraph jobGraph = new JobGraph("pin-overload1");
        jobGraph.addVertex(vertex);
        return jobGraph;
    }

    /**
     * StreamModel source→map→sink with STABLE transformation ids/keys (mirrors
     * {@code StreamExecutionEnvironment.buildStreamModel}) so fingerprints and vertex
     * ids are deterministic.
     */
    private static StreamModel integersStreamModel(List<Integer> sinkResults) {
        SourceTransformation<Integer> source = new SourceTransformation<>(
                "Source", boundedIntSource(), createTypeInfo(), 1);
        SinkTransformation<Integer> sink = new SinkTransformation<>(
                source, "Sink", (SinkFunction<Integer>) sinkResults::add,
                createTypeInfo(), 1);

        Map<String, Transformation<?>> transformMap = new LinkedHashMap<>();
        java.util.Set<Integer> usedIds = new java.util.HashSet<>();
        for (Transformation<?> t : Arrays.asList(source, sink)) {
            transformMap.put(t.getName(), t);
            int stableId = t.getName().hashCode() & Integer.MAX_VALUE;
            while (!usedIds.add(stableId)) {
                stableId = (stableId + 1) & Integer.MAX_VALUE;
            }
            t.assignStableId(stableId);
        }
        return new StreamModel(new StreamComponents(), transformMap);
    }

    private static PartitionedPlan partitionedPlanOf(StreamModel model) {
        // Same generation route the environment uses (and GMCE re-derives internally).
        List<Transformation<?>> sinks = new ArrayList<>();
        for (Transformation<?> t : model.getTransformations().values()) {
            if (t instanceof SinkTransformation) {
                sinks.add(t);
            }
        }
        StreamGraphGenerator graphGenerator = new StreamGraphGenerator();
        io.nop.stream.core.graph.StreamGraph streamGraph =
                graphGenerator.generate((List) sinks);
        JobGraph jobGraph = new JobGraphGenerator().generate(streamGraph);
        return new PartitionedPlanGenerator().generate(
                jobGraph, jobGraph.getStreamModel().computeFingerprint());
    }

    private static DeploymentPlan deploymentPlanOf(PartitionedPlan pp) {
        return new DeploymentPlan(
                pp.getJobId(), "pipeline-0", pp, "local", "memory", "local", null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> TypeInformation<T> createTypeInfo() {
        return new TypeInformation<T>() {
            @Override
            public Class<T> getTypeClass() {
                return (Class<T>) Object.class;
            }
        };
    }

    @io.nop.stream.core.operators.Shareable
    private static final class NoOpOperator<OUT> implements StreamOperator<OUT>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public void open() {
        }

        @Override
        public void finish() {
        }

        @Override
        public void close() {
        }

        @Override
        public void prepareSnapshotPreBarrier(long checkpointId) {
        }

        @Override
        public void setKeyContextElement1(StreamRecord<?> record) {
        }

        @Override
        public void setKeyContextElement2(StreamRecord<?> record) {
        }

        @Override
        public void setCurrentKey(Object key) {
        }

        @Override
        public Object getCurrentKey() {
            return null;
        }

        @Override
        public void notifyCheckpointComplete(long checkpointId) {
        }
    }
}
