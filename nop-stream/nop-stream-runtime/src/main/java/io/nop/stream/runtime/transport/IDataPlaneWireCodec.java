/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.stream.core.execution.transport.StreamMessageEnvelope;

/**
 * Adapts a {@link StreamMessageEnvelope} to / from the wire format expected by a
 * concrete {@code IMessageService} backend (Stage 40).
 *
 * <p><strong>Why this exists.</strong> The data-plane {@code Remote*} classes hand a
 * {@link StreamMessageEnvelope} to {@code IMessageService.send(...)}. The in-process
 * {@code LocalMessageService} carries the object reference verbatim, but the
 * production backends serialize and only faithfully preserve specific shapes:
 * <ul>
 *   <li>{@code SysDaoMessageService} persists an {@code ApiRequest}'s {@code data}
 *       field; a bare non-{@code ApiRequest} envelope loses its body (only the class
 *       name is recorded).</li>
 *   <li>{@code PulsarMessageService} (default {@code Schema.STRING}) requires a
 *       {@code String} value; a bare envelope fails the schema.</li>
 * </ul>
 *
 * <p>A codec therefore converts the envelope into the backend's faithful wire shape on
 * send, and reconstructs it from the shape the backend delivers on subscribe. The
 * {@code Remote*} classes stay backend-agnostic; the codec is selected per deployment
 * and applied transparently by {@link DataPlaneMessageServiceAdapter}.
 *
 * <p>Codec implementations do NOT depend on the backend's classes — they only produce /
 * consume the wire <em>format</em> ({@code ApiRequest} map / JSON string) the backend
 * expects. This keeps {@code nop-stream-runtime} free of hard backend dependencies
 * (vision §三 constraint 8: platform integration priority).
 */
public interface IDataPlaneWireCodec {

    /**
     * Converts an envelope into the wire shape the backend faithfully carries.
     *
     * @param envelope the envelope to send (never null)
     * @return the backend-specific wire object
     */
    Object toWire(StreamMessageEnvelope envelope);

    /**
     * Reconstructs an envelope from whatever the backend delivers to a subscriber.
     *
     * @param message the wire object delivered by the backend
     * @return the reconstructed envelope, or {@code null} if it cannot be decoded
     *         (the caller will discard it)
     */
    StreamMessageEnvelope fromWire(Object message);
}
