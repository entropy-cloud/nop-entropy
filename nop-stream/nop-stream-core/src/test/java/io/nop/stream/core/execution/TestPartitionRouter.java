package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestPartitionRouter {

    @Test
    void testForwardPartitionRouter_alwaysReturns0() {
        ForwardPartitionRouter router = new ForwardPartitionRouter(4);
        StreamRecord<String> record = new StreamRecord<>("test", 0L);
        assertEquals(0, router.selectChannel(record));
        assertEquals(4, router.getNumberOfPartitions());
    }

    @Test
    void testHashPartitionRouter_normalValue() {
        HashPartitionRouter router = new HashPartitionRouter(4, null);
        StreamRecord<String> record = new StreamRecord<>("hello", 0L);
        int channel = router.selectChannel(record);
        assertTrue(channel >= 0 && channel < 4,
                "Channel should be in [0, 4), got: " + channel);
    }

    @Test
    void testHashPartitionRouter_integerMinValue() {
        HashPartitionRouter router = new HashPartitionRouter(4, null);
        StreamRecord<Object> record = new StreamRecord<>(new Object() {
            @Override
            public int hashCode() {
                return Integer.MIN_VALUE;
            }
        }, 0L);
        int channel = router.selectChannel(record);
        assertTrue(channel >= 0 && channel < 4,
                "Math.floorMod should handle Integer.MIN_VALUE correctly, got: " + channel);
        assertEquals(0, channel,
                "floorMod(Integer.MIN_VALUE, 4) should be 0");
    }

    @Test
    void testHashPartitionRouter_negativeHashCode() {
        HashPartitionRouter router = new HashPartitionRouter(3, null);
        StreamRecord<Object> record = new StreamRecord<>(new Object() {
            @Override
            public int hashCode() {
                return -7;
            }
        }, 0L);
        int channel = router.selectChannel(record);
        assertTrue(channel >= 0 && channel < 3,
                "Negative hash should produce non-negative channel, got: " + channel);
        assertEquals(2, channel, "floorMod(-7, 3) should be 2");
    }

    @Test
    void testHashPartitionRouter_singlePartition() {
        HashPartitionRouter router = new HashPartitionRouter(1, null);
        StreamRecord<String> record = new StreamRecord<>("any", 0L);
        assertEquals(0, router.selectChannel(record));
    }

    @Test
    void testRebalancePartitionRouter_roundRobin() {
        RebalancePartitionRouter router = new RebalancePartitionRouter(3, 0);
        StreamRecord<String> record = new StreamRecord<>("x", 0L);
        assertEquals(0, router.selectChannel(record));
        assertEquals(1, router.selectChannel(record));
        assertEquals(2, router.selectChannel(record));
        assertEquals(0, router.selectChannel(record));
    }

    @Test
    void testRebalancePartitionRouter_overflowStillCorrect() {
        RebalancePartitionRouter router = new RebalancePartitionRouter(3, Integer.MAX_VALUE - 1);
        StreamRecord<String> record = new StreamRecord<>("x", 0L);
        int ch1 = router.selectChannel(record);
        int ch2 = router.selectChannel(record);
        assertTrue(ch1 >= 0 && ch1 < 3, "ch1 should be in [0,3), got: " + ch1);
        assertTrue(ch2 >= 0 && ch2 < 3, "ch2 should be in [0,3), got: " + ch2);
    }
}
