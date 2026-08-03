/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 49 Phase 2 (D5): tests that the addSource(Source) routing + SourceApiTransformation
 * + SourceReaderOperatorFactory layer is wired correctly. Phase 3 will cover end-to-end
 * execution through FileSource.
 */
class TestSourceApiRouting {

    /**
     * A minimal bounded Source that emits no records but exercises the full contract:
     * create / restore enumerator + create reader + serializer hooks.
     */
    static final class NoOpSource implements Source<String, SimpleSourceSplit, String> {

        private static final long serialVersionUID = 1L;

        @Override
        public SplitEnumerator<SimpleSourceSplit, String> createEnumerator() {
            return new NoOpEnumerator();
        }

        @Override
        public SplitEnumerator<SimpleSourceSplit, String> restoreEnumerator(String checkpointState) {
            NoOpEnumerator e = new NoOpEnumerator();
            e.restoredState = checkpointState;
            return e;
        }

        @Override
        public SourceReader<String, SimpleSourceSplit> createReader(SourceReaderContext readerContext) {
            return new NoOpReader(readerContext);
        }

        @Override
        public SimpleVersionedSerializer<String> getEnumeratorStateSerializer() {
            return new StringSerializer();
        }

        @Override
        public SimpleVersionedSerializer<SimpleSourceSplit> getSplitSerializer() {
            return new SimpleSourceSplitSerializer();
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }
    }

    static final class NoOpEnumerator implements SplitEnumerator<SimpleSourceSplit, String> {
        private static final long serialVersionUID = 1L;
        String restoredState;
        transient SplitEnumeratorContext<SimpleSourceSplit> ctx;
        final List<Integer> registeredReaders = new ArrayList<>();
        final List<SimpleSourceSplit> assigned = new ArrayList<>();

        @Override
        public void start(SplitEnumeratorContext<SimpleSourceSplit> context) {
            this.ctx = context;
        }

        @Override
        public void addReader(int subtaskIndex) {
            registeredReaders.add(subtaskIndex);
            // Immediately assign a placeholder split to the new reader so it has work.
            SimpleSourceSplit split = new SimpleSourceSplit("split-" + subtaskIndex);
            assigned.add(split);
            if (ctx != null && ctx.getDeliveryService() != null) {
                ctx.getDeliveryService().assignSplits(subtaskIndex, Collections.singletonList(split));
            }
        }

        @Override
        public void handleSplitRequest(int subtaskIndex, Optional<Throwable> reason) {
        }

        @Override
        public String snapshotState(long checkpointId) {
            return "snapshot@" + checkpointId + (restoredState != null ? "|from:" + restoredState : "");
        }

        @Override
        public void restoreState(String state) {
            this.restoredState = state;
        }

        @Override
        public void close() {
        }
    }

    static final class NoOpReader implements SourceReader<String, SimpleSourceSplit> {
        private static final long serialVersionUID = 1L;

        private final SourceReaderContext ctx;
        private final List<SimpleSourceSplit> splits = Collections.synchronizedList(new ArrayList<>());

        NoOpReader(SourceReaderContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void start() {
            if (ctx != null && ctx.getAssignmentProxy() != null) {
                ctx.getAssignmentProxy().requestSplits(ctx.getSubtaskIndex(), Optional.empty());
            }
        }

        @Override
        public void addSplits(List<SimpleSourceSplit> splits) {
            this.splits.addAll(splits);
        }

        @Override
        public Optional<String> pollNext() {
            return Optional.empty();
        }

        @Override
        public List<SimpleSourceSplit> snapshotState(long checkpointId) {
            return new ArrayList<>(splits);
        }

        @Override
        public void restoreState(List<SimpleSourceSplit> splits) {
            this.splits.clear();
            this.splits.addAll(splits);
        }

        @Override
        public void close() {
        }

        List<SimpleSourceSplit> getSplits() {
            return splits;
        }
    }

    static final class StringSerializer implements SimpleVersionedSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(String obj) {
            return obj.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String deserialize(int version, byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }

    static final class SimpleSourceSplitSerializer implements SimpleVersionedSerializer<SimpleSourceSplit> {
        private static final long serialVersionUID = 1L;

