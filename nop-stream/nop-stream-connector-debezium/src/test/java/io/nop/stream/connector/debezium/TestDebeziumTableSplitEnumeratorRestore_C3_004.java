/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.stream.core.source.AssignmentDeliveryService;
import io.nop.stream.core.source.SplitEnumeratorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2-C3-004 regression (plan 2503 Phase 3): after a restore, each table must land in
 * EXACTLY ONE share bucket —「每表恰一桶/恰一引擎」. Pre-fix the restored unassigned tables
 * were dumped into a single nextSubtaskIndex bucket while start()'s full recompute ALSO
 * registered them in their position buckets: the same table was assigned to two readers and
 * two engines consumed it (duplicate CDC delivery downstream — jdbc-2pc / file sink duplicate
 * rows). The restored snapshot is a "partial registration crash": readers 0 and 1 got their
 * shares, the job crashed before reader 2 registered.
 *
 * <p>The test uses only pre-existing API surface — the RED baseline is the true live
 * baseline (rule E2).
 */
public class TestDebeziumTableSplitEnumeratorRestore_C3_004 {

    static SplitEnumeratorContext<DebeziumTableSplit> context(int parallelism,
                                                              List<List<String>> sink) {
        return new SplitEnumeratorContext<>(parallelism,
                new AssignmentDeliveryService<DebeziumTableSplit>() {
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

    @Test
    void restoreThenStartAssignsEachTableExactlyOnce_C3_004() {
        List<String> tables = Arrays.asList("db.t0", "db.t1", "db.t2");

        // round 1: readers 0 and 1 registered, crash before reader 2
        DebeziumTableSplitEnumerator first = new DebeziumTableSplitEnumerator(tables);
        List<List<String>> firstDeliveries = new ArrayList<>();
        first.start(context(3, firstDeliveries));
        first.addReader(0);
        first.addReader(1);
        DebeziumCdcEnumeratorState snapshot = first.snapshotState(1L);
        assertEquals(Arrays.asList("db.t2"), snapshot.getUnassignedTables(),
                "the crash snapshot carries the never-delivered table");

        // round 2 (recovery): restore → start → all readers re-register
        DebeziumTableSplitEnumerator restored = new DebeziumTableSplitEnumerator(tables);
        restored.restoreState(snapshot);
        List<List<String>> deliveries = new ArrayList<>();
        restored.start(context(3, deliveries));
        restored.addReader(0);
        restored.addReader(1);
        restored.addReader(2);

        // C3-004: every table assigned EXACTLY once across all readers — no overlaps
        Map<String, Integer> owner = new HashMap<>();
        for (List<String> share : deliveries) {
            for (String table : share) {
                assertNull(owner.putIfAbsent(table, deliveries.indexOf(share)),
                        "pre-fix the restored unassigned table was double-registered (restore "
                                + "bucket + position bucket) and TWO engines consumed it: " + table);
            }
        }
        assertEquals(3, owner.size(), "every table must be assigned after recovery");
        assertTrue(owner.containsKey("db.t2"), "the restored unassigned table is re-offered");

        // nothing left unassigned after full re-registration
        DebeziumCdcEnumeratorState afterRestart = restored.snapshotState(2L);
        assertTrue(afterRestart.getUnassignedTables().isEmpty());
    }
}
