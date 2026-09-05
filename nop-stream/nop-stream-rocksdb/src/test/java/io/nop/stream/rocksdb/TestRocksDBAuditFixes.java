/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.io.File;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.accumulators.LongCounter;
import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.StateTtlUpdateType;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for the item 11 audit fixes (roadmap item 11,
 * rocksdb side):
 * <ul>
 *   <li>RK-3: legacy {@code valueTypeName}/{@code accumulatorTypeName} snapshot keys
 *       restore on the Reducing/Aggregating branches (same fallback order as the
 *       other branches and as MemoryStateSerDe's S-12 fix in item 7).</li>
 *   <li>RK-4: repeated {@code getState(...)} with an unchanged TTL config preserves
 *       the accumulated sidecar timestamps (no silent TTL window reset).</li>
 *   <li>RK-5: accumulator-type inference failure logs a warning instead of being
 *       silently swallowed.</li>
 *   <li>RK-8: a corrupted incremental checkpoint marker fails fast instead of
 *       silently falling through to an empty full-JSON restore.</li>
 * </ul>
 */
class TestRocksDBAuditFixes {

    private static final class FakeClock implements TtlTimeProvider {
        long now;

        @Override
        public long currentTimeMillis() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> newBackend(FakeClock clock) {
        RocksDBKeyedStateBackend<String> backend =
                (RocksDBKeyedStateBackend<String>) new RocksDBStateBackend(tempDir.getAbsolutePath())
                        .createKeyedStateBackend(String.class);
        if (clock != null) {
            backend.setTtlTimeProvider(clock);
        }
        return backend;
    }

    private static StateTtlConfig ttl(Duration d) {
        return StateTtlConfig.newBuilder(d).setUpdateType(StateTtlUpdateType.OnCreateAndWrite).build();
    }

    @Test
    void repeatedGetStatePreservesTtlTimestamps() throws Exception {
        FakeClock clock = new FakeClock();
        RocksDBKeyedStateBackend<String> backend = newBackend(clock);
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class, 0);
        desc.setTtlConfig(ttl(Duration.ofMillis(100)));

        ValueState<Integer> state = backend.getState(desc);
        backend.setCurrentKey("k1");
        state.update(42);
        assertEquals(42, state.value());

        clock.advance(50);
        // Re-fetching the state with the same descriptor (e.g. a user function calling
        // getState per record) must not wipe the sidecar timestamps: the original
        // expiry schedule (t=100) has to stay in force.
        ValueState<Integer> refetched = backend.getState(desc);
        assertEquals(42, refetched.value(), "entry must still be readable mid-window after re-getState");

        clock.advance(60); // t = 110 > 100: expired under the ORIGINAL schedule
        assertEquals(0, refetched.value(),
                "re-getState must not reset the TTL window: entry must expire on the original schedule");
        backend.close();
    }

    @Test
    void legacyTypeNameKeysRestoreReducingState() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend(null);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("namespace", "_default_");
        entry.put("key", "k1");
        entry.put("value", 12L);

        Map<String, Object> stateInfo = new LinkedHashMap<>();
        stateInfo.put("stateType", "ReducingState");
        // Legacy key spellings (pre-S-12-era snapshots): *TypeName instead of *Type.
        stateInfo.put("valueTypeName", "java.lang.Long");
        stateInfo.put("accumulatorTypeName", LongCounter.class.getName());
        stateInfo.put("entries", List.of(entry));

        Map<String, Object> states = new LinkedHashMap<>();
        states.put("r", stateInfo);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyType", String.class.getName());
        data.put("states", states);

        backend.restoreState(new StateSnapshot(data));

        ReducingStateDescriptor<Long> desc = new ReducingStateDescriptor<>("r", Long.class, LongCounter.class);
        ReducingState<Long> state = backend.getReducingState(desc);
        backend.setCurrentKey("k1");
        assertEquals(12L, state.get(), "legacy *TypeName snapshot keys must restore the value");
        backend.close();
    }

    @Test
    void accumulatorTypeInferenceFailureIsLogged() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend(null);
        // A live aggregate function registered for restore whose createAccumulator()
        // throws: the inference must degrade to the recorded type WITH a warning.
        AggregateFunction<Long, Long, Long> throwing = new AggregateFunction<>() {
            @Override
            public Long createAccumulator() {
                throw new IllegalStateException("boom");
            }

            @Override
            public Long add(Long value, Long accumulator) {
                return accumulator + value;
            }

            @Override
            public Long getResult(Long accumulator) {
                return accumulator;
            }

            @Override
            public Long merge(Long a, Long b) {
                return a + b;
            }
        };
        backend.registerRestoreAggregateFunction("a", throwing);

        ch.qos.logback.classic.Logger serdeLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RocksDBSnapshotSerDe.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        serdeLogger.addAppender(appender);
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("namespace", "_default_");
            entry.put("key", "k1");
            entry.put("value", 7);

            Map<String, Object> stateInfo = new LinkedHashMap<>();
            stateInfo.put("stateType", "AggregatingState");
            // valueType = java.lang.Object triggers the accumulator-type inference path.
            stateInfo.put("valueType", Object.class.getName());
            stateInfo.put("aggregateFunctionType", throwing.getClass().getName());
            stateInfo.put("entries", List.of(entry));

            Map<String, Object> states = new LinkedHashMap<>();
            states.put("a", stateInfo);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("keyType", String.class.getName());
            data.put("states", states);

            backend.restoreState(new StateSnapshot(data));

            boolean warned = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("infer accumulator type"));
            assertTrue(warned, "inference fallback must log a WARN (observable degradation, not silent)");
        } finally {
            serdeLogger.detachAppender(appender);
            appender.stop();
            backend.close();
        }
    }

    @Test
    void corruptedIncrementalMarkerFailsFast() {
        RocksDBKeyedStateBackend<String> backend = newBackend(null);
        Map<String, Object> data = new LinkedHashMap<>();
        // A marker that is neither IncrementalSnapshotResult nor a reconstructable map:
        // restoring it must fail fast instead of silently restoring nothing.
        data.put(IncrementalSnapshotResult.MARKER_KEY, "corrupted-marker");
        data.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD, RocksDBKeyEncoder.KEY_LAYOUT_VERSION);

        StreamException ex = assertThrows(StreamException.class, () -> backend.restoreState(new StateSnapshot(data)));
        assertTrue(ex.getMessage().contains("incremental checkpoint marker"),
                "error should identify the corrupted marker, got: " + ex.getMessage());
        backend.close();
    }
}
