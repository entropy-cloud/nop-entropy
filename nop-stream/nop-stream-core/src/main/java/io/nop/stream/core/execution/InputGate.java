/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

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

    private final List<InputChannel> channels;
    private final long[] currentWatermarks;
    private final EdgeConfig edgeConfig;
    private final boolean barrierAlignment;

    // Barrier alignment state
    private final boolean[] barrierReceived;
    private CheckpointBarrier pendingBarrier;
    private int barriersRemaining;
    private boolean barrierEmitted;
    private long alignmentStartTime;
    private final long barrierAlignmentTimeout;

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
        if (channels == null || channels.isEmpty()) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "channels");
        }
        this.channels = new ArrayList<>(channels);
        this.edgeConfig = edgeConfig;
        this.barrierAlignment = barrierAlignment;
        this.barrierAlignmentTimeout = barrierAlignmentTimeout;
        this.currentWatermarks = new long[channels.size()];
        for (int i = 0; i < currentWatermarks.length; i++) {
            currentWatermarks[i] = Long.MIN_VALUE;
        }
        this.barrierReceived = new boolean[channels.size()];
        this.barriersRemaining = 0;
        this.pendingBarrier = null;
        this.barrierEmitted = false;
        this.currentChannelIndex = 0;
        this.alignmentStartTime = 0;
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
        this.currentWatermarks = new long[]{Long.MIN_VALUE};
        this.barrierReceived = new boolean[1];
        this.barriersRemaining = 0;
        this.pendingBarrier = null;
        this.currentChannelIndex = 0;
        this.alignmentStartTime = 0;
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
            Thread.currentThread().interrupt();
            throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "InputGate interrupted");
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

                if (barrierAlignment && barrierReceived[channelIndex]) {
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
                                    if (result != null) return result;
                                }
                            }
                        }
                        continue;
                    }

                    if (element.isCheckpointBarrier()) {
                        Optional<StreamElement> result = handleBarrierNonRecursive(channelIndex, element.asCheckpointBarrier());
                        if (result != null) return result;
                        continue retry;
                    }

                    if (element.isWatermark()) {
                        Optional<StreamElement> result = handleWatermarkNonRecursive(channelIndex, element.asWatermark());
                        if (result != null) return result;
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
                if (elapsed > barrierAlignmentTimeout) {
                    throw new StreamException(ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT)
                            .param(ARG_TIMEOUT_MS, elapsed);
                }
            }

            LockSupport.parkNanos(10_000_000L);
        }
    }

    private Optional<StreamElement> handleBarrierNonRecursive(int channelIndex, CheckpointBarrier barrier) {
        if (!barrierReceived[channelIndex]) {
            barrierReceived[channelIndex] = true;
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
                return null;
            }

            if (barriersRemaining <= 0) {
                CheckpointBarrier aligned = pendingBarrier;
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

        return null;
    }

    private Optional<StreamElement> handleWatermarkNonRecursive(int channelIndex, Watermark watermark) {
        long oldWatermark = currentWatermarks[channelIndex];
        if (watermark.getTimestamp() <= oldWatermark) {
            return null;
        }
        currentWatermarks[channelIndex] = watermark.getTimestamp();

        long oldMin = minWatermarkExcluding(channelIndex, oldWatermark);
        long newMin = getCurrentWatermark();

        if (newMin > oldMin) {
            return Optional.of(new Watermark(newMin));
        }

        return null;
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
            resetBarrierState();
            return Optional.of(aligned);
        }
        if (!barrierAlignment && barriersRemaining <= 0 && pendingBarrier != null) {
            resetBarrierState();
        }
        return null;
    }
}
