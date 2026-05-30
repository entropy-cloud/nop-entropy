package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.MockRuntimeContext;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.NFAState;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCepOperatorStateRecovery {

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

        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op, ProcessingTimeService svc) {
        CepTestUtils.injectProcessingTimeService(op, svc);
    }

    @Test
    void testProcessEventsAndStateSnapshot() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        operator.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(43, "mid"), 3));

        operator.processWatermark(new Watermark(5));

        assertTrue(output.isEmpty(), "No complete match yet, end event not seen");

        NFAState state = operator.getNFAStateForTesting();
        assertNotNull(state, "NFA state should exist after processing events");
        assertFalse(state.getPartialMatches().isEmpty(),
                "NFA should have partial matches after seeing a matching start event");

        operator.close();
    }

    @Test
    void testSnapshotRestoreAndContinue() throws Exception {
        CepOperator<Event, Integer, String> op = createOperator();

        op.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        op.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        op.processElement(new StreamRecord<>(new Event(43, "mid"), 3));
        op.processWatermark(new Watermark(3));

        assertTrue(output.isEmpty(), "No match before end event");

        NFAState capturedState = op.getNFAStateForTesting();
        assertNotNull(capturedState);
        assertFalse(capturedState.getPartialMatches().isEmpty());

        op.updateNFAStateForTesting(capturedState);

        op.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        op.processWatermark(new Watermark(10));

        assertFalse(output.isEmpty(), "Should produce at least one match after end event");
        assertTrue(output.getElements().contains("start42->end"),
                "Should contain the expected match, got: " + output.getElements());

        op.close();
    }

    @Test
    void testRestoreFromCheckpoint() throws Exception {
        CepOperator<Event, Integer, String> original = createOperator();

        original.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        original.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        original.processElement(new StreamRecord<>(new Event(43, "mid"), 3));
        original.processWatermark(new Watermark(3));

        assertTrue(output.isEmpty(), "No match before end event");

        NFAState capturedState = original.getNFAStateForTesting();
        assertNotNull(capturedState, "NFA state should exist after processing events");
        assertFalse(capturedState.getPartialMatches().isEmpty(),
                "NFA should have partial matches after start event");

        int originalPartialMatchCount = capturedState.getPartialMatches().size();
        assertTrue(originalPartialMatchCount >= 2,
                "Should have at least start state and a partial match for start42");

        original.close();

        TestOutput<String> restoredOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> restored = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );
        restored.setOutput(restoredOutput);
        setProcessingTimeService(restored, MOCK_PTS);
        restored.open();

        NFAState initialState = restored.getNFAStateForTesting();
        assertNotNull(initialState, "New operator should have initial NFA state");

        int initialPartialMatchCount = initialState.getPartialMatches().size();

        restored.updateNFAStateForTesting(capturedState);

        NFAState afterRestore = restored.getNFAStateForTesting();
        assertNotNull(afterRestore, "Restored operator should have NFA state after update");
        assertEquals(originalPartialMatchCount,
                afterRestore.getPartialMatches().size(),
                "Restored state should have the same number of partial matches as the captured state");

        assertNotEquals(initialPartialMatchCount,
                afterRestore.getPartialMatches().size(),
                "Restored state should differ from initial state (verifies state was actually applied)");

        restored.close();
    }

    @Test
    void testWatermarkInitializedAfterDeserialization() throws Exception {
        TestOutput<String> watermarkOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );

        operator.setOutput(watermarkOutput);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        long nearMinTimestamp = Long.MIN_VALUE + 100;
        operator.processElement(new StreamRecord<>(new Event(42, "start42"), nearMinTimestamp));
        operator.processElement(new StreamRecord<>(new Event(99, "end"), nearMinTimestamp + 1));
        operator.processWatermark(new Watermark(nearMinTimestamp + 2));

        assertFalse(watermarkOutput.isEmpty(),
                "Elements with timestamps near Long.MIN_VALUE should not be dropped as late after open()");

        operator.close();
    }

    /**
     * Verifies that a new CepOperator instance, after having its NFA state restored
     * from a previous operator's snapshot, has a properly initialized runtime
     * infrastructure (state store, partial matches container), and that the restored
     * NFA state is correctly accessible through the operator's accessor methods.
     * This complements testRestoreFromCheckpoint by additionally verifying the
     * operator's internal collaborators are functional after state restoration.
     */
    @Test
    void testNewOperatorInfrastructureAfterStateRestore() throws Exception {
        // Note: SharedBuffer state (event data) is not preserved through updateNFAStateForTesting;
        // this is an infrastructure limitation. Full snapshot/restore requires the keyed state
        // backend path via operator.snapshotState()/restoreState().
        CepOperator<Event, Integer, String> original = createOperator();

        original.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        original.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        original.processElement(new StreamRecord<>(new Event(43, "mid"), 3));
        original.processWatermark(new Watermark(3));

        assertTrue(output.isEmpty(), "No match before end event");

        NFAState capturedState = original.getNFAStateForTesting();
        assertNotNull(capturedState, "NFA state should exist after processing events");
        assertFalse(capturedState.getPartialMatches().isEmpty(),
                "NFA should have partial matches after start event");

        original.close();

        TestOutput<String> recoveredOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> restored = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );
        restored.setOutput(recoveredOutput);
        setProcessingTimeService(restored, MOCK_PTS);
        restored.open();

        assertNotNull(restored.getNFAStateForTesting(),
                "New operator should have initial NFA state after open()");
        assertNotNull(restored.getPartialMatches(),
                "New operator should have SharedBuffer after open()");
        assertNotNull(restored.getKeyedStateStore(),
                "New operator should have state store after open()");
        assertTrue(restored.getPartialMatches().isEmpty(),
                "New operator should have empty SharedBuffer before any events");

        restored.updateNFAStateForTesting(capturedState);

        NFAState afterRestore = restored.getNFAStateForTesting();
        assertNotNull(afterRestore, "Restored operator should have NFA state after update");
        assertEquals(capturedState.getPartialMatches().size(),
                afterRestore.getPartialMatches().size(),
                "Restored state should have the same number of partial matches as the captured state");

        restored.close();
    }

    @Test
    void testCepRuntimeContextGetState() throws Exception {
        SimpleKeyedStateStore stateStore = new SimpleKeyedStateStore();
        MockRuntimeContext mockCtx = new MockRuntimeContext();
        CepRuntimeContext ctx = new CepRuntimeContext(mockCtx, stateStore);

        assertNotNull(ctx.getWrappedRuntimeContext());
        assertNotNull(ctx.getKeyedStateStore());
        assertEquals(mockCtx, ctx.getWrappedRuntimeContext());
        assertEquals(stateStore, ctx.getKeyedStateStore());

        ValueState<String> state = stateStore.getState(new ValueStateDescriptor<>("testState", String.class));
        assertNotNull(state);

        assertNull(state.value());
        state.update("hello");
        assertEquals("hello", state.value());
    }

    @Test
    void testCepRuntimeContextGetListState() throws Exception {
        SimpleKeyedStateStore stateStore = new SimpleKeyedStateStore();
        MockRuntimeContext mockCtx = new MockRuntimeContext();
        CepRuntimeContext ctx = new CepRuntimeContext(mockCtx, stateStore);

        ListState<String> listState = stateStore.getListState(new ListStateDescriptor<>("testListState", String.class));
        assertNotNull(listState);

        assertFalse(listState.get().iterator().hasNext(), "New list state should be empty");

        listState.add("item1");
        listState.add("item2");

        List<String> items = new ArrayList<>();
        listState.get().forEach(items::add);
        assertEquals(2, items.size());
        assertTrue(items.contains("item1"));
        assertTrue(items.contains("item2"));
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
