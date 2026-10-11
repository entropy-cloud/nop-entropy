/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

/**
 * Wire codec for the Apache Kafka {@code KafkaMessageService} backend.
 *
 * <p>{@code KafkaMessageService} uses a {@code StringSerializer}/{@code StringDeserializer}
 * pair, so its wire format is a JSON {@code String} — identical in shape to the Pulsar
 * STRING-schema backend. On subscribe the backend wraps the Kafka record value in an
 * {@code ApiMessage} whose {@code data} is that {@code String}.
 *
 * <p>Encoding/decoding is shared with the Pulsar codec via
 * {@link AbstractStringWireCodec} (WI6 plan 2416 — the previously deferred verbatim
 * duplication is resolved; a Kafka deployment's codec no longer carries the Pulsar
 * name).
 */
public final class KafkaStringWireCodec extends AbstractStringWireCodec {

    public static final KafkaStringWireCodec INSTANCE = new KafkaStringWireCodec();
}
