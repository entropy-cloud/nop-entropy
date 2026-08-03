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
import java.util.LinkedHashMap;
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

    /**
     * Stage 45 (multi-epoch): per-barrier alignment state, keyed by checkpoint id.
     * Insertion order is barrier-arrival order (barriers on a single channel are
     * strictly ordered, so the oldest in-flight barrier is the one currently
     * aligning). Replaces the legacy single {@code pendingBarrier} /
     * {@code barrierReceived[]} / {@code barriersRemaining} fields so that
     * overlapping barrier ids no longer throw and an aborted epoch's straggling
     * barrier is discarded instead of corrupting the next epoch's alignment
     * (design §2.8.1 D1).
     */
    private final LinkedHashMap<Long, BarrierAlignment> inFlightAlignments = new LinkedHashMap<>();

    /**
     * Stage 45: checkpoint ids whose alignment has been aborted. A barrier element
     * carrying one of these ids is silently discarded (the abort was already
     * signaled via the control channel; a late in-data-flow barrier must not
     * corrupt subsequent epochs). Bounded growth: cleared opportunistically when
     * an alignment completes at or above the aborted id.
     */
    private final Set<Long> abortedBarriers = new HashSet<>();

    /**
     * Channels currently blocked during aligned barrier alignment (union across all
     * in-flight alignments). Maintained in lockstep with each
     * {@link BarrierAlignment#blockedChannels} so {@link #resumeConsumptionAll()}
     * and {@link #blockConsumption(int)} keep working for external callers.
     */
    private final Set<Integer> blockedChannels = new HashSet<>();

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
        this.currentChannelIndex = 0;
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
                            // Stage 45: a finished channel will never deliver more
                            // barriers, so mark it as received for every in-flight
                            // alignment and complete any alignment that becomes
                            // satisfied (replaces the legacy single-pending check).
                            Optional<StreamElement> result = markFinishedChannel(channelIndex);
                            if (result.isPresent()) return result;
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

            // Stage 43/45: timeout / aligned→unaligned fallback applies to the
            // oldest in-flight alignment (the one currently aligning). Aligned
            // barriers serialize via channel blocking, so there is at most one
            // actively-aligning barrier at a time.
            BarrierAlignment oldest = oldestAligning();
            if (oldest != null && barrierAlignment
                    && oldest.receivedChannels.size() < channels.size()) {
                long elapsed = System.currentTimeMillis() - oldest.startTime;

                if (unalignedCheckpointEnabled && elapsed > unalignedThreshold) {
                    return Optional.of(switchToUnalignedAndEmit(oldest));
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
     * Stage 43/45 (unaligned checkpoint): switches the oldest in-flight checkpoint
     * from aligned to unaligned mode. Captures in-flight data from every channel
     * (per §2.11.2 semantics: aligned channels → post-barrier records; non-aligned
     * channels → all buffered records), resumes the channels this barrier blocked,
     * removes the alignment state, and stashes the {@link ChannelState} for the task
     * thread to retrieve via {@link #consumePendingChannelState()}.
     *
     * <p>Stage 45 (design §2.8.1 D4): unaligned stays single-in-flight. Aligned
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
            // D4: unaligned multi-in-flight is a Stage 47 successor; fail-fast here
            // so an unsupported config never silently captures state for the wrong epoch.
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_REASON,
                    "Unaligned checkpoint is enabled and multiple barriers are in-flight (ids="
                            + new ArrayList<>(inFlightAlignments.keySet())
                            + "); unaligned multi-in-flight is not supported (Stage 47 successor)");
        }
        long elapsed = System.currentTimeMillis() - align.startTime;
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
        long id = barrier.getId();

        if (abortedBarriers.contains(id)) {
            // Stage 45: late arrival of an aborted checkpoint's barrier. The abort
            // was already signaled via the control channel; discard the straggler
            // so it does not start a spurious alignment or corrupt the next epoch.
            if (LOG.isDebugEnabled()) {
                LOG.debug("Discarding barrier {} on channel {} (epoch aborted)", id, channelIndex);
            }
            return Optional.empty();
        }

        BarrierAlignment align = inFlightAlignments.get(id);
        if (align == null) {
            align = new BarrierAlignment(id, barrier, System.currentTimeMillis());
            inFlightAlignments.put(id, align);
        }

        if (align.receivedChannels.contains(channelIndex)) {
            // Duplicate barrier for the same id on the same channel: ignore.
            return Optional.empty();
        }
        align.receivedChannels.add(channelIndex);

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
     * Stage 45: marks a finished channel as having delivered every in-flight
     * barrier (it will never send more data), then completes any alignment that
     * becomes satisfied. Replaces the legacy single-pending finished-channel check.
     */
    private Optional<StreamElement> markFinishedChannel(int channelIndex) {
        CheckpointBarrier completed = null;
        for (BarrierAlignment align : new ArrayList<>(inFlightAlignments.values())) {
            if (!align.receivedChannels.contains(channelIndex)) {
                align.receivedChannels.add(channelIndex);
                boolean fullyReceived = align.receivedChannels.size() >= channels.size();
                if (barrierAlignment && fullyReceived && completed == null) {
                    // Barriers complete in id order; emit the lowest completed one.
                    inFlightAlignments.remove(align.checkpointId);
                    for (int c : align.blockedChannels) {
                        blockedChannels.remove(c);
                    }
                    cleanupAbortedBarriersUpTo(align.checkpointId);
                    completed = align.firstBarrier;
                } else if (!barrierAlignment && fullyReceived) {
                    inFlightAlignments.remove(align.checkpointId);
                }
            }
        }
        return completed != null ? Optional.of(completed) : Optional.empty();
    }

    /**
     * Stage 45: returns the oldest in-flight alignment (insertion order), or null.
     * This is the barrier currently aligning (aligned serialization guarantees only
     * one is actively aligning at a time).
     */
    private BarrierAlignment oldestAligning() {
        for (BarrierAlignment a : inFlightAlignments.values()) {
            return a;
        }
        return null;
    }

    /**
     * Stage 45: drops aborted-barrier markers that can no longer be observed
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
     * Stage 45: aborts alignment for a specific checkpoint id (epoch-precise).
     * Resumes channels this barrier had blocked and records the id so a straggling
     * in-data-flow barrier for the same epoch is discarded instead of starting a
     * new alignment. Other in-flight epochs are undisturbed.
     */
    public void abortBarrierAlignment(long checkpointId) {
        BarrierAlignment removed = inFlightAlignments.remove(checkpointId);
        if (removed != null) {
            for (int c : removed.blockedChannels) {
                blockedChannels.remove(c);
            }
            LOG.debug("Aborted alignment for checkpoint {} (resumed {} blocked channel(s))",
                    checkpointId, removed.blockedChannels.size());
        }
        abortedBarriers.add(checkpointId);
    }

    /**
     * Stage 45: snapshot of in-flight barrier ids (for tests / observability).
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

    /**
     * Stage 45: per-barrier alignment state. Each in-flight checkpoint owns an
     * independent record of which channels have delivered its barrier, which
     * channels it has blocked, and when alignment started (for timeout/unaligned).
     */
    private static final class BarrierAlignment {
        final long checkpointId;
        final CheckpointBarrier firstBarrier;
        final Set<Integer> receivedChannels = new HashSet<>();
        final Set<Integer> blockedChannels = new HashSet<>();
        final long startTime;
        boolean emitted; // AT_LEAST_ONCE: first-emit tracking

        BarrierAlignment(long checkpointId, CheckpointBarrier firstBarrier, long startTime) {
            this.checkpointId = checkpointId;
            this.firstBarrier = firstBarrier;
            this.startTime = startTime;
        }
    }
}
