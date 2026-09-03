/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.execution.InputChannel;
import io.nop.stream.core.execution.ResultPartition;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TOPIC;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TIMEOUT_MS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHANNEL_OVERFLOW;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHANNEL_TIMEOUT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

/**
 * Consumer-side channel that receives data from a {@link RemoteResultPartition}
 * via {@link IMessageService}.
 *
 * <p>Subscribes to a message service topic and decodes incoming
 * {@link StreamMessageEnvelope} instances back into {@link StreamElement}.
 * Decoded elements are placed into a local {@link LinkedBlockingQueue} for
 * consumption by the task thread.
 *
 * <p><strong>Fencing:</strong> Only envelopes whose monotonic fencing epoch
 * ({@code epochId}) matches the expected value are accepted. Stale messages are
 * discarded with a debug log (explicit, observable — not silently swallowed).
 * Stage 39 collapsed the legacy dual-key filter (String fencingToken equality +
 * long epochId equality) into a single long epoch comparison; the single key
 * encodes both leadership switch and same-leader recovery (see
 * {@code JobCoordinator.deriveHaFencingEpoch}), so both fencing invariants hold.
 *
 * <p><strong>Buffer pool exclusion (intentional, G53)</strong>: this class constructs
 * a dummy {@code super(new ResultPartition(1))} and uses its own local
 * {@link LinkedBlockingQueue}. It does not consume the per-job {@code IBufferPool}.
 * Cross-JVM producer-side bound is the responsibility of the {@code IMessageService}
 * backend (Stage 40). This exclusion is by design and documented in
 * {@code 01-architecture-baseline.md} §六, not an accidental omission.
 *
 * <p><strong>Lifecycle:</strong>
 * <ol>
 *   <li>Constructor subscribes to the topic (or, under items 28+31 D1
 *       subscription-scope convergence, constructs without subscribing —
 *       reading such a channel fails fast as a wiring error)</li>
 *   <li>{@link #read()} / {@link #read(long, TimeUnit)} consume from the local queue</li>
 *   <li>When an END_OF_STREAM control message is received, the channel is marked as finished</li>
 *   <li>Items 28+31 (D2): a full queue with zero consumer progress for the
 *       bounded enqueue window fails the channel typed
 *       (ERR_STREAM_CHANNEL_OVERFLOW) — the dispatch thread is never blocked
 *       indefinitely and the failure is observable/recoverable</li>
 *   <li>{@link #close()} cancels the subscription</li>
 * </ol>
 */
