/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p><strong>Barrier Alignment (barrierAlignment=true, STRICT_EXACTLY_ONCE):</strong>
 * When a barrier is received on one channel, that channel is blocked until barriers
 * arrive on all other channels. Once all barriers are collected, they are released
 * together as a single aligned barrier. This ensures exactly-once semantics but
 * may introduce latency as channels wait for each other.
 *
 * <p><strong>No Barrier Alignment (barrierAlignment=false, AT_LEAST_ONCE):</strong>
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
     * Stage 43 default for aligned→unaligned mode-switch threshold. Must be <
     * {@link #DEFAULT_ALIGNMENT_TIMEOUT_MS}. Used only by the legacy constructors
     * that do not opt into unaligned mode — the production path threads the value
     * from {@link io.nop.stream.core.checkpoint.CheckpointConfig}.
     */
    static final long DEFAULT_UNALIGNED_THRESHOLD_MS = 1000L;

    private final List<InputChannel> channels;
    private final long[] currentWatermarks;
    private final EdgeConfig edgeConfig;
    private final boolean barrierAlignment;

    // Barrier alignment state
    private final boolean[] barrierReceived;
    private final Set<Integer> blockedChannels;
    private CheckpointBarrier pendingBarrier;
    private int barriersRemaining;
    private boolean barrierEmitted;
    private long alignmentStartTime;
    private final long barrierAlignmentTimeout;

    /**
     * Stage 43 (unaligned checkpoint): whether aligned→unaligned fallback is
     * active for this gate. The legacy constructors default this to {@code false}
     * so existing behavior (alignment timeout → throw) is preserved; the
     * production constructor threads it from {@link
     * io.nop.stream.core.checkpoint.CheckpointConfig}.
     */
    private final boolean unalignedCheckpointEnabled;

    /**
     * Stage 43: aligned→unaligned mode-switch threshold in ms. Only consulted when
     * {@link #unalignedCheckpointEnabled} is {@code true}.
     */
    private final long unalignedThreshold;

    /**
     * Stage 43: channel state captured at the moment of an aligned→unaligned
     * mode switch. Stored here so the task thread can retrieve it (via
     * {@link #consumePendingChannelState()}) after {@link #read()} returns the
     * unaligned barrier, and forward it to {@link CheckpointBarrierTracker}.
     * Non-null only between the mode switch and the next {@code read()} that
     * returns the barrier.
     */
    private ChannelState pendingChannelState;

    private int currentChannelIndex;
    private int emptyRounds;

    public InputGate(List<InputChannel> channels) {
        this(channels, null, true);
    }

    /**
     * Creates an InputGate with multiple channels and optional edge configuration.
     * Uses default barrier alignment (true = STRICT_EXACTLY_ONCE behavior).
     *
     * @param channels   the input channels (must not be null or empty)
     * @param edgeConfig optional edge configuration for flow control (nullable)
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig) {
        this(channels, edgeConfig, true);
    }

    /**
     * Creates an InputGate with multiple channels, edge configuration, and
     * barrier alignment mode.
     *
     * @param channels         the input channels (must not be null or empty)
     * @param edgeConfig       optional edge configuration for flow control (nullable)
     * @param barrierAlignment if true, block channels after receiving barrier
     *                         (STRICT_EXACTLY_ONCE); if false, don't block (AT_LEAST_ONCE)
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig, boolean barrierAlignment) {
        this(channels, edgeConfig, barrierAlignment, DEFAULT_ALIGNMENT_TIMEOUT_MS);
    }

    /**
     * Creates an InputGate with multiple channels, edge configuration,
     * barrier alignment mode, and barrier alignment timeout.
     *
     * @param channels               the input channels (must not be null or empty)
     * @param edgeConfig             optional edge configuration for flow control (nullable)
     * @param barrierAlignment       if true, block channels after receiving barrier
     *                               (STRICT_EXACTLY_ONCE); if false, don't block (AT_LEAST_ONCE)
     * @param barrierAlignmentTimeout maximum time in milliseconds to wait for all barrier
     *                                alignments to complete before throwing a timeout exception
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig,
                     boolean barrierAlignment, long barrierAlignmentTimeout) {
        this(channels, edgeConfig, barrierAlignment, barrierAlignmentTimeout, false, DEFAULT_UNALIGNED_THRESHOLD_MS);
    }

    /**
     * Stage 43 (unaligned checkpoint): full constructor with aligned→unaligned
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
     * @param barrierAlignment         if true, block channels after receiving barrier
     * @param barrierAlignmentTimeout  maximum ms to wait for full alignment before
     *                                 throwing (absolute fail bound; only reached when
     *                                 unaligned is disabled)
     * @param unalignedCheckpointEnabled whether aligned→unaligned fallback is enabled
     * @param unalignedThreshold       aligned→unaligned mode-switch threshold in ms
     */
    public InputGate(List<InputChannel> channels, EdgeConfig edgeConfig,
                     boolean barrierAlignment, long barrierAlignmentTimeout,
                     boolean unalignedCheckpointEnabled, long unalignedThreshold) {
        if (channels == null || channels.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "channels");
        }
        this.channels = new ArrayList<>(channels);
        this.edgeConfig = edgeConfig;
        this.barrierAlignment = barrierAlignment;
        this.barrierAlignmentTimeout = barrierAlignmentTimeout;
        this.unalignedCheckpointEnabled = unalignedCheckpointEnabled;
        this.unalignedThreshold = unalignedThreshold;
        this.currentWatermarks = new long[channels.size()];
        for (int i = 0; i < currentWatermarks.length; i++) {
            currentWatermarks[i] = Long.MIN_VALUE;
        }
        this.barrierReceived = new boolean[channels.size()];
        this.blockedChannels = new HashSet<>();
        this.barriersRemaining = 0;
        this.pendingBarrier = null;
        this.barrierEmitted = false;
        this.currentChannelIndex = 0;
        this.alignmentStartTime = 0;
        this.pendingChannelState = null;
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
        this.barrierReceived = new boolean[1];
        this.blockedChannels = new HashSet<>();
        this.barriersRemaining = 0;
        this.pendingBarrier = null;
        this.currentChannelIndex = 0;
        this.alignmentStartTime = 0;
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
     * Blocks consumption from the specified channel during barrier alignment.
     * Only effective when {@link #barrierAlignment} is true.
     *
     * @param channelIndex the channel to block (0-based)
     * @throws IllegalArgumentException if channelIndex is out of range
     */
    public void blockConsumption(int channelIndex) {
        if (channelIndex < 0 || channelIndex >= channels.size()) {
            throw new IllegalArgumentException("Invalid channel index: " + channelIndex);
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
            throw new IllegalArgumentException("Invalid channel index: " + channelIndex);
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
     */
    public long getCurrentWatermark() {
        long min = Long.MAX_VALUE;
        for (long wm : currentWatermarks) {
            if (wm < min) {
                min = wm;
            }
        }
        return min == Long.MAX_VALUE ? Long.MIN_VALUE : min;
    }

    private Optional<StreamElement> readSingleChannel() {
        try {
            StreamElement element = channels.get(0).read();
            if (element == null) {
                return Optional.empty();
            }
            // Track watermark for single channel
            if (element.isWatermark()) {
                Watermark wm = element.asWatermark();
                currentWatermarks[0] = wm.getTimestamp();
            }
            return Optional.of(element);
        } catch (InterruptedException e) {
            // P1-8: Align with multi-input interrupt handling — set interrupt flag
            // and return empty. The caller (processInputGate) breaks on empty, and
            // SubtaskTask's state machine (state==CANCELING after cancel() set the
            // flag and interrupted this thread) transitions to CANCELED — not FAILED,
            // not mistaken SUCCESS/EOS.
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<StreamElement> readMultiChannel() {
        retry:
        while (true) {
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
                    StreamElement element = channel.read(50, TimeUnit.MILLISECONDS);
                    if (element == null) {
                        if (channel.isFinished()) {
                            if (pendingBarrier != null) {
                                if (!barrierReceived[channelIndex]) {
                                    barrierReceived[channelIndex] = true;
                                    barriersRemaining--;
                                    Optional<StreamElement> result = checkBarrierAlignmentComplete();
                                    if (result.isPresent()) return result;
                                }
                            }
                        }
                        continue;
                    }

                    if (element.isCheckpointBarrier()) {
                        Optional<StreamElement> result = handleBarrierNonRecursive(channelIndex, element.asCheckpointBarrier());
                        if (result.isPresent()) return result;
                        continue retry;
                    }

                    if (element.isWatermark()) {
                        Optional<StreamElement> result = handleWatermarkNonRecursive(channelIndex, element.asWatermark());
                        if (result.isPresent()) return result;
                        continue retry;
                    }

                    return Optional.of(element);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }

            if (isAllFinished()) {
                return Optional.empty();
            }

            if (pendingBarrier != null && barriersRemaining > 0 && barrierAlignment) {
                long elapsed = System.currentTimeMillis() - alignmentStartTime;

                // Stage 43: aligned→unaligned fallback. When enabled and alignment
                // has not completed within unalignedThreshold, capture in-flight
                // channel data and complete the barrier immediately (no throw).
                if (unalignedCheckpointEnabled && elapsed > unalignedThreshold) {
                    return Optional.of(switchToUnalignedAndEmit());
                }

                if (elapsed > barrierAlignmentTimeout) {
                    throw new StreamException(ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT)
                            .param(ARG_TIMEOUT_MS, elapsed);
                }
            }

            LockSupport.parkNanos(10_000_000L);
        }
    }

    /**
     * Stage 43 (unaligned checkpoint): switches the in-flight checkpoint from
     * aligned to unaligned mode. Captures in-flight data from every channel
     * (per §2.11.2 semantics: aligned channels → post-barrier records; non-aligned
     * channels → all buffered records), resumes all blocked channels, resets
     * alignment state, and stashes the {@link ChannelState} for the task thread
     * to retrieve via {@link #consumePendingChannelState()}.
     *
     * @return the aligned/unaligned barrier to emit downstream
     */
    private CheckpointBarrier switchToUnalignedAndEmit() {
        long elapsed = System.currentTimeMillis() - alignmentStartTime;
        ChannelState channelState = new ChannelState();
        for (int i = 0; i < channels.size(); i++) {
            // barrierReceived[i] reflects whether channel i has delivered its barrier:
            //   true  → aligned channel, drain post-barrier records
            //   false → non-aligned channel, drain all buffered (pre-barrier) records
            java.util.List<StreamElement> captured = channels.get(i).captureInFlightData(barrierReceived[i]);
            if (captured != null && !captured.isEmpty()) {
                channelState.putRecords(i, captured);
            }
        }
        this.pendingChannelState = channelState;

        CheckpointBarrier barrier = pendingBarrier;
        long checkpointId = barrier != null ? barrier.getId() : -1L;
        // Resume all channels — unaligned mode never blocks on alignment.
        resumeConsumptionAll();
        resetBarrierState();

        LOG.info("Checkpoint {} switched to unaligned mode after {}ms (threshold={}ms); "
                        + "captured {} in-flight record(s) across {} channel(s)",
                checkpointId, elapsed, unalignedThreshold,
                channelState.getTotalRecordCount(), channels.size());
        return barrier;
    }

    /**
     * Stage 43: returns and clears the channel state captured during the most
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
     * Stage 43: whether aligned→unaligned fallback is enabled for this gate.
     */
    public boolean isUnalignedCheckpointEnabled() {
        return unalignedCheckpointEnabled;
    }

    /**
     * Stage 43: the aligned→unaligned mode-switch threshold in ms.
     */
    public long getUnalignedThreshold() {
        return unalignedThreshold;
    }

    /**
     * Stage 43 (unaligned checkpoint recovery): injects previously captured
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
        if (!barrierReceived[channelIndex]) {
            barrierReceived[channelIndex] = true;
            if (barrierAlignment) {
                blockConsumption(channelIndex);
            }
            if (pendingBarrier == null) {
                pendingBarrier = barrier;
                barriersRemaining = channels.size();
                alignmentStartTime = System.currentTimeMillis();
            }
            barriersRemaining--;

            if (!barrierAlignment) {
                if (!barrierEmitted) {
                    barrierEmitted = true;
                    if (barriersRemaining <= 0) {
                        resetBarrierState();
                    }
                    return Optional.of(barrier);
                }
                if (barriersRemaining <= 0) {
                    resetBarrierState();
                }
                return Optional.empty();
            }

            if (barriersRemaining <= 0) {
                CheckpointBarrier aligned = pendingBarrier;
                resumeConsumptionAll();
                resetBarrierState();
                return Optional.of(aligned);
            }
        } else {
            if (pendingBarrier != null && barrier.getId() != pendingBarrier.getId()) {
                throw new StreamException(ERR_STREAM_CHECKPOINT_ABORTED).param(ARG_REASON,
                        "Overlapping checkpoint barrier: expected " + pendingBarrier.getId()
                                + " but got " + barrier.getId() + " on channel " + channelIndex);
            }
        }

        return Optional.empty();
    }

    private Optional<StreamElement> handleWatermarkNonRecursive(int channelIndex, Watermark watermark) {
        long oldWatermark = currentWatermarks[channelIndex];
        if (watermark.getTimestamp() <= oldWatermark) {
            return Optional.empty();
        }
        currentWatermarks[channelIndex] = watermark.getTimestamp();

        long oldMin = minWatermarkExcluding(channelIndex, oldWatermark);
        long newMin = getCurrentWatermark();

        if (newMin > oldMin) {
            return Optional.of(new Watermark(newMin));
        }

        return Optional.empty();
    }

    private long minWatermarkExcluding(int excludeIndex, long oldValue) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < currentWatermarks.length; i++) {
            long val = (i == excludeIndex) ? oldValue : currentWatermarks[i];
            if (val < min) {
                min = val;
            }
        }
        return min == Long.MAX_VALUE ? Long.MIN_VALUE : min;
    }

    private void resetBarrierState() {
        for (int i = 0; i < barrierReceived.length; i++) {
            barrierReceived[i] = false;
        }
        pendingBarrier = null;
        barriersRemaining = 0;
        barrierEmitted = false;
        alignmentStartTime = 0;
    }

    private Optional<StreamElement> checkBarrierAlignmentComplete() {
        if (barrierAlignment && barriersRemaining <= 0 && pendingBarrier != null) {
            CheckpointBarrier aligned = pendingBarrier;
            resumeConsumptionAll();
            resetBarrierState();
            return Optional.of(aligned);
        }
        if (!barrierAlignment && barriersRemaining <= 0 && pendingBarrier != null) {
            resetBarrierState();
        }
        return Optional.empty();
    }
}
