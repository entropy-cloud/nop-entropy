/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source;

import java.io.Serializable;
import java.util.Objects;

/**
 * A simple, cursor-carrying default implementation of {@link SourceSplit}. Useful for
 * tests and for sources whose split identity + cursor model fits in three fields
 * (id + description + opaque cursor), e.g. {@code FileSplit} (path + byte offset).
 *
 * <p>Stage 49 D6: this is the renamed/rehoused successor of the old concrete
 * {@code io.nop.stream.runtime.source.SourceSplit} class. The concrete is gone; this is
 * the only default split impl in the new contract.
 */
public final class SimpleSourceSplit implements SourceSplit {

    private static final long serialVersionUID = 1L;

    private final String splitId;
    private final String description;
    private final Serializable cursor;

    public SimpleSourceSplit(String splitId, String description, Serializable cursor) {
        this.splitId = splitId;
        this.description = description;
        this.cursor = cursor;
    }

    public SimpleSourceSplit(String splitId) {
        this(splitId, splitId, null);
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public String getDescription() {
        return description;
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getCursor() {
        return (T) cursor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof SourceSplit)) return false;
        return Objects.equals(splitId, ((SourceSplit) o).splitId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitId);
    }

    @Override
    public String toString() {
        return "SimpleSourceSplit{" + splitId + "}";
    }
}
