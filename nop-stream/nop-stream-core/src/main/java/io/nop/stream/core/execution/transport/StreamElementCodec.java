/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import io.nop.core.lang.json.JsonTool;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.streamrecord.StreamElement;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.exceptions.StreamException;

import io.nop.stream.core.exceptions.NopStreamErrors;
import static io.nop.stream.core.exceptions.NopStreamErrors.*;

/**
 * StreamElement 的编码器/解码器，将 {@link StreamElement} 与 {@link StreamMessageEnvelope} 互转。
 *
 * <p>编码规则：
 * <ul>
 *   <li>{@link StreamRecord}：payload 通过 {@link JsonTool#stringify} 序列化为 JSON 字符串，
 *       valueType 记录实际值的 Java 类名</li>
 *   <li>{@link CheckpointBarrier} / {@link Watermark} / {@link WatermarkStatus}：
 *       payload 直接内联为可序列化对象，无需 valueType</li>
 * </ul>
 */
public class StreamElementCodec {

    /**
     * 将 StreamElement 编码为 StreamMessageEnvelope。
     *
     * @param element       待编码的流元素
     * @param valueType     StreamRecord 载荷的 Java 类名（仅对 StreamRecord 有意义，可传 null）
     * @param fencingToken  fencing token
     * @param epochId       epoch id
     * @return 编码后的信封
     */
    public static StreamMessageEnvelope encode(StreamElement element, String valueType, String fencingToken, long epochId) {
        if (element == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "element");
        }

        if (element.isRecord()) {
            StreamRecord<?> record = element.asRecord();
            Object serializedPayload = record.getValue() != null ? JsonTool.stringify(record.getValue()) : null;
            String effectiveType = valueType != null ? valueType
                    : (record.getValue() != null ? record.getValue().getClass().getName() : null);
            return new StreamMessageEnvelope(fencingToken, epochId,
                    StreamMessageEnvelope.TYPE_STREAM_RECORD, effectiveType, serializedPayload,
                    record.getTimestamp(), record.hasTimestamp());
        }

        if (element.isCheckpointBarrier()) {
            CheckpointBarrier barrier = element.asCheckpointBarrier();
            return new StreamMessageEnvelope(fencingToken, epochId,
                    StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER, null, barrier);
        }

        if (element.isWatermark()) {
            return new StreamMessageEnvelope(fencingToken, epochId,
                    StreamMessageEnvelope.TYPE_WATERMARK, null, element.asWatermark());
        }

        if (element.isWatermarkStatus()) {
            return new StreamMessageEnvelope(fencingToken, epochId,
                    StreamMessageEnvelope.TYPE_WATERMARK_STATUS, null, element.asWatermarkStatus());
        }

        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Unsupported StreamElement type: " + element.getClass().getName());
    }

    /**
     * 将 StreamMessageEnvelope 解码为 StreamElement。
     *
     * @param envelope 待解码的信封
     * @return 解码后的流元素
     */
    @SuppressWarnings("unchecked")
    public static StreamElement decode(StreamMessageEnvelope envelope) {
        if (envelope == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "envelope");
        }

        String type = envelope.getType();
        if (type == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "type");
        }

        switch (type) {
            case StreamMessageEnvelope.TYPE_STREAM_RECORD: {
                Object payload = envelope.getPayload();
                Object value = payload;
                if (payload instanceof String && envelope.getValueType() != null) {
                    try {
                        Class<?> clazz = Class.forName(envelope.getValueType());
                        value = JsonTool.parseBeanFromText((String) payload, clazz);
                    } catch (ClassNotFoundException e) {
                        throw new StreamException("Failed to load valueType class: " + envelope.getValueType(), e);
                    }
                }
                if (envelope.isHasTimestamp()) {
                    return new StreamRecord<>(value, envelope.getTimestamp());
                } else {
                    return new StreamRecord<>(value);
                }
            }

            case StreamMessageEnvelope.TYPE_CHECKPOINT_BARRIER: {
                if (envelope.getPayload() instanceof CheckpointBarrier) {
                    return (CheckpointBarrier) envelope.getPayload();
                }
                // Fallback: reconstruct from map representation
                return decodeBarrierFromPayload(envelope.getPayload());
            }

            case StreamMessageEnvelope.TYPE_WATERMARK: {
                if (envelope.getPayload() instanceof Watermark) {
                    return (Watermark) envelope.getPayload();
                }
                return decodeWatermarkFromPayload(envelope.getPayload());
            }

            case StreamMessageEnvelope.TYPE_WATERMARK_STATUS: {
                if (envelope.getPayload() instanceof WatermarkStatus) {
                    return (WatermarkStatus) envelope.getPayload();
                }
                return decodeWatermarkStatusFromPayload(envelope.getPayload());
            }

            default:
                throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Unsupported envelope type: " + type);
        }
    }

    private static CheckpointBarrier decodeBarrierFromPayload(Object payload) {
        if (payload instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) payload;
            long id = ((Number) map.get("id")).longValue();
            long timestamp = ((Number) map.get("timestamp")).longValue();
            String cpTypeName = (String) map.get("checkpointType");
            CheckpointType cpType = cpTypeName != null ? CheckpointType.valueOf(cpTypeName) : CheckpointType.CHECKPOINT;
            return new CheckpointBarrier(id, timestamp, cpType);
        }
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Cannot decode CheckpointBarrier from payload: " + payload);
    }

    private static Watermark decodeWatermarkFromPayload(Object payload) {
        if (payload instanceof Watermark) {
            return (Watermark) payload;
        }
        if (payload instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) payload;
            long timestamp = ((Number) map.get("timestamp")).longValue();
            return new Watermark(timestamp);
        }
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Cannot decode Watermark from payload: " + payload);
    }

    private static WatermarkStatus decodeWatermarkStatusFromPayload(Object payload) {
        if (payload instanceof WatermarkStatus) {
            return (WatermarkStatus) payload;
        }
        if (payload instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) payload;
            int status = ((Number) map.get("status")).intValue();
            return new WatermarkStatus(status);
        }
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "Cannot decode WatermarkStatus from payload: " + payload);
    }
}
