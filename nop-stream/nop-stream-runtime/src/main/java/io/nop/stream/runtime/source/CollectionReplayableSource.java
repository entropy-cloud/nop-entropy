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

import io.nop.stream.core.common.functions.source.ReplayableSourceFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;

public class CollectionReplayableSource<T> implements ReplayableSourceFunction<T>, Serializable {

    private static final long serialVersionUID = 1L;

    private final List<T> data;
    private volatile long currentOffset = 0;
    private volatile boolean running = true;

    public CollectionReplayableSource(List<T> data) {
        this.data = data;
    }

    @Override
    public void run(SourceContext<T> ctx) throws Exception {
        while (running && currentOffset < data.size()) {
            ctx.collect(data.get((int) currentOffset));
            currentOffset++;
        }
    }

    @Override
    public void cancel() {
        running = false;
    }

    @Override
    public void seek(long offset) {
        this.currentOffset = offset;
    }

    @Override
    public long getCurrentOffset() {
        return currentOffset;
    }

    public int size() {
        return data.size();
    }
}
