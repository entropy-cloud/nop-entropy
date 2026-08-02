package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stage 43, Phase 1: channel heartbeat protocol — producer-sends-idle-heartbeat
 * model + consumer heartbeat-timeout failure detection.
 *
 * <p>Wires {@link RemoteResultPartition} (producer) and {@link RemoteInputChannel}
 * (consumer) together on the same {@link LocalMessageService} topic (plan guide
 * #23 接线验证: both sides exercised in the same test, not standalone unit tests).
 */
class TestRemoteInputChannelHeartbeat {

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

    private RemoteResultPartition producer(String topic, long heartbeatIntervalMs) {
        TypeRegistry types = new TypeRegistry();
        types.register("edge-1", String.class.getName());
        return new RemoteResultPartition(
                messageService, topic, types, "edge-1", EPOCH, heartbeatIntervalMs);
    }

    /**
     * Producer emits an idle-heartbeat envelope when no data has flowed for the
     * configured interval. Heartbeat is a TYPE_CONTROL / CONTROL_HEARTBEAT payload
     * carrying the fencing epoch.
     */
    @Test
    void testProducerSendsIdleHeartbeatWhenIdle() throws Exception {
        String topic = "job.hb.prod";
        long interval = 60L;
        RemoteResultPartition partition = producer(topic, interval);

        // Capture only heartbeat envelopes sent on the topic
        List<StreamMessageEnvelope> heartbeats = new ArrayList<>();
        messageService.subscribe(topic, (t, msg, ctx) -> {
            if (msg instanceof StreamMessageEnvelope) {
                StreamMessageEnvelope env = (StreamMessageEnvelope) msg;
                if (StreamMessageEnvelope.TYPE_CONTROL.equals(env.getType())
                        && StreamMessageEnvelope.CONTROL_HEARTBEAT.equals(env.getPayload())) {
                    heartbeats.add(env);
                }
            }
            return null;
        });

        // Just wrote data — not idle yet, heartbeat must NOT fire (return false).
        partition.write(new StreamRecord<>("data-1"));
        boolean immediateHb = partition.sendHeartbeatIfIdle();
        assertFalse(immediateHb, "No heartbeat expected immediately after a data write");
        assertTrue(heartbeats.isEmpty(), "No heartbeat should have been sent yet");

        // Wait beyond the idle threshold, then trigger the heartbeat check.
        Thread.sleep(interval + 30L);
        boolean sentHb = partition.sendHeartbeatIfIdle();

        assertTrue(sentHb, "Heartbeat should be sent once idle beyond interval");
        assertEquals(1, heartbeats.size());
        StreamMessageEnvelope hb = heartbeats.get(0);
        assertEquals(StreamMessageEnvelope.TYPE_CONTROL, hb.getType());
        assertEquals(StreamMessageEnvelope.CONTROL_HEARTBEAT, hb.getPayload());
        assertEquals(EPOCH, hb.getEpochId(), "Heartbeat must carry the fencing epoch");

        partition.close();
    }

    /**
     * Heartbeat emission is disabled when heartbeatIntervalMs <= 0 (back-compat).
     */
    @Test
    void testHeartbeatDisabledByDefault() throws Exception {
        String topic = "job.hb.disabled";
        RemoteResultPartition partition = producer(topic, 0L);
        Thread.sleep(5L);
        assertFalse(partition.sendHeartbeatIfIdle(), "Disabled heartbeat must never send");
        partition.close();
    }

    /**
     * Consumer detects producer failure when neither data, heartbeat, nor EOS
     * arrives within channelTimeout. read() throws ERR_STREAM_CHANNEL_TIMEOUT.
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
        Thread.sleep(channelTimeout + 80L);

        StreamException thrown = assertThrows(StreamException.class,
                () -> consumer.read(50, TimeUnit.MILLISECONDS));
        assertEquals("nop.err.stream.channel-timeout", thrown.getErrorCode().toString(),
                "Expected channel timeout error: " + thrown.getMessage());
    }

    /**
     * Normal end-of-stream is NOT mistaken for heartbeat timeout. EOS sets
     * finished=true and read() returns null.
     */
    @Test
    void testEndOfStreamIsNotHeartbeatTimeout() throws Exception {
        String topic = "job.hb.eos";
        long channelTimeout = 1000L; // long window
        RemoteResultPartition partition = producer(topic, 0L);
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
                "Finished channel must never report heartbeat timeout");
    }

    /**
     * Fencing invariant: a heartbeat carrying the wrong epoch is discarded and
     * does NOT count as liveness — the consumer still times out.
     */
    @Test
    void testWrongEpochHeartbeatDoesNotRefreshLiveness() throws Exception {
        String topic = "job.hb.fence";
        long channelTimeout = 150L;
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // A stale producer with the wrong epoch sends heartbeats.
        TypeRegistry types = new TypeRegistry();
        types.register("edge-1", String.class.getName());
        long wrongEpoch = EPOCH + 99L;
        RemoteResultPartition staleProducer = new RemoteResultPartition(
                messageService, topic, types, "edge-1", wrongEpoch, 10L);

        // Repeatedly push heartbeats from the stale producer.
        for (int i = 0; i < 5; i++) {
            staleProducer.sendHeartbeatIfIdle();
            Thread.sleep(30L);
        }

        // No correct-epoch message ever arrived → consumer must still time out.
        StreamException thrown = assertThrows(StreamException.class,
                () -> consumer.read(50, TimeUnit.MILLISECONDS));
        assertEquals("nop.err.stream.channel-timeout", thrown.getErrorCode().toString(),
                "Wrong-epoch heartbeats must not refresh liveness: " + thrown.getMessage());
    }

    /**
     * 接线验证 (plan guide #23): producer + consumer wired together — a real
     * heartbeat from the correct producer refreshes consumer liveness, preventing
     * the timeout that would otherwise fire. This proves the heartbeat path is
     * connected end-to-end through IMessageService.
     */
    @Test
    void testHeartbeatRefreshesConsumerLivenessWired() throws Exception {
        String topic = "job.hb.wired";
        long interval = 40L;
        long channelTimeout = 200L;
        RemoteResultPartition partition = producer(topic, interval);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);

        // Producer is idle (writes nothing) but emits heartbeats. The consumer
        // should NOT time out as long as heartbeats keep arriving.
        long deadline = System.currentTimeMillis() + (channelTimeout * 3);
        boolean timedOut = false;
        while (System.currentTimeMillis() < deadline) {
            partition.sendHeartbeatIfIdle();
            // read with short poll; heartbeat keeps liveness fresh so this returns
            // null (empty) rather than throwing.
            try {
                consumer.read(20, TimeUnit.MILLISECONDS);
            } catch (StreamException e) {
                if ("nop.err.stream.channel-timeout".equals(e.getErrorCode().toString())) {
                    timedOut = true;
                    break;
                }
                throw e;
            }
            Thread.sleep(15L);
        }

        assertFalse(timedOut, "Consumer must not time out while correct-epoch heartbeats flow");
        // Consumer liveness was refreshed by heartbeats (not data).
        assertTrue(consumer.getLastReceivedTime() > 0);
        partition.close();
    }

    /**
     * Data flow refreshes liveness too — a producer that keeps sending records
     * keeps the consumer alive without needing heartbeats.
     */
    @Test
    void testDataFlowKeepsConsumerAlive() throws Exception {
        String topic = "job.hb.data";
        long channelTimeout = 200L;
        RemoteResultPartition partition = producer(topic, 0L); // heartbeat disabled
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
