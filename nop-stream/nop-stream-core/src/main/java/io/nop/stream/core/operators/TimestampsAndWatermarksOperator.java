/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.api.core.time.CoreMetrics;
import java.util.concurrent.ScheduledFuture;

import io.nop.stream.core.common.eventtime.TimestampAssigner;
import io.nop.stream.core.common.eventtime.WatermarkGenerator;
import io.nop.stream.core.common.eventtime.WatermarkOutput;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimestampsAndWatermarksOperator<T>
        extends AbstractStreamOperator<T>
        implements OneInputStreamOperator<T, T> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(TimestampsAndWatermarksOperator.class);

    private static final long INITIAL_TIME = Long.MIN_VALUE + 1;
    private static final long DEFAULT_WATERMARK_INTERVAL_MS = 200;

    private final WatermarkStrategy<T> watermarkStrategy;
    private final long watermarkInterval;
    private transient TimestampAssigner<T> timestampAssigner;
    private transient WatermarkGenerator<T> watermarkGenerator;
    private transient volatile long lastWatermarkTimestamp;
    private transient long nextWatermarkTime;
    private transient long lastEmitTime;
    private transient volatile boolean idle;
    private transient ScheduledFuture<?> watermarkTimerFuture;

    public TimestampsAndWatermarksOperator(WatermarkStrategy<T> watermarkStrategy) {
        this(watermarkStrategy, DEFAULT_WATERMARK_INTERVAL_MS);
    }

    public TimestampsAndWatermarksOperator(WatermarkStrategy<T> watermarkStrategy, long watermarkInterval) {
        this.watermarkStrategy = watermarkStrategy;
        this.watermarkInterval = watermarkInterval;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Watermark operator: the watermark strategy (immutable configuration)
     * is shared across subtasks. Per-subtask mutable state (timestamp assigner,
     * generator, last watermark timestamp, scheduled timer future) is left null
     * and re-initialized by {@link #open()}.
     */
    @Override
    public TimestampsAndWatermarksOperator<T> copyForSubtask() {
        return new TimestampsAndWatermarksOperator<>(watermarkStrategy, watermarkInterval);
    }

    @Override
    public void open() throws Exception {
        super.open();
        this.timestampAssigner = watermarkStrategy.createTimestampAssigner(() -> null);
        this.watermarkGenerator = watermarkStrategy.createWatermarkGenerator(() -> null);
        this.lastWatermarkTimestamp = INITIAL_TIME;
        this.nextWatermarkTime = INITIAL_TIME;
        this.lastEmitTime = 0;
        this.idle = false;

        if (watermarkInterval > 0) {
            scheduleNextWatermarkTimer();
        }
    }

    private void scheduleNextWatermarkTimer() {
        if (processingTimeService != null) {
            long now = processingTimeService.getCurrentProcessingTime();
            watermarkTimerFuture = processingTimeService.registerTimer(
                    now + watermarkInterval,
                    this::onProcessingTimeCallback
            );
        }
    }

    private void onProcessingTimeCallback(long timestamp) throws Exception {
        watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
        scheduleNextWatermarkTimer();
    }

    @Override
    public void processElement(StreamRecord<T> element) throws Exception {
        this.idle = false;
        long recordTimestamp = element.hasTimestamp() ? element.getTimestamp() : TimestampAssigner.NO_TIMESTAMP;
        long extractedTs = timestampAssigner.extractTimestamp(element.getValue(), recordTimestamp);
        element.setTimestamp(extractedTs);

        watermarkGenerator.onEvent(element.getValue(), extractedTs, new OperatorWatermarkOutput());

        output.collect(element);

        long now = CoreMetrics.currentTimeMillis();

        boolean shouldEmit;
        if (watermarkInterval == 0) {
            shouldEmit = true;
        } else {
            shouldEmit = now >= nextWatermarkTime;
        }

        if (shouldEmit) {
            watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
            lastEmitTime = now;
            if (watermarkInterval > 0) {
                nextWatermarkTime = now + watermarkInterval;
            }
        }
    }

    /**
     * D-2 (plan 369 Phase 2, Flink 2.3 alignment decision): this operator is the SOLE
     * watermark authority for its pipeline segment — the generator's periodic/element-driven
     * emissions and an upstream watermark are two independent sources whose interleaving
     * could push an out-of-order watermark downstream. Upstream watermarks are therefore
     * IGNORED (no forwarding, no {@code lastWatermarkTimestamp} update); only the
     * end-of-stream {@link Watermark#MAX_WATERMARK} passes through (also emitted by
     * {@link #finish()}). This is a deliberate behavior change from the previous
     * forward-all-upstream-watermarks semantics and is pinned by the
     * {@code testProcessWatermarkIgnoresUpstream} /
     * {@code testProcessWatermarkForwardsOnlyMaxWatermark} cases of
     * {@code TestTimestampsAndWatermarksOperator}.
     */
    @Override
    public void processWatermark(Watermark mark) throws Exception {
        if (mark.getTimestamp() >= Watermark.MAX_WATERMARK.getTimestamp()) {
            output.emitWatermark(mark);
        }
    }

    @Override
    public void finish() throws Exception {
        if (watermarkTimerFuture != null) {
            watermarkTimerFuture.cancel(false);
            watermarkTimerFuture = null;
        }
        watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
        output.emitWatermark(Watermark.MAX_WATERMARK);
    }

    private class OperatorWatermarkOutput implements WatermarkOutput {

        @Override
        public void emitWatermark(Watermark watermark) {
            if (idle) return;
            long ts = watermark.getTimestamp();
            if (ts > lastWatermarkTimestamp) {
                lastWatermarkTimestamp = ts;
                output.emitWatermark(watermark);
            }
        }

        /**
         * AR-9 (plan 1326-2 Phase 4): idleness must CROSS task boundaries. The
         * generator's markIdle() previously only flipped the local flag — the IDLE
         * status died inside this operator, so a downstream task's InputGate kept
         * merging this channel's last watermark forever (event time pinned). Emit
         * the status downstream on the TRANSITION (no spam on repeated marks).
         */
        @Override
        public void markIdle() {
            if (!idle) {
                idle = true;
                output.emitWatermarkStatus(WatermarkStatus.IDLE);
            }
        }

        @Override
        public void markActive() {
            if (idle) {
                idle = false;
                output.emitWatermarkStatus(WatermarkStatus.ACTIVE);
            }
        }
    }
}
