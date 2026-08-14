package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-04 (open-audit) event-time timer registry tests for {@link CepOperator}:
 *
 * <ul>
 *   <li>restore → open symmetry: timers restored from a checkpoint must survive
 *       {@code open()} (the historical bug rebuilt the registry unconditionally in
 *       {@code open()}, wiping the restored timers — red before the fix);</li>
 *   <li>snapshot → restore round-trip must not lose timers;</li>
 *   <li>bookkeeping cleanup: the registry is a ledger of PENDING timers, so
 *       {@code onEventTime} must drop entries whose work is done (watermark reached)
 *       — the registry is checkpointed in full on every snapshot, so unbounded
 *       growth would leak into every checkpoint.</li>
 * </ul>
 *
 * <p>All timers here come from {@code bufferEvent} (each event timestamp with no
 * queue bucket yet registers a timer) — the pattern has no {@code within}, so
 * {@code nfa.getWindowTime() == 0} and no window-time timers are registered.
 */
public class TestCepEventTimeTimerRegistry {

    private TestOutput<String> output;
    private PatternProcessFunction<Event, String> function;
    private NFACompiler.NFAFactory<Event> nfaFactory;

    @BeforeEach
    void setUp() {
        output = new TestOutput<>();
        function = new PatternProcessFunction<>() {
            @Override
            public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
                Event start = match.get("start").get(0);
                Event end = match.get("end").get(0);
                out.collect(start.getName() + "->" + end.getName());
            }
        };

        Pattern<Event, ?> pattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));

        nfaFactory = NFACompiler.compileFactory(pattern, false);
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

    private CepOperator<Event, Integer, String> createOperator() throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    /**
     * restoreState() must be called BEFORE open() (the order pinned by
     * TestCepCheckpointRestoreE2E). Pre-fix: open() rebuilt the registry
     * unconditionally, so every restored timer was wiped — red; post-fix the
     * restored timers survive open().
     */
    private CepOperator<Event, Integer, String> createOperatorAfterRestore(
            OperatorSnapshotResult snapshot) throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        operator.restoreState(snapshot);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    @Test
    void testRestoredTimersSurviveOpen() throws Exception {
        CepOperator<Event, Integer, String> op = createOperator();

        // Event timestamps > initial watermark (MIN_VALUE) are buffered; each new
        // timestamp registers an event-time timer in the registry.
        op.processElement(new StreamRecord<>(new Event(1, "a1"), 10));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 20));

        assertEquals(Set.of(10L, 20L), op.getRegisteredEventTimeTimersForTesting(),
                "buffered event timestamps must be registered as event-time timers");

        OperatorSnapshotResult snapshot = op.snapshotState(new StateSnapshotContext(1L, System.currentTimeMillis()));
        op.close();

        CepOperator<Event, Integer, String> restored = createOperatorAfterRestore(snapshot);
        try {
            assertEquals(Set.of(10L, 20L), restored.getRegisteredEventTimeTimersForTesting(),
                    "restored timers must survive open() (restore-before-open order) — the historical "
                            + "bug wiped them with an unconditional registry rebuild in open()");
        } finally {
            restored.close();
        }
    }

    @Test
    void testSnapshotRestoreRoundTripKeepsTimers() throws Exception {
        CepOperator<Event, Integer, String> op = createOperator();
        op.processElement(new StreamRecord<>(new Event(1, "a1"), 10));
        op.processElement(new StreamRecord<>(new Event(2, "a2"), 20));
        op.processElement(new StreamRecord<>(new Event(3, "a3"), 35));

        OperatorSnapshotResult snapshot1 = op.snapshotState(new StateSnapshotContext(1L, System.currentTimeMillis()));
        op.close();

        CepOperator<Event, Integer, String> op2 = createOperatorAfterRestore(snapshot1);
        OperatorSnapshotResult snapshot2 = op2.snapshotState(new StateSnapshotContext(2L, System.currentTimeMillis()));
        op2.close();

        CepOperator<Event, Integer, String> op3 = createOperatorAfterRestore(snapshot2);
        try {
            assertEquals(Set.of(10L, 20L, 35L), op3.getRegisteredEventTimeTimersForTesting(),
                    "snapshot → restore → snapshot → restore round-trip must not lose timers");
        } finally {
            op3.close();
        }
    }

    @Test
    void testOnEventTimeCleansUpConsumedTimers() throws Exception {
        CepOperator<Event, Integer, String> op = createOperator();
        try {
            op.processElement(new StreamRecord<>(new Event(1, "a1"), 10));
            op.processElement(new StreamRecord<>(new Event(2, "a2"), 20));

            // Watermark 15 consumes the queue bucket at 10 (and its timer); 20 stays pending.
            op.processWatermark(new Watermark(15));
            assertEquals(Set.of(20L), op.getRegisteredEventTimeTimersForTesting(),
                    "onEventTime must drop registry entries whose work is done (bucket consumed at watermark)");
            assertEquals(0, op.getPQSize(0),
                    "queue bucket at 10 must be consumed by onEventTime");

            // Watermark 30 consumes the remaining bucket + timer.
            op.processWatermark(new Watermark(30));
            assertTrue(op.getRegisteredEventTimeTimersForTesting().isEmpty(),
                    "registry must be empty once every timer's bucket is consumed — no unbounded growth");
            assertEquals(0, op.getPQSize(0), "queue must be empty after the final watermark");
        } finally {
            op.close();
        }
    }

    private static class EventTypeSerializer implements TypeSerializer<Event> {
        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public TypeSerializer<Event> duplicate() {
            return this;
        }

        @Override
        public Event createInstance() {
            return new Event();
        }

        @Override
        public Event copy(Event from) {
            return new Event(from.getId(), from.getName());
        }

        @Override
        public Event copy(Event from, Event reuse) {
            return new Event(from.getId(), from.getName());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
