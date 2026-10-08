/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.stream.core.source.SourceSplit;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WI2 (plan 2343 Phase 3): one include-list table = one split (adjudication R2 — the
 * split mechanism naturally takes over the B-layer table routing). The split carries
 * the INSTANCE-LEVEL CDC offset map (a base64-serializable copy of
 * {@code NopStreamOffsetBackingStore} content at snapshot time): Debezium offsets are
 * connector-instance-granular and their Kafka-Connect keys are connector-shape
 * dependent, so per-table attribution is deliberately NOT attempted — every split of a
 * reader carries the same map copy, and restore takes the first one.
 */
public final class DebeziumTableSplit implements SourceSplit, Serializable {

    private static final long serialVersionUID = 1L;

    private final String tableEntry;
    private final Map<String, String> offsetMap;
    /** true when this split's offsetMap is the settled instance-level state. */
    private final boolean offsetCarrying;

    public DebeziumTableSplit(String tableEntry) {
        this(tableEntry, null, false);
    }

    public DebeziumTableSplit(String tableEntry, Map<String, String> offsetMap,
                              boolean offsetCarrying) {
        this.tableEntry = tableEntry;
        this.offsetMap = offsetMap == null ? null : new LinkedHashMap<>(offsetMap);
        this.offsetCarrying = offsetCarrying;
    }

    @Override
    public String splitId() {
        return tableEntry;
    }

    public String getTableEntry() {
        return tableEntry;
    }

    public Map<String, String> getOffsetMap() {
        return offsetMap;
    }

    public boolean isOffsetCarrying() {
        return offsetCarrying;
    }

    public DebeziumTableSplit withOffsetMap(Map<String, String> map) {
        return new DebeziumTableSplit(tableEntry, map, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DebeziumTableSplit)) return false;
        return Objects.equals(tableEntry, ((DebeziumTableSplit) o).tableEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableEntry);
    }

    @Override
    public String toString() {
        return "DebeziumTableSplit{" + tableEntry + ", offsetCarrying=" + offsetCarrying + "}";
    }
}
