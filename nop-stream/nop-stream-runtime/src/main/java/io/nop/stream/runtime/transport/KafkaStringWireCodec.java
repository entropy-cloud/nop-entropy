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
 * Wire codec for the Apache Kafka {@code KafkaMessageService} backend.
 *
 * <p>{@code KafkaMessageService} uses a {@code StringSerializer}/{@code StringDeserializer}
 * pair, so its wire format is a JSON {@code String} — identical in shape to the Pulsar
 * STRING-schema backend. On subscribe the backend wraps the Kafka record value in an
 * {@code ApiMessage} whose {@code data} is that {@code String}.
 *
 * <p>This codec therefore mirrors {@link PulsarStringWireCodec} exactly:
 * <ul>
 *   <li><b>send</b>: serializes the envelope to a JSON {@code String}, which
 *       {@code KafkaMessageService.sendAsync} passes to the StringSerializer.</li>
 *   <li><b>receive</b>: reads the JSON {@code String} out of the delivered
 *       {@code ApiMessage} and reconstructs the envelope.</li>
 * </ul>
 *
 * <p><strong>Why a separate class instead of reusing {@link PulsarStringWireCodec}.</strong>
 * {@code PulsarStringWireCodec} is {@code final} (cannot be extended), and naming a Kafka
 * deployment's codec "Pulsar" is misleading. This is a thin parallel implementation that
 * shares {@link DataPlaneWireSupport} for the actual (de)serialization logic, so there is
 * zero logic duplication — only the backend-identifying class name differs.
 *
 * <p>The codec only references {@link ApiMessage} / {@link JsonTool} — it does NOT depend
 * on {@code KafkaMessageService} or {@code kafka-clients}, keeping {@code nop-stream-runtime}
 * backend-dep-free (vision §三 constraint 8).
 */
public final class KafkaStringWireCodec implements IDataPlaneWireCodec {

    public static final KafkaStringWireCodec INSTANCE = new KafkaStringWireCodec();

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
