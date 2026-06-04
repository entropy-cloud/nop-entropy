/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

/**
 * A bounded buffer that holds {@link StreamElement} instances for inter-task data exchange.
 *
 * <p>ResultPartition is the producer side of the data exchange channel. It wraps a
 * {@link LinkedBlockingQueue} and provides thread-safe read/write operations for
 * single-producer single-consumer scenarios.
 *
 * <p>When the producer is done, it calls {@link #close()} to signal end-of-stream.
 * The consumer detects this via {@link #isFinished()} or by receiving an empty
 * result from {@link #read()} after the partition is closed and drained.
 */
public class ResultPartition implements IWriteStatus {

    private static final Logger LOG = LoggerFactory.getLogger(ResultPartition.class);

    /** Sentinel object placed into the queue to signal end-of-stream. */
    private static final StreamElement END_OF_STREAM = new StreamElement() {};

    /** Default bounded capacity for the internal queue. */
    public static final int DEFAULT_CAPACITY = 1024;

    private final LinkedBlockingQueue<StreamElement> queue;
    private volatile boolean finished;

    /**
     * Creates a ResultPartition with the default capacity (1024).
     */
    public ResultPartition() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a ResultPartition with the specified capacity.
     *
     * @param capacity the bounded queue capacity (must be positive)
     * @throws IllegalArgumentException if capacity is not positive
     */
    public ResultPartition(int capacity) {
        if (capacity <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_ARG_NAME, "capacity").param(ARG_DETAIL, "must be positive, got: " + capacity);
        }
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.finished = false;
    }

    /**
     * Writes a stream element into the partition, blocking if the queue is full.
     *
     * @param element the element to write (must not be null)
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws IllegalStateException if the partition is already finished
     */
    public void write(StreamElement element) throws InterruptedException {
        if (element == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "element");
        }
        if (finished) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "Cannot write to a finished ResultPartition");
        }
        queue.put(element);
    }

    /**
     * Reads the next stream element from the partition, blocking until one is available.
     *
     * <p>Returns {@code null} if the partition is finished and no more elements remain.
     *
     * @return the next element, or null if end-of-stream
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public StreamElement read() throws InterruptedException {
        StreamElement element = queue.take();
        if (element == END_OF_STREAM) {
            return null;
        }
        return element;
    }

    /**
     * Reads the next stream element with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the timeout
     * @return the next element, or null if timeout elapsed or end-of-stream reached
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
        StreamElement element = queue.poll(timeout, unit);
        if (element == null) {
            // Timeout - return null but don't mark as finished
            return null;
        }
        if (element == END_OF_STREAM) {
            return null;
        }
        return element;
    }

    /**
     * Signals that the producer has finished writing.
     *
     * <p>This method places a sentinel value into the queue so that any consumer
     * currently blocked on {@link #read()} will wake up and detect end-of-stream.
     */
    public void close() {
        finished = true;
        queue.drainTo(new ArrayList<>());
        try {
            queue.put(END_OF_STREAM);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            queue.offer(END_OF_STREAM);
        }
    }

    /**
     * Returns whether this partition has been closed by the producer.
     * Note that elements may still remain in the queue after close.
     *
     * @return true if the producer has called close()
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * Marks this partition as finished. Used by remote partition implementations
     * that do not use the internal queue.
     */
    protected void markFinished() {
        this.finished = true;
    }

    /**
     * Returns the current number of elements waiting in the queue.
     *
     * @return approximate queue size
     */
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isBackpressured() {
        return queue.remainingCapacity() < (queue.size() * 0.2 + 1);
    }

    @Override
    public int getAvailableCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public int getTotalCapacity() {
        return queue.size() + queue.remainingCapacity();
    }
}
