/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

/**
 * Information made available to a {@link SplitEnumerator} when it is created and started.
 * Stage 49 v1 carries the minimal context the enumerator needs to know the total
 * parallelism (subtask count) and to push split assignments down to readers. Custom
 * coordinator↔reader events are deferred (Stage 49 D1 — SourceEvent defer).
 */
public final class SplitEnumeratorContext<T extends SourceSplit> {

    private final int totalParallelism;

    private final AssignmentDeliveryService<T> deliveryService;

    public SplitEnumeratorContext(int totalParallelism,
                                  AssignmentDeliveryService<T> deliveryService) {
        this.totalParallelism = totalParallelism;
        this.deliveryService = deliveryService;
    }

    /** Total number of parallel source subtasks that the enumerator will assign splits to. */
    public int getTotalParallelism() {
        return totalParallelism;
    }

    /**
     * Access to the per-subtask delivery channel for split assignments. The enumerator
     * pushes splits here; the framework routes them to the corresponding reader's
     * {@link SourceReader#addSplits(java.util.List)}.
     *
     * <p>May be {@code null} in isolated unit tests that call
     * {@link SplitEnumerator#handleSplitRequest(int, java.util.Optional)} and inspect
     * enumerator state directly.
     */
    public AssignmentDeliveryService<T> getDeliveryService() {
        return deliveryService;
    }
}
