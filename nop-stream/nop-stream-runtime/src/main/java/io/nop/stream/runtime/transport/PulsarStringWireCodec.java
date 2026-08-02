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
 * Wire codec for the Apache Pulsar {@code PulsarMessageService} backend (Stage 40).
 *
 * <p>{@code PulsarMessageService} with the default {@code Schema.STRING} producer
 * requires a {@code String} value; sending a bare envelope object fails the schema.
 * On subscribe the backend wraps the Pulsar message value in an {@code ApiMessage}
 * whose {@code data} is that {@code String}.
 *
 * <p>This codec therefore:
 * <ul>
 *   <li><b>send</b>: serializes the envelope to a JSON {@code String}, which
 *       {@code PulsarHelper.buildPulsarMessage} passes through to the STRING-schema
 *       producer.</li>
 *   <li><b>receive</b>: reads the JSON {@code String} out of the delivered
 *       {@code ApiMessage} (or bare string) and reconstructs the envelope.</li>
 * </ul>
 *
 * <p>Pulsar's built-in producer flow control (queue full → send back-pressure) provides
 * cross-JVM back-pressure; nop-stream builds no credit-based / ACK_WINDOW layer
 * (vision §三 constraint 7). The codec only references {@link ApiMessage} /
 * {@link JsonTool} — it does NOT depend on {@code PulsarMessageService}, keeping
 * {@code nop-stream-runtime} backend-dep-free.
 */
public final class PulsarStringWireCodec implements IDataPlaneWireCodec {

    public static final PulsarStringWireCodec INSTANCE = new PulsarStringWireCodec();

    @Override
    public Object toWire(StreamMessageEnvelope envelope) {
        // Payload control objects (barrier/watermark) are not JSON beans, so the envelope
        // is flattened to a fully-serializable map first, then stringified for Pulsar's
        // STRING-schema producer.
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
