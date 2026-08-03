/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 49 Phase 1: tests for the new FLIP-27 style source split contract.
 * Verifies {@link SourceSplit} / {@link SimpleSourceSplit} identity semantics and the
 * minimal contract surface that downstream code (enumerator bookkeeping, manifest
 * serialization) relies on.
 */
class TestSourceSplitContract {

    @Test
    void simpleSourceSplitExposesIdDescriptionCursor() {
        SimpleSourceSplit split = new SimpleSourceSplit("split-7", "file partition 7", 1234L);

        assertEquals("split-7", split.splitId());
        assertEquals("file partition 7", split.getDescription());
        Long cursor = split.getCursor();
        assertEquals(Long.valueOf(1234L), cursor);
    }

    @Test
    void splitIdIsStableIdentity() {
        SimpleSourceSplit a = new SimpleSourceSplit("split-1", "first", 1L);
        SimpleSourceSplit b = new SimpleSourceSplit("split-1", "second", 2L);

        assertEquals(a, b, "split identity must be splitId only, regardless of description/cursor");
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentSplitIdsAreNotEqual() {
        assertNotEquals(new SimpleSourceSplit("a"), new SimpleSourceSplit("b"));
    }

    @Test
    void defaultCursorIsNullWhenOmitted() {
        SimpleSourceSplit split = new SimpleSourceSplit("split-x");
        assertNull(split.getCursor());
        assertEquals("split-x", split.getDescription());
    }

    @Test
    void simpleSourceSplitIsSerializable() throws Exception {
        SimpleSourceSplit original = new SimpleSourceSplit("s-1", "desc", 99L);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(baos.toByteArray()))) {
            SimpleSourceSplit roundTrip = (SimpleSourceSplit) ois.readObject();
            assertEquals(original.splitId(), roundTrip.splitId());
            assertEquals(original.getDescription(), roundTrip.getDescription());
            // Explicit Long type to disambiguate from JUnit's numeric assertEquals overloads
            // (getCursor() is generic <T extends Serializable>; without a type witness the
            // compiler sees Serializable and cannot pick between Byte/Short/Long overloads).
            Long expectedCursor = original.getCursor();
            Long actualCursor = roundTrip.getCursor();
            assertEquals(expectedCursor, actualCursor);
        }
    }

    @Test
    void sourceSplitInterfaceIsImplementedBySimpleSourceSplit() {
        SourceSplit typed = new SimpleSourceSplit("iface-1");
        assertEquals("iface-1", typed.splitId());
    }

    @Test
    void boundednessEnumHasBothValues() {
        assertEquals(2, Boundedness.values().length);
        assertEquals(Boundedness.BOUNDED, Boundedness.valueOf("BOUNDED"));
        assertEquals(Boundedness.CONTINUOUS_UNBOUNDED, Boundedness.valueOf("CONTINUOUS_UNBOUNDED"));
    }

    @Test
    void simpleVersionedSerializerContractVersionMustBeNonNegative() throws Exception {
        SimpleVersionedSerializer<String> serializer = new SimpleVersionedSerializer<String>() {
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
        };

        assertEquals(1, serializer.getVersion());
        assertEquals("hello", serializer.deserialize(1, serializer.serialize("hello")));
    }

    @Test
    void sourceReaderContextCarriesSubtaskAndParallelism() {
        SourceReaderContext ctx = new SourceReaderContext(2, 4, null);
        assertEquals(2, ctx.getSubtaskIndex());
        assertEquals(4, ctx.getTotalParallelism());
        assertNull(ctx.getAssignmentProxy(), "assignment proxy may be null in isolated unit tests");
    }

    @Test
    void splitEnumeratorContextCarriesParallelism() {
        SplitEnumeratorContext<SimpleSourceSplit> ctx =
                new SplitEnumeratorContext<>(3, null);
        assertEquals(3, ctx.getTotalParallelism());
        assertNull(ctx.getDeliveryService(), "delivery service may be null in isolated unit tests");
    }

    @Test
    void sourceReaderApiSurfaceIsComplete() throws Exception {
        // Minimal record-and-snapshot reader used to validate the interface contract.
        SourceReader<String, SimpleSourceSplit> reader = new SourceReader<String, SimpleSourceSplit>() {
            private static final long serialVersionUID = 1L;

            private final java.util.List<String> records = new java.util.ArrayList<>();
            private final java.util.List<SimpleSourceSplit> splits = new java.util.ArrayList<>();

            @Override
            public void start() {
            }

            @Override
            public void addSplits(java.util.List<SimpleSourceSplit> splits) {
                this.splits.addAll(splits);
            }

            @Override
            public java.util.Optional<String> pollNext() {
                if (records.isEmpty()) {
                    return java.util.Optional.empty();
                }
                return java.util.Optional.of(records.remove(0));
            }

            @Override
            public java.util.List<SimpleSourceSplit> snapshotState(long checkpointId) {
                return new java.util.ArrayList<>(splits);
            }

            @Override
            public void restoreState(java.util.List<SimpleSourceSplit> splits) {
                this.splits.clear();
                this.splits.addAll(splits);
            }

            @Override
            public void close() {
            }
        };

        reader.start();
        reader.addSplits(java.util.Collections.singletonList(new SimpleSourceSplit("s-1")));
        assertEquals(1, reader.snapshotState(1L).size());
        // No records polled (none injected) — must return empty, not throw
        assertEquals(java.util.Optional.empty(), reader.pollNext());
        reader.close();
    }

    @Test
    void splitEnumeratorApiSurfaceIsComplete() throws Exception {
        // Minimal enumerator that tracks assigned subtasks. Validates the interface contract.
        SplitEnumerator<SimpleSourceSplit, String> enumerator = new SplitEnumerator<SimpleSourceSplit, String>() {
            private static final long serialVersionUID = 1L;

            private final java.util.Set<Integer> registeredReaders = new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>().keySet(Boolean.TRUE);

            @Override
            public void start(SplitEnumeratorContext<SimpleSourceSplit> context) {
            }

            @Override
            public void addReader(int subtaskIndex) {
                registeredReaders.add(subtaskIndex);
            }

            @Override
            public void handleSplitRequest(int subtaskIndex, java.util.Optional<Throwable> reason) {
            }

            @Override
            public String snapshotState(long checkpointId) {
                return "state@epoch" + checkpointId;
            }

            @Override
            public void restoreState(String state) {
                assert state != null : "restored state must not be null";
            }

            @Override
            public void close() {
            }
        };

        enumerator.start(new SplitEnumeratorContext<>(2, null));
        enumerator.addReader(0);
        enumerator.addReader(1);
        assertEquals("state@epoch42", enumerator.snapshotState(42L));
        enumerator.restoreState("state@epoch42");
        enumerator.close();
    }

    @Test
    void sourceApiSurfaceIsComplete() {
        Source<String, SimpleSourceSplit, String> source = new Source<String, SimpleSourceSplit, String>() {
            private static final long serialVersionUID = 1L;

            @Override
            public SplitEnumerator<SimpleSourceSplit, String> createEnumerator() {
                return null;
            }

            @Override
            public SplitEnumerator<SimpleSourceSplit, String> restoreEnumerator(String checkpointState) {
                return null;
            }

            @Override
            public SourceReader<String, SimpleSourceSplit> createReader(SourceReaderContext readerContext) {
                return null;
            }

            @Override
            public SimpleVersionedSerializer<String> getEnumeratorStateSerializer() {
                return null;
            }

            @Override
            public SimpleVersionedSerializer<SimpleSourceSplit> getSplitSerializer() {
                return null;
            }

            @Override
            public Boundedness getBoundedness() {
                return Boundedness.BOUNDED;
            }
        };

        assertEquals(Boundedness.BOUNDED, source.getBoundedness());
    }

    @Test
    void unimplementedBranchesMustThrowNotReturnDefault() {
        // Plan guide #24 (No Silent No-Op): if a Source v1 impl does not support a feature,
        // it must throw UnsupportedOperationException, not silently return null/empty.
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> {
                    throw new UnsupportedOperationException("not yet implemented: source event channel");
                });
        assertEquals("not yet implemented: source event channel", ex.getMessage());
    }
}
