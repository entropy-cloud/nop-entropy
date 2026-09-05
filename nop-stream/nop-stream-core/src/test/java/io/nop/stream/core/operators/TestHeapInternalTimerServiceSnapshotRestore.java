/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private <K> Triggerable<K, String> recordingKeyedTriggerable(List<InternalTimer<K, String>> eventFired,
                                                                 List<InternalTimer<K, String>> processingFired) {
        return new Triggerable<K, String>() {
            @Override
            public void onEventTime(InternalTimer<K, String> timer) throws Exception {
                eventFired.add(timer);
            }

            @Override
            public void onProcessingTime(InternalTimer<K, String> timer) throws Exception {
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

    /**
     * AR-22 (P0) reproduction: the checkpoint JSON persist path (storageType=local,
     * {@code CheckpointSerDe}) round-trips timer keys through {@code TextScanner},
     * which parses {@code "123"} as {@code Integer} even when the timer key type is
     * {@code Long}. {@link TypedNamespaceAndKey#equals} is class-sensitive, so after
     * restore the timer fires with {@code Integer(123)} while the keyed state lookups
     * (window contents) use live {@code Long(123)} keys — everything silently misses.
     *
     * <p>The DTO round trip below mirrors exactly what {@code CheckpointSerDe} does:
     * {@code TimerSnapshot.toSerializableForm()} → {@code JsonTool} JSON → re-parse →
     * {@code TimerSnapshot.fromSerializableForm()} → {@code restoreTimers()} → fire.
     * The assertion is bound to the restore→fire downstream layer (the fired key's
     * type), NOT the DTO layer — post-fix the DTO still carries the JSON-parsed value.
     *
     * <p>Pre-fix (red): the restored timer fires with an {@code Integer} key.
     */
    @Test
    void testLongTimerKeyJsonRoundTripFiresWithLongKey() throws Exception {
        List<InternalTimer<Long, String>> eventFired = new ArrayList<>();
        HeapInternalTimerService<Long, String> original =
                new HeapInternalTimerService<>(recordingKeyedTriggerable(eventFired, new ArrayList<>()), () -> 123L);
        original.registerEventTimeTimer("ns-1", 1000L);

        Map<String, Object> form = original.snapshotTimers().toSerializableForm();
        Map<String, Object> parsed = JsonTool.parseMap(JsonTool.serialize(form, false));
        HeapInternalTimerService.TimerSnapshot<Long, String> restoredSnapshot =
                HeapInternalTimerService.TimerSnapshot.fromSerializableForm(parsed);

        List<InternalTimer<Long, String>> restoredFired = new ArrayList<>();
        HeapInternalTimerService<Long, String> restored =
                new HeapInternalTimerService<>(recordingKeyedTriggerable(restoredFired, new ArrayList<>()));
        // AR-22 fix wiring: the owning operator declares its key type (WindowOperator.open()
        // does this from keyClass), so restore re-materializes drifted keys.
        restored.setKeyType(Long.class);
        restored.restoreTimers(restoredSnapshot);

        restored.advanceWatermark(2000L);

        assertEquals(1, restoredFired.size());
        assertEquals(Long.valueOf(123L), restoredFired.get(0).getKey(),
                "Restored timer key must be Long(123) after JSON round-trip — pre-fix it "
                        + "drifts to Integer(123) and class-sensitive TypedNamespaceAndKey "
                        + "lookups silently miss (AR-22, same mechanism as AR-01)");
    }

    /**
     * AR-22 POJO-key variant: a POJO timer key is restored as a {@code LinkedHashMap}
     * by the JSON round trip (same mechanism as P2-INV-4 on the keyed-state face, which
     * AR-01 covered). The timer face has no coverage — the restored timer fires with a
     * map key and the equals-based keyed-state hit silently fails.
     *
     * <p>The key must be a {@code @DataBean} — the JSON persist path
     * ({@code JsonTool}) only serializes @DataBean / whitelisted classes. Non-@DataBean
     * POJO keys fail loudly at checkpoint serialize time (JsonTool guard, no silent
     * skip); @DataBean POJO keys round-trip and drift to {@code LinkedHashMap} (the
     * silent-loss variant covered here).
     *
     * <p>Pre-fix (red): the restored timer fires with a {@code LinkedHashMap} key.
     */
    @Test
    void testPojoTimerKeyJsonRoundTripFiresWithPojoKey() throws Exception {
        PojoKey originalKey = new PojoKey("k1", 42L);
        List<InternalTimer<PojoKey, String>> eventFired = new ArrayList<>();
        HeapInternalTimerService<PojoKey, String> original =
                new HeapInternalTimerService<>(recordingKeyedTriggerable(eventFired, new ArrayList<>()),
                        () -> originalKey);
        original.registerEventTimeTimer("ns-1", 1000L);

        Map<String, Object> form = original.snapshotTimers().toSerializableForm();
        Map<String, Object> parsed = JsonTool.parseMap(JsonTool.serialize(form, false));
        HeapInternalTimerService.TimerSnapshot<PojoKey, String> restoredSnapshot =
                HeapInternalTimerService.TimerSnapshot.fromSerializableForm(parsed);

        List<InternalTimer<PojoKey, String>> restoredFired = new ArrayList<>();
        HeapInternalTimerService<PojoKey, String> restored =
                new HeapInternalTimerService<>(recordingKeyedTriggerable(restoredFired, new ArrayList<>()));
        // AR-22 fix wiring: the owning operator declares its key type.
        restored.setKeyType(PojoKey.class);
        restored.restoreTimers(restoredSnapshot);

        restored.advanceWatermark(2000L);

        assertEquals(1, restoredFired.size());
        assertEquals(originalKey, restoredFired.get(0).getKey(),
                "Restored timer key must be rematerialized to PojoKey after JSON round-trip — "
                        + "pre-fix it is a LinkedHashMap and equals() misses (AR-22 POJO variant)");
    }

    /**
     * No-silent-skip (guide rule #24, AR-22): when a restored timer key cannot be
     * re-materialized to the declared keyType (e.g. a String-keyed snapshot restored
     * into a Long-keyed service), restore fails fast with {@code ERR_STREAM_STATE_ERROR}
     * instead of silently keeping the mismatched key (which class-sensitive
     * {@code TypedNamespaceAndKey} lookups would drop).
     */
    @SuppressWarnings("unchecked")
    @Test
    void testTimerKeyRematerializationFailureFailsFast() throws Exception {
        List<InternalTimer<String, String>> eventFired = new ArrayList<>();
        HeapInternalTimerService<String, String> original =
                new HeapInternalTimerService<>(recordingTriggerable(eventFired, new ArrayList<>()),
                        () -> "not-a-number");
        original.registerEventTimeTimer("ns-1", 1000L);

        Map<String, Object> form = original.snapshotTimers().toSerializableForm();
        Map<String, Object> parsed = JsonTool.parseMap(JsonTool.serialize(form, false));
        HeapInternalTimerService.TimerSnapshot<String, String> restoredSnapshot =
                HeapInternalTimerService.TimerSnapshot.fromSerializableForm(parsed);

        HeapInternalTimerService<Long, String> restored =
                new HeapInternalTimerService<>(recordingKeyedTriggerable(new ArrayList<>(), new ArrayList<>()));
        restored.setKeyType(Long.class);
        assertThrows(StreamException.class,
                () -> restored.restoreTimers((HeapInternalTimerService.TimerSnapshot<Long, String>)
                        (HeapInternalTimerService.TimerSnapshot<?, ?>) restoredSnapshot),
                "Restoring a non-Long timer key into a Long-keyed timer service must fail fast, "
                        + "not silently keep the mismatched key");
    }

    /**
     * Static nested {@code @DataBean} key with a public no-arg constructor — required
     * for {@code JsonTool} serialization and {@code JsonTool.parseBeanFromText}
     * reflective materialization (anonymous/inner classes cannot be rematerialized and
     * would throw ERR_STREAM_STATE_ERROR).
     */
    @DataBean
    public static class PojoKey {
        private String id;
        private long seq;

        public PojoKey() {
        }

        public PojoKey(String id, long seq) {
            this.id = id;
            this.seq = seq;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getSeq() {
            return seq;
        }

        public void setSeq(long seq) {
            this.seq = seq;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PojoKey)) return false;
            PojoKey pojoKey = (PojoKey) o;
            return seq == pojoKey.seq && Objects.equals(id, pojoKey.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, seq);
        }
    }
}
