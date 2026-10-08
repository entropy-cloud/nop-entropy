/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchLoaderProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitAssignmentProxy;
import io.nop.stream.core.source.SplitEnumeratorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 (plan 2343 Phase 1): the FLIP-27 batch-loader source semantics — serializer
 * round-trips, assign-once enumerator, incremental reader traversal with the AR-13
 * skip-offset restore convention, and the typed guards (skip shortfall, post-start
 * assignment/restore, single-writer).
 */
public class TestBatchLoaderSource {

    /** Loader over a fixed list, tracking the traversal position (deterministic order). */
    static IBatchLoaderProvider<String> listLoader(String... items) {
        List<String> all = Arrays.asList(items);
        return new IBatchLoaderProvider<>() {
            @Override
            public IBatchLoader<String> setup(IBatchTaskContext taskContext) {
                return new IBatchLoader<>() {
                    int position = 0;

                    @Override
                    public List<String> load(int batchSize, IBatchChunkContext chunkContext) {
                        if (position >= all.size()) {
                            return List.of();
                        }
                        int end = Math.min(all.size(), position + batchSize);
                        List<String> batch = all.subList(position, end);
                        position = end;
                        return batch;
                    }
                };
            }
        };
    }

    static SourceReaderContext readerContext(int parallelism, SplitAssignmentProxy proxy) {
        return new SourceReaderContext(0, parallelism, proxy);
    }

    // ---------------- serializer round-trips ----------------

    @Test
    void splitSerializerRoundTripsOffset() throws Exception {
        SimpleVersionedSerializer<BatchLoaderSplit> serializer = new BatchLoaderSource.SplitSerializer();
        byte[] bytes = serializer.serialize(new BatchLoaderSplit(42L));
        BatchLoaderSplit restored = serializer.deserialize(serializer.getVersion(), bytes);
        assertEquals(42L, restored.getOffset());
        assertEquals(BatchLoaderSplit.SPLIT_ID, restored.splitId());
        assertThrows(Exception.class, () -> serializer.deserialize(9, bytes),
                "version mismatch must fail, not silently mis-parse");
    }

    @Test
    void enumeratorStateSerializerRoundTripsAssignedFlag() throws Exception {
        SimpleVersionedSerializer<BatchLoaderEnumeratorState> serializer =
                new BatchLoaderSource.EnumeratorStateSerializer();
        assertEquals(true, serializer.deserialize(1, serializer.serialize(
                new BatchLoaderEnumeratorState(true))).isAssigned());
        assertEquals(false, serializer.deserialize(1, serializer.serialize(
                new BatchLoaderEnumeratorState(false))).isAssigned());
    }

    // ---------------- enumerator ----------------

    @Test
    void enumeratorAssignsTheSingleTokenExactlyOnce() {
        BatchLoaderSplitEnumerator enumerator = new BatchLoaderSplitEnumerator();
        List<List<BatchLoaderSplit>> deliveries = new ArrayList<>();
        enumerator.start(new SplitEnumeratorContext<>(1, new io.nop.stream.core.source.AssignmentDeliveryService<BatchLoaderSplit>() {
            @Override
            public void assignSplits(int subtaskIndex, List<BatchLoaderSplit> splits) {
                deliveries.add(splits);
            }

            @Override
            public boolean isReaderRegistered(int subtaskIndex) {
                return true;
            }
        }));

        enumerator.addReader(0);
        enumerator.handleSplitRequest(0, Optional.empty());
        enumerator.addReader(0); // repeated registration must not re-deliver

        assertEquals(1, deliveries.size(), "the single token is assigned exactly once");
        assertEquals(1, deliveries.get(0).size());
        assertEquals(0L, deliveries.get(0).get(0).getOffset());
        assertTrue(enumerator.isAssigned());
        assertEquals(true, enumerator.snapshotState(1L).isAssigned());

        // restore with assigned=true must NOT re-assign (the reader replays its own state)
        BatchLoaderSplitEnumerator restored = new BatchLoaderSplitEnumerator();
        restored.restoreState(new BatchLoaderEnumeratorState(true));
        List<List<BatchLoaderSplit>> afterRestore = new ArrayList<>(deliveries);
        restored.start(new SplitEnumeratorContext<>(1, new io.nop.stream.core.source.AssignmentDeliveryService<BatchLoaderSplit>() {
            @Override
            public void assignSplits(int subtaskIndex, List<BatchLoaderSplit> splits) {
                afterRestore.add(splits);
            }

            @Override
            public boolean isReaderRegistered(int subtaskIndex) {
                return true;
            }
        }));
        restored.addReader(0);
        assertEquals(1, afterRestore.size(), "restored assignment must not re-deliver the token");
    }

    // ---------------- reader traversal + guards ----------------

