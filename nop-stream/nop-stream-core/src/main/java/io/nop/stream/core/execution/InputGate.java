/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import io.nop.api.core.time.CoreMetrics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.ChannelState;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_REASON;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_TIMEOUT_MS;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CHECKPOINT_ABORTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * Manages multiple {@link InputChannel} instances and provides merged reading
 * with optional barrier alignment and watermark merging.
 *
 * <p><strong>Barrier Alignment (STRICT_EXACTLY_ONCE):</strong>
 * When a barrier is received on one channel, that channel is blocked until barriers
 * arrive on all other channels. Once all barriers are collected, they are released
 * together as a single aligned barrier. This ensures exactly-once semantics but
 * may introduce latency as channels wait for each other.
 *
 * <p><strong>No Barrier Alignment (AT_LEAST_ONCE):</strong>
 * When a barrier is received, it is tracked but the channel is NOT blocked. Records
 * from other channels continue to flow through. Each barrier is emitted immediately
 * upon receipt (first barrier triggers emission, subsequent barriers for the same
 * checkpoint are coalesced). This provides lower latency but at-least-once semantics.
 *
 * <p><strong>Watermark Merging:</strong> Tracks the watermark per channel and only
 * emits the minimum watermark when it advances. This ensures downstream operators
 * see a monotonically increasing watermark that is the min of all inputs.
 */
public class InputGate {

    private static final Logger LOG = LoggerFactory.getLogger(InputGate.class);

    static final long DEFAULT_ALIGNMENT_TIMEOUT_MS = 30000L;

    /**
     * The idle-return threshold. When all channels are momentarily
     * idle (no data, no EOS), {@link #readSingleChannel} / {@link #readMultiChannel}
     * return {@code Optional.empty()} after this much cumulative idle time so the
     * caller's loop top (mailbox drain in {@code StreamTaskInvokable#processInputGate})
     * can process control mails — in particular processing-time timer fire mails,
     * which otherwise sit in the mailbox forever on an idle task (the driver
     * delivers them, but nothing drains them until the next record arrives).
     *
     * <p><b>Contract with the channel heartbeat timeout:</b> the threshold MUST
     * stay above the producer-death channel timeout (150 ms in the liveness
     * tests; production disables the channel timeout entirely), otherwise an
     * idle return would preempt {@code RemoteInputChannel.checkChannelTimeout()}
     * and silently defeat the fast-fail-on-producer-death safety feature.
     * 250 ms keeps the mailbox drain cadence
     * bounded (~4 drains/sec) while staying strictly above 150 ms.
     */
    static final long IDLE_RETURN_THRESHOLD_MS = 250L;

    /**
     * Default for the aligned→unaligned mode-switch threshold. Must be <
     * {@link #DEFAULT_ALIGNMENT_TIMEOUT_MS}. Used only by the constructors
     * that do not opt into unaligned mode — the production path threads the value
     * from {@link io.nop.stream.core.checkpoint.CheckpointConfig}.
     */
    static final long DEFAULT_UNALIGNED_THRESHOLD_MS = 1000L;

    /**
     * Bounded poll timeout shared by {@link #readSingleChannel} and
     * {@link #readMultiChannel}. Each bounded
     * {@link InputChannel#read(long, TimeUnit)} re-enters the channel's timeout
     * check at its top (RemoteInputChannel.checkChannelTimeout), so a consumer
     * effectively parked between records re-fires the producer-death heartbeat
     * check at roughly this cadence instead of blocking forever in queue.take().
     */
    static final long CHANNEL_POLL_TIMEOUT_MS = 50L;

    /**
     * Park between full channel sweeps in {@link #readMultiChannel} once every
     * channel has been polled within a round and none had data. Keeps the idle
     * loop from busy-spinning while staying far below
     * {@link #IDLE_RETURN_THRESHOLD_MS} so the idle-return signal still wins.
     */
    static final long IDLE_PARK_NANOS = 10_000_000L;

    private final List<InputChannel> channels;
    private final java.util.concurrent.atomic.AtomicBoolean closed =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private final long[] currentWatermarks;
    private final EdgeConfig edgeConfig;
    private final boolean barrierAlignment;

    /**
     * Multi-epoch: per-barrier alignment state, keyed by checkpoint id.
     * Insertion order is barrier-arrival order (barriers on a single channel are
     * strictly ordered, so the oldest in-flight barrier is the one currently
     * aligning). Overlapping barrier ids are supported and an aborted epoch's
     * straggling barrier is discarded instead of corrupting the next epoch's
     * alignment (design §2.8.1 D1).
     *
     * <p>This map is a {@link ConcurrentHashMap} so the checkpoint
     * abort handler thread (which calls {@link #abortBarrierAlignment(long)} from
     * the checkpoint timeout / ACK path) can remove an entry while the owning task
     * thread iterates the in-flight alignments (e.g. {@link #markFinishedChannel})
     * without throwing {@link java.util.ConcurrentModificationException}.
     * Iteration is weakly-consistent and never throws CME. {@link #oldestAligning()}
     * selects the minimum checkpoint id (barrier ids are monotonically increasing)
     * rather than relying on insertion order, since {@link ConcurrentHashMap} does
     * not preserve it.
     */
    private final ConcurrentHashMap<Long, BarrierAlignment> inFlightAlignments = new ConcurrentHashMap<>();

    /**
     * Alignments that became fully received in a single
     * {@link #markFinishedChannel} call (a finished channel counts as having
     * delivered every in-flight barrier, so several alignments can complete in
     * the same round) but have not been emitted yet. Aligned barriers must be
     * emitted in checkpoint-id order, ONE per read() call; the completed
     * alignments wait here and stay in {@link #inFlightAlignments} until each
     * is emitted (remove-after-emit). Only the task thread touches the queue
     * (markFinishedChannel / the read-loop drain); the abort handler thread
     * interacts via {@link #abortedBarriers}, which the drain also consults so
     * an aborted pending barrier is dropped, never emitted.
     */
    private final ArrayDeque<BarrierAlignment> pendingBarrierEmissions = new ArrayDeque<>();

