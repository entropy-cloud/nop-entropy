/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.Map;

import io.nop.api.core.beans.ApiRequest;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 40: verifies {@link SysDaoWireCodec} produces the wire shape
 * {@code SysDaoMessageService} faithfully persists ({@code ApiRequest{data: map}}) and
 * reconstructs the envelope from the shape the backend delivers. The full DB traversal
 * (record lands in {@code NopSysEvent}) is covered by
 * {@code TestDataPlaneSysDaoBackendE2E} in nop-sys-dao; this test pins the codec logic.
 */
class TestSysDaoWireCodec {

    private final SysDaoWireCodec codec = SysDaoWireCodec.INSTANCE;

    @Test
    void toWireProducesApiRequestWithDataMap() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                7L, StreamMessageEnvelope.TYPE_STREAM_RECORD, String.class.getName(), "\"hello\"");

        Object wire = codec.toWire(envelope);

        assertTrue(wire instanceof ApiRequest, "wire must be an ApiRequest for SysDao backend");
        Object data = ((ApiRequest<?>) wire).getData();
        assertNotNull(data, "ApiRequest.data must carry the envelope body");
        assertTrue(data instanceof Map, "data must be a Map (persisted as eventData)");
        assertEquals(7L, ((Number) ((Map<?, ?>) data).get("epochId")).longValue());
        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, ((Map<?, ?>) data).get("type"));
    }

    @Test
    void roundTripsRecordEnvelope() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                5L, StreamMessageEnvelope.TYPE_STREAM_RECORD, String.class.getName(),
                "\"data\"", 123L, true);

        StreamMessageEnvelope back = codec.fromWire(codec.toWire(envelope));

        assertNotNull(back);
        assertEquals(5L, back.getEpochId());
        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, back.getType());
        assertEquals(String.class.getName(), back.getValueType());
        assertEquals("\"data\"", back.getPayload());
        assertEquals(123L, back.getTimestamp());
        assertTrue(back.isHasTimestamp());
    }

    @Test
    void roundTripsBarrierEnvelope() {
        CheckpointBarrier barrier = new CheckpointBarrier(99L, 1000L, CheckpointType.CHECKPOINT);
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                11L, StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER, null, barrier);

        StreamMessageEnvelope back = codec.fromWire(codec.toWire(envelope));

        assertNotNull(back);
        assertEquals(11L, back.getEpochId());
        assertEquals(StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER, back.getType());
    }

    @Test
    void roundTripsWatermarkEnvelope() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                3L, StreamMessageEnvelope.TYPE_WATERMARK, null, new Watermark(42L));

        StreamMessageEnvelope back = codec.fromWire(codec.toWire(envelope));

        assertNotNull(back);
        assertEquals(3L, back.getEpochId());
        assertEquals(StreamMessageEnvelope.TYPE_WATERMARK, back.getType());
    }

    @Test
    void roundTripsControlEnvelope() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                1L, StreamMessageEnvelope.TYPE_CONTROL, null, "END_OF_STREAM");

        StreamMessageEnvelope back = codec.fromWire(codec.toWire(envelope));

        assertNotNull(back);
        assertEquals(StreamMessageEnvelope.TYPE_CONTROL, back.getType());
        assertEquals("END_OF_STREAM", back.getPayload());
    }

    @Test
    void fromWireHandlesBareEnvelope() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                1L, StreamMessageEnvelope.TYPE_CONTROL, null, "END_OF_STREAM");

        // Defensive: if LocalMessageService delivers a bare envelope, it passes through.
        assertEquals(envelope, codec.fromWire(envelope));
    }
}
