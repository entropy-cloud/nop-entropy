package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestMemoryKeyedStateBackendRouteKeyOverflow {

    @Test
    public void testRouteKeyWithHashCodeIntegerMinValue() {
        Object keyWithMinHash = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };

        MemoryKeyedStateBackend<Object> backend = new MemoryKeyedStateBackend<>(Object.class, 4);

        Object routed = backend.routeKey(keyWithMinHash);

        assertNotNull(routed);
        assertTrue(routed instanceof ShardPrefixedKey);
        ShardPrefixedKey spk = (ShardPrefixedKey) routed;
        assertTrue(spk.shardId >= 0 && spk.shardId < 4,
                "Shard ID must be non-negative, got: " + spk.shardId);
    }

    @Test
    public void testRouteKeyWithHashCodeNegativeOne() {
        Object keyWithNegHash = new Object() {
            @Override
            public int hashCode() {
                return -1;
            }
        };

        MemoryKeyedStateBackend<Object> backend = new MemoryKeyedStateBackend<>(Object.class, 4);

        Object routed = backend.routeKey(keyWithNegHash);

        assertNotNull(routed);
        assertTrue(routed instanceof ShardPrefixedKey);
        ShardPrefixedKey spk = (ShardPrefixedKey) routed;
        assertTrue(spk.shardId >= 0 && spk.shardId < 4,
                "Shard ID must be non-negative, got: " + spk.shardId);
    }

    @Test
    public void testValueStateWorksWithIntegerMinValueHashCodeKey() throws Exception {
        MemoryKeyedStateBackend<Object> backend = new MemoryKeyedStateBackend<>(Object.class, 4);

        Object keyMinHash = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }

            @Override
            public String toString() {
                return "key-min-hash";
            }
        };

        ValueStateDescriptor<Long> desc = new ValueStateDescriptor<>("count", Long.class, 0L);
        ValueState<Long> state = backend.getState(desc);

        backend.setCurrentKey(keyMinHash);
        state.update(42L);
        assertEquals(Long.valueOf(42L), state.value());

        Object keyMinHash2 = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }

            @Override
            public boolean equals(Object o) {
                return this == o;
            }

            @Override
            public String toString() {
                return "key-min-hash-2";
            }
        };

        backend.setCurrentKey(keyMinHash2);
        assertEquals(Long.valueOf(0L), state.value(), "Different key instance should have independent state");
    }

    @Test
    public void testRouteKeySingleShardReturnsRawKey() {
        Object key = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };

        MemoryKeyedStateBackend<Object> backend = new MemoryKeyedStateBackend<>(Object.class, 1);

        Object routed = backend.routeKey(key);
        assertSame(key, routed, "With single shard, routeKey should return the raw key");
    }
}
