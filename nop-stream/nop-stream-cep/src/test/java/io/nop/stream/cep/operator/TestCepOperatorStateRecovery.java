package io.nop.stream.cep.operator;

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
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.streamrecord.LatencyMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCepOperatorStateRecovery {

    private List<String> results;
    private PatternProcessFunction<Event, String> function;
    private NFACompiler.NFAFactory<Event> nfaFactory;

    @BeforeEach
    void setUp() {
        results = new ArrayList<>();
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

        operator.setOutput(new TestOutput(results));
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private static void setProcessingTimeService(CepOperator<?, ?, ?> op, ProcessingTimeService svc) {
        try {
            Field f = io.nop.stream.core.operators.AbstractStreamOperator.class
                    .getDeclaredField("processingTimeService");
            f.setAccessible(true);
            f.set(op, svc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testProcessEventsAndStateSnapshot() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        operator.processElement(new StreamRecord<>(new Event(1, "a1"), 1));
        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(43, "mid"), 3));

        operator.processWatermark(new Watermark(5));

        assertTrue(results.isEmpty(), "No complete match yet, end event not seen");

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

        assertTrue(results.isEmpty(), "No match before end event");

        NFAState capturedState = op.getNFAStateForTesting();
        assertNotNull(capturedState);
        assertFalse(capturedState.getPartialMatches().isEmpty());

        op.updateNFAStateForTesting(capturedState);

        op.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        op.processWatermark(new Watermark(10));

        assertFalse(results.isEmpty(), "Should produce at least one match after end event");
        assertTrue(results.contains("start42->end"),
                "Should contain the expected match, got: " + results);

        op.close();
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

    private static class TestOutput implements Output<StreamRecord<String>> {
        private final List<String> results;

        TestOutput(List<String> results) {
            this.results = results;
        }

        @Override
        public void collect(StreamRecord<String> record) {
            results.add(record.getValue());
        }

        @Override
        public void close() {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
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
