/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

/**
 * Information made available to a {@link SourceReader} when it is created and started.
 * Stage 49 v1 carries the minimal context the reader needs to identify itself and report
 * finished splits; richer hooks (watermark estimator, custom source events) are deferred
 * (see {@code connector-design.md} §4.0 D1 — SourceEvent / WatermarkEstimator defer).
 */
public final class SourceReaderContext {

    private final int subtaskIndex;
    private final int totalParallelism;
    private final SplitAssignmentProxy assignmentProxy;

    public SourceReaderContext(int subtaskIndex,
                               int totalParallelism,
                               SplitAssignmentProxy assignmentProxy) {
        this.subtaskIndex = subtaskIndex;
        this.totalParallelism = totalParallelism;
        this.assignmentProxy = assignmentProxy;
    }

    /** Index of this reader among the parallel source subtasks, in {@code [0, totalParallelism)}. */
    public int getSubtaskIndex() {
        return subtaskIndex;
    }

    /** Total number of parallel source subtasks for this source vertex. */
    public int getTotalParallelism() {
        return totalParallelism;
    }

    /**
     * Access to the coordinator-side split assignment channel. May be {@code null} for
     * isolated unit tests that drive the reader directly via
     * {@link SourceReader#addSplits(java.util.List)}.
     */
    public SplitAssignmentProxy getAssignmentProxy() {
        return assignmentProxy;
    }
}
