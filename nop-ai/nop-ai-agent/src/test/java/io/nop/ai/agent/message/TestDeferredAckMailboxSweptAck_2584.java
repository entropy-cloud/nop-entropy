package io.nop.ai.agent.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2584 Phase 2 (audit Minor): a late ack/nack on a deliveryId that the
 * stale in-flight sweep already replayed is WARNed + counted — no longer a
 * silent {@code false}.
 */
public class TestDeferredAckMailboxSweptAck_2584 {

    @Test
    void lateAckOnSweptDeliveryIdIsCountedNotSilent() {
        DeferredAckMailbox mailbox = new DeferredAckMailbox(0, 2);
        mailbox.setStaleInFlightTimeoutMs(1);

        AgentMessageEnvelope envelope = new AgentMessageEnvelope(
                "sender", "target.topic", "corr-swept-1", AgentMessageKind.ASYNC, "m");
        assertTrue(mailbox.offer(envelope));

        MailboxEntry inFlight = mailbox.poll();
        assertNotNull(inFlight);
        long sweptId = inFlight.getDeliveryId();

        // sweep replays the stale in-flight entry with a NEW deliveryId
        assertEquals(1, mailbox.sweepStaleInFlightForTest(System.currentTimeMillis() + 10_000));

        // late ack on the swept id: observable, not silent
        assertFalse(mailbox.ack(sweptId));
        assertEquals(1, mailbox.getSweptAckNackCount());

        // late nack on the swept id: also counted
        assertFalse(mailbox.nack(sweptId, true));
        assertEquals(2, mailbox.getSweptAckNackCount());

        // an unknown id must NOT be counted as swept
        assertFalse(mailbox.ack(999_999L));
        assertEquals(2, mailbox.getSweptAckNackCount());

        // the replayed (new) deliveryId can still be acked normally
        MailboxEntry replayed = mailbox.poll();
        assertNotNull(replayed);
        assertTrue(replayed.getDeliveryId() != sweptId);
        assertTrue(mailbox.ack(replayed.getDeliveryId()));
        assertEquals(2, mailbox.getSweptAckNackCount());
    }
}
