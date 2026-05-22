/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.common.eventtime.TimestampAssigner;
import io.nop.stream.core.common.eventtime.WatermarkGenerator;
import io.nop.stream.core.common.eventtime.WatermarkOutput;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

public class TimestampsAndWatermarksOperator<T>
        extends AbstractStreamOperator<T>
        implements OneInputStreamOperator<T, T> {

    private static final long serialVersionUID = 1L;

    private static final long INITIAL_TIME = Long.MIN_VALUE + 1;
    private static final long DEFAULT_WATERMARK_INTERVAL_MS = 200;

    private final WatermarkStrategy<T> watermarkStrategy;
    private final long watermarkInterval;
    private transient TimestampAssigner<T> timestampAssigner;
    private transient WatermarkGenerator<T> watermarkGenerator;
    private transient long lastWatermarkTimestamp;
    private transient long nextWatermarkTime;
    private transient boolean idle;

    public TimestampsAndWatermarksOperator(WatermarkStrategy<T> watermarkStrategy) {
        this(watermarkStrategy, DEFAULT_WATERMARK_INTERVAL_MS);
    }

    public TimestampsAndWatermarksOperator(WatermarkStrategy<T> watermarkStrategy, long watermarkInterval) {
        this.watermarkStrategy = watermarkStrategy;
        this.watermarkInterval = watermarkInterval;
    }

    @Override
    public void open() throws Exception {
        super.open();
        this.timestampAssigner = watermarkStrategy.createTimestampAssigner(() -> null);
        this.watermarkGenerator = watermarkStrategy.createWatermarkGenerator(() -> null);
        this.lastWatermarkTimestamp = INITIAL_TIME;
        this.nextWatermarkTime = INITIAL_TIME;
        this.idle = false;
    }

    @Override
    public void processElement(StreamRecord<T> element) throws Exception {
        long recordTimestamp = element.hasTimestamp() ? element.getTimestamp() : TimestampAssigner.NO_TIMESTAMP;
        long extractedTs = timestampAssigner.extractTimestamp(element.getValue(), recordTimestamp);
        element.setTimestamp(extractedTs);

        watermarkGenerator.onEvent(element.getValue(), extractedTs, new OperatorWatermarkOutput());

        output.collect(element);

        long now = System.currentTimeMillis();
        if (now >= nextWatermarkTime) {
            watermarkGenerator.onPeriodicEmit(new OperatorWatermarkOutput());
            nextWatermarkTime = now + watermarkInterval;
        }
    }

    @Override
    public void processWatermark(Watermark mark) throws Exception {
        if (mark.getTimestamp() > lastWatermarkTimestamp) {
            lastWatermarkTimestamp = mark.getTimestamp();
            output.emitWatermark(mark);
        }
    }

    @Override
    public void finish() throws Exception {
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

        @Override
        public void markIdle() {
            idle = true;
        }

        @Override
        public void markActive() {
            idle = false;
        }
    }
}