    /**
     * Checkpoint ids whose alignment has been aborted. A barrier element
     * carrying one of these ids is silently discarded (the abort was already
     * signaled via the control channel; a late in-data-flow barrier must not
     * corrupt subsequent epochs). Bounded growth: cleared opportunistically when
     * an alignment completes at or above the aborted id.
     *
     * <p>Concurrent set so the abort handler thread ({@code add}) and
     * the task thread ({@code contains} / {@code removeIf}) do not throw CME.
     */
    private final Set<Long> abortedBarriers = ConcurrentHashMap.newKeySet();

    /**
     * Channels currently blocked during aligned barrier alignment (union across all
     * in-flight alignments). Maintained in lockstep with each
     * {@link BarrierAlignment#blockedChannels} so {@link #resumeConsumptionAll()}
     * and {@link #blockConsumption(int)} keep working for external callers.
     *
     * <p>Concurrent set so the abort handler thread
     * ({@link #abortBarrierAlignment} / {@link #resumeConsumptionAll()} from the
     * cancel branch) and the task thread ({@code add}/{@code remove}/{@code contains})
     * do not throw CME.
     */
    private final Set<Integer> blockedChannels = ConcurrentHashMap.newKeySet();

    private final long barrierAlignmentTimeout;

    /**
     * Highest barrier id accepted per channel. Barrier
     * ids are strictly increasing per channel, so a barrier with {@code id <=}
     * the channel's last accepted id is a duplicate or a dead-epoch straggler —
     * it is discarded instead of starting a fresh (never-completing) alignment.
     * Only touched from the task thread ({@code handleBarrierNonRecursive} via the
     * read loops), so a plain array is sufficient.
     */
    private final long[] lastAcceptedBarrierIds;

    /**
     * Per-channel idleness (Flink
     * {@code StatusWatermarkValve} semantics). An idle channel is excluded from
     * the min watermark merge so a silent upstream subtask no longer pins the
     * downstream event time at its last watermark. When ALL channels are idle the
     * IDLE status is forwarded downstream; when a channel reactivates the ACTIVE
     * status is forwarded (downstream re-aligns before trusting watermarks again).
     */
    private final boolean[] channelIdle;

    /**
     * Unaligned checkpoint: whether aligned→unaligned fallback is
     * active for this gate. The non-unaligned constructors default this to
     * {@code false} (alignment timeout → throw); the production constructor
     * threads it from {@link
     * io.nop.stream.core.checkpoint.CheckpointConfig}.
     */
    private final boolean unalignedCheckpointEnabled;

    /**
     * The aligned→unaligned mode-switch threshold in ms. Only consulted when
     * {@link #unalignedCheckpointEnabled} is {@code true}.
     */
    private final long unalignedThreshold;

    /**
     * Channel state captured at the moment of an aligned→unaligned
     * mode switch. Stored here so the task thread can retrieve it (via
     * {@link #consumePendingChannelState()}) after {@link #read()} returns the
     * unaligned barrier, and forward it to {@link CheckpointBarrierTracker}.
     * Non-null only between the mode switch and the next {@code read()} that
     * returns the barrier.
     */
    private ChannelState pendingChannelState;

    private int currentChannelIndex;

    public InputGate(List<InputChannel> channels) {
        this(channels, null, true);
    }

    /**
     * Barrier alignment mode of the gate.
     * <ul>
     *   <li>{@link #STRICT_EXACTLY_ONCE} — channels that already delivered a barrier are
     *       blocked until every channel delivered one (legacy {@code barrierAlignment=true});</li>
     *   <li>{@link #AT_LEAST_ONCE} — channels are never blocked after barrier delivery
     *       (legacy {@code barrierAlignment=false}).</li>
     * </ul>
     */
    public enum AlignmentMode {
        STRICT_EXACTLY_ONCE, AT_LEAST_ONCE
    }

    /**
     * Legacy-boolean mapping: {@code barrierAlignment=true} ↔
     * {@link AlignmentMode#STRICT_EXACTLY_ONCE}; {@code false} ↔
     * {@link AlignmentMode#AT_LEAST_ONCE}. Callers that receive the mode as a
     * boolean from upstream configuration resolution use this to pass a
     * readable enum value to the enum-based constructors.
     */
    public static AlignmentMode alignmentModeFor(boolean barrierAlignment) {
        return barrierAlignment ? AlignmentMode.STRICT_EXACTLY_ONCE : AlignmentMode.AT_LEAST_ONCE;
    }

