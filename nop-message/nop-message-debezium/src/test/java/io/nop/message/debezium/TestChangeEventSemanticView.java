/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium;

import io.nop.api.core.stream.RowKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI7 (plan 2336): the ChangeEvent -> engine-level change metadata mapping matrix.
 * c/r (and null/unknown, defensively) map to INSERT; u to UPDATE_AFTER; d to DELETE;
 * tableId rides the metadata table name.
 */
class TestChangeEventSemanticView {

    private static ChangeEvent event(String operation, String table) {
        return new ChangeEvent(new ChangeEventMetadata("mysql", "srv", "db", "schema", table, null, null, null,
                null, null), operation, null, null, null, 0L);
    }

    @Test
    void mappingMatrix() {
        assertEquals(RowKind.INSERT, event("c", "t").getRowKind());
        assertEquals(RowKind.INSERT, event("r", "t").getRowKind(), "snapshot reads are inserts");
        assertEquals(RowKind.UPDATE_AFTER, event("u", "t").getRowKind());
        assertEquals(RowKind.DELETE, event("d", "t").getRowKind());
    }

    @Test
    void unknownOperationMapsToInsertDefensively() {
        assertEquals(RowKind.INSERT, event(null, "t").getRowKind());
        assertEquals(RowKind.INSERT, event("x", "t").getRowKind(),
                "the original operation still rides the value for sinks to reject on their own contract");
    }

    @Test
    void tableIdRidesMetadata() {
        assertEquals("orders", event("c", "orders").getTableId());
        assertNull(event("c", null).getTableId());
    }
}
