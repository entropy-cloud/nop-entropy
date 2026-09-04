package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import io.nop.core.lang.json.JsonTool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-10 / AR-11 (Plan 2026-09-04-1326-1 Phase 4, adjudication D4): CEP recovery key
 * class and timer ledger.
 *
 * <ul>
 *   <li><b>AR-10 addressability</b>: a keyed CEP job whose keys are {@code Long}
 *       survives the checkpoint JSON persist round-trip (the drift vector: the storage
 *       layer parses every JSON number as Integer first) — the restored keyed backend
 *       is created with the checkpoint-carried key class, restored keyed entries
 *       re-materialize to the ORIGINAL class, and state stays addressable under the
 *       original {@code Long} key. Wiring verification: the key class really reaches
 *       {@code createKeyedStateBackend} (asserted via
 *       {@code MemoryKeyedStateBackend.getKeyType()}, which drives the
 *       {@code MemoryStateSerDe} re-materialization guard).</li>
 *   <li><b>AR-11 timers really fire</b>: pending event-time timers restored from the
 *       typed ledger fire for the REAL key after the watermark advances — queued
 *       events are drained (no lingering), timed-out partial matches surface via the
 *       timeout handler (no silent reset), the ledger clears.</li>
 *   <li><b>Non-keyed default path</b>: the global CEP path pins {@code Byte.class}
 *       (its NullByteKeySelector is deterministic), so the same guarantees hold for
 *       the default non-keyed form.</li>
 *   <li><b>kill/restore e2e</b>: slice A (partial match + queued event) → checkpoint
 *       → kill → restore → slice B completes the pattern — exactly one match, no
 *       duplicates, no losses, no silent state reset.</li>
 * </ul>
 *
 * <p>The JSON round-trip mirrors {@code TestCepCheckpointRestoreE2E}'s storage-layer
 * simulation ({@code storageType="local"} / {@code CheckpointSerDe}): every
 * operator-state entry (watermark / key class / timer ledger) and the queue
 * keyed-state's info map are JSON-persisted; NFA/SharedBuffer keyed states travel as
 * in-JVM objects exactly as today's production local-storage path persists them. The
 * event type is a {@code @DataBean} so the queue payload is JSON-serializable (same
 * constraint production imposes).
 */
public class TestCepKeyClassRecovery {

    /** JSON-serializable event (production constraint: queue payloads go through JsonTool). */
    @io.nop.api.core.annotations.data.DataBean
    public static class Ev {
        private int id;
        private String name;

        public Ev() {
        }

        public Ev(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static final List<String> TIMEOUT_SEEN = new java.util.concurrent.CopyOnWriteArrayList<>();

    static final class MatchFunction extends PatternProcessFunction<Ev, String> {
        @Override
        public void processMatch(Map<String, List<Ev>> match, Context ctx, Collector<String> out) {
            out.collect(match.get("start").get(0).getName() + "->" + match.get("end").get(0).getName());
        }
    }

    /** Both faces: process function + timeout handler (observable timeout output). */
    static final class TimeoutFunction extends PatternProcessFunction<Ev, String>
            implements TimedOutPartialMatchHandler<Ev> {
        @Override
        public void processMatch(Map<String, List<Ev>> match, Context ctx, Collector<String> out) {
            out.collect(match.get("start").get(0).getName() + "->" + match.get("end").get(0).getName());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Ev>> match, Context ctx) {
            TIMEOUT_SEEN.add(match.get("start").get(0).getName());
        }
    }

    private NFACompiler.NFAFactory<Ev> nfaFactory;
    private NFACompiler.NFAFactory<Ev> timeoutNfaFactory;

    @BeforeEach
    void setUp() {
        Pattern<Ev, ?> pattern = Pattern.<Ev>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));
        nfaFactory = NFACompiler.compileFactory(pattern, false);

        Pattern<Ev, ?> timeoutPattern = Pattern.<Ev>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(java.time.Duration.ofSeconds(60));
        timeoutNfaFactory = NFACompiler.compileFactory(timeoutPattern, true);
        TIMEOUT_SEEN.clear();
    }

    private static final ProcessingTimeService MOCK_PTS = new ProcessingTimeService() {
        private long time = 1000;

        @Override
        public long getCurrentProcessingTime() {
            return time++;
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            return null;
        }
    };

    /** Keyed-path operator: NO explicit key class (first-key capture + checkpoint carriage). */
    private CepOperator<Ev, Long, String> newKeyedOperator(
            NFACompiler.NFAFactory<Ev> factory, PatternProcessFunction<Ev, String> fn,
            TestOutput<String> out) throws Exception {
        CepOperator<Ev, Long, String> operator = new CepOperator<>(
                null, false, factory, null, null, fn, null);
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(out);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        return operator;
    }

