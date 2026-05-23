package io.nop.stream.core.common.state.shard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestStateShard {

    @Test
    void testSingleShard() {
        StateShard shard = StateShard.singleShard(0);
        assertEquals(1, shard.getStateShardCount());
        assertEquals(0, shard.getStateShardId());
        assertEquals(0, shard.getOwnerSubtask());
    }

    @Test
    void testComputeShardIdDeterministic() {
        StateShard shard = new StateShard(4, 0, 0, "DEFAULT");
        String key = "test-key";
        int id1 = shard.computeShardId(key);
        int id2 = shard.computeShardId(key);
        assertEquals(id1, id2);
    }

    @Test
    void testComputeShardIdSameKeyAcrossJVMInstances() {
        StateShard shard1 = new StateShard(4, 0, 0, "DEFAULT");
        StateShard shard2 = new StateShard(4, 0, 0, "DEFAULT");
        String key = "deterministic-key";
        assertEquals(shard1.computeShardId(key), shard2.computeShardId(key));
    }

    @Test
    void testSingleShardAlwaysZero() {
        StateShard shard = StateShard.singleShard(0);
        assertEquals(0, shard.computeShardId("any-key"));
        assertEquals(0, shard.computeShardId(null));
    }

    @Test
    void testKeyPrefixEmptyForSingleShard() {
        StateShard shard = StateShard.singleShard(0);
        assertEquals("", shard.keyPrefix());
    }

    @Test
    void testKeyPrefixForMultiShard() {
        StateShard shard = new StateShard(4, 2, 0, "DEFAULT");
        assertEquals("2/", shard.keyPrefix());
    }

    @Test
    void testInvalidShardCount() {
        assertThrows(IllegalArgumentException.class, () -> new StateShard(0, 0, 0, "DEFAULT"));
    }

    @Test
    void testInvalidShardIdOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new StateShard(4, 4, 0, "DEFAULT"));
        assertThrows(IllegalArgumentException.class, () -> new StateShard(4, -1, 0, "DEFAULT"));
    }
}
