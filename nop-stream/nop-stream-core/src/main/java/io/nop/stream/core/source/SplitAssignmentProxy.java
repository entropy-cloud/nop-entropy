/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import java.util.Optional;

/**
 * Channel from a {@link SourceReader} back to the {@link SplitEnumerator}, exposed through
 * {@link SourceReaderContext}. The reader uses it to:
 *
 * <ul>
 *   <li>request more splits when it has finished (or never received) its current assignment
 *       (pull model, Stage 49 D3/D4), and</li>
 *   <li>report splits it has fully consumed, so the enumerator can mark them finished.</li>
 * </ul>
 *
 * <p>In LOCAL execution mode this is a direct in-memory handoff to the coordinator-owned
 * enumerator. In DISTRIBUTED mode this is carried by Stage 39 control-plane RPC
 * ({@code IStreamTaskRpcService}) with fencing-token validation (see
 * {@code checkpoint-design.md} §2.1.2).
 */
public interface SplitAssignmentProxy {

    /**
     * Called by the reader when it has no more input and wants the enumerator to consider
     * assigning more splits to {@code subtaskIndex}.
     *
     * @param subtaskIndex the requesting reader's subtask index
     * @param reason       absent when this is a normal "I'm idle" request; present with a
     *                     failure cause if the reader is requesting because a prior split failed
     */
    void requestSplits(int subtaskIndex, Optional<Throwable> reason);

    /**
     * Called by the reader to report that the given splits have been fully consumed and can
     * be marked finished in the enumerator's bookkeeping.
     *
     * @param subtaskIndex the reporting reader's subtask index
     * @param finishedSplitIds the ids of splits this reader has finished
     */
    void reportFinishedSplits(int subtaskIndex, java.util.List<String> finishedSplitIds);
}
