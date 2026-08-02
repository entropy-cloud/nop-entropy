/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;

/**
 * Shared wire-format normalization for the data-plane codecs (Stage 40).
 *
 * <p>A {@link StreamMessageEnvelope}'s {@code payload} is an inline object for non-record
 * elements (a {@link CheckpointBarrier} / {@link Watermark} / {@link WatermarkStatus}).
 * {@code LocalMessageService} carries the envelope by reference so the raw object is
 * fine, but cross-JVM backends serialize the whole envelope and these control payloads
 * are not JSON-serializable beans. This helper flattens such payloads into plain maps on
 * send; {@link io.nop.stream.core.execution.transport.StreamElementCodec#decode} already
 * reconstructs them from maps on the consumer side, so the round-trip is faithful without
 * changing the envelope's own serialization format (Stage 40 Non-Goal).
 */
final class DataPlaneWireSupport {

    private DataPlaneWireSupport() {
    }

    /**
     * Builds a fully JSON-serializable map view of the envelope (payload normalized).
     */
    static Map<String, Object> toWireMap(StreamMessageEnvelope envelope) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("epochId", envelope.getEpochId());
        map.put("type", envelope.getType());
        map.put("valueType", envelope.getValueType());
        map.put("payload", normalizePayload(envelope.getPayload()));
        map.put("timestamp", envelope.getTimestamp());
        map.put("hasTimestamp", envelope.isHasTimestamp());
        return map;
    }

    private static Object normalizePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof CheckpointBarrier) {
            CheckpointBarrier barrier = (CheckpointBarrier) payload;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", barrier.getId());
            m.put("timestamp", barrier.getTimestamp());
            m.put("checkpointType",
                    barrier.getCheckpointType() != null ? barrier.getCheckpointType().name() : null);
            return m;
        }
        if (payload instanceof Watermark) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestamp", ((Watermark) payload).getTimestamp());
            return m;
        }
        if (payload instanceof WatermarkStatus) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", ((WatermarkStatus) payload).getStatus());
            return m;
        }
        // String (record value, control payload like END_OF_STREAM) — already serializable.
        return payload;
    }
}
