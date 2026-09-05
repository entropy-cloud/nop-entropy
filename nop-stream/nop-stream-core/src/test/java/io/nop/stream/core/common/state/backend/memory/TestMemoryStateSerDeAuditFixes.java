package io.nop.stream.core.common.state.backend.memory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.typeutils.IStreamSerializer;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the 2026-09-01 core audit fixes (plan 0938-2):
 * S-3 serializer-failure observability, S-6 corrupt-snapshot stateType guard,
 * S-12 legacy valueTypeName/accumulatorTypeName restore fallback.
 */
class TestMemoryStateSerDeAuditFixes {

    private ListAppender<ILoggingEvent> appender;
    private Logger serdeLogger;

    @BeforeEach
    void attachAppender() {
        serdeLogger = (Logger) LoggerFactory.getLogger(MemoryStateSerDe.class);
        appender = new ListAppender<>();
        appender.start();
        serdeLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        serdeLogger.detachAppender(appender);
        appender.stop();
    }

    private boolean warnedWith(String fragment) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(m -> m.contains(fragment));
    }

    /** A custom serializer whose serialize() always fails. */
    static class FailingSerializer implements IStreamSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }

        @Override
        public byte[] serialize(String value) {
            throw new IllegalStateException("serializer boom");
        }

        @Override
        public String deserialize(byte[] data, Class<String> type) {
            throw new IllegalStateException("never reached");
        }
    }

    /**
     * S-3: a failing custom serializer falls back to the raw value (P2-09-02c
     * compatibility path — snapshot must still succeed) AND the degradation is
     * observable via a WARN log naming the serializer.
     */
    @Test
    void testFailingSerializerFallsBackWithWarning() throws Exception {
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ValueStateDescriptor<String> desc = new ValueStateDescriptor<>("s3-state", String.class);
        desc.setSerializer(new FailingSerializer());
        io.nop.stream.core.common.state.ValueState<String> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update("payload");

        StateSnapshot snapshot = backend.snapshotState();

        assertNotNull(snapshot, "snapshot must succeed despite serializer failure (documented fallback)");
        assertTrue(warnedWith("falling back to raw value"),
                "the silent degradation must be logged: " + appender.list);

        // Restore without the custom serializer: the raw value round-trips as-is.
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(snapshot);
        restored.setCurrentKey("k1");
        assertEquals("payload", restored.getState(
                new ValueStateDescriptor<>("s3-state", String.class)).value());
    }

    /**
     * S-6: a snapshot state entry without stateType fails fast with a
     * StreamException naming the state — not a bare NPE from switch-on-null.
     */
    @Test
    void testMissingStateTypeFailsFastWithStateName() {
        Map<String, Object> statesInner = new HashMap<>();
        statesInner.put("corrupt-state", new HashMap<String, Object>()); // no stateType key
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("states", statesInner);

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        StreamException ex = assertThrows(StreamException.class,
                () -> backend.restoreState(new StateSnapshot(stateData)));
        assertTrue(String.valueOf(ex.getParam("detail")).contains("corrupt-state"),
                "error must name the corrupt state: " + ex.getParam("detail"));
    }

    /**
     * S-6 companion: a states entry that is not a per-state info map fails fast
     * with the actual type — not a bare ClassCastException.
     */
    @Test
    void testNonMapStateEntryFailsFast() {
        Map<String, Object> statesInner = new HashMap<>();
        statesInner.put("bad", "just-a-string");
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("states", statesInner);

        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        StreamException ex = assertThrows(StreamException.class,
                () -> backend.restoreState(new StateSnapshot(stateData)));
        assertNotNull(ex.getParam("actualType"));
    }

    /**
     * S-12: legacy snapshots that spell the type keys as valueTypeName /
     * accumulatorTypeName restore for ReducingState exactly as they already do
     * for Appending/List state (the fallback was missing and had drifted).
     */
    @Test
    void testReducingStateRestoresFromLegacyTypeNameKeys() throws Exception {
        // 1. Produce a normal snapshot with a reducing state.
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>(
                "legacy-reduce", Long.class, LongCounter.class);
        backend.setCurrentKey("key1");
        ReducingState<Long> state = backend.getReducingState(desc);
        state.add(5L);
        state.add(7L);

        StateSnapshot snapshot = backend.snapshotState();

        // 2. Rewrite the info keys to the LEGACY spellings (valueTypeName /
        //    accumulatorTypeName), dropping the modern valueType/accumulatorType.
        Map<String, Object> stateData = snapshot.getStateData();
        @SuppressWarnings("unchecked")
        Map<String, Object> statesMap = (Map<String, Object>) stateData.get("states");
        @SuppressWarnings("unchecked")
        Map<String, Object> stateInfo = (Map<String, Object>) statesMap.get("legacy-reduce");
        assertNotNull(stateInfo, "snapshot must contain the reducing state info");

        Object valueType = stateInfo.remove("valueType");
        Object accumulatorType = stateInfo.remove("accumulatorType");
        assertNotNull(valueType);
        assertNotNull(accumulatorType);
        stateInfo.put("valueTypeName", valueType);
        stateInfo.put("accumulatorTypeName", accumulatorType);

        // 3. Restore into a fresh backend: must succeed and preserve values.
        MemoryKeyedStateBackend<String> restored = new MemoryKeyedStateBackend<>(String.class);
        restored.restoreState(new StateSnapshot(stateData));

        restored.setCurrentKey("key1");
        ReducingState<Long> restoredState = restored.getReducingState(
                new ReducingStateDescriptor<>("legacy-reduce", Long.class, LongCounter.class));
        assertEquals(Long.valueOf(12L), restoredState.get(),
                "legacy key spelling must restore the accumulated value");
    }
}
