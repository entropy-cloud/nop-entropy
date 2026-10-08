/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.api.core.stream.RowKind;
import io.nop.stream.core.execution.transport.StreamElementCodec;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI7 (plan 2336): change metadata survives the unaligned-checkpoint in-flight path —
 * {@link ChannelState} envelope-map serialization keeps rowKind/tableId so records
 * replayed from a checkpoint restore still trigger the R4 sink guard.
 */
class TestChannelStateChangeMetadata {

    @Test
    void metadataSurvivesEnvelopeMapRoundTrip() {
        StreamRecord<String> record = new StreamRecord<>("payload", 9L)
                .withChangeMetadata(RowKind.DELETE, "db.orders");
        StreamMessageEnvelope envelope = StreamElementCodec.encode(record, String.class.getName(), 3L);

        Map<String, Object> map = ChannelState.envelopeToMapViaReflection(envelope);
        assertEquals("DELETE", map.get("rowKind"));
        assertEquals("db.orders", map.get("tableId"));

        StreamMessageEnvelope restored = ChannelState.envelopeFromMapViaReflection(map);
        assertEquals("DELETE", restored.getRowKind());
        assertEquals("db.orders", restored.getTableId());

        StreamElement decoded = StreamElementCodec.decode(restored);
        assertEquals(RowKind.DELETE, decoded.asRecord().getRowKind());
        assertEquals("db.orders", decoded.asRecord().getTableId());
    }

    @Test
    void absentMetadataRoundTripsAsNull() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope();
        Map<String, Object> map = ChannelState.envelopeToMapViaReflection(envelope);
        assertNull(map.get("rowKind"));
        assertNull(map.get("tableId"));
        StreamMessageEnvelope restored = ChannelState.envelopeFromMapViaReflection(map);
        assertNull(restored.getRowKind());
        assertNull(restored.getTableId());
    }
}
