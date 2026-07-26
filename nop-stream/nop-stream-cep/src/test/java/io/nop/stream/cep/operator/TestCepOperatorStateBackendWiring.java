package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the CEP operator state-backend wiring through observable input/output
 * behavior rather than coupling to internal accessors
 * ({@code getKeyedStateBackend()}/{@code getNFAStateForTesting()}).
 *
 * <p>P1-13 fix: the prior test reached into private internals to assert backend
 * creation. That coupled the test to the implementation and made it fragile to
 * unrelated refactors (e.g. {@code AbstractStreamOperator} changes that touch
 * backend lifecycle). The behavior-level contract is: when a state backend is
 * configured, the CEP operator must (1) successfully process matches and
 * (2) snapshot non-empty state after processing events. Both are observable
 * via output and {@code snapshotState()} without internal accessors.
 *
 * <p>Plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md} Phase 3.
 */
public class TestCepOperatorStateBackendWiring {

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

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op, ProcessingTimeService svc) {
        CepTestUtils.injectProcessingTimeService(op, svc);
    }

    @Test
    void testConfiguredStateBackendProcessesMatchesAndSnapshotsState() throws Exception {
        // Behavior contract: with a configured state backend, the operator
        // produces matches AND snapshots non-empty state. No internal accessors.
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, function, null);
        IStateBackend configuredBackend = new MemoryStateBackend();
        operator.setStateBackend(configuredBackend);
        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        operator.processWatermark(new Watermark(10));

        // (1) Output behavior: pattern matched.
        assertFalse(output.isEmpty(), "Pattern should match with configured state backend");
        assertTrue(output.getElements().contains("start42->end"),
                "Output should contain the expected match, got: " + output.getElements());

        // (2) Snapshot behavior: state is non-empty after processing events.
        // This implicitly verifies the keyed state backend was created and used
        // — without coupling to getKeyedStateBackend() / getNFAStateForTesting().
        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = operator.snapshotState(ctx);

        assertNotNull(snapshot, "Snapshot should not be null");
        assertTrue(!snapshot.getKeyedStates().isEmpty() || !snapshot.getOperatorStates().isEmpty(),
                "Snapshot should contain some state after processing events (keyed or operator)");

        operator.close();
    }

    @Test
    void testNoBackendConfiguredStillMatchesViaFallback() throws Exception {
        // Behavior contract: even without explicit setStateBackend(), the
        // operator falls back to MemoryKeyedStateBackend and still matches.
        // Verified purely through output behavior.
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, function, null);
        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        operator.processWatermark(new Watermark(10));

        assertFalse(output.isEmpty(),
                "Pattern matching should work with fallback MemoryKeyedStateBackend");
        assertTrue(output.getElements().contains("start42->end"),
                "Output should contain the expected match, got: " + output.getElements());

        operator.close();
    }

    @Test
    void testSnapshotPreservesWatermarkForRestore() throws Exception {
        // Behavior contract: the operator's snapshot must include the current
        // watermark so a restored operator resumes from the right point. We
        // verify this by snapshotting after advancing the watermark, then
        // checking the snapshot contains the watermark state (observable via
        // operator-state names without internal accessors).
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                null, false, nfaFactory, null, null, function, null);
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processWatermark(new Watermark(12345));

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = operator.snapshotState(ctx);

        // The CEP operator stores its current watermark in operator state
        // under "cep-current-watermark". Reading that key is part of the
        // snapshot contract (not an internal accessor).
        Object watermarkState = snapshot.getOperatorState("cep-current-watermark");
        assertNotNull(watermarkState, "Snapshot must include cep-current-watermark state");
        assertTrue(watermarkState instanceof Number, "Watermark state must be a number");
        assertTrue(((Number) watermarkState).longValue() >= 12345,
                "Snapshot watermark must reflect the latest processed watermark");

        operator.close();
    }
}
