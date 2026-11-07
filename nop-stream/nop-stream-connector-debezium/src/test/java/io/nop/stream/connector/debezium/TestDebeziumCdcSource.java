/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.Boundedness;
import io.nop.stream.core.source.SimpleVersionedSerializer;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitAssignmentProxy;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI2 (plan 2343 Phase 3): the FLIP-27 debezium source semantics — serializer
 * round-trips, the R2 table=split enumerator (shares, restore, parallelism gate), the
 * reader wiring (instance-scoped engine host via the M-1 combination path, instance-
 * level offset map lifted into split snapshots, restore replay, D-4 late-assignment
 * guard), and support-class scoping rules.
 */
public class TestDebeziumCdcSource {

    static DebeziumConfig config(String name, String includeList) {
        DebeziumConfig cfg = new DebeziumConfig();
        cfg.setName(name);
        cfg.setTableIncludeList(includeList);
        return cfg;
    }

    static List<List<String>> delivered() {
        return new ArrayList<>();
    }

    static SplitEnumeratorContext<DebeziumTableSplit> context(
            int parallelism, List<List<String>> sink) {
        return new SplitEnumeratorContext<>(parallelism,
                new io.nop.stream.core.source.AssignmentDeliveryService<DebeziumTableSplit>() {
                    @Override
                    public void assignSplits(int subtaskIndex, List<DebeziumTableSplit> splits) {
                        List<String> tables = new ArrayList<>();
                        for (DebeziumTableSplit split : splits) {
                            tables.add(split.getTableEntry());
                        }
                        sink.add(tables);
                    }

                    @Override
                    public boolean isReaderRegistered(int subtaskIndex) {
                        return true;
                    }
                });
    }

    // ---------------- serializer round-trips ----------------

    @Test
    void splitSerializerRoundTripsTableAndOffsetMap() throws Exception {
        SimpleVersionedSerializer<DebeziumTableSplit> serializer = new DebeziumCdcSource.TableSplitSerializer();
        Map<String, String> offsets = new LinkedHashMap<>();
        offsets.put("k1", "v1");
        byte[] bytes = serializer.serialize(new DebeziumTableSplit("db.t1", offsets, true));
        DebeziumTableSplit restored = serializer.deserialize(serializer.getVersion(), bytes);
        assertEquals("db.t1", restored.getTableEntry());
        assertTrue(restored.isOffsetCarrying());
        assertEquals("v1", restored.getOffsetMap().get("k1"));

        byte[] bare = serializer.serialize(new DebeziumTableSplit("db.t2"));
        DebeziumTableSplit bareRestored = serializer.deserialize(1, bare);
        assertEquals("db.t2", bareRestored.getTableEntry());
        assertFalse(bareRestored.isOffsetCarrying());
        assertThrows(Exception.class, () -> serializer.deserialize(9, bytes),
                "version mismatch must fail");
    }

    @Test
    void enumeratorStateSerializerRoundTripsUnassignedTables() throws Exception {
        SimpleVersionedSerializer<DebeziumCdcEnumeratorState> serializer =
                new DebeziumCdcSource.EnumeratorStateSerializer();
        DebeziumCdcEnumeratorState restored = serializer.deserialize(1, serializer.serialize(
                new DebeziumCdcEnumeratorState(Arrays.asList("db.a", "db.b"), 2)));
        assertEquals(Arrays.asList("db.a", "db.b"), restored.getUnassignedTables());
        assertEquals(2, restored.getNextSubtaskIndex());
    }

    // ---------------- enumerator: 表 = split（R2） ----------------

    @Test
    void enumeratorDeliversWholeRoundRobinSharesAtRegistration() throws Exception {
        DebeziumCdcSource source = new DebeziumCdcSource(config("cdc", "db.a,db.b,db.c"));
        SplitEnumerator<DebeziumTableSplit, DebeziumCdcEnumeratorState> enumerator =
                source.createEnumerator();
        List<List<String>> deliveries = delivered();
        enumerator.start(context(2, deliveries));

        enumerator.addReader(0);
        enumerator.addReader(1);
        assertEquals(2, deliveries.size());
        // round-robin: reader0=[a,c], reader1=[b] — each table assigned exactly once
        assertEquals(Arrays.asList("db.a", "db.c"), deliveries.get(0));
        assertEquals(List.of("db.b"), deliveries.get(1));

        // snapshot: nothing unassigned after full registration
        DebeziumCdcEnumeratorState state = enumerator.snapshotState(1L);
        assertTrue(state.getUnassignedTables().isEmpty());
    }

    @Test
    void enumeratorRejectsParallelismAboveTableCount() {
        DebeziumCdcSource source = new DebeziumCdcSource(config("cdc", "db.a"));
        SplitEnumerator<DebeziumTableSplit, DebeziumCdcEnumeratorState> enumerator =
                source.createEnumerator();
        StreamException ex = assertThrows(StreamException.class,
                () -> enumerator.start(context(2, delivered())),
                "parallelism above the table count must fail at enumerator start (legacy parity)");
        assertTrue(String.valueOf(ex).contains("exceeds tableIncludeList size"));

        DebeziumCdcSource emptySource = new DebeziumCdcSource(config("cdc", ""));
        SplitEnumerator<DebeziumTableSplit, DebeziumCdcEnumeratorState> emptyEnum =
                emptySource.createEnumerator();
        assertThrows(StreamException.class, () -> emptyEnum.start(context(1, delivered())),
                "an empty include list must fail typed (no silent idle engine)");
    }

    // ---------------- reader wiring ----------------

