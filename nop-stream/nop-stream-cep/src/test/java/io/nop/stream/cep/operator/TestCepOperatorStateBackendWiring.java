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
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void testConfiguredStateBackendIsUsed() throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );
        IStateBackend configuredBackend = new MemoryStateBackend();
        operator.setStateBackend(configuredBackend);
        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        IKeyedStateBackend<?> keyedBackend = operator.getKeyedStateBackend();
        assertNotNull(keyedBackend, "Keyed state backend should be created from configured state backend");

        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        operator.processWatermark(new Watermark(10));

        assertFalse(output.isEmpty(), "Pattern should match with configured state backend");

        operator.close();
    }

    @Test
    void testSnapshotContainsKeyedStateWithConfiguredBackend() throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(),
                false,
                nfaFactory,
                null,
                null,
                function,
                null
        );
        IStateBackend configuredBackend = new MemoryStateBackend();
        operator.setStateBackend(configuredBackend);
        operator.setOutput(output);
        setProcessingTimeService(operator, MOCK_PTS);
        operator.open();

        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(43, "mid"), 3));
        operator.processWatermark(new Watermark(5));

        StateSnapshotContext ctx = new StateSnapshotContext(1L, System.currentTimeMillis());
        OperatorSnapshotResult snapshot = operator.snapshotState(ctx);

        assertNotNull(snapshot, "Snapshot should not be null");
        assertTrue(!snapshot.getKeyedStates().isEmpty() || !snapshot.getOperatorStates().isEmpty(),
                "Snapshot should contain some state after processing events");

        operator.close();
    }

    @Test
    void testNoBackendConfiguredDefaultsToMemoryKeyedBackend() throws Exception {
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

        IKeyedStateBackend<?> keyedBackend = operator.getKeyedStateBackend();
        assertNull(keyedBackend, "Without setStateBackend, keyedStateBackend should remain null in AbstractStreamOperator");

        NFAState state = operator.getNFAStateForTesting();
        assertNotNull(state, "NFA should still operate with MemoryKeyedStateBackend fallback in CepOperator");

        operator.processElement(new StreamRecord<>(new Event(42, "start42"), 2));
        operator.processElement(new StreamRecord<>(new Event(99, "end"), 4));
        operator.processWatermark(new Watermark(10));

        assertFalse(output.isEmpty(), "Pattern matching should work with fallback MemoryKeyedStateBackend");

        operator.close();
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
