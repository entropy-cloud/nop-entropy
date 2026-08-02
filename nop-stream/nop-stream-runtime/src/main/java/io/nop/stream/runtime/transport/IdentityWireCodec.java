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
 * Passthrough codec for the in-process {@code LocalMessageService}: the envelope
 * object is carried by reference, so no wire-format adaptation is needed.
 *
 * <p>This is the default codec used when no cross-JVM backend is wired (single-JVM
 * execution / unit tests). Deployments that inject {@code SysDaoMessageService} or
 * {@code PulsarMessageService} select the matching codec instead.
 */
public final class IdentityWireCodec implements IDataPlaneWireCodec {

    public static final IdentityWireCodec INSTANCE = new IdentityWireCodec();

    @Override
    public Object toWire(StreamMessageEnvelope envelope) {
        return envelope;
    }

    @Override
    public StreamMessageEnvelope fromWire(Object message) {
        if (message instanceof StreamMessageEnvelope) {
            return (StreamMessageEnvelope) message;
        }
        return null;
    }
}
