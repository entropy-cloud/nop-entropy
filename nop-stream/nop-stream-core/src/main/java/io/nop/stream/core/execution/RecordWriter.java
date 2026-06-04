/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.commons.partition.IPartitioner;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.execution.flow.EdgeConfig;
import io.nop.stream.core.execution.flow.FlowControlPolicy;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

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
    private final EdgeConfig edgeConfig;
    private final PartitionRouter partitionRouter;
    private final boolean isBroadcast;

    /**
     * Creates a RecordWriter that forwards to a single partition.
     *
     * @param partition the single downstream partition
     */
    public RecordWriter(ResultPartition partition) {
        this(partition, null);
    }

    /**
     * Creates a RecordWriter that forwards to a single partition with optional EdgeConfig.
     *
     * @param partition  the single downstream partition
     * @param edgeConfig optional edge configuration for flow control (nullable)
     */
    public RecordWriter(ResultPartition partition, EdgeConfig edgeConfig) {
        if (partition == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partition");
        }
        this.partitions = new ResultPartition[]{partition};
        this.partitioner = null;
        this.edgeConfig = edgeConfig;
        this.partitionRouter = null;
        this.isBroadcast = false;
        validateFlowControlPolicy();
    }

    /**
     * Creates a RecordWriter with multiple partitions and an optional partitioner.
     *
     * @param partitions  the downstream partitions (must not be null or empty)
     * @param partitioner optional partitioner for record routing; null means forward to partition 0
     */
    public RecordWriter(ResultPartition[] partitions, IPartitioner<T> partitioner) {
        this(partitions, partitioner, null);
    }

    /**
     * Creates a RecordWriter with multiple partitions, an optional partitioner, and edge configuration.
     *
     * @param partitions  the downstream partitions (must not be null or empty)
     * @param partitioner optional partitioner for record routing; null means forward to partition 0
     * @param edgeConfig  optional edge configuration for flow control (nullable)
     */
    public RecordWriter(ResultPartition[] partitions, IPartitioner<T> partitioner, EdgeConfig edgeConfig) {
        this(partitions, partitioner, edgeConfig, null);
    }

    /**
     * Creates a RecordWriter with multiple partitions, an optional partitioner, edge configuration,
     * and an explicit {@link PartitionRouter} for advanced partition routing.
     *
     * <p>When a PartitionRouter is provided, it takes precedence over the partitioner for
     * channel selection. This enables HASH/FORWARD/REBALANCE routing strategies independent
     * of the IPartitioner interface.
     *
     * @param partitions      the downstream partitions (must not be null or empty)
     * @param partitioner     optional partitioner for record routing (nullable)
     * @param edgeConfig      optional edge configuration for flow control (nullable)
     * @param partitionRouter explicit partition router (nullable; falls back to partitioner)
     */
    public RecordWriter(ResultPartition[] partitions, IPartitioner<T> partitioner,
                        EdgeConfig edgeConfig, PartitionRouter partitionRouter) {
        if (partitions == null || partitions.length == 0) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "partitions");
        }
        this.partitions = partitions;
        this.partitioner = partitioner;
        this.edgeConfig = edgeConfig;
        this.partitionRouter = partitionRouter;
        this.isBroadcast = partitionRouter instanceof BroadcastPartitionRouter;
        validateFlowControlPolicy();
    }

    /**
     * Validates that the flow control policy is supported in the local runtime.
     *
     * <p>BLOCKING_QUEUE (the default) is the only supported policy for local execution.
     * CREDIT_BASED and ACK_WINDOW are reserved for distributed runtime and will throw
     * UnsupportedOperationException if used locally.
     */
    private void validateFlowControlPolicy() {
        if (edgeConfig != null) {
            FlowControlPolicy policy = edgeConfig.getFlowControlPolicy();
            if (policy != FlowControlPolicy.BLOCKING_QUEUE) {
                throw new UnsupportedOperationException(
                        "Flow control policy " + policy + " is not supported in local runtime");
            }
        }
    }

    /**
     * Emits a stream record to the appropriate downstream partition.
     *
     * @param record the record to emit
     */
    @SuppressWarnings("unchecked")
    public void emit(StreamRecord<T> record) {
        if (isBroadcast) {
            int delivered = 0;
            for (ResultPartition partition : partitions) {
                try {
                    partition.write(record);
                    delivered++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("Interrupted during broadcast emit: {}/{} partitions delivered",
                            delivered, partitions.length);
                    throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e).param(ARG_DETAIL, "record");
                }
            }
        } else {
            int targetChannel = selectChannel(record);
            try {
                partitions[targetChannel].write(record);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e).param(ARG_DETAIL, "record");
            }
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
                throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e).param(ARG_DETAIL, "watermark");
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
                throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e).param(ARG_DETAIL, "barrier");
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
        try {
            for (ResultPartition partition : partitions) {
                partition.write(element);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e).param(ARG_DETAIL, "element");
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
     * Returns the write status for the first (or only) downstream partition.
     */
    public IWriteStatus getOutputStatus() {
        return partitions[0];
    }

    /**
     * Returns the number of downstream partitions.
     */
    public int getNumberOfPartitions() {
        return partitions.length;
    }

    private int selectChannel(StreamRecord<T> record) {
        // Priority 1: Explicit PartitionRouter (HASH/FORWARD/REBALANCE strategies)
        if (partitionRouter != null) {
            return partitionRouter.selectChannel(record);
        }
        // Priority 2: Legacy IPartitioner
        if (partitioner != null) {
            int channel = partitioner.partition(record.getValue(), partitions.length);
            return Math.floorMod(channel, partitions.length);
        }
        return 0;
    }
}
