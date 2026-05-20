/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Manages multiple {@link InputChannel} instances and provides merged reading
 * with barrier alignment and watermark merging.
 *
 * <p><strong>Barrier Alignment:</strong> When a barrier is received on one channel,
 * that channel is blocked until barriers arrive on all other channels. Once all
 * barriers are collected, they are released together as a single aligned barrier.
 *
 * <p><strong>Watermark Merging:</strong> Tracks the watermark per channel and only
 * emits the minimum watermark when it advances. This ensures downstream operators
 * see a monotonically increasing watermark that is the min of all inputs.
 */
public class InputGate {

    private static final Logger LOG = LoggerFactory.getLogger(InputGate.class);

    private final List<InputChannel> channels;
    private final long[] currentWatermarks;

    // Barrier alignment state
    private final boolean[] barrierReceived;
    private CheckpointBarrier pendingBarrier;
    private int barriersRemaining;

    private int currentChannelIndex;

    public InputGate(List<InputChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("Channels must not be null or empty");
        }
        this.channels = new ArrayList<>(channels);
        this.currentWatermarks = new long[channels.size()];
        for (int i = 0; i < currentWatermarks.length; i++) {
            currentWatermarks[i] = Long.MIN_VALUE;
        }
        this.barrierReceived = new boolean[channels.size()];
        this.barriersRemaining = 0;
        this.pendingBarrier = null;
        this.currentChannelIndex = 0;
    }

    /**
     * Creates an InputGate with a single channel.
     */
    public InputGate(InputChannel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("InputChannel must not be null");
        }
        this.channels = new ArrayList<>();
        this.channels.add(channel);
        this.currentWatermarks = new long[]{Long.MIN_VALUE};
        this.barrierReceived = new boolean[1];
        this.barriersRemaining = 0;
        this.pendingBarrier = null;
        this.currentChannelIndex = 0;
    }

    /**
     * Reads the next element from the input channels, performing round-robin
     * selection and barrier alignment.
     *
     * <p>For single-channel gates, delegates directly to the channel.
     * For multi-channel gates, uses round-robin with barrier alignment:
     * channels that have delivered a barrier are skipped until all channels
     * have delivered their barriers.
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
            return Optional.empty();
        }
    }

    private Optional<StreamElement> readMultiChannel() {
        int channelsChecked = 0;
        int totalChannels = channels.size();

        while (channelsChecked < totalChannels) {
            int channelIndex = currentChannelIndex % totalChannels;
            currentChannelIndex = (currentChannelIndex + 1) % totalChannels;
            channelsChecked++;

            // Skip channels that are blocked by barrier alignment
            if (barrierReceived[channelIndex]) {
                continue;
            }

            InputChannel channel = channels.get(channelIndex);
            try {
                StreamElement element = channel.read(50, TimeUnit.MILLISECONDS);
                if (element == null) {
                    // Timeout or end-of-stream on this channel
                    if (channel.isFinished()) {
                        barrierReceived[channelIndex] = true;
                        barriersRemaining--;
                        checkBarrierAlignmentComplete();
                    }
                    continue;
                }

                if (element.isCheckpointBarrier()) {
                    return handleBarrier(channelIndex, element.asCheckpointBarrier());
                }

                if (element.isWatermark()) {
                    return handleWatermark(channelIndex, element.asWatermark());
                }

                return Optional.of(element);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }

        // All channels checked but nothing available; check if all finished
        if (isAllFinished()) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private Optional<StreamElement> handleBarrier(int channelIndex, CheckpointBarrier barrier) {
        if (!barrierReceived[channelIndex]) {
            barrierReceived[channelIndex] = true;
            if (pendingBarrier == null) {
                pendingBarrier = barrier;
                barriersRemaining = channels.size();
            }
            barriersRemaining--;

            if (barriersRemaining <= 0) {
                // All barriers received - reset and emit aligned barrier
                CheckpointBarrier aligned = pendingBarrier;
                resetBarrierState();
                return Optional.of(aligned);
            }
        }

        // Need to wait for more barriers, try reading again
        return readMultiChannel();
    }

    private Optional<StreamElement> handleWatermark(int channelIndex, Watermark watermark) {
        long oldWatermark = currentWatermarks[channelIndex];
        currentWatermarks[channelIndex] = watermark.getTimestamp();

        long oldMin = minWatermarkExcluding(channelIndex, oldWatermark);
        long newMin = getCurrentWatermark();

        if (newMin > oldMin) {
            return Optional.of(new Watermark(newMin));
        }

        // Watermark did not advance the minimum, skip and read next
        return readMultiChannel();
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
    }

    private void checkBarrierAlignmentComplete() {
        if (barriersRemaining <= 0 && pendingBarrier != null) {
            resetBarrierState();
        }
    }
}
