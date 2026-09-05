package io.nop.stream.runtime.transport;

import io.nop.api.core.message.IMessageService;
import io.nop.message.core.local.LocalMessageService;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.InputGate;
import io.nop.stream.core.execution.transport.TypeRegistry;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-1 (audit {@code nop-stream-independent-audit}, P1): proves the
 * {@link InputGate#read() single-channel remote-read} path surfaces producer
 * death via the Stage 43 channel heartbeat-timeout within a bounded window,
 * instead of hanging forever.
 *
 * <p>The legacy {@code InputGate.readSingleChannel()} called the unbounded
 * blocking {@code read()} overload, which parked in {@code queue.take()}.
 * Once a single-input consumer parked inside {@code take()}, a remote producer
 * that subsequently died (crash / partition / stopped emitting data and
 * heartbeats) left the consumer blocked indefinitely — the heartbeat-timeout
 * check at the top of {@code RemoteInputChannel.read()} could never re-fire
 * because the thread never returned to {@code checkChannelTimeout()}. This
 * silently disabled the documented fast-fail-on-producer-death safety feature
 * for the most common streaming topology (one upstream → one operator → one
 * sink) in the cross-JVM lane.
 *
 * <p>The fix rewrites {@code readSingleChannel} to loop on the bounded
 * {@code read(50, MILLISECONDS)} overload (mirroring the already-correct
 * {@code readMultiChannel} path), so {@code checkChannelTimeout()} re-fires
 * every ~50 ms even while the consumer is effectively parked. These tests lift
 * the existing {@code TestRemoteInputChannelHeartbeat} channel-level proof to
 * the {@link InputGate} layer — the gap the prior tests did not cover.
 *
 * <p>Plan guide coverage:
 * <ul>
 *   <li><b>#22 End-to-End / Anti-Hollow</b>: the liveness test exercises the
 *       full path {@code InputGate.readSingleChannel} →
 *       {@code RemoteInputChannel.read(long, TimeUnit)} → {@code queue.poll}
 *       → {@code checkChannelTimeout} re-firing each loop.</li>
 *   <li><b>#23 Wiring Verification</b>: the timeout fires during a <em>parked</em>
 *       read (loop re-enters {@code checkChannelTimeout} each round), not just
 *       once at the top — the core AR-1 fix.</li>
 *   <li><b>#25 Test-Mandated Feature</b>: tests assert the RESULT (timeout within
 *       a bounded window for a silent producer; elements returned for a live
 *       producer; prompt empty return on EOS), not merely absence of a hang.</li>
 * </ul>
 */
