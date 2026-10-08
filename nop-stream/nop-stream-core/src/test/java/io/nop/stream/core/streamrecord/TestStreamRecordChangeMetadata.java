/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.streamrecord;

import io.nop.api.core.stream.RowKind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 (plan 2336): optional change metadata on the engine-level record carrier —
 * default null = insert-only append (zero behavior change), copy/copyTo carry it,
 * replace preserves it (same side-band contract as the timestamp).
 */
class TestStreamRecordChangeMetadata {

    @Test
    void defaultRecordHasNoChangeMetadata() {
        StreamRecord<String> record = new StreamRecord<>("a");
        assertNull(record.getRowKind(), "null rowKind = insert-only append default");
        assertNull(record.getTableId());
    }

    @Test
    void copyCarriesMetadata() {
        StreamRecord<String> record = new StreamRecord<>("a", 5L)
                .withChangeMetadata(RowKind.DELETE, "db.t1");
        StreamRecord<String> copy = record.copy("b");
        assertEquals("b", copy.getValue());
        assertEquals(5L, copy.getTimestamp());
        assertEquals(RowKind.DELETE, copy.getRowKind());
        assertEquals("db.t1", copy.getTableId());

        StreamRecord<String> target = new StreamRecord<>(null);
        record.copyTo("c", target);
        assertEquals("c", target.getValue());
        assertEquals(RowKind.DELETE, target.getRowKind());
        assertEquals("db.t1", target.getTableId());
    }

    @Test
    void replacePreservesMetadataLikeTimestamp() {
        StreamRecord<String> record = new StreamRecord<>("a", 7L)
                .withChangeMetadata(RowKind.UPDATE_AFTER, "db.t1");
        record.replace("b");
        assertEquals("b", record.getValue());
        assertEquals(7L, record.getTimestamp(), "replace keeps the timestamp side-band");
        assertEquals(RowKind.UPDATE_AFTER, record.getRowKind(),
                "clearing the kind would silently demote a change to an append");
        assertEquals("db.t1", record.getTableId());
    }

    @Test
    void toStringAppendsMetadataOnlyWhenPresent() {
        assertTrue(new StreamRecord<>("a").toString().contains("rowKind") == false,
                "plain records keep the historic toString");
        String str = new StreamRecord<>("a").withChangeMetadata(RowKind.INSERT, "t").toString();
        assertTrue(str.contains("rowKind=INSERT") && str.contains("tableId=t"), str);
    }
}
