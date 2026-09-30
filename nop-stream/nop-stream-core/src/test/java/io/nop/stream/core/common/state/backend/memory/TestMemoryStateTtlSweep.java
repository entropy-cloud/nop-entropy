/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import java.time.Duration;

import io.nop.stream.core.common.state.StateTtlConfig;
import io.nop.stream.core.common.state.StateTtlUpdateType;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.TtlTimeProvider;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 3 ST-12: the memory backend gains a TTL sweep entry point
 * OUTSIDE the checkpoint path — an explicit
 * {@link MemoryKeyedStateBackend#cleanupExpiredEntries()} plus an automatic
 * time-gated trigger on {@link MemoryKeyedStateBackend#setCurrentKey} — and
 * the sidecar sweep is a single iterator-remove pass
 * ({@link TtlContext#sweepExpired}), so no full-table copy and no
 * ConcurrentModificationException even when every entry in the map is removed
 * during the iteration.
 */
class TestMemoryStateTtlSweep {

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

    private static StateTtlConfig ttl(Duration d) {
        return StateTtlConfig.newBuilder(d).setUpdateType(StateTtlUpdateType.OnCreateAndWrite).build();
    }

    @Test
    void explicitSweepRemovesExpiredEntriesWithoutAnyAccess() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setTtlSweepIntervalMillis(0); // disable the automatic trigger; explicit entry only
        backend.setTtlTimeProvider(clock);

        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));
        @SuppressWarnings("unchecked")
        MemoryValueState<Integer> state = (MemoryValueState<Integer>) backend.getState(desc);

        backend.setCurrentKey("dead1");
        state.update(1);
        backend.setCurrentKey("dead2");
        state.update(2);
        clock.advance(60); // both expired
        backend.setCurrentKey("alive");
        state.update(3); // fresh entry

        // The sweep must DELETE the expired entries (not merely hide them):
        // asserted on the raw storage map, so no read-path lazy eviction can
        // explain the result away.
        int swept = backend.cleanupExpiredEntries();
        assertEquals(2, swept, "sweep reclaimed both expired entries");
        assertEquals(1, state.ttlStorage().size(), "only the fresh entry survives the sweep");
        TtlContext<TypedNamespaceAndKey> ctx = state.ttlContext();
        assertNotNull(ctx);
        // Storage and sidecar shrink together (iterator remove of the very
        // entry being visited), so a second sweep finds nothing left.
        assertEquals(0, backend.cleanupExpiredEntries(), "sweep is idempotent — no stale sidecar entries");
        assertTrue(ctx.hasTimestamp(state.ttlStorage().keySet().iterator().next()),
                "fresh entry keeps its sidecar timestamp");
    }

    @Test
    void sweepRemovesAllEntriesWithoutConcurrentModificationException() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setTtlSweepIntervalMillis(0);
        backend.setTtlTimeProvider(clock);

        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));
        @SuppressWarnings("unchecked")
        MemoryValueState<Integer> state = (MemoryValueState<Integer>) backend.getState(desc);

        int n = 500;
        for (int i = 0; i < n; i++) {
            backend.setCurrentKey("k" + i);
            state.update(i);
        }
        clock.advance(60); // every entry expired — the sweep removes WHILE iterating

        assertEquals(n, backend.cleanupExpiredEntries(),
                "iterator-remove sweep must reclaim every expired entry with no CME");
        assertTrue(state.ttlStorage().isEmpty(), "storage fully reclaimed");
    }

    @Test
    void automaticSweepFiresFromSetCurrentKeyWhenIntervalElapses() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setTtlSweepIntervalMillis(1); // smallest positive interval (1ms of nanoTime)
        backend.setTtlTimeProvider(clock);

        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));
        @SuppressWarnings("unchecked")
        MemoryValueState<Integer> state = (MemoryValueState<Integer>) backend.getState(desc);

        backend.setCurrentKey("dead");
        state.update(1);
        clock.advance(60); // expired — and no value()/snapshot read may touch it below

        try {
            Thread.sleep(5); // let the nanotime gate elapse (deterministic in practice)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        backend.setCurrentKey("unrelated"); // the periodic trigger point — no read of "dead"

        assertTrue(state.ttlStorage().isEmpty(),
                "the automatic setCurrentKey trigger must have swept the expired entry "
                        + "outside any checkpoint or read path");
    }

    @Test
    void nonPositiveIntervalDisablesTheAutomaticSweep() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setTtlSweepIntervalMillis(0);
        backend.setTtlTimeProvider(clock);

        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        desc.setTtlConfig(ttl(Duration.ofMillis(50)));
        @SuppressWarnings("unchecked")
        MemoryValueState<Integer> state = (MemoryValueState<Integer>) backend.getState(desc);

        backend.setCurrentKey("dead");
        state.update(1);
        clock.advance(60);
        backend.setCurrentKey("a");
        backend.setCurrentKey("b");

        assertEquals(1, state.ttlStorage().size(),
                "interval <= 0 disables the automatic trigger; the expired entry stays until "
                        + "an explicit sweep / lazy eviction / snapshot");
        assertEquals(1, backend.cleanupExpiredEntries(), "explicit entry still available");
    }

    @Test
    void ttlFreeJobHasNoSweepAndNoReclaim() throws Exception {
        FakeClock clock = new FakeClock();
        MemoryKeyedStateBackend<String> backend = new MemoryKeyedStateBackend<>(String.class);
        backend.setTtlTimeProvider(clock);

        ValueStateDescriptor<Integer> plainDesc = new ValueStateDescriptor<>("v", Integer.class);
        ValueState<Integer> state = backend.getState(plainDesc);
        backend.setCurrentKey("k");
        state.update(1);
        clock.advance(10_000);
        backend.setCurrentKey("k2");

        assertEquals(0, backend.cleanupExpiredEntries(), "no TTL-bound state -> nothing to sweep");
        backend.setCurrentKey("k");
        assertEquals(1, state.value(), "plain entry untouched");
    }
}