    /**
     * Creates an InputGate with multiple channels and optional edge configuration.
     * Uses default barrier alignment (STRICT_EXACTLY_ONCE behavior).
     *
     * @param channels   the input channels (must not be null or empty)
     * @param edgeConfig optional edge configuration for flow control (nullable)
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig) {
        this(channels, edgeConfig, true);
    }

    /**
     * Legacy boolean form of {@link #InputGate(List, EdgeConfig, AlignmentMode)}.
     * {@code barrierAlignment=true} maps to {@link AlignmentMode#STRICT_EXACTLY_ONCE};
     * {@code false} maps to {@link AlignmentMode#AT_LEAST_ONCE}.
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig, boolean barrierAlignment) {
        this(channels, edgeConfig, alignmentModeFor(barrierAlignment));
    }

    /**
     * Creates an InputGate with multiple channels, edge configuration, and
     * barrier alignment mode.
     *
     * @param channels      the input channels (must not be null or empty)
     * @param edgeConfig    optional edge configuration for flow control (nullable)
     * @param alignmentMode STRICT_EXACTLY_ONCE blocks channels after receiving a barrier;
     *                      AT_LEAST_ONCE does not block
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig, AlignmentMode alignmentMode) {
        this(channels, edgeConfig, alignmentMode, DEFAULT_ALIGNMENT_TIMEOUT_MS);
    }

    /**
     * Legacy boolean form of
     * {@link #InputGate(List, EdgeConfig, AlignmentMode, long)}.
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig,
                     boolean barrierAlignment, long barrierAlignmentTimeout) {
        this(channels, edgeConfig, alignmentModeFor(barrierAlignment), barrierAlignmentTimeout);
    }

    /**
     * Creates an InputGate with multiple channels, edge configuration,
     * barrier alignment mode, and barrier alignment timeout.
     *
     * @param channels                the input channels (must not be null or empty)
     * @param edgeConfig              optional edge configuration for flow control (nullable)
     * @param alignmentMode           STRICT_EXACTLY_ONCE blocks channels after receiving a
     *                                barrier; AT_LEAST_ONCE does not block
     * @param barrierAlignmentTimeout maximum time in milliseconds to wait for all barrier
     *                                alignments to complete before throwing a timeout exception
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig,
                     AlignmentMode alignmentMode, long barrierAlignmentTimeout) {
        this(channels, edgeConfig, alignmentMode, barrierAlignmentTimeout, false, DEFAULT_UNALIGNED_THRESHOLD_MS);
    }

    /**
     * Unaligned checkpoint: full constructor with aligned→unaligned
     * fallback configuration. Threaded from {@link
     * io.nop.stream.core.checkpoint.CheckpointConfig} via
     * {@code GraphExecutionPlan.build(...)}.
     *
     * <p>When {@code unalignedCheckpointEnabled} is {@code true} and alignment does
     * not complete within {@code unalignedThreshold} ms, the gate captures in-flight
     * channel data, emits the barrier immediately (unaligned mode), and resumes all
     * blocked channels — instead of waiting until {@code barrierAlignmentTimeout}
     * and throwing. The captured {@link ChannelState} is retrievable via
     * {@link #consumePendingChannelState()} right after the barrier is read.
     *
     * <p>Precondition: {@code unalignedCheckpointEnabled=true} requires
     * {@code unalignedThreshold < barrierAlignmentTimeout}; validated upstream by
     * {@code CheckpointConfig.validateUnalignedConfig()}.
     *
     * @param channels                 the input channels (must not be null or empty)
     * @param edgeConfig               optional edge configuration for flow control (nullable)
     * @param alignmentMode            STRICT_EXACTLY_ONCE blocks channels after receiving a
     *                                 barrier; AT_LEAST_ONCE does not block
     * @param barrierAlignmentTimeout  maximum ms to wait for full alignment before
     *                                 throwing (absolute fail bound; only reached when
     *                                 unaligned is disabled)
     * @param unalignedCheckpointEnabled whether aligned→unaligned fallback is enabled
     * @param unalignedThreshold       aligned→unaligned mode-switch threshold in ms
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig,
                     AlignmentMode alignmentMode, long barrierAlignmentTimeout,
                     boolean unalignedCheckpointEnabled, long unalignedThreshold) {
        if (channels == null || channels.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "channels");
        }
        this.channels = new ArrayList<>(channels);
        this.edgeConfig = edgeConfig;
        this.barrierAlignment = (alignmentMode == AlignmentMode.STRICT_EXACTLY_ONCE);
        this.barrierAlignmentTimeout = barrierAlignmentTimeout;
        this.unalignedCheckpointEnabled = unalignedCheckpointEnabled;
        this.unalignedThreshold = unalignedThreshold;
        this.currentWatermarks = new long[channels.size()];
        for (int i = 0; i < currentWatermarks.length; i++) {
            currentWatermarks[i] = Long.MIN_VALUE;
        }
        this.lastAcceptedBarrierIds = new long[channels.size()];
        java.util.Arrays.fill(this.lastAcceptedBarrierIds, -1L);
        this.channelIdle = new boolean[channels.size()];
        this.currentChannelIndex = 0;
        this.pendingChannelState = null;
    }

    /**
     * Legacy boolean form of
     * {@link #InputGate(List, EdgeConfig, AlignmentMode, long, boolean, long)}.
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig,
                     boolean barrierAlignment, long barrierAlignmentTimeout,
                     boolean unalignedCheckpointEnabled, long unalignedThreshold) {
        this(channels, edgeConfig, alignmentModeFor(barrierAlignment), barrierAlignmentTimeout,
                unalignedCheckpointEnabled, unalignedThreshold);
    }

    /**
     * Creates an InputGate with a single channel.
     */
    public InputGate(InputChannel channel) {
        this(channel, null);
    }

