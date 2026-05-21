/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.functions.source;

/**
 * A checkpointed source that supports seeking to a specific offset for replay.
 *
 * <p>When the stream recovers from a checkpoint, the operator restores the saved
 * offset and calls {@link #seek(long)} before {@link #run(SourceContext)}, so the
 * source re-emits from the correct position.
 *
 * @param <T> The type of the elements produced by this source
 */
public interface ReplayableSourceFunction<T> extends CheckpointedSourceFunction<T> {

    /**
     * Reposition the source so that the next {@link #run(SourceContext)} call
     * starts emitting from the given offset.
     *
     * @param offset the offset to seek to
     */
    void seek(long offset);

    /**
     * Returns the current emission offset. Used by the operator to snapshot
     * source progress during checkpoints.
     *
     * @return the current offset
     */
    long getCurrentOffset();
}
