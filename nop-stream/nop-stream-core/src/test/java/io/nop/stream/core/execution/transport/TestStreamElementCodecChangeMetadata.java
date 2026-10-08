/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import io.nop.api.core.stream.RowKind;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7 (plan 2336): optional change metadata rides the cross-process envelope
 * (review P9 ruling A) — round-trip through {@link StreamElementCodec} keeps the R4
 * sink guard effective across remote edges; absent metadata stays absent (backward
 * compatible with pre-WI7 envelopes).
 */
class TestStreamElementCodecChangeMetadata {

    @Test
    void metadataRoundTripsThroughEnvelope() {
        StreamRecord<String> record = new StreamRecord<>("payload", 42L)
                .withChangeMetadata(RowKind.UPDATE_AFTER, "db.orders");

        StreamMessageEnvelope envelope = StreamElementCodec.encode(record, String.class.getName(), 7L);
        assertEquals("UPDATE_AFTER", envelope.getRowKind());
        assertEquals("db.orders", envelope.getTableId());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        StreamRecord<?> back = decoded.asRecord();
        assertEquals("payload", back.getValue());
        assertEquals(42L, back.getTimestamp());
        assertEquals(RowKind.UPDATE_AFTER, back.getRowKind());
        assertEquals("db.orders", back.getTableId());
    }

    @Test
    void absentMetadataStaysAbsent() {
        StreamRecord<String> record = new StreamRecord<>("plain", 1L);

        StreamMessageEnvelope envelope = StreamElementCodec.encode(record, String.class.getName(), 7L);
        assertNull(envelope.getRowKind());
        assertNull(envelope.getTableId());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertNull(decoded.asRecord().getRowKind(), "historic envelopes decode to insert-only records");
        assertNull(decoded.asRecord().getTableId());
    }
}
