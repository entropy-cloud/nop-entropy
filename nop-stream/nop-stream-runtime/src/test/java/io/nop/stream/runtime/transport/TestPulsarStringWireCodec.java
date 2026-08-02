/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.api.core.beans.ApiRequest;
import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.watermark.Watermark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 40: verifies {@link PulsarStringWireCodec} produces the wire shape
 * {@code PulsarMessageService} (default {@code Schema.STRING}) faithfully carries (a
 * JSON {@code String}) and reconstructs the envelope from the shape the backend
 * delivers ({@code ApiMessage{data: string}}). The full broker traversal is covered by
 * {@code TestDataPlanePulsarBackendE2E} (gated, CI-provided broker); this test pins the
 * codec logic.
 */
class TestPulsarStringWireCodec {

    private final PulsarStringWireCodec codec = PulsarStringWireCodec.INSTANCE;

    @Test
    void toWireProducesJsonString() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                7L, StreamMessageEnvelope.TYPE_STREAM_RECORD, String.class.getName(), "\"hi\"");

        Object wire = codec.toWire(envelope);

        assertTrue(wire instanceof String, "wire must be a JSON String for Pulsar STRING schema");
        String json = (String) wire;
        assertTrue(json.contains("\"epochId\":7"), "JSON must carry epochId: " + json);
        assertTrue(json.contains("\"type\":\"STREAM_RECORD\""), "JSON must carry type: " + json);
    }

    @Test
    void roundTripsViaDeliveredApiMessage() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                5L, StreamMessageEnvelope.TYPE_STREAM_RECORD, String.class.getName(),
                "\"data\"", 123L, true);

        // Pulsar consumer (buildApiMessage) delivers ApiMessage{data: jsonString}.
        Object wireString = codec.toWire(envelope);
        ApiRequest<Object> delivered = new ApiRequest<>();
        delivered.setData(wireString);

        StreamMessageEnvelope back = codec.fromWire(delivered);

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

        ApiRequest<Object> delivered = new ApiRequest<>();
        delivered.setData(codec.toWire(envelope));

        StreamMessageEnvelope back = codec.fromWire(delivered);

        assertNotNull(back);
        assertEquals(11L, back.getEpochId());
        assertEquals(StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER, back.getType());
    }

    @Test
    void roundTripsWatermarkEnvelope() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                3L, StreamMessageEnvelope.TYPE_WATERMARK, null, new Watermark(42L));

        ApiRequest<Object> delivered = new ApiRequest<>();
        delivered.setData(codec.toWire(envelope));

        StreamMessageEnvelope back = codec.fromWire(delivered);
        assertNotNull(back);
        assertEquals(StreamMessageEnvelope.TYPE_WATERMARK, back.getType());
    }

    @Test
    void fromWireHandlesBareString() {
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(
                1L, StreamMessageEnvelope.TYPE_CONTROL, null, "END_OF_STREAM");

        // Defensive: a bare JSON string (no ApiMessage wrapper) also decodes.
        StreamMessageEnvelope back = codec.fromWire(codec.toWire(envelope));
        assertNotNull(back);
        assertEquals(StreamMessageEnvelope.TYPE_CONTROL, back.getType());
        assertEquals("END_OF_STREAM", back.getPayload());
    }
}
