/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a partition of data that can be assigned to a subtask.
 * For example, a Kafka partition, a database shard, or a file range.
 */
public class SourceSplit implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String splitId;
    private final String description;
    private final Object cursor;

    public SourceSplit(String splitId, String description, Object cursor) {
        this.splitId = splitId;
        this.description = description;
        this.cursor = cursor;
    }

    public SourceSplit(String splitId) {
        this(splitId, splitId, null);
    }

    public String getSplitId() {
        return splitId;
    }

    public String getDescription() {
        return description;
    }

    public Object getCursor() {
        return cursor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceSplit that = (SourceSplit) o;
        return Objects.equals(splitId, that.splitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitId);
    }

    @Override
    public String toString() {
        return "SourceSplit{" + splitId + "}";
    }
}
