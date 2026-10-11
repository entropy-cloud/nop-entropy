/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import java.util.Map;

import io.nop.api.core.beans.ApiMessage;
import io.nop.core.lang.json.JsonTool;
import io.nop.stream.core.execution.transport.StreamMessageEnvelope;

/**
 * Shared string-schema wire codec implementation for STRING-schema message backends
 * (WI6 plan 2416 — resolves the deliberately deferred Kafka/Pulsar duplication).
 *
 * <p>Backends of this family require a {@code String} payload (Kafka
 * {@code StringSerializer}; Pulsar {@code Schema.STRING}), so the wire format is a
 * JSON {@code String}: <b>send</b> flattens the envelope via
 * {@link DataPlaneWireSupport#toWireMap} (payload control objects such as
 * barrier/watermark are not JSON beans) and stringifies it; <b>receive</b> unwraps the
 * backend's {@link ApiMessage} envelope and reconstructs the {@link StreamMessageEnvelope}
 * from the JSON text or map.
 *
 * <p>Any new wire form or decode fix lands here once; the backend-specific subclasses
 * ({@link KafkaStringWireCodec}, {@link PulsarStringWireCodec}) carry only naming and
 * backend-contract documentation. The codecs reference only {@link ApiMessage} /
 * {@link JsonTool} — no backend client dependency, keeping {@code nop-stream-runtime}
 * backend-dep-free (vision §三 constraint 8).
 */
abstract class AbstractStringWireCodec implements IDataPlaneWireCodec {

    @Override
    public Object toWire(StreamMessageEnvelope envelope) {
        return JsonTool.stringify(DataPlaneWireSupport.toWireMap(envelope));
    }

    @Override
    public StreamMessageEnvelope fromWire(Object message) {
        Object data = extractData(message);
        if (data == null) {
            return null;
        }
        if (data instanceof StreamMessageEnvelope) {
            return (StreamMessageEnvelope) data;
        }
        if (data instanceof String) {
            return JsonTool.parseBeanFromText((String) data, StreamMessageEnvelope.class);
        }
        if (data instanceof Map) {
            return (StreamMessageEnvelope) JsonTool.jsonObjectToBean(data, StreamMessageEnvelope.class);
        }
        return null;
    }

    private static Object extractData(Object message) {
        if (message instanceof ApiMessage) {
            return ((ApiMessage) message).getData();
        }
        return message;
    }
}