public class RemoteInputChannel extends InputChannel {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteInputChannel.class);

    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    /**
     * Items 28+31 (D2): default bounded enqueue wait. A full queue with ZERO
     * consumer progress for this whole window is a stalled downstream — the
     * channel fails typed (ERR_STREAM_CHANNEL_OVERFLOW) instead of blocking the
     * message-backend dispatch thread forever. Healthy slow consumers never hit
     * it: any slot freed within the window lets the offer succeed (bounded wait
     * degenerates to per-record short waits = dispatch-level backpressure).
     * 10s keeps a safety margin against the 60s per-task liveness timeout and
     * the 30s lease expiry threshold (lease renewal itself runs on a dedicated
     * thread that does not traverse the message backend).
     */
    static final long DEFAULT_ENQUEUE_OFFER_TIMEOUT_MS = 10_000L;

    /** Sentinel placed into the queue to signal end-of-stream. */
    private static final StreamElement END_OF_STREAM = new StreamElement() {};

    private final LinkedBlockingQueue<StreamElement> queue;
    private final String topic;
    private final long expectedEpochId;
    private final IMessageSubscription subscription;
    private volatile boolean finished;
    private volatile Throwable decodeError;

    /**
     * Items 28+31 (D2): bounded enqueue wait in ms (see
     * {@link #DEFAULT_ENQUEUE_OFFER_TIMEOUT_MS}). A full queue with zero
     * consumer progress for this whole window trips the typed overflow
     * failure instead of blocking the dispatch thread indefinitely.
     */
    private final long enqueueOfferTimeoutMs;

    /**
     * Items 28+31 (D2): typed overflow failure set when a bounded enqueue wait
     * expired with zero consumer progress. Same observability pattern as
     * {@link #decodeError}: the reader thread surfaces it as a typed
     * StreamException (it must NEVER be surfaced as a normal end-of-stream —
     * that would silently acknowledge truncated data).
     */
    private volatile Throwable overflowError;

    /**
     * Items 28+31 (D1): whether this channel's subscription was activated at
     * construction. Subscription-scope convergence (per-subtask / zero-scope
     * builds) constructs channels WITHOUT subscribing; they exist only to
     * mirror the full plan structure. Reading such a channel is a wiring error
     * and fails fast with a typed error instead of blocking forever.
     */
    private final boolean subscriptionActive;

    /**
     * Stage 43: channel heartbeat timeout in ms. When {@code > 0}, {@link #read()}
     * / {@link #read(long, TimeUnit)} check that <em>some</em> message (data,
     * barrier, watermark, or heartbeat) has arrived within this window; otherwise
     * the producer is presumed dead / partitioned and the channel fails fast with
     * {@link ERR_STREAM_CHANNEL_TIMEOUT}. {@code <= 0} disables timeout detection
     * (back-compat default). This is faster than the coarse lease timeout
     * (~15-20s).
     */
    private final long channelTimeoutMs;

    /**
     * Stage 43: monotonic timestamp of the last <em>accepted</em> message of any
     * kind. Updated by {@link EnvelopeConsumer} only AFTER the fencing-epoch
     * filter passes, so a wrong-epoch message never refreshes liveness. Volatile:
     * writer is the message-service dispatch thread, reader is the task thread.
     */
    private volatile long lastReceivedTime;

    /**
     * Creates a RemoteInputChannel that subscribes to the given topic.
     *
     * @param messageService  the message service to subscribe to
     * @param topic           the topic to subscribe to
     * @param expectedEpochId expected monotonic fencing epoch for message filtering
     *                        (Stage 39: the single long fencing key)
     */
    public RemoteInputChannel(IMessageService messageService,
                              String topic,
                              long expectedEpochId) {
        this(messageService, topic, expectedEpochId, DEFAULT_QUEUE_CAPACITY, 0L);
    }

    /**
     * Creates a RemoteInputChannel with a custom queue capacity.
     *
     * @param messageService  the message service to subscribe to
     * @param topic           the topic to subscribe to
     * @param expectedEpochId expected monotonic fencing epoch for message filtering
     * @param queueCapacity   capacity of the local element queue
     */
    public RemoteInputChannel(IMessageService messageService,
                              String topic,
                              long expectedEpochId,
                              int queueCapacity) {
        this(messageService, topic, expectedEpochId, queueCapacity, 0L);
    }

    /**
     * Stage 43: creates a RemoteInputChannel with heartbeat timeout detection.
     *
     * <p>When {@code channelTimeoutMs > 0}, {@link #read()} / {@link #read(long,
     * TimeUnit)} fail fast with {@link ERR_STREAM_CHANNEL_TIMEOUT} if no message
     * (data, barrier, watermark, or heartbeat) is accepted within the window.
     * The liveness clock starts at subscription time, so a producer that never
     * sends anything (including no heartbeat) is detected within one window.
     *
     * @param messageService   the message service to subscribe to
     * @param topic            the topic to subscribe to
     * @param expectedEpochId  expected monotonic fencing epoch for message filtering
     * @param queueCapacity    capacity of the local element queue
     * @param channelTimeoutMs channel heartbeat timeout in ms; {@code <= 0} disables
     */
    public RemoteInputChannel(IMessageService messageService,
                              String topic,
                              long expectedEpochId,
                              int queueCapacity,
                              long channelTimeoutMs) {
        this(messageService, topic, expectedEpochId, queueCapacity, channelTimeoutMs,
                DEFAULT_ENQUEUE_OFFER_TIMEOUT_MS, true);
    }

    /**
     * Items 28+31 (D1/D2): full-form constructor with subscription activation
     * control and bounded enqueue wait.
     *
     * @param messageService         the message service to subscribe to (when
     *                               {@code subscribe} is {@code true})
     * @param topic                  the topic this channel maps to
     * @param expectedEpochId        expected monotonic fencing epoch for message filtering
     * @param queueCapacity          capacity of the local element queue
     * @param channelTimeoutMs       channel heartbeat timeout in ms; {@code <= 0} disables
     * @param enqueueOfferTimeoutMs  bounded enqueue wait in ms; a full queue with zero
     *                               consumer progress for this window trips the typed
     *                               overflow failure (D2)
     * @param subscribe              {@code true} = subscribe at construction (existing
     *                               full-subscription semantics); {@code false} = construct
     *                               WITHOUT subscribing (D1 subscription-scope convergence:
     *                               the channel only mirrors the plan structure; reading it
     *                               fails fast as a wiring error)
     */
    public RemoteInputChannel(IMessageService messageService,
                              String topic,
                              long expectedEpochId,
                              int queueCapacity,
                              long channelTimeoutMs,
                              long enqueueOfferTimeoutMs,
                              boolean subscribe) {
        // Pass a dummy partition to the parent; we override all read methods
        super(new ResultPartition(1));
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.topic = topic;
        this.expectedEpochId = expectedEpochId;
        this.finished = false;
        this.channelTimeoutMs = channelTimeoutMs;
        this.enqueueOfferTimeoutMs = enqueueOfferTimeoutMs;
        this.lastReceivedTime = System.currentTimeMillis();
        this.subscriptionActive = subscribe;

        if (subscribe) {
            this.subscription = messageService.subscribe(topic, new EnvelopeConsumer());
        } else {
            // D1: constructed-but-not-subscribed channel. Producers keep their
            // fan-out capability (the send side never subscribes); this channel
            // simply never joins the delivery surface.
            this.subscription = null;
            LOG.info("RemoteInputChannel constructed WITHOUT subscription (scope-converged) "
                    + "for topic={}, epochId={}", topic, expectedEpochId);
            return;
        }
        LOG.info("RemoteInputChannel subscribed to topic={}, epochId={}, channelTimeoutMs={}, "
                + "enqueueOfferTimeoutMs={}", topic, expectedEpochId, channelTimeoutMs, enqueueOfferTimeoutMs);
    }

    /**
     * Items 28+31 (D1): whether this channel is an active subscriber. Test and
     * diagnostic hook for subscription-scope assertions.
     */
    public boolean isSubscriptionActive() {
        return subscriptionActive;
    }

    /**
     * Reads the next element from the local queue (blocking).
     *
     * @return the next element, or null if end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public StreamElement read() throws InterruptedException {
        checkReadable();
        checkChannelError();
        // Stage 43: heartbeat-based timeout detection (piggybacks on the read
        // path — no dedicated timer thread per channel).
        checkChannelTimeout();
        StreamElement element = queue.take();
        // Items 28+31 (D2): an overflow/decode failure may have been flagged
        // while this reader was blocked in take(); the EOS sentinel placed by
        // the failure path wakes us up — surface the typed error, never a
        // clean end-of-stream.
        checkChannelError();
        if (element == END_OF_STREAM) {
            return null;
        }
        return element;
    }

    /**
     * Reads the next element with a timeout.
     *
     * @param timeout maximum wait time
     * @param unit    time unit
     * @return the next element, or null on timeout / end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    @Override
    public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
        checkReadable();
        checkChannelError();
        // Stage 43: heartbeat-based timeout detection (piggybacks on the read path).
        checkChannelTimeout();
        StreamElement element = queue.poll(timeout, unit);
        // Items 28+31 (D2): see read() — a flagged failure must surface as the
        // typed error even when the wake-up element is the EOS sentinel.
        checkChannelError();
        if (element == null) {
            return null;
        }
        if (element == END_OF_STREAM) {
            return null;
        }
        return element;
    }

    /**
     * Returns whether the upstream producer has finished sending.
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * Stage 43: has the channel exceeded its heartbeat timeout? {@code true} when
     * detection is enabled ({@code channelTimeoutMs > 0}), the channel is not
     * finished, no decode error has occurred, and no accepted message has arrived
     * within {@code channelTimeoutMs}. An explicit {@link
     * StreamMessageEnvelope#CONTROL_END_OF_STREAM} sets {@code finished=true} and
     * therefore suppresses timeout (EOS is a normal, distinguishable completion).
     */
    public boolean isChannelTimedOut() {
        if (channelTimeoutMs <= 0 || finished || decodeError != null) {
            return false;
        }
        return (System.currentTimeMillis() - lastReceivedTime) > channelTimeoutMs;
    }

    /**
     * Stage 43: throws {@link ERR_STREAM_CHANNEL_TIMEOUT} if {@link
     * #isChannelTimedOut()} holds. Invoked at the top of every {@link #read()}
     * / {@link #read(long, TimeUnit)} so the task thread fails fast instead of
     * blocking on an empty queue forever.
     */
    private void checkChannelTimeout() {
        if (isChannelTimedOut()) {
            throw new StreamException(ERR_STREAM_CHANNEL_TIMEOUT)
                    .param(ARG_TIMEOUT_MS, channelTimeoutMs);
        }
    }

    private void checkDecodeError() {
        if (decodeError != null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR, decodeError)
                    .param(ARG_DETAIL, "Decode error in RemoteInputChannel");
        }
    }

    /**
     * Items 28+31: surfaces the first flagged channel failure (decode or D2
     * overflow) as a typed StreamException. Overflow carries its own error
     * code so recovery/ops can distinguish "downstream stalled" from "decode
     * corruption".
     */
    private void checkChannelError() {
        if (overflowError != null) {
            throw new StreamException(ERR_STREAM_CHANNEL_OVERFLOW, overflowError)
                    .param(ARG_TIMEOUT_MS, enqueueOfferTimeoutMs)
                    .param(ARG_TOPIC, topic);
        }
        checkDecodeError();
    }

    /**
     * Items 28+31 (D1): reading a channel that was constructed without a
     * subscription is a wiring error (scope-converged channels exist only to
     * mirror the plan structure). Fail fast with a typed error instead of
     * blocking on an empty queue forever (guide #24 — no silent no-op).
     */
    private void checkReadable() {
        if (!subscriptionActive) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "RemoteInputChannel is not subscribed (scope-converged, "
                            + "construct-only) — reading it is a wiring error");
        }
    }

    /**
     * Stage 43: returns the configured channel heartbeat timeout in ms
     * ({@code <= 0} means disabled).
     */
    public long getChannelTimeoutMs() {
        return channelTimeoutMs;
    }

    /**
     * Stage 43: returns the monotonic timestamp of the last accepted message.
     * Test/diagnostic hook.
     */
    public long getLastReceivedTime() {
        return lastReceivedTime;
    }

    /**
     * Stage 43 (unaligned checkpoint): drains this channel's local
     * {@link LinkedBlockingQueue} (excluding the end-of-stream sentinel) and
     * returns the in-flight records. Override of {@link InputChannel#captureInFlightData}
     * because {@code RemoteInputChannel} does not consume its dummy
     * {@link ResultPartition}'s queue.
     *
     * @param barrierReceived whether this channel has delivered its barrier
     * @return the drained in-flight elements (possibly empty); never null
     */
    @Override
    public java.util.List<StreamElement> captureInFlightData(boolean barrierReceived) {
        java.util.List<StreamElement> drained = new java.util.ArrayList<>();
        StreamElement e;
        while ((e = queue.poll()) != null) {
            if (e == END_OF_STREAM) {
                // Re-place the sentinel so a subsequent read still observes EOS.
                queue.offer(END_OF_STREAM);
                break;
            }
            drained.add(e);
        }
        return drained;
    }

    /**
     * Stage 43 (unaligned checkpoint recovery): injects previously captured
     * in-flight records at the front of the local queue so they are processed
     * before any newly delivered upstream records. Override of
     * {@link InputChannel#injectElements} for the remote channel's local queue.
     */
    @Override
    public void injectElements(java.util.List<StreamElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        // Drain current contents (preserve EOS sentinel if present).
        java.util.List<StreamElement> existing = new java.util.ArrayList<>();
        boolean sawEos = false;
        StreamElement e;
        while ((e = queue.poll()) != null) {
            if (e == END_OF_STREAM) {
                sawEos = true;
                break;
            }
            existing.add(e);
        }
        // Replayed in-flight records first, then existing content, then EOS.
        for (StreamElement injected : elements) {
            queue.offer(injected);
        }
        for (StreamElement old : existing) {
            queue.offer(old);
        }
        if (sawEos) {
            queue.offer(END_OF_STREAM);
        }
    }

    /**
     * Cancels the message subscription and releases resources.
     */
    public void close() {
        if (subscription != null && !subscription.isCancelled()) {
            subscription.cancel();
        }
        // Ensure readers can unblock
        if (!finished) {
            finished = true;
            queue.offer(END_OF_STREAM);
        }
    }

    /**
     * Returns the current number of elements waiting in the local queue.
     */
    public int queueSize() {
        return queue.size();
    }

    /**
     * Items 28+31 (D2): whether the bounded enqueue wait expired with zero
     * consumer progress (typed overflow failure flagged). Observable
     * diagnostic hook — the failure is never silent.
     */
    public boolean isOverflowed() {
        return overflowError != null;
    }

    /**
     * Items 28+31 (D2): the configured bounded enqueue wait in ms.
     */
    public long getEnqueueOfferTimeoutMs() {
        return enqueueOfferTimeoutMs;
    }

    /**
     * IMessageConsumer that decodes envelopes and puts elements into the local queue.
     */
    private class EnvelopeConsumer implements IMessageConsumer {

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            if (finished) {
                return null;
            }

            if (!(message instanceof StreamMessageEnvelope)) {
                LOG.warn("Ignoring non-envelope message on topic={}: {}", topic, message);
                return null;
            }

            StreamMessageEnvelope envelope = (StreamMessageEnvelope) message;

            // Stage 39: single monotonic long epoch fencing comparison. The legacy
            // dual-key filter (String fencingToken equality + long epochId equality)
            // is collapsed into one long key. The single epoch encodes both
            // leadership switch and same-leader recovery, so both invariants hold.
            if (envelope.getEpochId() != expectedEpochId) {
                LOG.debug("Discarding stale message: expected epochId={}, got={}",
                        expectedEpochId, envelope.getEpochId());
                return null;
            }

            // Stage 43: any message that passed the fencing filter counts as
            // producer liveness. Refresh BEFORE branching on type so heartbeat,
            // data, barrier, watermark all reset the timeout window. A wrong-epoch
            // message (handled above) never reaches here, so stale heartbeats do
            // NOT count as liveness (fencing invariant).
            lastReceivedTime = System.currentTimeMillis();

            // Handle control messages
            if (StreamMessageEnvelope.TYPE_CONTROL.equals(envelope.getType())) {
                Object payload = envelope.getPayload();
                if (StreamMessageEnvelope.CONTROL_END_OF_STREAM.equals(payload)) {
                    finished = true;
                    queue.offer(END_OF_STREAM);
                    return null;
                }
                if (StreamMessageEnvelope.CONTROL_HEARTBEAT.equals(payload)) {
                    // Pure liveness signal — already refreshed lastReceivedTime
                    // above. Do not enqueue anything; the read loop must not see
                    // a control element as data.
                    LOG.debug("Received heartbeat on topic={}", topic);
                    return null;
                }
                // Unknown control payload: explicit, observable — not silently swallowed.
                LOG.warn("Ignoring unknown control payload on topic={}: {}", topic, payload);
                return null;
            }

            // Decode the element
            try {
                StreamElement element = StreamElementCodec.decode(envelope);
                // re-check finished flag after decode to avoid race with close()
                if (!finished) {
                    // Items 28+31 (D2): bounded enqueue wait. The legacy
                    // queue.put(element) blocked FOREVER once the queue was
                    // full — under a shared dispatch loop (e.g. the polling
                    // JDBC backend's 2-thread pool serving data AND control
                    // topics) two dead channels stalled every delivery on the
                    // TaskManager. A full queue with zero consumer progress
                    // for the whole window is a stalled downstream: fail the
                    // channel typed (observable, recoverable) instead. Healthy
                    // slow consumers never trip this — any freed slot within
                    // the window lets the offer succeed (dispatch-level
                    // backpressure is preserved).
                    if (!queue.offer(element, enqueueOfferTimeoutMs, TimeUnit.MILLISECONDS)) {
                        overflowError = new IllegalStateException(
                                "Enqueue offer timed out after " + enqueueOfferTimeoutMs
                                        + "ms with a full queue (no consumer progress) on topic=" + topic);
                        finished = true;
                        queue.offer(END_OF_STREAM);
                        LOG.error("RemoteInputChannel enqueue overflow on topic={}: queue full with no "
                                + "consumer progress for {}ms — failing channel typed (downstream stalled); "
                                + "recovery re-subscription replays from the backend", topic,
                                enqueueOfferTimeoutMs);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                decodeError = e;
                finished = true;
                queue.offer(END_OF_STREAM);
                LOG.warn("Interrupted while enqueueing decoded element", e);
            } catch (Exception e) {
                decodeError = e;
                finished = true;
                queue.offer(END_OF_STREAM);
                LOG.error("Failed to decode envelope on topic={}", topic, e);
            }

            return null;
        }
    }
}
