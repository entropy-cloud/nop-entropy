/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.concurrent.TimeUnit;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Consumer-side handle to a {@link ResultPartition}. Wraps a single partition
 * for reading stream elements from an upstream task.
 */
public class InputChannel {

    private final ResultPartition partition;

    public InputChannel(ResultPartition partition) {
        if (partition == null) {
            throw new StreamException("ResultPartition must not be null");
        }
        this.partition = partition;
    }

    /**
     * Reads the next element from the underlying partition (blocking).
     *
     * @return the next element, or null if end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    public StreamElement read() throws InterruptedException {
        return partition.read();
    }

    /**
     * Reads with a timeout.
     *
     * @param timeout maximum wait time
     * @param unit    time unit
     * @return the next element, or null on timeout / end-of-stream
     * @throws InterruptedException if interrupted while waiting
     */
    public StreamElement read(long timeout, TimeUnit unit) throws InterruptedException {
        return partition.read(timeout, unit);
    }

    /**
     * Returns whether the upstream producer has finished.
     */
    public boolean isFinished() {
        return partition.isFinished();
    }

    public ResultPartition getPartition() {
        return partition;
    }
}
