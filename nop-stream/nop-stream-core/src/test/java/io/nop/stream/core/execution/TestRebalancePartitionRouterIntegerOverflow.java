package io.nop.stream.core.execution;

import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRebalancePartitionRouterIntegerOverflow {

    @Test
    void testCounterAtIntegerMinValue() throws Exception {
        RebalancePartitionRouter router = new RebalancePartitionRouter(3, 0);

        Field counterField = RebalancePartitionRouter.class.getDeclaredField("roundRobinCounter");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(router);
        counter.set(Integer.MIN_VALUE);

        StreamRecord<String> record = new StreamRecord<>("x", 0L);
        int channel = router.selectChannel(record);

        assertTrue(channel >= 0 && channel < 3,
                "Channel must be non-negative even when counter starts at Integer.MIN_VALUE, got: " + channel);
    }

    @Test
    void testCounterAtIntegerMinValueMultipleCalls() throws Exception {
        RebalancePartitionRouter router = new RebalancePartitionRouter(4, 0);

        Field counterField = RebalancePartitionRouter.class.getDeclaredField("roundRobinCounter");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(router);
        counter.set(Integer.MIN_VALUE);

        StreamRecord<String> record = new StreamRecord<>("x", 0L);

        for (int i = 0; i < 10; i++) {
            int channel = router.selectChannel(record);
            assertTrue(channel >= 0 && channel < 4,
                    "Channel must be in [0,4) on call " + i + ", got: " + channel);
        }
    }

    @Test
    void testCounterWrappingAroundMaxValue() throws Exception {
        RebalancePartitionRouter router = new RebalancePartitionRouter(3, 0);

        Field counterField = RebalancePartitionRouter.class.getDeclaredField("roundRobinCounter");
        counterField.setAccessible(true);
        AtomicInteger counter = (AtomicInteger) counterField.get(router);
        counter.set(Integer.MAX_VALUE);

        StreamRecord<String> record = new StreamRecord<>("x", 0L);
        int ch1 = router.selectChannel(record);
        int ch2 = router.selectChannel(record);

        assertTrue(ch1 >= 0 && ch1 < 3, "ch1 should be in [0,3), got: " + ch1);
        assertTrue(ch2 >= 0 && ch2 < 3, "ch2 should be in [0,3) after overflow, got: " + ch2);
    }
}
