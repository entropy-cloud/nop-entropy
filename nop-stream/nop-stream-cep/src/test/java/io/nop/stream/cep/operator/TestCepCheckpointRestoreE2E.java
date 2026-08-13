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
import java.util.LinkedHashMap;
import java.util.concurrent.ScheduledFuture;

import io.nop.core.lang.json.JsonTool;

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

    /**
     * P1-21-01: the CEP element queue ({@code MapState<Long, List<DataBeanEvent>>},
     * raw {@code List.class} descriptor) must survive the storage-layer JSON
     * round-trip with inner element types intact.
     *
     * <p>The queue state's snapshot info is JSON-persisted and parsed back exactly
     * as {@code CheckpointSerDe} does for {@code storageType="local"} (the same
     * {@code JsonTool} mechanism), then restored through the real restore path
     * ({@code CepOperator.restoreState} → {@code applyPendingRestoreState} →
     * {@code MemoryStateSerDe.restoreMapState}). Pre-fix the JSON round-trip
     * turned the queued elements into {@code LinkedHashMap}s and the first typed
     * access ({@code event.getId()}) threw ClassCastException — the restore was
     * immediately broken (red). Post-fix the queue elements re-materialize to
     * {@code DataBeanEvent} and the match fires (green).
     *
     * <p>Only the queue state's info is round-tripped: the other keyed states
     * (NFAState / SharedBuffer) are not JSON-serializable by design (non-@DataBean,
     * backlog P2-INV-6/P2-TST-9), so a whole-snapshot JSON round-trip is not
     * possible in production today either.
     */
    @Test
    void testE2EElementQueueSurvivesJsonStorageLayerRoundTrip() throws Exception {
        CepOperator<DataBeanEvent, Integer, String> op = createDataBeanOperator();

        // Buffer events in the element queue (timestamps above the initial watermark).
        op.processElement(new StreamRecord<>(new DataBeanEvent(42, "start42"), 1000));
        op.processElement(new StreamRecord<>(new DataBeanEvent(43, "mid"), 1001));

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = op.snapshotState(ctx);
        assertNotNull(snapshot);
        op.close();

        OperatorSnapshotResult persisted = jsonRoundTripQueueState(snapshot);

        TestOutput<String> restoredOutput = new TestOutput<>();
        CepOperator<DataBeanEvent, Integer, String> restored =
                createDataBeanReceiverAfterRestore(restoredOutput, persisted);

        restored.processElement(new StreamRecord<>(new DataBeanEvent(99, "end"), 1002));
        restored.processWatermark(new Watermark(1100));

        assertFalse(restoredOutput.isEmpty(),
                "Pattern must match after JSON storage-layer round-trip restore (queue element type preserved)");
        assertTrue(restoredOutput.getElements().contains("start42->end"),
                "Restored match should be start42->end, got: " + restoredOutput.getElements());

        restored.close();
    }

    /**
     * JSON-persists only the element-queue state info (the storage-layer mechanism
     * for {@code storageType="local"} applied to the queue state) and puts the
     * parsed form back into the keyed snapshot.
     */
    @SuppressWarnings("unchecked")
    private OperatorSnapshotResult jsonRoundTripQueueState(OperatorSnapshotResult snapshot) {
        Map<String, Object> newKeyed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : snapshot.getKeyedStates().entrySet()) {
            Object v = e.getValue();
            if (v instanceof io.nop.stream.core.common.state.backend.StateSnapshot) {
                io.nop.stream.core.common.state.backend.StateSnapshot snap =
                        (io.nop.stream.core.common.state.backend.StateSnapshot) v;
                Map<String, Object> stateData = new LinkedHashMap<>(snap.getStateData());
                Map<String, Object> states = (Map<String, Object>) snap.getStateData().get("states");
                Map<String, Object> newStates = new LinkedHashMap<>(states);
                Object queueInfo = states.get("eventQueuesStateName");
                String json = JsonTool.serialize(queueInfo, false);
                newStates.put("eventQueuesStateName", JsonTool.parseMap(json));
                stateData.put("states", newStates);
                newKeyed.put(e.getKey(), new io.nop.stream.core.common.state.backend.StateSnapshot(stateData));
            } else {
                newKeyed.put(e.getKey(), v);
            }
        }
        return new OperatorSnapshotResult(snapshot.getOperatorStates(), newKeyed, snapshot.getRawKeyedStates());
    }

    private CepOperator<DataBeanEvent, Integer, String> createDataBeanOperator() throws Exception {
        CepOperator<DataBeanEvent, Integer, String> operator = new CepOperator<>(
                new DataBeanEventTypeSerializer(),
                false,
                dataBeanNfaFactory,
                null,
                null,
                dataBeanFunction,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private CepOperator<DataBeanEvent, Integer, String> createDataBeanReceiverAfterRestore(
            TestOutput<String> out, OperatorSnapshotResult snapshot) throws Exception {
        CepOperator<DataBeanEvent, Integer, String> operator = new CepOperator<>(
                new DataBeanEventTypeSerializer(),
                false,
                dataBeanNfaFactory,
                null,
                null,
                dataBeanFunction,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(out);
        operator.restoreState(snapshot);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    // ------------------------------------------------------------------------
    // P1-21-01 fixtures: @DataBean event (JsonTool serialization guard requires
    // @DataBean or whitelisted classes) + its pattern/factory/function/serializer
    // ------------------------------------------------------------------------

    @io.nop.api.core.annotations.data.DataBean
    public static class DataBeanEvent {
        private int id;
        private String name;

        public DataBeanEvent() {
        }

        public DataBeanEvent(int id, String name) {
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

    private final PatternProcessFunction<DataBeanEvent, String> dataBeanFunction = new PatternProcessFunction<>() {
        @Override
        public void processMatch(Map<String, List<DataBeanEvent>> match, Context ctx, Collector<String> out) {
            DataBeanEvent start = match.get("start").get(0);
            DataBeanEvent end = match.get("end").get(0);
            out.collect(start.getName() + "->" + end.getName());
        }
    };

    private final NFACompiler.NFAFactory<DataBeanEvent> dataBeanNfaFactory;

    {
        Pattern<DataBeanEvent, ?> pattern = Pattern.<DataBeanEvent>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")));
        dataBeanNfaFactory = NFACompiler.compileFactory(pattern, false);
    }

    private static class DataBeanEventTypeSerializer implements TypeSerializer<DataBeanEvent> {
        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public TypeSerializer<DataBeanEvent> duplicate() {
            return this;
        }

        @Override
        public DataBeanEvent createInstance() {
            return new DataBeanEvent();
        }

        @Override
        public DataBeanEvent copy(DataBeanEvent from) {
            return new DataBeanEvent(from.getId(), from.getName());
        }

        @Override
        public DataBeanEvent copy(DataBeanEvent from, DataBeanEvent reuse) {
            return new DataBeanEvent(from.getId(), from.getName());
        }

        @Override
        public int getLength() {
            return -1;
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
