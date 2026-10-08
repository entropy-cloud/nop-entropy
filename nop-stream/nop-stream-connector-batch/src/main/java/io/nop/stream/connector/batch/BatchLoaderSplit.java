/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.batch;

import io.nop.stream.core.source.SourceSplit;

import java.io.Serializable;
import java.util.Objects;

/**
 * WI2 (plan 2343 Phase 1): the single assignment token of the batch-loader FLIP-27
 * source. The loader traversal is single-writer (one logical dataset, no shard
 * decomposition), so there is exactly ONE split per job — {@link #SPLIT_ID} is a
 * constant. The split carries the traversal cursor under the AR-13 next-index
 * convention ({@code offset} = number of records already emitted) — it is the
 * checkpoint-state carrier for the reader (snapshot writes it, restore reads it).
 */
public final class BatchLoaderSplit implements SourceSplit, Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SPLIT_ID = "batch-loader-0";

    /** Records already emitted (next-index convention, aligned with the legacy function). */
    private final long offset;

    public BatchLoaderSplit(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative, got: " + offset);
        }
        this.offset = offset;
    }

    @Override
    public String splitId() {
        return SPLIT_ID;
    }

    public long getOffset() {
        return offset;
    }

    public BatchLoaderSplit withOffset(long newOffset) {
        return new BatchLoaderSplit(newOffset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BatchLoaderSplit)) return false;
        BatchLoaderSplit that = (BatchLoaderSplit) o;
        return offset == that.offset;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset);
    }

    @Override
    public String toString() {
        return "BatchLoaderSplit{id=" + SPLIT_ID + ", offset=" + offset + "}";
    }
}
