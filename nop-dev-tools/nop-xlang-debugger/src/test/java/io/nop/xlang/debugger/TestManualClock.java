package io.nop.xlang.debugger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestManualClock {

    @Test
    public void testTimeMovesOnlyWhenAdvanced() {
        ManualClock clock = new ManualClock(1000);
        assertEquals(1000, clock.currentTimeMillis());
        assertEquals(1000, clock.currentTimeMillis());
        clock.advance(250);
        assertEquals(1250, clock.currentTimeMillis());
        assertEquals(1250L * 1_000_000L, clock.nanoTime());
    }

    @Test
    public void testReadsAreCountedPerThread() throws Exception {
        ManualClock clock = new ManualClock(0);
        Thread worker = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                clock.currentTimeMillis();
            }
        });
        worker.start();
        worker.join(10000);

        assertEquals(3, clock.readsBy(worker));
        assertEquals(0, clock.readsBy(Thread.currentThread()));
        clock.currentTimeMillis();
        assertEquals(1, clock.readsBy(Thread.currentThread()));
        assertEquals(3, clock.readsBy(worker));
    }

    @Test
    public void testPeekDoesNotCountAsRead() {
        ManualClock clock = new ManualClock(42);
        assertEquals(42, clock.peekMillis());
        assertEquals(0, clock.readsBy(Thread.currentThread()));
    }

    @Test
    public void testCannotMoveBackwards() {
        ManualClock clock = new ManualClock(0);
        assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
    }
}