    /** Non-keyed-path operator: Byte.class explicit (PatternStreamBuilder's channel). */
    private CepOperator<Ev, Byte, String> newGlobalOperator(TestOutput<String> out) throws Exception {
        CepOperator<Ev, Byte, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, new MatchFunction(), null, Byte.class);
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(out);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        return operator;
    }

    /**
     * The storage-layer JSON round-trip: operator states are JSON-persisted (numbers
     * come back Integer-first — the key drift vector), and the queue keyed-state's
     * info map is round-tripped like {@code CheckpointSerDe} does for
     * {@code storageType="local"}.
     */
    @SuppressWarnings("unchecked")
    private OperatorSnapshotResult jsonStorageRoundTrip(OperatorSnapshotResult snapshot) {
        Map<String, Object> operatorStates = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : snapshot.getOperatorStates().entrySet()) {
            operatorStates.put(e.getKey(), JsonTool.parse(
                    JsonTool.serialize(e.getValue(), false)));
        }
        Map<String, Object> newKeyed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : snapshot.getKeyedStates().entrySet()) {
            Object v = e.getValue();
            if (v instanceof io.nop.stream.core.common.state.backend.StateSnapshot snap) {
                Map<String, Object> stateData = new LinkedHashMap<>(snap.getStateData());
                Map<String, Object> states = (Map<String, Object>) snap.getStateData().get("states");
                Map<String, Object> newStates = new LinkedHashMap<>(states);
                Object queueInfo = states.get("eventQueuesStateName");
                if (queueInfo != null) {
                    String json = JsonTool.serialize(queueInfo, false);
                    newStates.put("eventQueuesStateName", JsonTool.parseMap(json));
                }
                stateData.put("states", newStates);
                newKeyed.put(e.getKey(),
                        new io.nop.stream.core.common.state.backend.StateSnapshot(stateData));
            } else {
                newKeyed.put(e.getKey(), v);
            }
        }
        return new OperatorSnapshotResult(operatorStates, newKeyed, snapshot.getRawKeyedStates());
    }

    // ------------------------------------------------------------------
    // AR-10 + kill/restore e2e: Long-keyed restore stays addressable
    // ------------------------------------------------------------------

    @Test
    void longKeyedStateAddressableAfterJsonRoundTripRestore() throws Exception {
        TestOutput<String> out = new TestOutput<>();
        CepOperator<Ev, Long, String> op = newKeyedOperator(nfaFactory, new MatchFunction(), out);
        op.open();

        // slice A: establish a partial match under key 7L, then queue an event above
        // the watermark (the queue's keyed entry is the JSON drift surface)
        op.setCurrentKey(7L);
        op.processElement(new StreamRecord<>(new Ev(42, "start42"), 1000));
        op.processWatermark(new Watermark(1001)); // drain -> start processed -> partial match
        assertTrue(op.hasNonEmptySharedBuffer(7L), "partial match established under Long key 7");
        op.processElement(new StreamRecord<>(new Ev(7, "noise"), 1010)); // queued (1010 > 1001)
        assertTrue(op.hasNonEmptyPQ(7L), "event queued above the watermark under Long key 7");

        // checkpoint + kill: the storage-layer JSON round-trip drifts every JSON
        // number to Integer-first — keys must re-materialize to the original class
        OperatorSnapshotResult snapshot = op.snapshotState(new StateSnapshotContext(1L, 1L));
        assertNotNull(snapshot.getOperatorState(CepOperator.KEY_CLASS_STATE_NAME),
                "checkpoint carries the captured key class (AR-10 carriage)");
        OperatorSnapshotResult persisted = jsonStorageRoundTrip(snapshot);
        op.close();

        // restore: restoreState BEFORE open() (pinned lifecycle order)
        TestOutput<String> restoredOut = new TestOutput<>();
        CepOperator<Ev, Long, String> restored = newKeyedOperator(nfaFactory, new MatchFunction(), restoredOut);
        restored.restoreState(persisted);
        restored.open();

        // wiring verification: the key class really reached the keyed backend creation
        // (MemoryStateSerDe's re-materialization guard keys on exactly this type)
        Object backend = restored.getKeyedStateBackend();
        assertNotNull(backend, "keyed backend created via createKeyedStateBackend");
        assertEquals(Long.class, ((MemoryKeyedStateBackend<?>) backend).getKeyType(),
                "backend created with the checkpoint-carried Long key class");

        // AR-10 addressability: state reachable under the ORIGINAL Long key — pre-fix
        // the JSON round-trip drifted the entry keys to Integer and the Long lookups
        // silently missed (state reset)
        assertTrue(restored.hasNonEmptySharedBuffer(7L),
                "partial match addressable under the original Long key after restore");
        assertTrue(restored.hasNonEmptyPQ(7L),
                "queued event addressable under the original Long key after restore");

        // slice B: the pattern completes — exactly once, no losses, no silent reset
        restored.setCurrentKey(7L);
        restored.processElement(new StreamRecord<>(new Ev(99, "end"), 1050));
        restored.processWatermark(new Watermark(1100));

        assertEquals(List.of("start42->end"), restoredOut.getElements(),
                "exactly one match after kill/restore — no duplicates, no losses");
        restored.close();
    }

    // ------------------------------------------------------------------
    // AR-11: restored timers really fire for the REAL key
    // ------------------------------------------------------------------

    @Test
    void pendingTimersFireForRealKeyAfterRestore() throws Exception {
        TestOutput<String> out = new TestOutput<>();
        CepOperator<Ev, Long, String> op = newKeyedOperator(timeoutNfaFactory, new TimeoutFunction(), out);
        op.open();

        // partial match under key 9L; the queued start event (above the watermark)
        // registers the bucket timer, the window registers start+60s — both in the
        // per-key ledger under the LIVE Long key
        op.setCurrentKey(9L);
        op.processElement(new StreamRecord<>(new Ev(42, "start42"), 1000));
        assertFalse(op.getLedgerForTesting().isEmpty(), "timer ledger carries pending timers");
        assertEquals(1, op.getLedgerForTesting().keySet().stream()
                        .filter(k -> Long.class.equals(k.getClass())).count(),
                "ledger key is the live Long key");

        OperatorSnapshotResult persisted = jsonStorageRoundTrip(
                op.snapshotState(new StateSnapshotContext(1L, 1L)));
        op.close();

        TestOutput<String> restoredOut = new TestOutput<>();
        CepOperator<Ev, Long, String> restored = newKeyedOperator(timeoutNfaFactory, new TimeoutFunction(), restoredOut);
        restored.restoreState(persisted);
        restored.open();

        // AR-11 typed ledger: the restored ledger key re-materialized to Long — NOT
        // the Integer the raw JSON parse produces
        assertEquals(1, restored.getLedgerForTesting().keySet().size(), "single ledger entry");
        assertEquals(1, restored.getLedgerForTesting().keySet().stream()
                        .filter(k -> Long.class.equals(k.getClass())).count(),
                "restored ledger key class is Long (typed persistence, not drifted Integer)");

        // AR-11 timers really fire: advance the watermark past start+window — the
        // drain must switch to the REAL key context, run onEventTime, consume the
        // queued event and time out the partial match (observable timeout)
        restored.processWatermark(new Watermark(1000 + 60_000 + 1));

        assertEquals(List.of("start42"), TIMEOUT_SEEN,
                "timed-out partial match surfaces via the timeout handler (timer fired for the real key)");
        assertFalse(restored.hasNonEmptyPQ(9L), "queued event drained — nothing lingers");
        assertTrue(restored.getLedgerForTesting().isEmpty(),
                "ledger cleared after the drain (timer consumed)");
        restored.close();
    }

    // ------------------------------------------------------------------
    // Non-keyed default path: Byte.class pinned explicitly
    // ------------------------------------------------------------------

    @Test
    void nonKeyedDefaultPathRestoresUnderByteKey() throws Exception {
        TestOutput<String> out = new TestOutput<>();
        CepOperator<Ev, Byte, String> op = newGlobalOperator(out);
        op.open();

        op.setCurrentKey((byte) 0);
        op.processElement(new StreamRecord<>(new Ev(42, "start42"), 1000));
        op.processWatermark(new Watermark(1001));
        assertTrue(op.hasNonEmptySharedBuffer((byte) 0),
                "partial match under the Byte sentinel key");

        OperatorSnapshotResult persisted = jsonStorageRoundTrip(
                op.snapshotState(new StateSnapshotContext(1L, 1L)));
        op.close();

        TestOutput<String> restoredOut = new TestOutput<>();
        CepOperator<Ev, Byte, String> restored = newGlobalOperator(restoredOut);
        restored.restoreState(persisted);
        restored.open();

        assertEquals(Byte.class,
                ((MemoryKeyedStateBackend<?>) restored.getKeyedStateBackend()).getKeyType(),
                "non-keyed path backend is Byte-typed (NullByteKeySelector is deterministic)");
        assertTrue(restored.hasNonEmptySharedBuffer((byte) 0),
                "state addressable under the Byte key after JSON round-trip restore");

        restored.setCurrentKey((byte) 0);
        restored.processElement(new StreamRecord<>(new Ev(99, "end"), 1050));
        restored.processWatermark(new Watermark(1100));
        assertEquals(List.of("start42->end"), restoredOut.getElements(),
                "default non-keyed path matches after restore");
        restored.close();
    }
}
