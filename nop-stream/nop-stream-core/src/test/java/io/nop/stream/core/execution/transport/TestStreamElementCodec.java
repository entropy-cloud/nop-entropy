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

    private static final String FENCING_TOKEN = "test-fence-001";
    private static final long EPOCH_ID = 42L;

    // ===== StreamRecord tests =====

    @Test
    void streamRecordWithStringPayload_roundTrips() {
        StreamRecord<String> original = new StreamRecord<>("hello-world");
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, String.class.getName(), FENCING_TOKEN, EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, envelope.getType());
        assertEquals(String.class.getName(), envelope.getValueType());
        assertEquals(FENCING_TOKEN, envelope.getFencingToken());
        assertEquals(EPOCH_ID, envelope.getEpochId());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertEquals("hello-world", decoded.asRecord().getValue());
    }

    @Test
    void streamRecordWithNumericPayload_roundTrips() {
        StreamRecord<Integer> original = new StreamRecord<>(12345, System.currentTimeMillis());
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, Integer.class.getName(), FENCING_TOKEN, EPOCH_ID);

        assertEquals(StreamMessageEnvelope.TYPE_STREAM_RECORD, envelope.getType());
        assertEquals(Integer.class.getName(), envelope.getValueType());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertEquals(12345, decoded.asRecord().getValue());
    }

    @Test
    void streamRecordWithNullPayload_roundTrips() {
        StreamRecord<String> original = new StreamRecord<>(null);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, String.class.getName(), FENCING_TOKEN, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertNull(decoded.asRecord().getValue());
    }

    @Test
    void streamRecord_autoDetectsValueType() {
        StreamRecord<Double> original = new StreamRecord<>(3.14);
        // Pass null valueType to trigger auto-detection
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

        assertEquals(Double.class.getName(), envelope.getValueType());

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isRecord());
        assertEquals(3.14, decoded.asRecord().getValue());
    }

    // ===== CheckpointBarrier tests =====

    @Test
    void checkpointBarrier_roundTrips() {
        CheckpointBarrier original = new CheckpointBarrier(100L, 200L, CheckpointType.CHECKPOINT);
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

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
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

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
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

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
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isWatermark());
        assertEquals(Long.MAX_VALUE, decoded.asWatermark().getTimestamp());
    }

    // ===== WatermarkStatus tests =====

    @Test
    void watermarkStatus_idle_roundTrips() {
        WatermarkStatus original = WatermarkStatus.IDLE;
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

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
        StreamMessageEnvelope envelope = StreamElementCodec.encode(original, null, FENCING_TOKEN, EPOCH_ID);

        StreamElement decoded = StreamElementCodec.decode(envelope);
        assertTrue(decoded.isWatermarkStatus());
        assertTrue(decoded.asWatermarkStatus().isActive());
    }

    // ===== Edge cases / error handling =====

    @Test
    void encode_nullElement_throws() {
        assertThrows(StreamException.class,
                () -> StreamElementCodec.encode(null, null, FENCING_TOKEN, EPOCH_ID));
    }

    @Test
    void decode_nullEnvelope_throws() {
        assertThrows(StreamException.class,
                () -> StreamElementCodec.decode(null));
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
