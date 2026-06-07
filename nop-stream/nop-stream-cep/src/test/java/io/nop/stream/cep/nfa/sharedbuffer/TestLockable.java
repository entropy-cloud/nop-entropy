package io.nop.stream.cep.nfa.sharedbuffer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TestLockable {

    @Test
    void testReleaseReturnsTrueWhenCounterReachesZero() {
        Lockable<String> lockable = new Lockable<>("test", 2);
        assertFalse(lockable.release());
        assertTrue(lockable.release());
    }

    @Test
    void testReleaseThrowsWhenCounterAlreadyZero() {
        Lockable<String> lockable = new Lockable<>("test", 0);
        assertThrows(IllegalStateException.class, lockable::release);
        assertEquals(0, lockable.getRefCounter());
    }

    @Test
    void testConcurrentReleaseDoesNotCauseNegativeRefCount() throws Exception {
        int initialCount = 10;
        int threadCount = 20;
        Lockable<String> lockable = new Lockable<>("test", initialCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    results.add(lockable.release());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertTrue(lockable.getRefCounter() >= 0,
                "refCounter must never be negative, was: " + lockable.getRefCounter());

        long trueCount = results.stream().filter(b -> b).count();
        assertTrue(trueCount >= 1, "At least one release should return true");
    }

    @Test
    void testLockIncrementsCounter() {
        Lockable<String> lockable = new Lockable<>("test", 0);
        assertEquals(0, lockable.getRefCounter());
        lockable.lock();
        assertEquals(1, lockable.getRefCounter());
        lockable.lock();
        assertEquals(2, lockable.getRefCounter());
    }
}
