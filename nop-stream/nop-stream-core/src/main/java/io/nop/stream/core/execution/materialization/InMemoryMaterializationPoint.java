/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.materialization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.streamrecord.StreamElement;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_POINT_ID;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_MATERIALIZE_POINT_SEALED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * In-memory {@link IMaterializationPoint} implementation.
 *
 * <p>Backed by a synchronized {@link ArrayList}. This is the correctness-first
 * implementation for region-boundary materialization (option B). Production-grade
 * RocksDB/disk materialization is a non-goal of the current plan and is deferred
 * as an optimization candidate.
 *
 * <p><b>Concurrency</b>: all mutating and read operations are synchronized on
 * {@code this}, so the producer write path and the recovery replay path can run
 * concurrently. The bypass write does not block on capacity (in-memory list
 * grows unboundedly); global memory pressure is expected to be controlled
 * upstream by the {@code IBufferPool} on the main queue.
 */
public class InMemoryMaterializationPoint implements IMaterializationPoint {

    private final String pointId;
    private final List<MaterializedElement> store = new ArrayList<>();
    private volatile boolean sealed;
    private long lastEpoch = -1L;

    /**
     * @param pointId the independently addressable id (must not be null)
     */
    public InMemoryMaterializationPoint(String pointId) {
        if (pointId == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "pointId");
        }
        this.pointId = pointId;
    }

    @Override
    public String getPointId() {
        return pointId;
    }

    @Override
    public synchronized void write(StreamElement element, long epoch) throws InterruptedException {
        if (sealed) {
            throw new StreamException(ERR_STREAM_MATERIALIZE_POINT_SEALED)
                    .param(ARG_POINT_ID, pointId)
                    .param(ARG_DETAIL, "cannot write to a sealed materialization point");
        }
        // MaterializedElement constructor rejects null element.
        store.add(new MaterializedElement(element, epoch));
        if (epoch > lastEpoch) {
            lastEpoch = epoch;
        }
    }

    @Override
    public synchronized List<MaterializedElement> replay(long fromEpoch) {
        if (fromEpoch <= 0L) {
            // epoch <= 0 means "from the beginning"; avoid skipping elements
            // whose epoch is legitimately 0.
            return replayAll();
        }
        List<MaterializedElement> out = new ArrayList<>();
        for (MaterializedElement me : store) {
            if (me.getEpoch() >= fromEpoch) {
                out.add(me);
            }
        }
        return out;
    }

    @Override
    public synchronized List<MaterializedElement> replayAll() {
        return new ArrayList<>(store);
    }

    @Override
    public synchronized void seal() {
        this.sealed = true;
    }

    @Override
    public boolean isSealed() {
        return sealed;
    }

    @Override
    public synchronized int size() {
        return store.size();
    }

    @Override
    public synchronized long getLastEpoch() {
        return lastEpoch;
    }
}
