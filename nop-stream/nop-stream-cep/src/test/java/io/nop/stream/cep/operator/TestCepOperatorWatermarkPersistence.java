package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
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

public class TestCepOperatorWatermarkPersistence {

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
        @Override public long getCurrentProcessingTime() { return time++; }
        @Override public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) { return null; }
    };

    private CepOperator<Event, Integer, String> createOperator() throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null
        );
        operator.setOutput(output);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    private CepOperator<Event, Integer, String> createOperatorWithRestore(
            OperatorSnapshotResult snapshot) throws Exception {
        TestOutput<String> restoredOutput = new TestOutput<>();
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new EventTypeSerializer(), false, nfaFactory, null, null, function, null
        );
        operator.setOutput(restoredOutput);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.restoreState(snapshot);
        operator.open();
        return operator;
    }

    @Test
    void testWatermarkPersistedInSnapshot() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();

        operator.processWatermark(new Watermark(100));
        assertEquals(100, operator.getCurrentWatermark());

        OperatorSnapshotResult snapshot = operator.snapshotState(
                new StateSnapshotContext(1, System.currentTimeMillis()));

        Object wmObj = snapshot.getOperatorState("cep-current-watermark");
        assertEquals(100L, wmObj);

        operator.close();
    }

    @Test
    void testWatermarkRestoredFromSnapshot() throws Exception {
        CepOperator<Event, Integer, String> original = createOperator();

        original.processWatermark(new Watermark(200));
        assertEquals(200, original.getCurrentWatermark());

        OperatorSnapshotResult snapshot = original.snapshotState(
                new StateSnapshotContext(1, System.currentTimeMillis()));
        original.close();

        CepOperator<Event, Integer, String> restored = createOperatorWithRestore(snapshot);
        assertEquals(200, restored.getCurrentWatermark());

        restored.close();
    }

    @Test
    void testWatermarkAdvancementAfterRestore() throws Exception {
        CepOperator<Event, Integer, String> original = createOperator();

        original.processWatermark(new Watermark(150));
        OperatorSnapshotResult snapshot = original.snapshotState(
                new StateSnapshotContext(1, System.currentTimeMillis()));
        original.close();

        CepOperator<Event, Integer, String> restored = createOperatorWithRestore(snapshot);
        assertEquals(150, restored.getCurrentWatermark());

        restored.processWatermark(new Watermark(300));
        assertEquals(300, restored.getCurrentWatermark());

        restored.processWatermark(new Watermark(100));
        assertEquals(300, restored.getCurrentWatermark());

        restored.close();
    }

    @Test
    void testWatermarkMinValueWithoutRestore() throws Exception {
        CepOperator<Event, Integer, String> operator = createOperator();
        assertEquals(Long.MIN_VALUE, operator.getCurrentWatermark());
        operator.close();
    }

    private static class EventTypeSerializer implements TypeSerializer<Event> {
        @Override public boolean isImmutableType() { return false; }
        @Override public TypeSerializer<Event> duplicate() { return this; }
        @Override public Event createInstance() { return new Event(); }
        @Override public Event copy(Event from) { return new Event(from.getId(), from.getName()); }
        @Override public Event copy(Event from, Event reuse) { return new Event(from.getId(), from.getName()); }
        @Override public int getLength() { return -1; }
    }
}
