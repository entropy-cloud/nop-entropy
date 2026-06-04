package io.nop.stream.core.common.state.shard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStateShardBoundary {

    @Test
    void testMinValueHashProducesNonNegativeShard() {
        StateShard shard = new StateShard(16, 0, 0, "DEFAULT");
        Object keyWithMinHash = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };

        int shardId = shard.computeShardId(keyWithMinHash);
        assertTrue(shardId >= 0, "Shard ID must be non-negative but was " + shardId);
        assertTrue(shardId < 16, "Shard ID must be less than stateShardCount but was " + shardId);
    }

    @Test
    void testNegativeHashProducesNonNegativeShard() {
        StateShard shard = new StateShard(8, 0, 0, "DEFAULT");
        Object keyWithNegHash = new Object() {
            @Override
            public int hashCode() {
                return -42;
            }
        };

        int shardId = shard.computeShardId(keyWithNegHash);
        assertTrue(shardId >= 0);
        assertTrue(shardId < 8);
    }

    @Test
    void testPositiveHashProducesNonNegativeShard() {
        StateShard shard = new StateShard(8, 0, 0, "DEFAULT");
        Object keyWithPosHash = new Object() {
            @Override
            public int hashCode() {
                return 42;
            }
        };

        int shardId = shard.computeShardId(keyWithPosHash);
        assertTrue(shardId >= 0);
        assertTrue(shardId < 8);
    }

    @Test
    void testSingleShardAlwaysReturnsZero() {
        StateShard shard = StateShard.singleShard(0);
        Object anyKey = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };

        assertEquals(0, shard.computeShardId(anyKey));
    }

    @Test
    void testStableHashHandlesNull() {
        assertEquals(0, StateShard.stableHash(null));
    }

    @Test
    void testConsistencyAcrossMultipleMinValueCalls() {
        StateShard shard = new StateShard(16, 0, 0, "DEFAULT");
        Object key = new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        };

        int first = shard.computeShardId(key);
        int second = shard.computeShardId(key);
        assertEquals(first, second);
    }
}