    @Test
    void readerScopesTheEngineInstanceAndLiftsOffsetsIntoSplitSnapshots() throws Exception {
        DebeziumConfig cfg = config("cdc", "db.a,db.b");
        DebeziumCdcSourceReader reader = new StubbedReader(cfg, null, null);
        // parallelism 2 → instance name carries the subtask suffix (legacy parity)
        reader.setContextForTest(readerCtx(0, 2));
        reader.start();
        reader.addSplits(List.of(new DebeziumTableSplit("db.a"), new DebeziumTableSplit("db.b")));

        StubbedReader stub = (StubbedReader) reader;
        assertNotNull(stub.launchedConfig.get(), "the engine host must launch on assignment");
        assertEquals("cdc-0", stub.launchedConfig.get().getName(),
                "instance name = base-subtask (offset registry bucket isolation)");
        assertEquals("db.a,db.b", stub.launchedConfig.get().getTableIncludeList(),
                "the assigned table share scopes the engine include list (M-1 combination path)");
        assertEquals("cdc-0", stub.launchedConfig.get().getServerName(),
                "serverName instance-qualified (JMX/topic prefix parity)");
        assertTrue(stub.launchedConfig.get().getSchemaHistoryJdbcTable().endsWith("cdc_0"),
                "schema history instance-qualified (AR parity)");

        // the offset map lifts into EVERY split snapshot (instance-level copy, M-2)
        stub.offsets.put("partition-key", "base64-offset");
        List<DebeziumTableSplit> snapshot = reader.snapshotState(7L);
        assertEquals(2, snapshot.size());
        for (DebeziumTableSplit split : snapshot) {
            assertTrue(split.isOffsetCarrying());
            assertEquals("base64-offset", split.getOffsetMap().get("partition-key"));
        }
        assertFalse(reader.isFinished(), "UNBOUNDED reader only stops via close");

        reader.close();
    }

    @Test
    void restoredOffsetsReplayIntoTheEmbeddedStateContract() throws Exception {
        DebeziumConfig cfg = config("cdc", "db.a");
        StubbedReader reader = new StubbedReader(cfg, null, null);
        Map<String, String> offsets = new LinkedHashMap<>();
        // cdc-offsets keys AND values are base64 (NopStreamOffsetBackingStore.toSerializable)
        offsets.put(java.util.Base64.getEncoder().encodeToString("p".getBytes()),
                java.util.Base64.getEncoder().encodeToString("offset-42".getBytes()));
        // restore BEFORE start (the runtime restore path): tables + instance offset map
        reader.restoreState(List.of(new DebeziumTableSplit("db.a", offsets, true)));
        reader.setContextForTest(readerCtx(0, 1));
        reader.start();

        assertNotNull(reader.getEmbeddedFunctionForTest(), "the engine host must launch from restored splits");
        assertNotNull(reader.getEmbeddedFunctionForTest().getOffsetStore(),
                "the embedded initializeState contract must have run (offset store bound)");
        // single-instance run keeps the base connector name (legacy parity)
        assertEquals("cdc", reader.launchedConfig.get().getName());
        reader.close();
    }

    @Test
    void lateAssignmentOfNewTablesFailsTyped() throws Exception {
        DebeziumConfig cfg = config("cdc", "db.a,db.b");
        StubbedReader reader = new StubbedReader(cfg, null, null);
        reader.setContextForTest(readerCtx(0, 2));
        reader.start();
        reader.addSplits(List.of(new DebeziumTableSplit("db.a")));
        reader.addSplits(List.of(new DebeziumTableSplit("db.a"))); // re-delivery: idempotent
        StreamException ex = assertThrows(StreamException.class,
                () -> reader.addSplits(List.of(new DebeziumTableSplit("db.b"))),
                "a live engine cannot reconfigure its include list — D-4 typed guard");
        assertTrue(String.valueOf(ex).contains("late assignment"));
        reader.close();
    }

    static SourceReaderContext readerCtx(int subtask, int parallelism) {
        return new SourceReaderContext(subtask, parallelism, new SplitAssignmentProxy() {
            @Override
            public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
            }

            @Override
            public void reportFinishedSplits(int subtaskIndex, List<String> finishedSplitIds) {
            }
        });
    }

    /** Reader with a stub engine host: run() blocks until cancel, offsets test-driven. */
    static class StubbedReader extends DebeziumCdcSourceReader {
        final AtomicReference<DebeziumConfig> launchedConfig = new AtomicReference<>();
        final Map<String, String> offsets = new LinkedHashMap<>();

        StubbedReader(DebeziumConfig config, io.nop.credential.api.ICredentialProvider provider,
                      Map<String, String> initialOffsets) {
            super(config, provider, initialOffsets);
        }

        final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();
        /** 显式交接信号：cancel 释放 run() 的阻塞等待（60s 上限仅挂死保护）。 */
        final java.util.concurrent.CountDownLatch cancelSignal =
                new java.util.concurrent.CountDownLatch(1);

        @Override
        protected DebeziumCdcSourceFunction createEmbeddedFunction(DebeziumConfig engineConfig) {
            launchedConfig.set(engineConfig);
            return new DebeziumCdcSourceFunction(engineConfig) {
                @Override
                public io.nop.stream.core.checkpoint.OperatorSnapshotResult snapshotState(long id) {
                    var result = new io.nop.stream.core.checkpoint.OperatorSnapshotResult();
                    result.setCheckpointId(id);
                    result.putOperatorState(DebeziumCdcSourceFunction.CDC_OFFSETS_KEY,
                            new LinkedHashMap<>(offsets));
                    return result;
                }

                @Override
                public void run(io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<io.nop.message.debezium.ChangeEvent> ctx)
                        throws Exception {
                    // live-subscription stand-in: block until cancel (explicit latch handoff,
                    // no wall-clock polling)
                    cancelSignal.await(60, java.util.concurrent.TimeUnit.SECONDS);
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                    cancelSignal.countDown();
                }
            };
        }
    }
}
