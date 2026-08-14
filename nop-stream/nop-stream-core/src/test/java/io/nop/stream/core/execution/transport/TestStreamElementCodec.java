/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.SideOutputElement;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

/**
 * 测试 {@link StreamElementCodec} 的编码/解码往返正确性。
 */
class TestStreamElementCodec {

    private static final long EPOCH_ID = 42L;

    // ===== StreamRecord tests =====

    @Test
    void streamRecordWithStringPayload_roundTrips() {
        StreamRecord<String> original = new StreamRecord<>("hello-world");
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, String.class.getName(), EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, envelope.getType());
        assertEquals(String.class.getName(), envelope.getValueType());
        assertEquals(EPOCH_ID, envelope.getEpochId());
        assertEquals(EPOCH_ID, envelope.getEpochId());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertEquals("hello-world", decoded.asRecord().getValue());
    }

    @Test
    void streamRecordWithNumericPayload_roundTrips() {
        StreamRecord<Integer> original = new StreamRecord<>(12345, System.currentTimeMillis());
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, Integer.class.getName(), EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, envelope.getType());
        assertEquals(Integer.class.getName(), envelope.getValueType());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertEquals(12345, decoded.asRecord().getValue());
    }

    @Test
    void streamRecordWithNullPayload_roundTrips() {
        StreamRecord<String> original = new StreamRecord<>(null);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, String.class.getName(), EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertNull(decoded.asRecord().getValue());
    }

    @Test
    void streamRecord_autoDetectsValueType() {
        StreamRecord<Double> original = new StreamRecord<>(3.14);
        // Pass null valueType to trigger auto-detection
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        assertEquals(Double.class.getName(), envelope.getValueType());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertEquals(3.14, decoded.asRecord().getValue());
    }

    // ===== CheckpointBarrier tests =====

    @Test
    void checkpointBarrier_roundTrips() {
        CheckpointBarrier original = new CheckpointBarrier(100L, 200L, CheckpointType.CHECKPOINT);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER, envelope.getType());
        assertNull(envelope.getValueType());
        assertInstanceOf(CheckpointBarrier.class, envelope.getPayload());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isCheckpointBarrier());
        CheckpointBarrier barrier = decoded.asCheckpointBarrier();
        assertEquals(100L, barrier.getId());
        assertEquals(200L, barrier.getTimestamp());
        assertEquals(CheckpointType.CHECKPOINT, barrier.getCheckpointType());
    }

    @Test
    void checkpointBarrier_savepoint_roundTrips() {
        CheckpointBarrier original = new CheckpointBarrier(999L, 888L, CheckpointType.SAVEPOINT);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isCheckpointBarrier());
        CheckpointBarrier barrier = decoded.asCheckpointBarrier();
        assertEquals(999L, barrier.getId());
        assertEquals(CheckpointType.SAVEPOINT, barrier.getCheckpointType());
    }

    // ===== Watermark tests =====

    @Test
    void watermark_roundTrips() {
        Watermark original = new Watermark(12345L);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_WATERMARK, envelope.getType());
        assertNull(envelope.getValueType());
        assertInstanceOf(Watermark.class, envelope.getPayload());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isWatermark());
        assertEquals(12345L, decoded.asWatermark().getTimestamp());
    }

    @Test
    void watermark_maxWatermark_roundTrips() {
        Watermark original = Watermark.MAX_WATERMARK;
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isWatermark());
        assertEquals(Long.MAX_VALUE, decoded.asWatermark().getTimestamp());
    }

    // ===== WatermarkStatus tests =====

    @Test
    void watermarkStatus_idle_roundTrips() {
        WatermarkStatus original = WatermarkStatus.IDLE;
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_WATERMARK_STATUS, envelope.getType());
        assertNull(envelope.getValueType());
        assertInstanceOf(WatermarkStatus.class, envelope.getPayload());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isWatermarkStatus());
        assertTrue(decoded.asWatermarkStatus().isIdle());
    }

    @Test
    void watermarkStatus_active_roundTrips() {
        WatermarkStatus original = WatermarkStatus.ACTIVE;
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isWatermarkStatus());
        assertTrue(decoded.asWatermarkStatus().isActive());
    }

    // ===== Edge cases / error handling =====

    @Test
    void encode_nullElement_throws() {
        assertThrows(StreamException.class,
                () -> StreamElementCodec.encode(null, null, EPOCH_ID));
    }

    @Test
    void decode_nullEnvelope_throws() {
        assertThrows(StreamException.class,
                () -> StreamElementCodec.decode(null));
    }

    // ===== SideOutputElement tests (HG-01, 2026-08-14) =====

    @Test
    void sideOutput_roundTrips_withTagId() {
        SideOutputElement original = new SideOutputElement("side-tag-1", new StreamRecord<>("side-value"));
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_SIDE_OUTPUT_RECORD, envelope.getType());
        assertEquals("side-tag-1", envelope.getOutputTagId());
        assertEquals(String.class.getName(), envelope.getValueType());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isSideOutput());
        assertFalse(decoded.isRecord(), "SideOutputElement must not satisfy isRecord()");
        SideOutputElement side = decoded.asSideOutput();
        assertEquals("side-tag-1", side.getOutputTagId());
        assertEquals("side-value", side.getRecord().getValue());
    }

    @Test
    void sideOutput_roundTrips_withTimestamp() {
        SideOutputElement original = new SideOutputElement("tag-ts", new StreamRecord<>(42, 999L));
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        assertTrue(envelope.isHasTimestamp());
        assertEquals(999L, envelope.getTimestamp());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        SideOutputElement side = decoded.asSideOutput();
        assertEquals(42, side.getRecord().getValue());
        assertTrue(side.getRecord().hasTimestamp());
        assertEquals(999L, side.getRecord().getTimestamp());
    }

    @Test
    void sideOutput_roundTrips_withNullPayload() {
        SideOutputElement original = new SideOutputElement("tag-null", new StreamRecord<>(null));
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        SideOutputElement side = decoded.asSideOutput();
        assertEquals("tag-null", side.getOutputTagId());
        assertNull(side.getRecord().getValue());
    }

    @Test
    void sideOutput_tagIdWithSpecialChars_roundTrips() {
        SideOutputElement original = new SideOutputElement("tag-with-特殊字符-/-space _dots", new StreamRecord<>("v"));
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertEquals("tag-with-特殊字符-/-space _dots", decoded.asSideOutput().getOutputTagId());
    }

    @Test
    void sideOutput_ignoresEdgeLevelValueType() {
        // HG-01 Phase 1 decision (review M2): the passed-in valueType (edge-level) is
        // intentionally ignored — valueType is always derived from the inner record value.
        SideOutputElement original = new SideOutputElement("tag-m2", new StreamRecord<>(3.14));
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, "java.lang.String", EPOCH_ID);

        assertEquals(Double.class.getName(), envelope.getValueType(),
                "side-output valueType must be derived from inner record value, ignoring edge-level valueType");

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertEquals(3.14, decoded.asSideOutput().getRecord().getValue());
    }

    @Test
    void sideOutput_copy_detachesInnerRecord() {
        SideOutputElement original = new SideOutputElement("tag-copy", new StreamRecord<>("shared", 5L));
        SideOutputElement copy = original.copy();

        assertEquals("tag-copy", copy.getOutputTagId());
        assertNotSame(original.getRecord(), copy.getRecord());
        assertEquals(original.getRecord().getValue(), copy.getRecord().getValue());
        assertEquals(5L, copy.getRecord().getTimestamp());
    }

    @Test
    void unknownEnvelopeType_stillThrows() {
        // HG-01 no-silent-skip invariant: an unknown envelope type must throw, not decode to null.
        StreamMessageEnvelope envelope = new StreamMessageEnvelope(EPOCH_ID, "NOT_A_TYPE", null, null);
        assertThrows(StreamException.class, () -> StreamElementCodec.decode(envelope));
    }

    // ===== TypeRegistry tests =====

    @Test
    void typeRegistry_registerAndGet() {
        TypeRegistry registry = new TypeRegistry();
        registry.register("edge-1", "java.lang.String");
        assertEquals("java.lang.String", registry.getOutputTypeClassName("edge-1"));
        assertTrue(registry.isRegistered("edge-1"));
        assertEquals(1, registry.size());
    }

    @Test
    void typeRegistry_unregister() {
        TypeRegistry registry = new TypeRegistry();
        registry.register("edge-1", "java.lang.String");
        registry.unregister("edge-1");
        assertNull(registry.getOutputTypeClassName("edge-1"));
        assertFalse(registry.isRegistered("edge-1"));
        assertEquals(0, registry.size());
    }

    @Test
    void typeRegistry_clear() {
        TypeRegistry registry = new TypeRegistry();
        registry.register("edge-1", "java.lang.String");
        registry.register("edge-2", "java.lang.Integer");
        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    void typeRegistry_unknownEdge_returnsNull() {
        TypeRegistry registry = new TypeRegistry();
        assertNull(registry.getOutputTypeClassName("nonexistent"));
    }

    @Test
    void typeRegistry_nullEdgeId_throws() {
        TypeRegistry registry = new TypeRegistry();
        assertThrows(StreamException.class,
                () -> registry.register(null, "java.lang.String"));
    }

    @Test
    void typeRegistry_nullOutputType_throws() {
        TypeRegistry registry = new TypeRegistry();
        assertThrows(StreamException.class,
                () -> registry.register("edge-1", null));
    }
}
