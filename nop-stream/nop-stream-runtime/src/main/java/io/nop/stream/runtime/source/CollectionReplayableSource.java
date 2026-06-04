/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.*;

public class CollectionReplayableSource<T> implements ReplayableSourceFunction<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private final List<T> data;
    private final AtomicLong currentOffset = new AtomicLong(0);
    private volatile boolean running = true;

    public CollectionReplayableSource(List<T> data) {
        this.data = data;
    }

    @Override
    public void run(SourceContext<T> ctx) throws Exception {
        while (running && currentOffset.get() < data.size()) {
            int idx = (int) currentOffset.getAndIncrement();
            if (idx < data.size()) {
                ctx.collect(data.get(idx));
            }
        }
    }

    @Override
    public void cancel() {
        running = false;
    }

    @Override
    public void seek(long offset) {
        if (offset < 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "offset")
                    .param(ARG_DETAIL, "offset must be non-negative, got: " + offset);
        }
        if (offset > data.size()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_ARG_NAME, "offset")
                    .param(ARG_DETAIL, "offset " + offset + " exceeds data size " + data.size());
        }
        currentOffset.set(offset);
    }

    @Override
    public long getCurrentOffset() {
        return currentOffset.get();
    }

    public int size() {
        return data.size();
    }
}
