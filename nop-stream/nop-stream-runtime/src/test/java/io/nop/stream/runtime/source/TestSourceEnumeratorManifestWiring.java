/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CompletedCheckpoint;
import io.nop.stream.core.checkpoint.EpochManifest;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.SourceEnumeratorSnapshot;
import io.nop.stream.core.execution.task.StreamTaskInvokable;
import io.nop.stream.core.jobgraph.JobGraph;
import io.nop.stream.core.jobgraph.JobVertex;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.operators.SourceReaderOperator;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.Source;
import io.nop.stream.core.source.SourceReader;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SourceSplit;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;
import io.nop.stream.core.source.coordinator.LocalSourceCoordinator;
import io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry;
import io.nop.stream.runtime.checkpoint.CheckpointCoordinator;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 49 Phase 2/3 (D2 + B1 fix): verifies that {@code GraphModelCheckpointExecutor}
 * wires source-api vertices into the {@link CheckpointCoordinator} via
 * {@code registerSourceEnumeratorVertex(int)}, so that
 * {@code buildEpochManifest} → {@code snapshotSourceEnumerators} produces a non-empty
 * {@code sourceEnumeratorSnapshots} manifest section.
 *
 * <p>Anti-Hollow coverage for the B1 fix — if the registration call site regresses,
 * the manifest section will be empty and this test will fail.
 */
class TestSourceEnumeratorManifestWiring {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCoordinatorRegistry() {
        SourceCoordinatorRegistry.clearForTest();
    }

    /**
     * A minimal Source whose enumerator state is a single integer, used to verify the
     * coordinator-state checkpoint mechanism end-to-end at the wiring level.
     */
    static final class IntStateSource implements Source<String, SimpleTestSplit, Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public SplitEnumerator<SimpleTestSplit, Integer> createEnumerator() {
            return new IntStateEnumerator();
        }

        @Override
        public SplitEnumerator<SimpleTestSplit, Integer> restoreEnumerator(Integer state) {
            return new IntStateEnumerator();
        }

        @Override
        public SourceReader<String, SimpleTestSplit> createReader(SourceReaderContext ctx) {
            return new IntStateReader();
        }

        @Override
        public SimpleVersionedSerializer<Integer> getEnumeratorStateSerializer() {
            return new IntSerializer();
        }

