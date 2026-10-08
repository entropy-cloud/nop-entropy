/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SplitEnumerator;
import io.nop.stream.core.source.SplitEnumeratorContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;

/**
 * WI2 (plan 2343 Phase 1): enumerator of the single-token batch-loader source. The one
 * {@link BatchLoaderSplit} is assigned once to the first registered/requesting reader —
 * a repeated assignment is refused (the token is the whole dataset; re-delivering it
 * would duplicate the traversal). Restore re-establishes the assigned flag so a
 * checkpointed assignment is not re-delivered after recovery.
 */
public class BatchLoaderSplitEnumerator implements SplitEnumerator<BatchLoaderSplit, BatchLoaderEnumeratorState> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(BatchLoaderSplitEnumerator.class);

    private boolean assigned;
    private transient SplitEnumeratorContext<BatchLoaderSplit> context;

    @Override
    public void start(SplitEnumeratorContext<BatchLoaderSplit> context) {
        this.context = context;
    }

    @Override
    public void addReader(int subtaskIndex) {
        assignIfNeeded(subtaskIndex);
    }

    @Override
    public void handleSplitRequest(int subtaskIndex, java.util.Optional<Throwable> reason) {
        assignIfNeeded(subtaskIndex);
    }

    private void assignIfNeeded(int subtaskIndex) {
        if (assigned) {
            return;
        }
        assigned = true;
        if (context != null) {
            context.getDeliveryService().assignSplits(subtaskIndex,
                    java.util.Collections.singletonList(new BatchLoaderSplit(0L)));
            LOG.info("BatchLoaderSplitEnumerator assigned the single batch split to subtask {}", subtaskIndex);
        }
    }

    @Override
    public BatchLoaderEnumeratorState snapshotState(long checkpointId) {
        return new BatchLoaderEnumeratorState(assigned);
    }

    @Override
    public void restoreState(BatchLoaderEnumeratorState state) {
        if (state == null) {
            return;
        }
        this.assigned = state.isAssigned();
    }

    @Override
    public void close() {
        // no external resources
    }

    /** Test visibility: whether the single token has been handed out. */
    public boolean isAssigned() {
        return assigned;
    }
}
