/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.operators.SourceReaderOperator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * R5 ST-13 regression pin: the split list handed to the checkpoint snapshot
 * must be a point-in-time container view. The reader keeps mutating its live
 * split list after the barrier; clearing/re-adding entries there must never
 * leak into the snapshot (split impls carry final fields, so the operator's
 * shallow container copy is the freeze point).
 */
class TestSourceReaderOperatorSnapshotIsolation {

    /** Mirrors the real contract: split impls carry final fields (immutable). */
    static class SimpleTestSplit implements SourceSplit {
        private static final long serialVersionUID = 1L;
        private final String splitId;

        SimpleTestSplit(String splitId) {
            this.splitId = splitId;
        }

        @Override
        public String splitId() {
            return splitId;
        }
    }

    static class FakeReader implements SourceReader<String, SimpleTestSplit> {
        final List<SimpleTestSplit> splits = new ArrayList<>();

        @Override
        public void start() {
        }

        @Override
        public void addSplits(List<SimpleTestSplit> newSplits) {
            splits.addAll(newSplits);
        }

        @Override
        public Optional<String> pollNext() {
            return Optional.empty();
        }

        @Override
        public List<SimpleTestSplit> snapshotState(long checkpointId) {
            return splits;
        }

        @Override
        public void restoreState(List<SimpleTestSplit> restored) {
            splits.addAll(restored);
        }

        @Override
        public void close() {
        }
    }

    static class FakeSource implements Source<String, SimpleTestSplit, Void> {
        private static final long serialVersionUID = 1L;

        @Override
        public SplitEnumerator<SimpleTestSplit, Void> createEnumerator() {
            throw new UnsupportedOperationException("not needed for this pin");
        }

        @Override
        public SplitEnumerator<SimpleTestSplit, Void> restoreEnumerator(Void state) {
            throw new UnsupportedOperationException("not needed for this pin");
        }

        @Override
        public SourceReader<String, SimpleTestSplit> createReader(SourceReaderContext ctx) {
            return new FakeReader();
        }

        @Override
        public SimpleVersionedSerializer<Void> getEnumeratorStateSerializer() {
            throw new UnsupportedOperationException("not needed for this pin");
        }

        @Override
        public SimpleVersionedSerializer<SimpleTestSplit> getSplitSerializer() {
            // WI2 plan 2343 Phase 0: the operator encodes split snapshots through this
            // serializer (the FLIP-27 contract) into the JSON-safe envelope.
            return new SimpleVersionedSerializer<SimpleTestSplit>() {
                @Override
                public int getVersion() {
                    return 1;
                }

                @Override
                public byte[] serialize(SimpleTestSplit split) {
                    return split.splitId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }

                @Override
                public SimpleTestSplit deserialize(int version, byte[] bytes) throws java.io.IOException {
                    if (version != 1) {
                        throw new java.io.IOException("Unsupported SimpleTestSplit version: " + version);
                    }
                    return new SimpleTestSplit(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                }
            };
        }

        @Override
        public Boundedness getBoundedness() {
            return Boundedness.BOUNDED;
        }
    }

    @Test
    void testSplitListMutationAfterSnapshotDoesNotLeak() throws Exception {
        FakeReader reader = new FakeReader();
        SourceReaderOperator<String> operator = new SourceReaderOperator<>(new FakeSource(), 0) {
            @Override
            public void open() {
                // bypass Source wiring; inject the reader directly
                try {
                    java.lang.reflect.Field f = SourceReaderOperator.class.getDeclaredField("reader");
                    f.setAccessible(true);
                    f.set(this, reader);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };

        SimpleTestSplit split = new SimpleTestSplit("s1");
        reader.splits.add(split);

        operator.open();
        OperatorSnapshotResult snapshot =
                operator.snapshotState(new StateSnapshotContext(1L, System.currentTimeMillis()));

        // WI2 Phase 0 envelope: {serializerVersion, encodedSplits(base64)} — the split
        // payload travels through the source's own serializer (JSON-safe).
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> envelope =
                (java.util.Map<String, Object>) snapshot.getOperatorState("source-reader-splits");
        assertNotNull(envelope);
        assertEquals(1, envelope.get("serializerVersion"));
        @SuppressWarnings("unchecked")
        List<String> encoded = (List<String>) envelope.get("encodedSplits");
        assertNotNull(encoded);
        assertEquals(1, encoded.size());
        // The snapshot must carry its own container, not the reader's live list
        // (split impls carry final fields; the freeze point is the encoded copy).

        // Post-barrier: reader restructures its live split list
        reader.splits.clear();
        reader.splits.add(new SimpleTestSplit("s2"));

        assertEquals(1, encoded.size(), "post-snapshot split list restructure leaked into the snapshot");
        assertEquals("s1", new String(java.util.Base64.getDecoder().decode(encoded.get(0)),
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
