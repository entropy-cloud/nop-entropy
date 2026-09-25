/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

/**
 * Implemented by data-plane consumers that can surface a <em>wire-level</em>
 * decode failure as a typed channel failure instead of silently dropping the
 * affected message.
 *
 * <p>The {@link DataPlaneMessageServiceAdapter} reconstructs envelopes from the
 * backend wire format before they reach the inner consumer. When a delivery
 * cannot be decoded at the wire level ({@code fromWire} throws or returns
 * {@code null}), the adapter forwards the failure through this interface so the
 * consuming channel fails typed — a consumer must never observe a silent gap in
 * its data stream (guide #24 — no silent no-op).
 *
 * <p>Wire-level failures are corruption / version-skew signals, not fencing:
 * stale-epoch messages are valid wire envelopes and are filtered by the
 * channel's epoch check, so failing the channel here is safe.
 */
public interface WireDecodeFailureAware {

    /**
     * Notifies the consumer that a delivered wire message could not be decoded
     * into a {@code StreamMessageEnvelope}. Implementations must fail typed and
     * observably (never discard the message silently).
     *
     * @param topic   the data-plane topic the message was delivered on
     * @param message the raw wire object that could not be decoded
     * @param cause   the decode failure, or {@code null} when the codec returned
     *                {@code null} for an undecodable message
     */
    void onWireDecodeFailure(String topic, Object message, Throwable cause);
}
