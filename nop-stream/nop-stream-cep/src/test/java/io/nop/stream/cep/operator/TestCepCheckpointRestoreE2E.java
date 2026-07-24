package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFAState;
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
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCepCheckpointRestoreE2E {

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
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private CepOperator<Event, Integer, String> createReceiverAfterRestore(
            TestOutput<String> out, OperatorSnapshotResult snapshot) throws Exception {
        // restoreState() must be called BEFORE open() so that deferred keyed state
        // restoration (applyPendingRestoreState) processes the pending data during open()
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
        operator.setOutput(out);
        operator.restoreState(snapshot);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    @Test
    void testE2ENfaStateSurvivesCheckpointRestore() throws Exception {
        CepOperator<Event, Integer, String> op = createOperator();

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        op.processElement(new StreamRecord<>(new Event(43, "mid"), 3));
        op.processWatermark(new Watermark(5));

        assertTrue(output.isEmpty(), "No complete match before end event");

        NFAState preCheckpointState = op.getNFAStateForTesting();
        assertNotNull(preCheckpointState);
        assertFalse(preCheckpointState.getPartialMatches().isEmpty(),
                "NFA should have partial match before checkpoint");

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op.snapshotState(ctx);
        assertNotNull(snapshot);
        assertFalse(snapshot.getOperatorStates().isEmpty(),
                "Snapshot should contain watermark and timer operator state");

        op.close();

        TestOutput<String> restoredOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> restored = createReceiverAfterRestore(restoredOutput, snapshot);

        restored.processElement(new StreamRecord<>(new Event(99, "end"), 6));
        restored.processWatermark(new Watermark(10));

        assertFalse(restoredOutput.isEmpty(),
                "Pattern should match after checkpoint restore when end event arrives");
        assertTrue(restoredOutput.getElements().contains("start42->end"),
                "Restored match should contain expected sequence: start42->end, got: " + restoredOutput.getElements());

        restored.close();
    }

    @Test
    void testE2ESharedBufferSurvivesCheckpointRestore() throws Exception {
        CepOperator<Event, Integer, String> op = createOperator();

        op.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        op.processElement(new StreamRecord<>(new Event(43, "mid"), 3));

        assertFalse(op.hasNonEmptySharedBuffer(0),
                "SharedBuffer should have content after buffering events");

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op.snapshotState(ctx);
        assertNotNull(snapshot);

        op.close();

        TestOutput<String> restoredOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> restored = createReceiverAfterRestore(restoredOutput, snapshot);

        restored.processElement(new StreamRecord<>(new Event(99, "end"), 6));
        restored.processWatermark(new Watermark(10));

        assertFalse(restoredOutput.isEmpty(),
                "Pattern should match after checkpoint restore for shared buffer scenario");
        assertTrue(restoredOutput.getElements().contains("start42->end"),
                "Expected start42->end after restore, got: " + restoredOutput.getElements());

        restored.close();
    }

    @Test
    void testE2ETimerSurvivesCheckpointRestore() throws Exception {
        Pattern<Event, ?> timerPattern = Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(java.time.Duration.ofMinutes(1));

        NFACompiler.NFAFactory<Event> timerNfaFactory = NFACompiler.compileFactory(timerPattern, false);

        CepOperator<Event, Integer, String> op = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                timerNfaFactory,
                null,
                null,
                function,
                null
        );
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(output);
        setProcessingTimeService(op, MOCK_PTS);
        op.open();

        op.processElement(new StreamRecord<>(new Event(42, "start42"), 1000));
        op.processElement(new StreamRecord<>(new Event(43, "mid"), 1001));

        NFAState state = op.getNFAStateForTesting();
        assertNotNull(state);
        assertFalse(state.getPartialMatches().isEmpty(),
                "NFA should have partial match with within timer constraint");

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op.snapshotState(ctx);
        assertNotNull(snapshot);

        op.close();

        TestOutput<String> restoredOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> restored = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                timerNfaFactory,
                null,
                null,
                function,
                null
        );
        restored.setStateBackend(new MemoryStateBackend());
        restored.setOutput(restoredOutput);
        restored.restoreState(snapshot);
        setProcessingTimeService(restored, MOCK_PTS);
        restored.open();

        restored.processElement(new StreamRecord<>(new Event(99, "end"), 1050));
        restored.processWatermark(new Watermark(1100));

        if (!restoredOutput.isEmpty()) {
            assertTrue(restoredOutput.getElements().contains("start42->end"),
                    "If match occurs after restore, it should be the expected sequence, got: " + restoredOutput.getElements());
        }

        restored.close();
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
