/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.commons.partition.IPartitioner;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes stream elements to one or more downstream {@link ResultPartition} instances.
 *
 * <p>When an {@link IPartitioner} is provided, records are routed to a specific
 * partition based on the partitioning logic. Watermarks and barriers are broadcast
 * to all partitions.
 *
 * <p>Without a partitioner (single downstream), all elements are forwarded to the
 * sole partition.
 */
public class RecordWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(RecordWriter.class);

    private final ResultPartition[] partitions;
    private final IPartitioner<T> partitioner;

    /**
     * Creates a RecordWriter that forwards to a single partition.
     *
     * @param partition the single downstream partition
     */
    public RecordWriter(ResultPartition partition) {
        if (partition == null) {
            throw new IllegalArgumentException("ResultPartition must not be null");
        }
        this.partitions = new ResultPartition[]{partition};
        this.partitioner = null;
    }

    /**
     * Creates a RecordWriter with multiple partitions and an optional partitioner.
     *
     * @param partitions  the downstream partitions (must not be null or empty)
     * @param partitioner optional partitioner for record routing; null means forward to partition 0
     */
    public RecordWriter(ResultPartition[] partitions, IPartitioner<T> partitioner) {
        if (partitions == null || partitions.length == 0) {
            throw new IllegalArgumentException("Partitions must not be null or empty");
        }
        this.partitions = partitions;
        this.partitioner = partitioner;
    }

    /**
     * Emits a stream record to the appropriate downstream partition.
     *
     * @param record the record to emit
     */
    @SuppressWarnings("unchecked")
    public void emit(StreamRecord<T> record) {
        int targetChannel = selectChannel(record);
        try {
            partitions[targetChannel].write(record);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while writing record", e);
        }
    }

    /**
     * Broadcasts a watermark to all downstream partitions.
     *
     * @param watermark the watermark to emit
     */
    public void emitWatermark(Watermark watermark) {
        for (ResultPartition partition : partitions) {
            try {
                partition.write(watermark);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while writing watermark", e);
            }
        }
    }

    /**
     * Broadcasts a checkpoint barrier to all downstream partitions.
     *
     * @param barrier the barrier to emit
     */
    public void emitBarrier(CheckpointBarrier barrier) {
        for (ResultPartition partition : partitions) {
            try {
                partition.write(barrier);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while writing barrier", e);
            }
        }
    }

    /**
     * Writes an arbitrary StreamElement to the selected partition.
     * Used for watermark status and other element types.
     *
     * @param element the element to write
     */
    public void emitElement(StreamElement element) {
        int channel = 0;
        if (partitioner != null) {
            channel = 0;
        }
        try {
            partitions[channel].write(element);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while writing element", e);
        }
    }

    /**
     * Closes all downstream partitions to signal end-of-stream.
     */
    public void close() {
        for (ResultPartition partition : partitions) {
            partition.close();
        }
    }

    /**
     * Returns the number of downstream partitions.
     */
    public int getNumberOfPartitions() {
        return partitions.length;
    }

    private int selectChannel(StreamRecord<T> record) {
        if (partitioner != null) {
            int channel = partitioner.partition(record.getValue(), partitions.length);
            return Math.abs(channel % partitions.length);
        }
        return 0;
    }
}