class TestInputGateSingleChannelRemoteLiveness {

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
        // heartbeatIntervalMs=0: the producer does not auto-emit heartbeats; the
        // liveness case relies on the producer going fully silent to simulate
        // death.
        return new RemoteResultPartition(messageService, topic, types, "edge-1", EPOCH, 0L);
    }

    /**
     * Liveness case (core AR-1 proof + revert guard + wiring #23): a
     * single-channel {@link InputGate} over a {@link RemoteInputChannel} fails
     * fast with {@code ERR_STREAM_CHANNEL_TIMEOUT} within a bounded window when
     * the producer goes silent, instead of hanging forever.
     *
     * <p>The consumer drains a seed element first so its parking read ENTERS the
     * poll loop while liveness is fresh. This is the revert guard: with the old
     * unbounded {@code read()} the consumer parks in {@code queue.take()} BEFORE
     * the timeout elapses, so the single top-level {@code checkChannelTimeout()}
     * does not fire and the read hangs (the bounded-window assertion then
     * fails). Only the bounded-poll loop re-fires {@code checkChannelTimeout()}
     * each ~50 ms and throws — proving the wiring (#23) is live.
     */
    @Test
    void testSingleChannelGateTimesOutOnProducerDeath() throws Exception {
        String topic = "job.ar1.liveness";
        long channelTimeout = 150L;
        RemoteResultPartition producer = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        InputGate gate = new InputGate(Collections.singletonList(consumer));

        // Send ONE seed element first so the consumer's parking read enters the
        // poll loop while liveness is fresh (lastReceivedTime just reset).
        producer.write(new StreamRecord<>("seed"));

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicBoolean drainedSeed = new AtomicBoolean(false);
        Thread reader = new Thread(() -> {
            try {
                // First read drains the seed element (liveness fresh at this
                // instant, so no top-level checkChannelTimeout fires).
                Optional<StreamElement> first = gate.read();
                if (first.isPresent()) {
                    drainedSeed.set(true);
                }
                // Second read parks in the bounded-poll loop; the producer is
                // now silent → checkChannelTimeout re-fires each ~50ms and
                // throws ERR_STREAM_CHANNEL_TIMEOUT once channelTimeoutMs
                // elapses. With the old unbounded read() this call would hang
                // in queue.take() (revert guard).
                gate.read();
            } catch (Throwable t) {
                error.set(t);
            }
        }, "ar1-liveness-reader");
        reader.setDaemon(true);
        reader.start();

        // Bounded window: 2 poll rounds (~100ms) + channelTimeout (150ms) +
        // generous CI slack. If the read hangs (reverted code), join returns
        // with the reader still alive → the assertion fails.
        long boundedWindowMs = 2L * 50L + channelTimeout + 2000L;
        reader.join(boundedWindowMs);
        assertFalse(reader.isAlive(),
                "single-channel read must surface producer death within bounded window ("
                        + boundedWindowMs + "ms), not hang");
        assertTrue(drainedSeed.get(),
                "reader should have drained the seed element before parking");
        Throwable t = error.get();
        assertNotNull(t, "parking read should have thrown channel-timeout, not return normally");
        assertTrue(t instanceof StreamException,
                "expected StreamException, got " + (t == null ? "null" : t.getClass()));
        assertEquals("nop.err.stream.channel-timeout",
                ((StreamException) t).getErrorCode().toString(),
                "Expected channel timeout: " + t.getMessage());

        producer.close();
        consumer.close();
    }

    /**
     * Negative control (live producer): a single-channel {@link InputGate} does
     * NOT time out and keeps returning elements while the producer keeps
     * emitting within the channel-timeout window — even across a total duration
     * well beyond a single {@code channelTimeoutMs}. This directly contrasts
     * with the liveness case and proves the loop does not spuriously fire.
     *
     * <p>Also covers the momentarily-idle-but-live case: between two emits the
     * consumer's read parks briefly via {@code poll(50ms)} (no busy-spin) yet
     * stays alive because liveness is refreshed before the window elapses.
     */
    @Test
    void testSingleChannelGateStaysAliveWhileProducerEmits() throws Exception {
        String topic = "job.ar1.live";
        long channelTimeout = 200L;
        RemoteResultPartition producer = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        InputGate gate = new InputGate(Collections.singletonList(consumer));

        // Drive producer + consumer in lockstep (LocalMessageService dispatch is
        // synchronous, so write() enqueues into the consumer before returning).
        // Total duration > channelTimeout proves the consumer would have timed
        // out if the producer were silent, but does not because liveness is
        // refreshed by each emit.
        long end = System.currentTimeMillis() + (channelTimeout * 2L);
        int received = 0;
        while (System.currentTimeMillis() < end) {
            producer.write(new StreamRecord<>("d-" + received));
            Optional<StreamElement> el = gate.read();
            assertTrue(el.isPresent(),
                    "live producer must keep the consumer alive (no timeout); iteration " + received);
            assertTrue(el.get().isRecord());
            received++;
            // Brief idle (< channelTimeout) between emits: the next read will
            // park via poll(50ms) but the producer is still live within the
            // window, so no timeout fires.
            Thread.sleep(channelTimeout / 4);
        }
        assertTrue(received > 1,
                "should have received multiple records over the window without timing out");
        assertFalse(consumer.isChannelTimedOut(),
                "consumer must stay alive while the producer keeps emitting");

        producer.close();
        consumer.close();
    }

    /**
     * EOS control (review B1 — {@code isFinished()} disambiguation): a producer
     * that sends {@code CONTROL_END_OF_STREAM} makes the single-channel read
     * return promptly (empty), with no busy-spin and no timeout. This directly
     * verifies the {@code null}-via-{@code isFinished()} branch: the bounded
     * overload returns {@code null} for BOTH poll-timeout and EOS, and only
     * {@code isFinished()} distinguishes them. Treating all null as "continue"
     * would busy-spin here; treating all null as "empty" would silently
     * terminate the liveness case's parking read on its first idle poll.
     */
    @Test
    void testSingleChannelGateReturnsEmptyOnEndOfStream() throws Exception {
        String topic = "job.ar1.eos";
        long channelTimeout = 1000L; // long window so timeout never fires
        RemoteResultPartition producer = producer(topic);
        RemoteInputChannel consumer = new RemoteInputChannel(
                messageService, topic, EPOCH, 16, channelTimeout);
        InputGate gate = new InputGate(Collections.singletonList(consumer));

        // Producer finishes (synchronously dispatches CONTROL_END_OF_STREAM).
        producer.close();

        long start = System.currentTimeMillis();
        Optional<StreamElement> el = gate.read();
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(el.isPresent(), "EOS should return empty promptly, not hang or busy-spin");
        assertTrue(consumer.isFinished(),
                "channel must be marked finished on END_OF_STREAM");
        assertFalse(consumer.isChannelTimedOut(),
                "finished channel must never report heartbeat timeout");
        // Prompt: well under one poll round (no busy-spin on EOS, which would
        // be near-instant but never return; no hang, which would be >> 50ms).
        assertTrue(elapsed < 500L,
                "EOS read should return promptly (elapsed=" + elapsed + "ms)");

        consumer.close();
    }
}