    /**
     * Creates an InputGate with a single channel and optional edge configuration.
     *
     * @param channel    the input channel (must not be null)
     * @param edgeConfig optional edge configuration for flow control (nullable)
     */
    public InputGate(InputChannel channel, EdgeConfig edgeConfig) {
        if (channel == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "channel");
        }
        this.channels = new ArrayList<>();
        this.channels.add(channel);
        this.edgeConfig = edgeConfig;
        this.barrierAlignment = true;
        this.barrierAlignmentTimeout = DEFAULT_ALIGNMENT_TIMEOUT_MS;
        this.unalignedCheckpointEnabled = false;
        this.unalignedThreshold = DEFAULT_UNALIGNED_THRESHOLD_MS;
        this.currentWatermarks = new long[]{Long.MIN_VALUE};
        this.lastAcceptedBarrierIds = new long[]{-1L};
        this.channelIdle = new boolean[1];
        this.currentChannelIndex = 0;
        this.pendingChannelState = null;
    }

    /**
     * Reads the next element from the input channels, performing round-robin
     * selection and optional barrier alignment.
     *
     * <p>For single-channel gates, delegates directly to the channel.
     * For multi-channel gates:
     * <ul>
     *   <li><b>barrierAlignment=true:</b> channels that have delivered a barrier are
     *       skipped until all channels have delivered their barriers.</li>
     *   <li><b>barrierAlignment=false:</b> channels are never blocked after barrier
     *       receipt; records continue flowing through (AT_LEAST_ONCE semantics).</li>
     * </ul>
     *
     * @return Optional containing the next element, or empty on end-of-stream
     */
    public Optional<StreamElement> read() {
        if (channels.size() == 1) {
            return readSingleChannel();
        }
        return readMultiChannel();
    }

    /**
     * Returns whether all upstream producers have finished.
     */
    public boolean isAllFinished() {
        for (InputChannel channel : channels) {
            if (!channel.isFinished()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of input channels.
     */
    public int getNumberOfChannels() {
        return channels.size();
    }

    /**
     * Supervision loop support: returns an unmodifiable view of
     * the input channels. Used by the region-restart path to extract the
     * underlying {@link ResultPartition} references (and their attached
     * materialization points) so that restarted consumer tasks can be wired to
     * fresh partitions sharing the same materialization store.
     *
     * @return unmodifiable list of input channels (never null, possibly a single channel)
     */
    public List<InputChannel> getChannels() {
        return Collections.unmodifiableList(channels);
    }

    /**
     * Releases every channel's external resources. For {@code RemoteInputChannel}
     * this cancels the underlying message-service subscription — without this
     * teardown a finished / redeployed task would leak one live subscription per
     * channel and keep occupying the shared message-backend dispatch surface.
     * Local in-JVM channels hold no external resources; their {@code close} is a
     * no-op.
     *
     * <p>Idempotent. Channel failures during close are logged and do not prevent
     * the remaining channels from being released (best-effort teardown).
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (InputChannel channel : channels) {
            try {
                channel.close();
            } catch (Exception e) {
                LOG.error("Failed to close input channel {} - continuing teardown of remaining channels",
                        channel, e);
            }
        }
    }

    /**
     * Blocks consumption from the specified channel during barrier alignment.
     * Only effective when {@link #barrierAlignment} is true.
     *
     * @param channelIndex the channel to block (0-based)
     * @throws IllegalArgumentException if channelIndex is out of range
     */
    public void blockConsumption(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channels.size()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid channel index: " + channelIndex);
        }
        blockedChannels.add(channelIndex);
    }

    /**
     * Resumes consumption from the specified channel. Safe no-op if the channel
     * is not currently blocked.
     *
     * @param channelIndex the channel to resume (0-based)
     * @throws IllegalArgumentException if channelIndex is out of range
     */
    public void resumeConsumption(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channels.size()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Invalid channel index: " + channelIndex);
        }
        blockedChannels.remove(channelIndex);
    }

    /**
     * Resumes consumption from all channels. Called when alignment completes
     * or when a checkpoint is aborted.
     */
    public void resumeConsumptionAll() {
        blockedChannels.clear();
    }

    /**
     * Returns the current minimum watermark across all channels.
     *
     * <p>Idle channels are EXCLUDED from the min —
     * an idle upstream subtask's last watermark must not pin the merged event
     * time. When every channel is idle there is no active constraint; the min
     * over all channels is returned (no rewinding of previously emitted
     * progress; downstream already received the IDLE status in that case).
     */
    public long getCurrentWatermark() {
        long min = Long.MAX_VALUE;
        boolean anyActive = false;
        for (int i = 0; i < currentWatermarks.length; i++) {
            if (channelIdle[i]) {
                continue;
            }
            anyActive = true;
            if (currentWatermarks[i] < min) {
                min = currentWatermarks[i];
            }
        }
        if (!anyActive) {
            min = Long.MAX_VALUE;
            for (long wm : currentWatermarks) {
                if (wm < min) {
                    min = wm;
                }
            }
        }
        return min == Long.MAX_VALUE ? Long.MIN_VALUE : min;
    }

    /**
     * Returns whether every channel is currently idle.
     */
    public boolean isAllChannelsIdle() {
        for (boolean idle : channelIdle) {
            if (!idle) {
                return false;
            }
        }
        return true;
    }

    private Optional<StreamElement> readSingleChannel() {
        // Bounded-read loop: poll with CHANNEL_POLL_TIMEOUT_MS instead of parking
        // in the unbounded read() overload (which blocks in queue.take() forever).
        // Each iteration re-enters
        // RemoteInputChannel.read(long, TimeUnit), which re-runs
        // checkChannelTimeout() at its top, so the channel heartbeat-timeout
        // re-fires every ~50 ms even while the consumer is effectively parked
        // waiting for data — a dead producer cannot leave the single-input
        // consumer blocked indefinitely. channelTimeoutMs lives on
        // RemoteInputChannel (not the
        // base InputChannel type) so it is not readable here; the fixed 50 ms poll
        // is the same ceiling readMultiChannel uses, and is what re-fires the
        // timeout check inside the bounded overload.
        InputChannel channel = channels.get(0);
        long idleSince = -1L;
        try {
            while (true) {
                StreamElement element = channel.read(CHANNEL_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (element == null) {
                    if (isChannelEndOfStream(channel)) {
                        return Optional.empty();
                    }
                    // Idle drain signal. After the idle-return threshold of
                    // consecutive idle polls, return empty (NOT EOS) so the caller
                    // (processInputGate) can drain control mails at its loop top
                    // and then re-read. The channel heartbeat timeout (where
                    // enabled) fires first because the threshold is larger.
                    idleSince = stampIdleStart(idleSince);
                    if (idleThresholdReached(idleSince)) {
                        return Optional.empty();
                    }
                    continue;
                }
                idleSince = -1L;
                // Track watermark for single channel
                if (element.isWatermark()) {
                    Watermark wm = element.asWatermark();
                    currentWatermarks[0] = wm.getTimestamp();
                }
                // Track idleness on the single-channel path
                // too — the sole channel going idle means ALL channels idle, so the
                // status passes through to the operator chain (which forwards it across
                // the next task boundary); getCurrentWatermark's idle exclusion also
                // applies. No merge synthesis is needed with a single channel.
                if (element.isWatermarkStatus()) {
                    io.nop.stream.core.streamrecord.watermark.WatermarkStatus status = element.asWatermarkStatus();
                    if (status.isIdle()) {
                        channelIdle[0] = true;
                    } else {
                        channelIdle[0] = false;
                    }
                    return Optional.of(element);
                }
                // A single-channel gate must apply the SAME
                // barrier integrity contract as the multi-channel path: a
                // straggler barrier for an aborted epoch is discarded (the abort was
                // already signaled via the control channel; returning it would trigger a
                // spurious snapshot and downstream forwarding), and a same-channel
                // duplicate barrier id is de-duplicated (delivered exactly once). With
                // one channel the alignment is trivially satisfied on first receipt, so
                // normal barriers still pass through unchanged.
                if (element.isCheckpointBarrier()) {
                    Optional<StreamElement> result = handleBarrierNonRecursive(0, element.asCheckpointBarrier());
                    if (result.isPresent()) {
                        return result;
                    }
                    // filtered (aborted-epoch straggler / duplicate / coalesced):
                    // keep reading
                    continue;
                }
                return Optional.of(element);
            }
        } catch (InterruptedException e) {
            // Align with multi-input interrupt handling — set interrupt flag
            // and return empty. The caller (processInputGate) breaks on empty, and
            // SubtaskTask's state machine (state==CANCELING after cancel() set the
            // flag and interrupted this thread) transitions to CANCELED — not FAILED,
            // not mistaken SUCCESS/EOS.
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Disambiguates a {@code null} return from the bounded channel read overload:
     * null means EITHER poll-timeout OR end-of-stream.
     *   finished  -> producer is done -> end-of-stream
     *   !finished -> momentary idle   -> keep polling
     * Treating all null as "continue" would busy-spin on EOS; treating all null
     * as "empty" would silently terminate on every idle poll. Shared verbatim by
     * {@link #readSingleChannel} and {@link #readMultiChannel} so both read paths
     * apply the same null-branch contract.
     */
    private static boolean isChannelEndOfStream(InputChannel channel) {
        return channel.isFinished();
    }

    /**
     * Stamps the idle-drain clock on the first consecutive idle poll.
     *
     * @return the (possibly freshly stamped) idle-since timestamp to thread
     *         through the read loop
     */
    private static long stampIdleStart(long idleSince) {
        return idleSince < 0L ? CoreMetrics.currentTimeMillis() : idleSince;
    }

    /**
     * Whether the cumulative idle stretch has reached
     * {@link #IDLE_RETURN_THRESHOLD_MS} — the signal to return empty (NOT EOS) so
     * the caller's loop top can drain control mails and then re-read.
     */
    private static boolean idleThresholdReached(long idleSince) {
        return CoreMetrics.currentTimeMillis() - idleSince >= IDLE_RETURN_THRESHOLD_MS;
    }

    private Optional<StreamElement> readMultiChannel() {
        long idleSince = -1L;
        retry:
        while (true) {
            Optional<StreamElement> pendingResult = emitPendingBarriers();
            if (pendingResult.isPresent()) {
                return pendingResult;
            }

            // Evaluate the oldest in-flight alignment's
            // elapsed time at the ENTRY of every read(), decoupled from whether a
            // channel returned data — a continuously-active channel
            // (sustained traffic during alignment) must not starve the unaligned
            // escape (1s) or the fail-fast alignment timeout (30s); either
            // starvation would degrade signaling down to
            // the coordinator-side checkpointTimeout. Oldest in-flight alignment is
            // the baseline: escape emits
            // the barrier with captured ChannelState, timeout throws
            // ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT.
            Optional<StreamElement> elapsedResult = checkAlignmentElapsed();
            if (elapsedResult.isPresent()) {
                return elapsedResult;
            }

            int channelsChecked = 0;
            int totalChannels = channels.size();

            while (channelsChecked < totalChannels) {
                int channelIndex = currentChannelIndex % totalChannels;
                currentChannelIndex = (currentChannelIndex + 1) % totalChannels;
                channelsChecked++;

                if (barrierAlignment && blockedChannels.contains(channelIndex)) {
                    continue;
                }

                InputChannel channel = channels.get(channelIndex);
                try {
                    StreamElement element = channel.read(CHANNEL_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (element == null) {
                        if (isChannelEndOfStream(channel)) {
                            // A finished channel will never deliver more
                            // barriers, so mark it as received for every in-flight
                            // alignment and complete any alignment that becomes
                            // satisfied.
                            Optional<StreamElement> result = markFinishedChannel(channelIndex);
                            if (result.isPresent()) return result;
                        }
                        continue;
                    }

                    // Any element is data-plane progress: reset the idle clock so
                    // the idle-return signal only fires after real idle stretches.
                    idleSince = -1L;

                    Optional<StreamElement> dispatched = dispatchChannelElement(channelIndex, element);
                    if (dispatched.isPresent()) {
                        return dispatched;
                    }
                    // Filtered control element (aborted/stale barrier, non-advancing
                    // watermark or watermark status): restart from the loop top.
                    continue retry;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }

            if (isAllFinished()) {
                return Optional.empty();
            }

            // Idle drain signal — a full sweep with zero returns and no
            // EOS yet. Return empty (NOT EOS) once the idle-return threshold has
            // been idle, so the caller's loop top can drain control mails
            // (processing-time timer fires) and then re-read. The caller must
            // distinguish this from EOS via isAllFinished().
            idleSince = stampIdleStart(idleSince);
            if (idleThresholdReached(idleSince)) {
                return Optional.empty();
            }

            LockSupport.parkNanos(IDLE_PARK_NANOS);
        }
    }

    /**
     * Emits (one per read(), in checkpoint-id order) any barrier completed
     * by an earlier {@link #markFinishedChannel} call — a single channel-finish
     * can complete several in-flight alignments at once. A pending barrier whose
     * checkpoint was aborted in the meantime is dropped (never emitted) and must
     * not block later emissions: the drain keeps restarting from the queue head
     * until it either emits the oldest live pending barrier or empties the queue.
     *
     * @return the barrier to emit downstream, or empty when no live pending
     *         emission remains (the caller proceeds to the channel sweep)
     */
    private Optional<StreamElement> emitPendingBarriers() {
        while (barrierAlignment && !pendingBarrierEmissions.isEmpty()) {
            BarrierAlignment pending = pendingBarrierEmissions.pollFirst();
            if (abortedBarriers.contains(pending.checkpointId)) {
                for (int c : pending.blockedChannels) {
                    blockedChannels.remove(c);
                }
                continue;
            }
            return emitCompletedAlignment(pending);
        }
        return Optional.empty();
    }

    /**
     * Dispatches one non-null element polled from a channel, in dispatch order:
     * checkpoint barrier, watermark, watermark
     * status, then plain data.
     *
     * @return the element to emit downstream — the control handler's result when
     *         present, otherwise the data element itself; empty only when a
     *         control element was filtered by its handler (the caller restarts
     *         the read loop from the top)
     */
    private Optional<StreamElement> dispatchChannelElement(int channelIndex, StreamElement element) {
        if (element.isCheckpointBarrier()) {
            return handleBarrierNonRecursive(channelIndex, element.asCheckpointBarrier());
        }
        if (element.isWatermark()) {
            return handleWatermarkNonRecursive(channelIndex, element.asWatermark());
        }
        if (element.isWatermarkStatus()) {
            // Idle/active status participates
            // in the merge (idle channels excluded from min; IDLE/ACTIVE
            // transitions forwarded to the operator chain).
            return handleWatermarkStatusNonRecursive(channelIndex, element.asWatermarkStatus());
        }
        return Optional.of(element);
    }

    /**
     * Evaluates the oldest in-flight alignment's
     * elapsed time at every {@link #readMultiChannel()} entry. Returns the
     * unaligned-mode barrier when the escape threshold fired, throws
     * {@code ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT} when the fail-fast timeout
     * fired, and returns empty when no alignment is overdue. Fully-received
     * (pending-emission) alignments never trip the gates.
     */
    private Optional<StreamElement> checkAlignmentElapsed() {
        // Timeout / aligned→unaligned fallback applies to the
        // oldest in-flight alignment (the one currently aligning). Aligned
        // barriers serialize via channel blocking, so there is at most one
        // actively-aligning barrier at a time.
        BarrierAlignment oldest = oldestAligning();
        if (oldest == null || !barrierAlignment
                || oldest.receivedChannels.size() >= channels.size()) {
            return Optional.empty();
        }
        long elapsed = CoreMetrics.currentTimeMillis() - oldest.startTime;

        if (unalignedCheckpointEnabled && elapsed > unalignedThreshold) {
            return Optional.of(switchToUnalignedAndEmit(oldest));
        }

        if (elapsed > barrierAlignmentTimeout) {
            throw new StreamException(ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT)
                    .param(ARG_TIMEOUT_MS, elapsed);
        }
        return Optional.empty();
    }

    /**
     * Unaligned checkpoint: switches the oldest in-flight checkpoint
     * from aligned to unaligned mode. Captures in-flight data from every channel
     * (per §2.11.2 semantics: aligned channels → post-barrier records; non-aligned
     * channels → all buffered records), resumes the channels this barrier blocked,
     * removes the alignment state, and stashes the {@link ChannelState} for the task
     * thread to retrieve via {@link #consumePendingChannelState()}.
     *
     * <p>Unaligned stays single-in-flight (design §2.8.1 D4). Aligned
     * barriers serialize via channel blocking so there is at most one
     * actively-aligning barrier; if more than one is somehow in-flight at the
     * switch instant (unsupported unaligned+multi config), fail-fast rather than
     * silently capturing state for the wrong epoch.
     *
     * @param align the oldest in-flight alignment to switch
     * @return the aligned/unaligned barrier to emit downstream
     */
    private CheckpointBarrier switchToUnalignedAndEmit(BarrierAlignment align) {
        if (unalignedCheckpointEnabled && inFlightAlignments.size() > 1) {
            // Unaligned multi-in-flight is unsupported; fail-fast here
            // so an unsupported config never silently captures state for the wrong epoch.
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_REASON,
                    "Unaligned checkpoint is enabled and multiple barriers are in-flight (ids="
                            + new ArrayList<>(inFlightAlignments.keySet())
                            + "); unaligned multi-in-flight is not supported (Stage 47 successor)");
        }
        long elapsed = CoreMetrics.currentTimeMillis() - align.startTime;
        ChannelState channelState = new ChannelState();
        for (int i = 0; i < channels.size(); i++) {
            // align.receivedChannels reflects whether channel i has delivered this
            // barrier: true → aligned channel, drain post-barrier records; false →
            // non-aligned channel, drain all buffered (pre-barrier) records.
            boolean received = align.receivedChannels.contains(i);
            java.util.List<StreamElement> captured = channels.get(i).captureInFlightData(received);
            if (captured != null && !captured.isEmpty()) {
                channelState.putRecords(i, captured);
            }
        }
        this.pendingChannelState = channelState;

        CheckpointBarrier barrier = align.firstBarrier;
        long checkpointId = barrier != null ? barrier.getId() : -1L;
        // Resume the channels this barrier had blocked.
        for (int c : align.blockedChannels) {
            blockedChannels.remove(c);
        }
        inFlightAlignments.remove(align.checkpointId);
        cleanupAbortedBarriersUpTo(checkpointId);

        LOG.info("Checkpoint {} switched to unaligned mode after {}ms (threshold={}ms); "
                        + "captured {} in-flight record(s) across {} channel(s)",
                checkpointId, elapsed, unalignedThreshold,
                channelState.getTotalRecordCount(), channels.size());
        return barrier;
    }

    /**
     * Returns and clears the channel state captured during the most
     * recent aligned→unaligned mode switch. Intended to be called by the task
     * thread immediately after {@link #read()} returns the unaligned barrier, so
     * the state can be forwarded to {@link CheckpointBarrierTracker#setChannelState}.
     *
     * @return the captured channel state, or {@code null} if the last barrier was
     *         completed in aligned mode (no channel state)
     */
    public ChannelState consumePendingChannelState() {
        ChannelState cs = this.pendingChannelState;
        this.pendingChannelState = null;
        return cs;
    }

    /**
     * Whether aligned→unaligned fallback is enabled for this gate.
     */
    public boolean isUnalignedCheckpointEnabled() {
        return unalignedCheckpointEnabled;
    }

    /**
     * The aligned→unaligned mode-switch threshold in ms.
     */
    public long getUnalignedThreshold() {
        return unalignedThreshold;
    }

    /**
     * Unaligned checkpoint recovery: injects previously captured
     * in-flight records back into the corresponding channel buffers, so they are
     * processed BEFORE any new upstream records when the recovered task resumes
     * reading. Called by the recovery path after operator state restore and before
     * the task thread starts processing (see {@code checkpoint-design.md} §2.11.4).
     *
     * <p>Records for a channel index are pre-pended to that channel's buffer via
     * {@link InputChannel#injectElements(List)}. Channel indices absent from
     * {@code channelState} are left untouched. Safe to call on a freshly-built gate
     * before any read has occurred.
     *
     * @param channelState the captured in-flight state (null/empty = no-op)
     */
    public void restoreChannelState(ChannelState channelState) {
        if (channelState == null || channelState.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<StreamElement>> e : channelState.getAllRecords().entrySet()) {
            int idx = e.getKey();
            if (idx >= 0 && idx < channels.size()) {
                channels.get(idx).injectElements(e.getValue());
            }
        }
        if (LOG.isInfoEnabled()) {
            LOG.info("Restored channel state: {} record(s) across {} channel(s)",
                    channelState.getTotalRecordCount(), channelState.getAllRecords().size());
        }
    }

    private Optional<StreamElement> handleBarrierNonRecursive(int channelIndex, CheckpointBarrier barrier) {
        long id = barrier.getId();

        if (abortedBarriers.contains(id)) {
            // Late arrival of an aborted checkpoint's barrier. The abort
            // was already signaled via the control channel; discard the straggler
            // so it does not start a spurious alignment or corrupt the next epoch.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discarding barrier {} on channel {} (epoch aborted)", id, channelIndex);
            }
            return Optional.empty();
        }

        // Barrier ids are strictly increasing per
        // channel, so id <= the channel's last accepted id is a duplicate or a
        // dead-epoch straggler (e.g. a duplicate arriving AFTER its alignment
        // completed and was removed).
        if (id <= lastAcceptedBarrierIds[channelIndex]) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discarding stale/duplicate barrier {} on channel {} (last accepted: {})",
                        id, channelIndex, lastAcceptedBarrierIds[channelIndex]);
            }
            return Optional.empty();
        }

        BarrierAlignment align = inFlightAlignments.get(id);
        if (align == null) {
            align = new BarrierAlignment(id, barrier, CoreMetrics.currentTimeMillis());
            inFlightAlignments.put(id, align);
        }

        if (align.receivedChannels.contains(channelIndex)) {
            // Duplicate barrier for the same id on the same channel: ignore
            // (explicit debug semantics, not a traceless drop).
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discarding duplicate barrier {} on channel {} (already received)", id, channelIndex);
            }
            return Optional.empty();
        }
        align.receivedChannels.add(channelIndex);
        lastAcceptedBarrierIds[channelIndex] = id;

        if (barrierAlignment) {
            align.blockedChannels.add(channelIndex);
            blockedChannels.add(channelIndex);
        }

        boolean fullyReceived = align.receivedChannels.size() >= channels.size();

        if (!barrierAlignment) {
            // AT_LEAST_ONCE: emit on first receipt, coalesce the rest.
            if (!align.emitted) {
                align.emitted = true;
                if (fullyReceived) {
                    inFlightAlignments.remove(id);
                }
                return Optional.of(barrier);
            }
            if (fullyReceived) {
                inFlightAlignments.remove(id);
            }
            return Optional.empty();
        }

        // Aligned: emit only when fully received, then unblock this barrier's channels.
        if (fullyReceived) {
            inFlightAlignments.remove(id);
            for (int c : align.blockedChannels) {
                blockedChannels.remove(c);
            }
            cleanupAbortedBarriersUpTo(id);
            return Optional.of(align.firstBarrier);
        }

        return Optional.empty();
    }

    /**
     * Marks a finished channel as having delivered every in-flight
     * barrier (it will never send more data), then completes any alignment that
     * becomes satisfied.
     *
     * <p>A finished channel delivers EVERY in-flight barrier, so several
     * alignments can become fully received in the same call. ALL of them are
     * collected and emitted in checkpoint-id order, one per read() call (the
     * lowest completes now; the rest wait in {@link #pendingBarrierEmissions});
     * each is removed from {@link #inFlightAlignments} only at emission —
     * a fully-received alignment that is never emitted would never deliver its
     * barrier (downstream never snapshots
     * that checkpoint) and, being fully received, would permanently mask the
     * alignment-timeout / unaligned-fallback gates as the min-id in-flight item.
     */
    private Optional<StreamElement> markFinishedChannel(int channelIndex) {
        List<BarrierAlignment> completed = new ArrayList<>();
        for (BarrierAlignment align : inFlightAlignments.values()) {
            if (!align.receivedChannels.contains(channelIndex)) {
                align.receivedChannels.add(channelIndex);
                if (align.receivedChannels.size() >= channels.size()) {
                    if (!barrierAlignment) {
                        // AT_LEAST_ONCE: emission happened at first receipt;
                        // removal at full receipt is cleanup only.
                        inFlightAlignments.remove(align.checkpointId);
                    } else {
                        completed.add(align);
                    }
                }
            }
        }
        if (completed.isEmpty()) {
            return Optional.empty();
        }
        // Barrier ids are monotonically increasing from the coordinator, so the
        // completed alignments must be emitted in ascending checkpoint-id order.
        completed.sort(Comparator.comparingLong(a -> a.checkpointId));
        for (int i = 1; i < completed.size(); i++) {
            pendingBarrierEmissions.addLast(completed.get(i));
        }
        return emitCompletedAlignment(completed.get(0));
    }

    /**
     * Removes the alignment from the in-flight map, resumes the channels
     * it had blocked, opportunistically clears aborted markers, and returns the
     * barrier for emission. Shared by {@link #markFinishedChannel} and the
     * pending-emission drain in {@link #readMultiChannel}.
     */
    private Optional<StreamElement> emitCompletedAlignment(BarrierAlignment align) {
        inFlightAlignments.remove(align.checkpointId);
        for (int c : align.blockedChannels) {
            blockedChannels.remove(c);
        }
        cleanupAbortedBarriersUpTo(align.checkpointId);
        return Optional.of(align.firstBarrier);
    }

    /**
     * Returns the oldest in-flight alignment (lowest checkpoint id), or
     * null. This is the barrier currently aligning (aligned serialization via
     * channel blocking guarantees at most one is actively aligning at a time).
     *
     * <p>{@link #inFlightAlignments} is a {@link ConcurrentHashMap}
     * (no insertion order), so the oldest is selected by minimum checkpoint id.
     * Barrier ids are monotonically increasing from the coordinator, so min id ==
     * oldest in-flight barrier.
     */
    private BarrierAlignment oldestAligning() {
        BarrierAlignment oldest = null;
        for (BarrierAlignment a : inFlightAlignments.values()) {
            if (oldest == null || a.checkpointId < oldest.checkpointId) {
                oldest = a;
            }
        }
        return oldest;
    }

    /**
     * Drops aborted-barrier markers that can no longer be observed
     * (any aborted id &le; the just-completed id is unreachable because barriers
     * are strictly ordered per channel). Keeps {@link #abortedBarriers} bounded.
     */
    private void cleanupAbortedBarriersUpTo(long completedId) {
        if (abortedBarriers.isEmpty()) {
            return;
        }
        abortedBarriers.removeIf(id -> id <= completedId);
    }

    /**
     * Aborts alignment for a specific checkpoint id (epoch-precise).
     * Resumes channels this barrier had blocked and records the id so a straggling
     * in-data-flow barrier for the same epoch is discarded instead of starting a
     * new alignment. Other in-flight epochs are undisturbed.
     *
     * <p>Ordering: the aborted id is recorded in {@link #abortedBarriers}
     * BEFORE the in-flight alignment is removed. This closes a re-creation window:
     * with remove-first, a racing {@code handleBarrierNonRecursive} (task thread)
     * could observe the removed alignment, miss the not-yet-added aborted id, and
     * re-create a fresh alignment that never completes (leaked in-flight state).
     * With add-first, any alignment that exists at remove-time is reaped by the
     * subsequent {@code remove}, and any barrier observed after the add is
     * discarded by the {@code abortedBarriers.contains} check. The collections are
     * concurrent-safe, so these cross-thread ops do not throw
     * {@link java.util.ConcurrentModificationException}.
     */
    public void abortBarrierAlignment(long checkpointId) {
        abortedBarriers.add(checkpointId);
        BarrierAlignment removed = inFlightAlignments.remove(checkpointId);
        if (removed != null) {
            for (int c : removed.blockedChannels) {
                blockedChannels.remove(c);
            }
            LOG.debug("Aborted alignment for checkpoint {} (resumed {} blocked channel(s))",
                    checkpointId, removed.blockedChannels.size());
        }
    }

    /**
     * Snapshot of in-flight barrier ids (for tests / observability).
     */
    public List<Long> getInFlightBarrierIds() {
        return new ArrayList<>(inFlightAlignments.keySet());
    }

    private Optional<StreamElement> handleWatermarkNonRecursive(int channelIndex, Watermark watermark) {
        long oldWatermark = currentWatermarks[channelIndex];
        if (watermark.getTimestamp() <= oldWatermark) {
            return Optional.empty();
        }
        currentWatermarks[channelIndex] = watermark.getTimestamp();

        // A watermark from a currently-idle channel is recorded but does not
        // drive the combined output (the channel must first signal ACTIVE — Flink
        // StatusWatermarkValve semantics).
        if (channelIdle[channelIndex]) {
            return Optional.empty();
        }

        long oldMin = minWatermarkExcluding(channelIndex, oldWatermark);
        long newMin = getCurrentWatermark();

        if (newMin > oldMin) {
            return Optional.of(new Watermark(newMin));
        }

        return Optional.empty();
    }

    /**
     * Handles a {@code WatermarkStatus} element from
     * one channel, mirroring Flink's {@code StatusWatermarkValve}:
     * <ul>
     *   <li>IDLE transition: the channel is excluded from the min merge; if ALL
     *       channels are now idle the IDLE status is forwarded downstream;
     *       otherwise the combined watermark over the remaining active channels
     *       is re-emitted if it advanced (the idle channel may have held the min).</li>
     *   <li>ACTIVE transition: the ACTIVE status is forwarded downstream (at
     *       least one channel active again); watermark emission resumes on the
     *       next watermark arrival.</li>
     * </ul>
     */
    private Optional<StreamElement> handleWatermarkStatusNonRecursive(
            int channelIndex, io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
        if (status.isIdle()) {
            if (!channelIdle[channelIndex]) {
                channelIdle[channelIndex] = true;
                if (isAllChannelsIdle()) {
                    return Optional.of(status);
                }
                // partial idle: the excluded channel may have held the min —
                // re-emit the advanced combined watermark if it moved
                long newMin = getCurrentWatermark();
                if (newMin > minWatermarkAllIgnoringIdleStatus()) {
                    return Optional.of(new Watermark(newMin));
                }
            }
            return Optional.empty();
        }
        // ACTIVE
        if (channelIdle[channelIndex]) {
            channelIdle[channelIndex] = false;
            if (!isAllChannelsIdle()) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /**
     * The merged watermark this gate would report with NO idle exclusion, used
     * to detect whether excluding the just-ided channel actually advanced the
     * combined output.
     */
    private long minWatermarkAllIgnoringIdleStatus() {
        long min = Long.MAX_VALUE;
        for (long wm : currentWatermarks) {
            if (wm < min) {
                min = wm;
            }
        }
        return min == Long.MAX_VALUE ? Long.MIN_VALUE : min;
    }

    private long minWatermarkExcluding(int excludeIndex, long oldValue) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < currentWatermarks.length; i++) {
            if (channelIdle[i] && i != excludeIndex) {
                // Idle channels do not constrain the merge
                continue;
            }
            long val = (i == excludeIndex) ? oldValue : currentWatermarks[i];
            if (val < min) {
                min = val;
            }
        }
        return min == Long.MAX_VALUE ? Long.MIN_VALUE : min;
    }

    /**
     * Per-barrier alignment state. Each in-flight checkpoint owns an
     * independent record of which channels have delivered its barrier, which
     * channels it has blocked, and when alignment started (for timeout/unaligned).
     *
     * <p>{@code receivedChannels} / {@code blockedChannels} are
     * concurrent sets. The checkpoint abort handler thread may remove the owning
     * alignment from {@link #inFlightAlignments} and then read
     * {@code blockedChannels} (to resume those channels) while the task thread is
     * mid-iteration; concurrent sets prevent a {@link java.util.ConcurrentModificationException}
     * on those inner collections.
     */
    private static final class BarrierAlignment {
        final long checkpointId;
        final CheckpointBarrier firstBarrier;
        final Set<Integer> receivedChannels = ConcurrentHashMap.newKeySet();
        final Set<Integer> blockedChannels = ConcurrentHashMap.newKeySet();
        final long startTime;
        boolean emitted; // AT_LEAST_ONCE: first-emit tracking

        BarrierAlignment(long checkpointId, CheckpointBarrier firstBarrier, long startTime) {
            this.checkpointId = checkpointId;
            this.firstBarrier = firstBarrier;
            this.startTime = startTime;
        }
    }
}
