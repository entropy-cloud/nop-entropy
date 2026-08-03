/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

/**
 * Service used by a {@link SplitEnumerator} to push split assignments down to readers.
 *
 * <p>Stage 49 D3: splits are delivered via this channel after task deploy (not embedded
 * in {@code TaskDeploymentDescriptor}). In LOCAL mode this is a direct in-memory handoff
 * that puts a control mail on the target task's {@code MailboxExecutor}; in DISTRIBUTED
 * mode this is Stage 39 control-plane RPC.
 *
 * @param <T> the split type
 */
public interface AssignmentDeliveryService<T extends SourceSplit> {

    /**
     * Pushes the given splits to the specified subtask's reader. Must be idempotent
     * against duplicate delivery of the same split id (the framework dedupes on the
     * reader side via {@link SourceReader#addSplits(java.util.List)}).
     *
     * @param subtaskIndex target subtask index, in {@code [0, totalParallelism)}
     * @param splits       splits to assign; must not be {@code null} or empty
     */
    void assignSplits(int subtaskIndex, java.util.List<T> splits);

    /**
     * Returns true if the target subtask is currently registered (its reader has
     * announced readiness via {@link SplitAssignmentProxy#requestSplits}). The enumerator
     * may use this to decide whether to push proactively or wait for a pull.
     */
    boolean isReaderRegistered(int subtaskIndex);
}
