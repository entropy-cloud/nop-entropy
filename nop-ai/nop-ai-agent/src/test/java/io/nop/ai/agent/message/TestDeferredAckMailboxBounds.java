package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-C5-017（plan 2556 裁定 E）regression carrier: DeferredAckMailbox bounds.
 *
 * <ul>
 *   <li>deadLetter retention is bounded — a full dead-letter list evicts its
 *       OLDEST entry (FIFO) with a WARN.</li>
 *   <li>stale inFlight entries are lazily replayed by the sweep (driven from
 *       the offer/poll/nack critical sections or the test seam — no scheduler
 *       thread), re-pollable with a fresh deliveryId and incremented
 *       deliveryCount; entries past maxDeliveryAttempts are dead-lettered.</li>
 *   <li>The bounded-default factory ({@code boundedDefaultFactory}) produces
 *       bounded mailboxes for the explicit-enable wiring path.</li>
 * </ul>
 */
public class TestDeferredAckMailboxBounds {

    private AgentMessageEnvelope env(String payload) {
        return new AgentMessageEnvelope("s", "agent.t.inbox", null, AgentMessageKind.ASYNC, payload);
    }

    // ---- dead-letter bound --------------------------------------------------

    @Test
    void deadLetterBoundedEvictsOldest() {
        // maxDeliveryAttempts=1 → every nack(requeue=true) dead-letters.
        // deadLetterCapacity=2 → retention capped at 2, oldest evicted.
        DeferredAckMailbox mb = new DeferredAckMailbox(0, 1, 2);
        assertEquals(2, mb.getDeadLetterCapacity());

        for (int i = 1; i <= 3; i++) {
            assertTrue(mb.offer(env("m" + i)));
            MailboxEntry e = mb.poll();
            assertNotNull(e);
            assertTrue(mb.nack(e.getDeliveryId(), true));
        }

        assertEquals(2, mb.deadLetterCount(),
                "dead-letter retention must be bounded by deadLetterCapacity");
        // FIFO eviction: the OLDEST entries (m1, m2) are gone; m3 + m2 remain
        // (m1 evicted when m3 arrived).
        assertEquals("m2", mb.deadLetterEntries().get(0).getEnvelope().getPayload(),
                "the oldest dead-lettered entry must have been evicted");
        assertEquals("m3", mb.deadLetterEntries().get(1).getEnvelope().getPayload());
    }

    @Test
    void deadLetterDefaultBounded() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        assertEquals(DeferredAckMailbox.DEFAULT_DEAD_LETTER_CAPACITY, mb.getDeadLetterCapacity(),
                "the default constructor must carry the bounded dead-letter retention");
    }

    // ---- stale inFlight replay ----------------------------------------------

    @Test
    void staleInFlightReplayedBySweep() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        mb.setStaleInFlightTimeoutMs(100L);
        assertTrue(mb.offer(env("m1")));
        MailboxEntry first = mb.poll();
        assertNotNull(first);
        assertEquals(1, mb.inFlightCount());
        assertTrue(first.getPolledAt() > 0);

        // Simulated clock past the stale threshold.
        int replayed = mb.sweepStaleInFlightForTest(first.getPolledAt() + 101);
        assertEquals(1, replayed, "the stale in-flight entry must be replayed");
        assertEquals(0, mb.inFlightCount());
        assertEquals(1, mb.pendingCount(), "the replayed entry must be pending again");

        // Re-pollable: fresh deliveryId, incremented deliveryCount.
        MailboxEntry second = mb.poll();
        assertNotNull(second);
        assertEquals(first.getDeliveryCount() + 1, second.getDeliveryCount());
        assertTrue(second.getDeliveryId() != first.getDeliveryId(),
                "a replayed entry receives a NEW deliveryId");
        assertEquals("m1", second.getEnvelope().getPayload());
        assertTrue(mb.ack(second.getDeliveryId()), "the replayed entry is ackable");
        assertEquals(0, mb.inFlightCount());
    }

    @Test
    void freshInFlightNotReplayed() {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        mb.setStaleInFlightTimeoutMs(60_000L);
        assertTrue(mb.offer(env("m1")));
        MailboxEntry e = mb.poll();
        int replayed = mb.sweepStaleInFlightForTest(e.getPolledAt() + 1);
        assertEquals(0, replayed, "a fresh in-flight entry must NOT be replayed");
        assertEquals(1, mb.inFlightCount());
    }

    @Test
    void stalePastMaxAttemptsDeadLettered() {
        DeferredAckMailbox mb = new DeferredAckMailbox(0, 1, 2);
        mb.setStaleInFlightTimeoutMs(100L);
        assertTrue(mb.offer(env("m1")));
        MailboxEntry e = mb.poll();
        int replayed = mb.sweepStaleInFlightForTest(e.getPolledAt() + 101);
        assertEquals(1, replayed);
        assertEquals(0, mb.pendingCount(),
                "an entry past maxDeliveryAttempts must NOT be re-queued");
        assertEquals(1, mb.deadLetterCount(), "it must be dead-lettered instead");
        assertEquals(0, mb.inFlightCount());
    }

    /**
     * The lazy sweep is also driven by the poll() critical section on real
     * time (no scheduler thread needed).
     */
    @Test
    void lazySweepDrivenByPoll() throws InterruptedException {
        DeferredAckMailbox mb = new DeferredAckMailbox();
        mb.setStaleInFlightTimeoutMs(50L);
        assertTrue(mb.offer(env("m1")));
        MailboxEntry first = mb.poll();
        Thread.sleep(80); // let the entry go stale on real time
        MailboxEntry second = mb.poll(); // sweep runs inside poll
        assertNotNull(second, "the stale entry must have been replayed and re-pollable");
        assertEquals(first.getDeliveryCount() + 1, second.getDeliveryCount());
        // The replayed entry was just polled → it is in flight again.
        assertEquals(1, mb.inFlightCount());
        assertEquals(0, mb.pendingCount());
    }

    // ---- bounded-default factory --------------------------------------------

    @Test
    void boundedDefaultFactoryProducesBoundedMailboxes() {
        var factory = DeferredAckMailbox.boundedDefaultFactory();
        IMailbox mailbox = factory.apply("sess-1");
        assertTrue(mailbox instanceof DeferredAckMailbox);
        DeferredAckMailbox bounded = (DeferredAckMailbox) mailbox;
        assertEquals(DeferredAckMailbox.DEFAULT_DEAD_LETTER_CAPACITY, bounded.getDeadLetterCapacity(),
                "the bounded-default factory must produce mailboxes with bounded retention");
    }
}