        @Override
        public SimpleVersionedSerializer<SimpleTestSplit> getSplitSerializer() {
            return new SimpleTestSplitSerializer();
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }
    }

    static final class SimpleTestSplit implements SourceSplit {
        private static final long serialVersionUID = 1L;
        private final String id;
        SimpleTestSplit(String id) { this.id = id; }
        @Override public String splitId() { return id; }
    }

    static final class IntStateEnumerator implements SplitEnumerator<SimpleTestSplit, Integer> {
        private static final long serialVersionUID = 1L;
        private int state = 42;
        @Override public void start(SplitEnumeratorContext<SimpleTestSplit> ctx) {}
        @Override public void addReader(int subtaskIndex) {}
        @Override public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {}
        @Override public Integer snapshotState(long checkpointId) { return state; }
        @Override public void restoreState(Integer state) { this.state = state; }
        @Override public void close() {}
    }

    static final class IntStateReader implements SourceReader<String, SimpleTestSplit> {
        private static final long serialVersionUID = 1L;
        @Override public void start() {}
        @Override public void addSplits(List<SimpleTestSplit> splits) {}
        @Override public Optional<String> pollNext() { return Optional.empty(); }
        @Override public List<SimpleTestSplit> snapshotState(long checkpointId) { return Collections.emptyList(); }
        @Override public void restoreState(List<SimpleTestSplit> splits) {}
        @Override public void close() {}
    }

    static final class IntSerializer implements SimpleVersionedSerializer<Integer> {
        private static final long serialVersionUID = 1L;
        @Override public byte[] serialize(Integer obj) { return obj.toString().getBytes(); }
        @Override public Integer deserialize(int version, byte[] bytes) { return Integer.parseInt(new String(bytes)); }
        @Override public int getVersion() { return 1; }
    }

    static final class SimpleTestSplitSerializer implements SimpleVersionedSerializer<SimpleTestSplit> {
        private static final long serialVersionUID = 1L;
        @Override public byte[] serialize(SimpleTestSplit obj) { return obj.splitId().getBytes(); }
        @Override public SimpleTestSplit deserialize(int version, byte[] bytes) { return new SimpleTestSplit(new String(bytes)); }
        @Override public int getVersion() { return 1; }
    }

    @Test
    void registerSourceApiVerticesMethodIsWiredIntoCreateCoordinator() throws Exception {
        // B1 Anti-Hollow fix: verify that createCoordinator(_, _, _, _, _, JobGraph) exists
        // AND calls registerSourceApiVertices(coordinator, jobGraph). Without this wiring,
        // registeredSourceVertexIds is always empty → manifest section always empty →
        // enumerator state is lost on restore.
        //
        // Functional end-to-end coverage (where a real checkpoint fires and the manifest
        // is inspected) is provided by TestFileSourceCheckpointRestore (manifest-section
        // round-trip) + TestFileSourceE2E (data path through execute()).

        // 1. The 6-arg createCoordinator must exist (with JobGraph parameter).
        Method createMethod = Class.forName("io.nop.stream.runtime.execution.GraphModelCheckpointExecutor")
                .getDeclaredMethod("createCoordinator",
                        String.class, String.class,
                        CheckpointIDCounter.class,
                        io.nop.stream.core.checkpoint.storage.ICheckpointStorage.class,
                        CheckpointConfig.class,
                        JobGraph.class);
        assertNotNull(createMethod, "createCoordinator(jobId, ..., JobGraph) must exist (B1 fix)");

        // 2. registerSourceApiVertices must exist and call coordinator.registerSourceEnumeratorVertex.
        Method registerMethod = Class.forName("io.nop.stream.runtime.execution.GraphModelCheckpointExecutor")
                .getDeclaredMethod("registerSourceApiVertices",
                        CheckpointCoordinator.class, JobGraph.class);
        assertNotNull(registerMethod, "registerSourceApiVertices(coordinator, jobGraph) must exist");

        // 3. CheckpointCoordinator.registerSourceEnumeratorVertex must exist (Phase 2 added it).
        Method coordRegisterMethod = CheckpointCoordinator.class.getMethod(
                "registerSourceEnumeratorVertex", int.class);
        assertNotNull(coordRegisterMethod,
                "CheckpointCoordinator.registerSourceEnumeratorVertex(int) must exist");

        // 4. SourceCoordinatorRegistry must exist and expose get / registerIfAbsent / unregister
        //    (used by the coordinator-state checkpoint snapshot loop).
        assertNotNull(SourceCoordinatorRegistry.class.getMethod("get", Integer.class));
        assertNotNull(SourceCoordinatorRegistry.class.getMethod(
                "registerIfAbsent", Integer.class, java.util.function.Function.class));
        assertNotNull(SourceCoordinatorRegistry.class.getMethod("unregister", Integer.class));

        // 5. CheckpointCoordinator.buildEpochManifest must call snapshotSourceEnumerators,
        //    which iterates registeredSourceVertexIds. Verify the registeredSourceVertexIds
        //    field exists (added by Phase 2 + B1 fix).
        java.lang.reflect.Field f = CheckpointCoordinator.class.getDeclaredField("registeredSourceVertexIds");
        f.setAccessible(true);
        assertNotNull(f, "CheckpointCoordinator.registeredSourceVertexIds field must exist");
    }

    @Test
    void sourceEnumeratorSnapshotSerializerRoundTripThroughManifestEntry() {
        // Direct verification of the manifest entry shape — version + bytes — that
        // buildEpochManifest produces via snapshotSourceEnumerators → coord.snapshotState
        // → new SourceEnumeratorSnapshot(version, bytes).
        IntSerializer ser = new IntSerializer();
        byte[] bytes = ser.serialize(99);
        SourceEnumeratorSnapshot snap = new SourceEnumeratorSnapshot(ser.getVersion(), bytes);
        assertEquals(1, snap.getVersion());
        assertNotNull(snap.getStateBytes());
        assertEquals(99, ser.deserialize(snap.getVersion(), snap.getStateBytes()));
    }

    // ==================== R5-TE-05: real-driver wiring tests ====================
    //
    // The reflection assertions above only prove the wiring METHODS EXIST.
    // These tests drive the real call chain — a JobGraph with a
    // SourceReaderOperator-headed vertex through GraphModelCheckpointExecutor
    // .createCoordinator → registerSourceApiVertices → CheckpointCoordinator
    // .buildEpochManifest — and assert the recorded side effects: the vertex id
    // lands in the coordinator's registeredSourceVertexIds and the built
    // manifest carries a non-empty sourceEnumeratorSnapshots section. Deleting
    // the wiring call site fails these tests.

    /**
     * Builds a real JobGraph whose single vertex carries a {@link SourceReaderOperator}
     * head (the FLIP-27 source-api path) under the given vertex id.
     */
    private static JobGraph newSourceApiJobGraph(String vertexId) {
        SourceReaderOperator<String> head = new SourceReaderOperator<>(new IntStateSource(), 7);
        OperatorChain chain = new OperatorChain(Collections.singletonList(head));
        JobVertex vertex = new JobVertex(vertexId, "source-vertex", 1,
                Collections.singletonList(chain), new StreamTaskInvokable(chain));
        JobGraph jobGraph = new JobGraph("b1-wiring-job");
        jobGraph.addVertex(vertex);
        return jobGraph;
    }

    private CheckpointCoordinator newCoordinatorViaCreateCoordinator(JobGraph jobGraph)
            throws Exception {
        Method createMethod = Class.forName("io.nop.stream.runtime.execution.GraphModelCheckpointExecutor")
                .getDeclaredMethod("createCoordinator",
                        String.class, String.class,
                        CheckpointIDCounter.class,
                        io.nop.stream.core.checkpoint.storage.ICheckpointStorage.class,
                        CheckpointConfig.class,
                        JobGraph.class);
        createMethod.setAccessible(true);
        return (CheckpointCoordinator) createMethod.invoke(null,
                "b1-wiring-job", "b1-pipe", new CheckpointIDCounter(),
                new LocalFileCheckpointStorage(tempDir.resolve("b1-store").toString()),
                new CheckpointConfig(), jobGraph);
    }

    private static java.util.Set<Integer> registeredVertexIdsOf(CheckpointCoordinator coordinator)
            throws Exception {
        java.lang.reflect.Field f = CheckpointCoordinator.class.getDeclaredField("registeredSourceVertexIds");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<Integer> ids = (java.util.Set<Integer>) f.get(coordinator);
        return ids;
    }

    @Test
    void createCoordinatorActuallyRegistersSourceApiVertex() throws Exception {
        JobGraph jobGraph = newSourceApiJobGraph("vertex-7");

        CheckpointCoordinator coordinator = newCoordinatorViaCreateCoordinator(jobGraph);

        // The observable side effect of registerSourceApiVertices being CALLED:
        // the source vertex id is recorded in the coordinator's registration set.
        java.util.Set<Integer> ids = registeredVertexIdsOf(coordinator);
        assertTrue(ids.contains(7),
                "createCoordinator must call registerSourceEnumeratorVertex(7) for a "
                        + "SourceReaderOperator-headed vertex; registered=" + ids
                        + " (missing registration means the manifest section stays empty "
                        + "and enumerator state is lost on restore)");
    }

    @Test
    void jobGraphWithoutSourceHeadRegistersNoVertices() throws Exception {
        // Control: a plain-operator vertex must NOT be registered — proves the
        // registration is driven by the vertex scan, not a hardcoded add.
        io.nop.stream.core.operators.StreamMap<String, String> mapHead =
                new io.nop.stream.core.operators.StreamMap<>(value -> value);
        OperatorChain chain = new OperatorChain(Collections.singletonList(mapHead));
        JobVertex vertex = new JobVertex("vertex-9", "map-vertex", 1,
                Collections.singletonList(chain), new StreamTaskInvokable(chain));
        JobGraph jobGraph = new JobGraph("b1-control-job");
        jobGraph.addVertex(vertex);

        CheckpointCoordinator coordinator = newCoordinatorViaCreateCoordinator(jobGraph);

        assertTrue(registeredVertexIdsOf(coordinator).isEmpty(),
                "a non-source vertex must not be registered for enumerator snapshotting");
    }

    @Test
    void manifestBuiltByRealCoordinatorCarriesEnumeratorState() throws Exception {
        // Full real-driver chain: registered source vertex + a live
        // LocalSourceCoordinator → buildEpochManifest → non-empty manifest section.
        JobGraph jobGraph = newSourceApiJobGraph("vertex-7");
        CheckpointCoordinator coordinator = newCoordinatorViaCreateCoordinator(jobGraph);
        assertTrue(registeredVertexIdsOf(coordinator).contains(7));

        // Boot a real coordinator for vertex 7 (the same registry the operator's
        // open() populates in production).
        IntStateSource source = new IntStateSource();
        LocalSourceCoordinator<SimpleTestSplit, Integer> coord =
                SourceCoordinatorRegistry.registerIfAbsent(7,
                        v -> new LocalSourceCoordinator<>("vertex-7", source, 1));
        coord.startEnumerator(null);

        CompletedCheckpoint completed = CompletedCheckpoint.builder()
                .jobId("b1-wiring-job").pipelineId("b1-pipe").checkpointId(3L)
                .triggerTimestamp(1L).completedTimestamp(2L)
                .checkpointType(CheckpointType.SAVEPOINT)
                .taskStates(Collections.emptyMap())
                .build();

        Method buildManifest = CheckpointCoordinator.class.getDeclaredMethod(
                "buildEpochManifest", CompletedCheckpoint.class);
        buildManifest.setAccessible(true);
        EpochManifest manifest = (EpochManifest) buildManifest.invoke(coordinator, completed);

        Map<String, SourceEnumeratorSnapshot> section = manifest.getSourceEnumeratorSnapshots();
        assertTrue(section.containsKey("7"),
                "the built manifest must carry a sourceEnumeratorSnapshots entry for the "
                        + "registered source vertex; section=" + section.keySet());
        SourceEnumeratorSnapshot snap = section.get("7");
        assertNotNull(snap);
        assertEquals(42, new IntSerializer().deserialize(snap.getVersion(), snap.getStateBytes()),
                "the manifest entry must carry the live enumerator's state (42)");
    }
}
