/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

/**
 * Wire codec for the Apache Pulsar {@code PulsarMessageService} backend (Stage 40).
 *
 * <p>{@code PulsarMessageService} with the default {@code Schema.STRING} producer
 * requires a {@code String} value; sending a bare envelope object fails the schema.
 * On subscribe the backend wraps the Pulsar message value in an {@code ApiMessage}
 * whose {@code data} is that {@code String}.
 *
 * <p>Pulsar's built-in producer flow control (queue full → send back-pressure) provides
 * cross-JVM back-pressure; nop-stream builds no credit-based / ACK_WINDOW layer
 * (vision §三 constraint 7). Encoding/decoding is shared with the Kafka codec via
 * {@link AbstractStringWireCodec} (WI6 plan 2416).
 */
public final class PulsarStringWireCodec extends AbstractStringWireCodec {

    public static final PulsarStringWireCodec INSTANCE = new PulsarStringWireCodec();
}
