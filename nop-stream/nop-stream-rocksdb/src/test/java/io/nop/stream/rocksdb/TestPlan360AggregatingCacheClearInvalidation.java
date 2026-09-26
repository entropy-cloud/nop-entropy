/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.functions.AggregateFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plan 360 R3-audit regression: the accumulator front cache (R2 optimization)
 * is only WRITTEN when ttl == null, but {@code clear()} must invalidate it
 * unconditionally. A surviving cached accumulator after clear() would merge the
 * next add() into the pre-clear value (double counting after window purge) —
 * exactly the session-window fire+purge+rebuild pattern.
 */
class TestPlan360AggregatingCacheClearInvalidation {

    private Path dir;
    private RocksDBKeyedStateBackend<Long> backend;
    private RocksDBInternalAggregatingState<Long, io.nop.stream.core.windowing.windows.TimeWindow, Long, Long, Long> state;

    /** Long sum accumulator: add() merges into the cached instance — any stale cache corrupts the result. */
    static class LongSum implements AggregateFunction<Long, Long, Long> {
        private static final long serialVersionUID = 1L;

        @Override
        public Long createAccumulator() {
            return 0L;
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
    }

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("p360-agg-clear");
        backend = new RocksDBKeyedStateBackend<>(dir.toAbsolutePath().toString(), Long.class, 128, null);
        state = (RocksDBInternalAggregatingState<Long, io.nop.stream.core.windowing.windows.TimeWindow, Long, Long, Long>)
                (RocksDBInternalAggregatingState<?, ?, ?, ?, ?>) backend.getInternalAppendingState(
                        new AggregatingStateDescriptor<>("p360-agg-clear", new LongSum(), Long.class));
        state.setCurrentNamespace(new io.nop.stream.core.windowing.windows.TimeWindow(0, 60_000));
    }

    @AfterEach
    void tearDown() throws IOException {
        backend.close();
        deleteRecursively(dir);
    }

    @Test
    void clearInvalidatesCachedAccumulator_soNextAddStartsFresh() throws Exception {
        backend.setCurrentKey(7L);

        state.add(10L);
        state.add(20L);
        assertEquals(30L, state.get());

        // Window purge path: clear() while the accumulator cache holds 30.
        state.clear();

        // Rebuild the same (key, namespace) window: the add MUST start from
        // createAccumulator (0), not merge into the cleared value.
        state.add(5L);
        assertEquals(5L, state.get(),
                "add() after clear() must start fresh — a stale cached accumulator would double count");
    }

    @Test
    void repeatedClearAddCyclesStayConsistent() throws Exception {
        backend.setCurrentKey(9L);
        for (int cycle = 1; cycle <= 5; cycle++) {
            state.add(1L);
            state.add(1L);
            assertEquals(2L, state.get(), "cycle " + cycle + ": value must be 2 after clear+2 adds");
            state.clear();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new io.nop.stream.core.exceptions.StreamException(
                            io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e);
                }
            });
        }
    }
}
