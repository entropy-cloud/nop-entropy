/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HeapInternalTimerService#snapshotTimers()} and
 * {@link HeapInternalTimerService#restoreTimers(HeapInternalTimerService.TimerSnapshot)}.
 *
 * <p>These tests verify G2 (timer state survives checkpoint/restore) at the unit level:
 * the round-trip preserves all registered timers, restored timers fire at the correct
 * timestamp, and the restore path is a correct no-op for empty snapshots.
 *
 * <p>The deferred-application pattern (restoreState stores snapshot, open() applies it)
 * is verified at the WindowOperator level in
 * {@code nop-stream-runtime/src/test/.../checkpoint/TestTimerCheckpointRestoreE2E}.
 */
public class TestHeapInternalTimerServiceSnapshotRestore {

    private Triggerable<String, String> recordingTriggerable(List<InternalTimer<String, String>> eventFired,
                                                              List<InternalTimer<String, String>> processingFired) {
        return new Triggerable<String, String>() {
            @Override
            public void onEventTime(InternalTimer<String, String> timer) throws Exception {
                eventFired.add(timer);
            }

            @Override
            public void onProcessingTime(InternalTimer<String, String> timer) throws Exception {
                processingFired.add(timer);
            }
        };
    }

    @Test
    void testSnapshotRoundTripPreservesAllTimers() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        List<InternalTimer<String, String>> processingFired = new ArrayList<>();
        HeapInternalTimerService<String, String> original =
                new HeapInternalTimerService<>(recordingTriggerable(eventFired, processingFired), () -> "key-A");

        original.registerEventTimeTimer("ns-et-1", 1000L);
        original.registerEventTimeTimer("ns-et-2", 2000L);
        original.registerProcessingTimeTimer("ns-pt-1", 1500L);
        original.registerProcessingTimeTimer("ns-pt-2", 3000L);
        original.advanceWatermark(500L);

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = original.snapshotTimers();

        assertEquals(2, snapshot.getEventTimeTimers().size());
        assertEquals(2, snapshot.getProcessingTimeTimers().size());
        assertEquals(500L, snapshot.getCurrentWatermark());
        assertFalse(snapshot.isEmpty());

        HeapInternalTimerService<String, String> restored =
                new HeapInternalTimerService<>(recordingTriggerable(new ArrayList<>(), new ArrayList<>()));
        restored.restoreTimers(snapshot);

        assertEquals(2, restored.numEventTimeTimers());
        assertEquals(2, restored.numProcessingTimeTimers());
        assertEquals(500L, restored.currentWatermark());
    }

    @Test
    void testRestoredEventTimeTimersFireAtCorrectTimestamp() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        HeapInternalTimerService<String, String> original =
                new HeapInternalTimerService<>(recordingTriggerable(eventFired, new ArrayList<>()), () -> "key-A");
        original.registerEventTimeTimer("ns-1", 1000L);
        original.registerEventTimeTimer("ns-2", 2000L);

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = original.snapshotTimers();

        List<InternalTimer<String, String>> restoredFired = new ArrayList<>();
        HeapInternalTimerService<String, String> restored =
                new HeapInternalTimerService<>(recordingTriggerable(restoredFired, new ArrayList<>()));
        restored.restoreTimers(snapshot);

        assertEquals(0, restoredFired.size(), "Restoring should not fire timers");

        restored.advanceWatermark(1500L);
        assertEquals(1, restoredFired.size());
        assertEquals(1000L, restoredFired.get(0).getTimestamp());
        assertEquals("ns-1", restoredFired.get(0).getNamespace());
        assertEquals("key-A", restoredFired.get(0).getKey());

        restored.advanceWatermark(2500L);
        assertEquals(2, restoredFired.size());
        assertEquals(2000L, restoredFired.get(1).getTimestamp());
    }

    @Test
    void testRestoredProcessingTimeTimersFireAtCorrectTimestamp() throws Exception {
        List<InternalTimer<String, String>> processingFired = new ArrayList<>();
        HeapInternalTimerService<String, String> original =
                new HeapInternalTimerService<>(recordingTriggerable(new ArrayList<>(), processingFired), () -> "key-A");
        original.registerProcessingTimeTimer("ns-1", 1000L);
        original.registerProcessingTimeTimer("ns-2", 2000L);

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = original.snapshotTimers();

        List<InternalTimer<String, String>> restoredFired = new ArrayList<>();
        HeapInternalTimerService<String, String> restored =
                new HeapInternalTimerService<>(recordingTriggerable(new ArrayList<>(), restoredFired));
        restored.restoreTimers(snapshot);

        restored.fireProcessingTimeTimers(1500L);
        assertEquals(1, restoredFired.size());
        assertEquals(1000L, restoredFired.get(0).getTimestamp());
        assertEquals("ns-1", restoredFired.get(0).getNamespace());

        restored.fireProcessingTimeTimers(2500L);
        assertEquals(2, restoredFired.size());
        assertEquals(2000L, restoredFired.get(1).getTimestamp());
    }

    @Test
    void testEmptySnapshotRestoreIsNoOp() {
        HeapInternalTimerService<String, String> empty =
                new HeapInternalTimerService<>(recordingTriggerable(new ArrayList<>(), new ArrayList<>()));

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = empty.snapshotTimers();
        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.size());

        HeapInternalTimerService<String, String> target =
                new HeapInternalTimerService<>(recordingTriggerable(new ArrayList<>(), new ArrayList<>()));
        target.registerEventTimeTimer("pre-existing", 1000L);

        target.restoreTimers(snapshot);

        assertEquals(1, target.numEventTimeTimers(), "Empty snapshot must not clear pre-existing timers");
    }

    @Test
    void testNullSnapshotRestoreIsNoOp() {
        HeapInternalTimerService<String, String> target =
                new HeapInternalTimerService<>(recordingTriggerable(new ArrayList<>(), new ArrayList<>()));
        target.registerEventTimeTimer("pre-existing", 1000L);

        target.restoreTimers(null);

        assertEquals(1, target.numEventTimeTimers(), "null snapshot must be treated as empty (no-op)");
    }

    @Test
    void testSnapshotExcludesAlreadyFiredTimers() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        HeapInternalTimerService<String, String> service =
                new HeapInternalTimerService<>(recordingTriggerable(eventFired, new ArrayList<>()), () -> "key-A");

        service.registerEventTimeTimer("fired-early", 500L);
        service.registerEventTimeTimer("still-pending", 2000L);
        service.advanceWatermark(1000L);

        assertEquals(1, eventFired.size());

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = service.snapshotTimers();
        assertEquals(1, snapshot.getEventTimeTimers().size());
        assertEquals("still-pending", snapshot.getEventTimeTimers().get(0).getNamespace());
    }

    @Test
    void testRestoredTimerKeyIsFromSnapshotNotCurrentSupplier() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        String[] keyHolder = {"key-at-checkpoint"};
        HeapInternalTimerService<String, String> original =
                new HeapInternalTimerService<>(recordingTriggerable(eventFired, new ArrayList<>()), () -> keyHolder[0]);
        original.registerEventTimeTimer("ns-1", 1000L);

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = original.snapshotTimers();

        List<InternalTimer<String, String>> restoredFired = new ArrayList<>();
        HeapInternalTimerService<String, String> restored =
                new HeapInternalTimerService<>(recordingTriggerable(restoredFired, new ArrayList<>()), () -> "stale-key-at-restore");
        restored.restoreTimers(snapshot);

        restored.advanceWatermark(2000L);

        assertEquals(1, restoredFired.size());
        assertEquals("key-at-checkpoint", restoredFired.get(0).getKey(),
                "Restored timer must carry the key captured at checkpoint time, not the current supplier value");
    }

    @Test
    void testRestoredWatermarkPreventsReFiringPastTimers() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        HeapInternalTimerService<String, String> original =
                new HeapInternalTimerService<>(recordingTriggerable(eventFired, new ArrayList<>()), () -> "key-A");
        original.registerEventTimeTimer("ns-1", 5000L);
        original.advanceWatermark(3000L);

        HeapInternalTimerService.TimerSnapshot<String, String> snapshot = original.snapshotTimers();

        List<InternalTimer<String, String>> restoredFired = new ArrayList<>();
        HeapInternalTimerService<String, String> restored =
                new HeapInternalTimerService<>(recordingTriggerable(restoredFired, new ArrayList<>()));
        restored.restoreTimers(snapshot);

        restored.advanceWatermark(3000L);
        assertEquals(0, restoredFired.size(), "advanceWatermark to restored watermark should not re-fire");

        restored.advanceWatermark(5000L);
        assertEquals(1, restoredFired.size());
    }
}