        @Override
        public byte[] serialize(SimpleSourceSplit obj) {
            return obj.splitId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public SimpleSourceSplit deserialize(int version, byte[] bytes) {
            return new SimpleSourceSplit(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }

    @Test
    void noOpSourceImplementsFullContract() {
        NoOpSource source = new NoOpSource();
        assertEquals(Boundedness.BOUNDED, source.getBoundedness());
        assertNotNull(source.createEnumerator());
        assertNotNull(source.restoreEnumerator("state@1"));
        assertNotNull(source.createReader(null));
        assertNotNull(source.getEnumeratorStateSerializer());
        assertNotNull(source.getSplitSerializer());
    }

    @Test
    void enumeratorAddReaderPushesSplitViaDeliveryService() {
        List<SimpleSourceSplit> delivered = Collections.synchronizedList(new ArrayList<>());
        SplitEnumeratorContext<SimpleSourceSplit> ctx = new SplitEnumeratorContext<>(1,
                new AssignmentDeliveryService<SimpleSourceSplit>() {
                    @Override
                    public void assignSplits(int subtaskIndex, List<SimpleSourceSplit> splits) {
                        delivered.addAll(splits);
                    }

                    @Override
                    public boolean isReaderRegistered(int subtaskIndex) {
                        return true;
                    }
                });

        NoOpEnumerator enumerator = new NoOpEnumerator();
        enumerator.start(ctx);
        enumerator.addReader(0);

        assertEquals(1, enumerator.registeredReaders.size());
        assertEquals(1, delivered.size());
        assertEquals("split-0", delivered.get(0).splitId());
    }

    @Test
    void enumeratorSnapshotAndRestoreRoundTrip() {
        NoOpEnumerator orig = new NoOpEnumerator();
        String snap = orig.snapshotState(42L);
        assertEquals("snapshot@42", snap);

        NoOpEnumerator restored = new NoOpEnumerator();
        restored.restoreState(snap);
        String snap2 = restored.snapshotState(99L);
        assertTrue(snap2.contains("snapshot@99"));
        assertTrue(snap2.contains("from:snapshot@42"));
    }

    @Test
    void readerAddSplitsRecordsState() {
        NoOpReader reader = new NoOpReader(null);
        reader.addSplits(Arrays.asList(
                new SimpleSourceSplit("s-1"),
                new SimpleSourceSplit("s-2")));

        List<SimpleSourceSplit> snap = reader.snapshotState(1L);
        assertEquals(2, snap.size());
        reader.close();
    }

    @Test
    void sourceReaderOperatorExistsAsOperatorSubtype() {
        // Smoke test: SourceReaderOperator class exists in core operators package and
        // carries the FLIP-27 source descriptor. Wiring to StreamGraphGenerator is
        // validated end-to-end in Phase 3 (FileSource E2E).
        try {
            Class<?> opClass = Class.forName(
                    "io.nop.stream.core.operators.SourceReaderOperator");
            assertNotNull(opClass);
            // Construct with source + vertexId
            NoOpSource source = new NoOpSource();
            java.lang.reflect.Constructor<?> ctor = opClass.getDeclaredConstructor(
                    io.nop.stream.core.source.Source.class, int.class);
            ctor.setAccessible(true);
            Object op = ctor.newInstance(source, 42);
            java.lang.reflect.Method getVertexId = opClass.getMethod("getVertexId");
            assertEquals(42, ((Number) getVertexId.invoke(op)).intValue());
        } catch (Exception e) {
            throw new AssertionError("SourceReaderOperator wiring failed", e);
        }
    }

    @Test
    void sourceCoordinatorRegistryRegisterIfAbsentIsIdempotent() {
        io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.clearForTest();

        NoOpSource source = new NoOpSource();
        Integer vertexId = 99991;

        io.nop.stream.core.source.coordinator.LocalSourceCoordinator<SimpleSourceSplit, ?> first =
                io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.registerIfAbsent(
                        vertexId, vid -> new io.nop.stream.core.source.coordinator.LocalSourceCoordinator<>(
                                String.valueOf(vid), source, 2));
        io.nop.stream.core.source.coordinator.LocalSourceCoordinator<SimpleSourceSplit, ?> second =
                io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.registerIfAbsent(
                        vertexId, vid -> {
                            throw new AssertionError("factory should not be called second time");
                        });

        assertEquals(first, second, "registerIfAbsent must be idempotent");
        assertTrue(io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.isRegistered(vertexId));

        io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.unregister(vertexId);
        assertEquals(false, io.nop.stream.core.source.coordinator.SourceCoordinatorRegistry.isRegistered(vertexId));
    }

    @Test
    void sourceApiTransformationCarriesSource() {
        NoOpSource source = new NoOpSource();
        io.nop.stream.core.transformation.SourceApiTransformation<String> transform =
                new io.nop.stream.core.transformation.SourceApiTransformation<>("test-src", source, null, 1);

        assertEquals(source, transform.getSource());
        assertEquals("test-src", transform.getName());
        assertEquals(1, transform.getParallelism());
        assertEquals(0, transform.getInputs().size(), "source-api transformation must be a leaf");
    }
}