    @Test
    void readerTraversesIncrementallyAndSnapshotsTheCursor() throws Exception {
        BatchLoaderSourceReader<String> reader = new BatchLoaderSourceReader<>(listLoader("a", "b", "c"), 2);
        reader.addSplits(List.of(new BatchLoaderSplit(0L))); // pre-open assignment (engine order)
        reader.start();

        assertEquals(Optional.of("a"), reader.pollNext());
        assertEquals(Optional.of("b"), reader.pollNext());
        assertEquals(2L, reader.getCurrentOffsetForTest());
        assertEquals(1, reader.snapshotState(1L).size());
        assertEquals(2L, reader.snapshotState(1L).get(0).getOffset(),
                "the split cursor follows the next-index convention");

        assertEquals(Optional.of("c"), reader.pollNext());
        assertEquals(Optional.empty(), reader.pollNext(), "empty batch is natural EOS");
        assertTrue(reader.isFinished());
    }

    @Test
    void restorePositionsTheTraversalWithClientSideSkip() throws Exception {
        BatchLoaderSourceReader<String> reader = new BatchLoaderSourceReader<>(listLoader("a", "b", "c", "d"), 10);
        reader.restoreState(List.of(new BatchLoaderSplit(2L))); // pre-open restore (engine order)
        reader.start();

        // a/b consumed but not emitted (AR-13 client-side skip); emission resumes at c
        assertEquals(Optional.of("c"), reader.pollNext());
        assertEquals(Optional.of("d"), reader.pollNext());
        assertEquals(Optional.empty(), reader.pollNext());
        assertEquals(4L, reader.getCurrentOffsetForTest());
    }

    @Test
    void skipShortfallFailsTypedInsteadOfEmittingFromWrongPosition() throws Exception {
        BatchLoaderSourceReader<String> reader = new BatchLoaderSourceReader<>(listLoader("a", "b"), 10);
        reader.restoreState(List.of(new BatchLoaderSplit(5L)));
        reader.start();
        StreamException ex = assertThrows(StreamException.class, () -> reader.pollNext(),
                "loader exhausted while skips remain — typed failure, never a silent wrong-position emit");
        assertTrue(String.valueOf(ex).contains("skip shortfall"));
    }

    @Test
    void postStartAssignmentAndRestoreFailTyped() throws Exception {
        BatchLoaderSourceReader<String> reader = new BatchLoaderSourceReader<>(listLoader("a"), 1);
        reader.addSplits(List.of(new BatchLoaderSplit(0L)));
        reader.start();
        // post-start: the traversal is live — both late arrivals fail typed
        assertThrows(StreamException.class, () -> reader.addSplits(List.of(new BatchLoaderSplit(0L))),
                "a post-start assignment would restart the traversal — typed refusal");
        assertThrows(StreamException.class, () -> reader.restoreState(List.of(new BatchLoaderSplit(0L))),
                "a post-start restore would rewind the live cursor — typed refusal");
    }

    @Test
    void parallelismGreaterThanOneIsRejectedAtStartAndByValidation() {
        BatchLoaderSource<String> source = new BatchLoaderSource<>(listLoader("a"));
        assertThrows(StreamException.class, () -> source.validateParallelism(2),
                "single-writer deployment gate");
        source.validateParallelism(1); // idempotent at the supported value
        assertEquals(Boundedness.BOUNDED, source.getBoundedness());

        // reader-side belt-and-braces: parallelism > 1 fails at start, before any record
        BatchLoaderSourceReader<String> gated = new BatchLoaderSourceReader<>(listLoader("a"), 1);
        // simulate a >1 deployment: the reader constructor has no parallelism, the start
        // guard reads it from the context
        SourceReaderContext ctx = readerContext(2, new SplitAssignmentProxy() {
            @Override
            public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
            }

            @Override
            public void reportFinishedSplits(int subtaskIndex, List<String> finishedSplitIds) {
            }
        });
        setContext(gated, ctx);
        assertThrows(StreamException.class, () -> gated.start(),
                "reader-side single-writer gate (belt-and-braces)");
    }

    @Test
    void connectivityProbeSurfacesNullLoaderTyped() throws Exception {
        BatchLoaderSource<String> source = new BatchLoaderSource<>(
                taskContext -> null, 1);
        StreamException ex = assertThrows(StreamException.class, source::checkConnection,
                "a null loader must fail the probe typed, not pass silently");
        assertTrue(String.valueOf(ex).contains("null loader"));
        // a working loader closes cleanly through the probe
        new BatchLoaderSource<>(listLoader("a"), 1).checkConnection();
    }

    static void setContext(BatchLoaderSourceReader<String> reader, SourceReaderContext ctx) {
        try {
            java.lang.reflect.Field f = BatchLoaderSourceReader.class
                    .getDeclaredField("context");
            f.setAccessible(true);
            f.set(reader, ctx);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
