package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 focused tests for {@link DeferredAckMailbox} — the functional
 * 3-phase reservation mailbox (offer → poll → ack/nack) covering capacity,
 * FIFO order, redelivery, dead-lettering, and thread safety.
 */
public class TestDeferredAckMailbox {

    private AgentMessageEnvelope env(String payload) {
        return new AgentMessageEnvelope("s", "agent.t.inbox", null, AgentMessageKind.ASYNC, payload);
    }

    // ========================================================================
    // offer → poll → ack happy path
    // ========================================================================

    @Test
    void offerPollAckHappyPath() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertTrue(mb.offer(env("m1")));
        assertEquals(1, mb.pendingCount());
        assertEquals(0, mb.inFlightCount());

        MailboxEntry entry = mb.poll();
        assertNotNull(entry);
        assertEquals(MailboxDeliveryState.IN_FLIGHT, entry.getState());
        assertEquals(1, entry.getDeliveryCount());
        assertEquals("m1", entry.getEnvelope().getPayload());
        assertEquals(0, mb.pendingCount());
        assertEquals(1, mb.inFlightCount());
        assertTrue(entry.getPolledAt() > 0);

        assertTrue(mb.ack(entry.getDeliveryId()));
        assertEquals(0, mb.inFlightCount());
        assertEquals(0, mb.pendingCount());
    }

    @Test
    void pollEmptyReturnsNull() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertNull(mb.poll());
    }

    @Test
    void ackUnknownIdReturnsFalse() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertFalse(mb.ack(999L));
    }

    @Test
    void nackUnknownIdReturnsFalse() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertFalse(mb.nack(999L, true));
    }

    @Test
    void offerNullEnvelopeThrowsNpe() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertThrows(NullPointerException.class, () -> mb.offer(null));
    }

    // ========================================================================
    // FIFO order
    // ========================================================================

    @Test
    void pollReturnsEntriesInFifoOrder() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        for (int i = 0; i < 5; i++) {
            mb.offer(env("m" + i));
        }
        for (int i = 0; i < 5; i++) {
            MailboxEntry e = mb.poll();
            assertNotNull(e);
            assertEquals("m" + i, e.getEnvelope().getPayload());
        }
        assertNull(mb.poll());
    }

    // ========================================================================
    // Bounded capacity / back-pressure
    // ========================================================================

    @Test
    void boundedCapacityOfferFullReturnsFalse() {
        DeferredAckMailbox mb = new DeferredAckMailbox(2, 5);
        assertTrue(mb.offer(env("a")));
        assertTrue(mb.offer(env("b")));
        // capacity counts PENDING + IN_FLIGHT = 2 → full
        assertFalse(mb.offer(env("c")), "offer on a full bounded mailbox must return false (back-pressure)");

        // poll moves pending → in-flight; total (pending+in-flight) still 2 → still full
        MailboxEntry e = mb.poll();
        assertNotNull(e);
        assertFalse(mb.offer(env("c")), "poll does not free a slot (in-flight still counts toward capacity)");

        // ack removes the in-flight entry → total drops to 1 → slot freed
        assertTrue(mb.ack(e.getDeliveryId()));
        assertTrue(mb.offer(env("c")), "after ack, a slot is freed and offer succeeds again");
        assertEquals(2, mb.pendingCount());
        assertEquals(0, mb.inFlightCount());
    }

    @Test
    void inFlightCountsTowardCapacity() {
        DeferredAckMailbox mb = new DeferredAckMailbox(1, 5);
        assertTrue(mb.offer(env("a")));
        // poll moves it to in-flight, but capacity counts pending+in-flight
        assertNotNull(mb.poll());
        assertEquals(1, mb.inFlightCount());
        assertFalse(mb.offer(env("b")), "in-flight entries count toward capacity");
    }

    @Test
    void unboundedDefaultNeverRejects() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        for (int i = 0; i < 1000; i++) {
            assertTrue(mb.offer(env("m" + i)));
        }
        assertEquals(1000, mb.pendingCount());
    }

    @Test
    void ackFreesCapacitySlot() {
        DeferredAckMailbox mb = new DeferredAckMailbox(1, 5);
        assertTrue(mb.offer(env("a")));
        MailboxEntry e = mb.poll();
        assertFalse(mb.offer(env("b")), "full while in-flight");
        assertTrue(mb.ack(e.getDeliveryId()));
        assertTrue(mb.offer(env("b")), "ack freed the slot");
    }

    // ========================================================================
    // nack redelivery
    // ========================================================================

    @Test
    void nackRequeueReturnsToPendingWithIncrementedCountAndNewId() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        mb.offer(env("m1"));
        MailboxEntry e1 = mb.poll();
        long firstId = e1.getDeliveryId();

        assertTrue(mb.nack(firstId, true));
        assertEquals(1, mb.pendingCount());
        assertEquals(0, mb.inFlightCount());

        MailboxEntry e2 = mb.poll();
        assertNotNull(e2);
        assertEquals(2, e2.getDeliveryCount(), "deliveryCount must increment on redelivery");
        assertNotEquals(firstId, e2.getDeliveryId(), "redelivery must assign a new deliveryId");
        assertEquals("m1", e2.getEnvelope().getPayload(), "same payload redelivered");
    }

    @Test
    void nackNoRequeueRemovesEntry() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        mb.offer(env("m1"));
        MailboxEntry e = mb.poll();
        assertTrue(mb.nack(e.getDeliveryId(), false));
        assertEquals(0, mb.pendingCount());
        assertEquals(0, mb.inFlightCount());
        assertNull(mb.poll(), "nack(requeue=false) must not redeliver");
    }

    @Test
    void ackedIdCannotBeNacked() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        mb.offer(env("m1"));
        MailboxEntry e = mb.poll();
        assertTrue(mb.ack(e.getDeliveryId()));
        assertFalse(mb.nack(e.getDeliveryId(), true), "already-acked id must not be nackable");
    }

    // ========================================================================
    // dead-letter
    // ========================================================================

    @Test
    void deadLetterAfterMaxDeliveryAttempts() {
        DeferredAckMailbox mb = new DeferredAckMailbox(0, 3);
        assertTrue(mb.offer(env("poison")));

        long currentId;
        // delivery 1: count=1
        MailboxEntry e1 = mb.poll();
        assertEquals(1, e1.getDeliveryCount());
        mb.nack(e1.getDeliveryId(), true);
        // delivery 2: count=2
        MailboxEntry e2 = mb.poll();
        assertEquals(2, e2.getDeliveryCount());
        mb.nack(e2.getDeliveryId(), true);
        // delivery 3: count=3 == max → redelivery should dead-letter
        MailboxEntry e3 = mb.poll();
        assertEquals(3, e3.getDeliveryCount());
        assertTrue(mb.nack(e3.getDeliveryId(), true), "nack must still return true when dead-lettering");

        assertEquals(0, mb.pendingCount(), "no redelivery after dead-letter");
        assertEquals(1, mb.deadLetterCount(), "entry must be in dead-letter");
        assertEquals(0, mb.inFlightCount());

        MailboxEntry dead = mb.deadLetterEntries().get(0);
        assertEquals(MailboxDeliveryState.DEAD_LETTERED, dead.getState());
        assertEquals("poison", dead.getEnvelope().getPayload());
    }

    @Test
    void deadLetterEntryIsNeverRedelivered() {
        DeferredAckMailbox mb = new DeferredAckMailbox(0, 1);
        mb.offer(env("once"));
        MailboxEntry e = mb.poll();
        assertEquals(1, e.getDeliveryCount());
        // max=1, so first requeue-nack dead-letters immediately
        mb.nack(e.getDeliveryId(), true);
        assertEquals(1, mb.deadLetterCount());
        assertNull(mb.poll());
        assertEquals(0, mb.pendingCount());
    }

    // ========================================================================
    // constructor validation
    // ========================================================================

    @Test
    void constructorRejectsZeroMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new DeferredAckMailbox(0, 0));
    }

    @Test
    void constructorRejectsNegativeMaxAttempts() {
        assertThrows(IllegalArgumentException.class, () -> new DeferredAckMailbox(0, -1));
    }

    @Test
    void accessorsReturnConfiguredValues() {
        DeferredAckMailbox mb = new DeferredAckMailbox(7, 9);
        assertEquals(7, mb.getCapacity());
        assertEquals(9, mb.getMaxDeliveryAttempts());
    }

    @Test
    void defaultMaxDeliveryAttemptsIsFive() {
        assertEquals(5, DeferredAckMailbox.DEFAULT_MAX_DELIVERY_ATTEMPTS);
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertEquals(5, mb.getMaxDeliveryAttempts());
    }

    // ========================================================================
    // Thread safety: concurrent offer + poll/ack
    // ========================================================================

    @Test
    void concurrentOfferAndPollNoLossNoDuplication() throws InterruptedException {
        final DeferredAckMailbox mb = new DeferredAckMailbox();
        final int total = 500;
        final int producers = 4;
        final ExecutorService pool = Executors.newFixedThreadPool(producers + 1);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger produced = new AtomicInteger(0);
        final AtomicInteger consumed = new AtomicInteger(0);
        final List<Long> ackedIds = java.util.Collections.synchronizedList(new ArrayList<>());

        // producers
        for (int p = 0; p < producers; p++) {
            final int base = p * (total / producers);
            final int end = base + (total / producers);
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int i = base; i < end; i++) {
                    if (mb.offer(env("m" + i))) {
                        produced.incrementAndGet();
                    }
                }
            });
        }

        // single consumer: poll → ack until we've acked `total`
        pool.submit(() -> {
            try {
                start.await();
            } catch (InterruptedException e) {
                return;
            }
            while (consumed.get() < total) {
                MailboxEntry e = mb.poll();
                if (e != null) {
                    assertTrue(mb.ack(e.getDeliveryId()));
                    ackedIds.add(e.getDeliveryId());
                    consumed.incrementAndGet();
                }
            }
        });

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(total, produced.get(), "all offers must succeed (unbounded)");
        assertEquals(total, consumed.get(), "all entries must be consumed exactly once");
        assertEquals(total, ackedIds.size());
        // no duplicate deliveryIds consumed
        assertEquals(total, ackedIds.stream().distinct().count(),
                "each entry must have a distinct deliveryId (no duplication)");
        assertEquals(0, mb.pendingCount());
        assertEquals(0, mb.inFlightCount());
    }
}
