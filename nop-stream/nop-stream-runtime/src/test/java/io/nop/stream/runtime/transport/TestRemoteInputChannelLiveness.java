package io.nop.stream.runtime.transport;

import io.nop.stream.runtime.testsupport.TestAwait;
import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer-side channel liveness (inactivity) timeout detection on
 * {@link RemoteInputChannel}: liveness is carried by accepted
 * data/barrier/watermark/control traffic alone — there is no dedicated
 * producer idle-heartbeat protocol message.
 *
 * <p>Wires {@link RemoteResultPartition} (producer) and {@link RemoteInputChannel}
 * (consumer) together on the same {@link LocalMessageService} topic (plan guide
 * #23 接线验证: both sides exercised in the same test, not standalone unit tests).
 */
class TestRemoteInputChannelLiveness {

    private static final long EPOCH = 7L;

    private LocalMessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new LocalMessageService();
    }

    @AfterEach
    void tearDown() {
        messageService.clearConsumers();
    }

    private RemoteResultPartition producer(String topic) {
        TypeRegistry types = new TypeRegistry();
        types.register("edge-1", String.class.getName());
        return new RemoteResultPartition(messageService, topic, types, "edge-1", EPOCH);
    }

    /**
     * Consumer detects producer failure when neither data nor EOS arrives within
     * channelTimeout. read() throws ERR_STREAM_CHANNEL_TIMEOUT.
     */
    @Test
    void testConsumerTimesOutWhenSilent() throws Exception {
        String topic = "job.hb.silent";
        long channelTimeout = 120L;
        // Consumer subscribes; producer exists but never sends anything.
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Within the window the consumer simply polls empty (no timeout yet).
        long birth = consumer.getLastReceivedTime();
        assertTrue(birth > 0);

        // Wait beyond the window without any traffic.
        // 时间语义（channel timeout 窗口需流逝），循环形式等待
        TestAwait.elapsed("channel timeout window elapses", channelTimeout + 80L);

        StreamException thrown = assertThrows(StreamException.class,
                () -> consumer.read(50, TimeUnit.MILLISECONDS));
        assertEquals("nop.err.stream.channel-timeout", thrown.getErrorCode().toString(),
                "Expected channel timeout error: " + thrown.getMessage());
    }

    /**
     * Normal end-of-stream is NOT mistaken for a timeout. EOS sets
     * finished=true and read() returns null.
     */
    @Test
    void testEndOfStreamIsNotTimeout() throws Exception {
        String topic = "job.hb.eos";
        long channelTimeout = 1000L; // long window
        RemoteResultPartition partition = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Producer finishes (sends END_OF_STREAM).
        partition.close();

        // Drain until EOS returns null — must NOT throw channel timeout even if
        // we wait past channelTimeout (finished suppresses timeout).
        StreamElement el = consumer.read(500, TimeUnit.MILLISECONDS);
        assertNull(el, "EOS should return null, not throw");
        assertTrue(consumer.isFinished());
        assertFalse(consumer.isChannelTimedOut(),
                "Finished channel must never report timeout");
    }

    /**
     * Fencing invariant: a message carrying the wrong epoch is discarded and
     * does NOT count as liveness — the consumer still times out.
     */
    @Test
    void testWrongEpochMessagesDoNotRefreshLiveness() throws Exception {
        String topic = "job.hb.fence";
        long channelTimeout = 150L;
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // A stale producer with the wrong epoch keeps sending data records.
        TypeRegistry types = new TypeRegistry();
        types.register("edge-1", String.class.getName());
        long wrongEpoch = EPOCH + 99L;
        RemoteResultPartition staleProducer = new RemoteResultPartition(
                messageService, topic, types, "edge-1", wrongEpoch);

        // Repeatedly push wrong-epoch records.
        for (int i = 0; i < 5; i++) {
            staleProducer.write(new StreamRecord<>("stale-" + i));
            Thread.sleep(30L);
        }

        // No correct-epoch message ever arrived → consumer must still time out.
        StreamException thrown = assertThrows(StreamException.class,
                () -> consumer.read(50, TimeUnit.MILLISECONDS));
        assertEquals("nop.err.stream.channel-timeout", thrown.getErrorCode().toString(),
                "Wrong-epoch messages must not refresh liveness: " + thrown.getMessage());
    }

    /**
     * Data flow refreshes liveness — a producer that keeps sending records
     * keeps the consumer alive.
     */
    @Test
    void testDataFlowKeepsConsumerAlive() throws Exception {
        String topic = "job.hb.data";
        long channelTimeout = 200L;
        RemoteResultPartition partition = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Send a record and read it — liveness refreshed by the record.
        partition.write(new StreamRecord<>("d"));
        StreamElement el = consumer.read(500, TimeUnit.MILLISECONDS);
        assertNotNull(el, "Should read the data record");
        assertTrue(el.isRecord());
        assertFalse(consumer.isChannelTimedOut(),
                "A recently-accepted record must keep the channel alive");

        partition.close();
    }
}
