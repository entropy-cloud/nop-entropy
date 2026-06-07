/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import java.util.Optional;

import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

/**
 * Reads stream elements from a single {@link InputChannel}.
 *
 * <p>For multi-input reading, use {@link InputGate} instead.
 */
public class RecordReader<T> {

    private final InputChannel channel;

    public RecordReader(InputChannel channel) {
        if (channel == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "channel");
        }
        this.channel = channel;
    }

    /**
     * Reads the next element from the input channel.
     *
     * @return an Optional containing the next element, or empty if end-of-stream
     */
    public Optional<StreamElement> read() {
        try {
            StreamElement element = channel.read();
            return Optional.ofNullable(element);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamException(ERR_STREAM_INTERRUPTED_WRITE, e);
        }
    }

    /**
     * Returns whether the upstream producer has finished.
     */
    public boolean isFinished() {
        return channel.isFinished();
    }
}
