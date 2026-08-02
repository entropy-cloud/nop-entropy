/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.core.reflect.IFunctionModel;
import io.nop.rpc.core.reflect.DefaultRpcMessageTransformer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stage 39 Phase 2: RPC message transformer for the streaming control plane.
 *
 * <p>Extends the platform {@link DefaultRpcMessageTransformer} (argument-by-name
 * mapping) with two streaming-specific transport policies:
 * <ul>
 *   <li><b>Void control calls are one-way</b> (fire-and-forget). The control plane
 *       is dominated by void signals ({@code receiveAssignment}, {@code triggerCheckpoint},
 *       {@code cancelTask}, {@code updateFencingToken}, {@code receiveCheckpointAck},
 *       {@code reportTaskStatus}, …). The coordinator must not block waiting for a
 *       per-task response on every barrier/assignment; the wiring-verification contract
 *       (plan guide #23) is asserted via observable side effects (state changes /
 *       counters), not via a return value. So void methods are marked
 *       {@link ApiHeaders#setOneWay oneWay}.</li>
 *   <li><b>Non-void calls get a default timeout</b> ({@link #DEFAULT_TIMEOUT_MS}) so
 *       request-response methods (e.g. {@code getJobStatus()}) do not wait forever
 *       when the caller omitted an explicit timeout.</li>
 * </ul>
 *
 * <p>This is a documented customization of the supported transformer extension point
 * (plan Phase 2: "verify default reflective dispatch first, add an adapter only if
 * insufficient"). Reflective dispatch IS sufficient; only the one-way/timeout policy
 * is streaming-specific.
 */
public class StreamControlRpcTransformer extends DefaultRpcMessageTransformer {

    public static final StreamControlRpcTransformer INSTANCE = new StreamControlRpcTransformer();

    /** Default request-response timeout when the caller omits one (30s). */
    static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /**
     * Monotonic request-id sequence for request-response correlation over
     * {@code MessageRpcClient} (which, unlike {@code SimpleRpcClient}, does not
     * synthesize a request id — it reads {@link ApiHeaders#getId}). One-way calls
     * do not need correlation but a unique id is harmless and useful for tracing.
     */
    private final AtomicLong requestIdSeq = new AtomicLong(0);

    @Override
    public ApiRequest<Object> toRequest(String serviceName, IFunctionModel method, Object[] args) {
        ApiRequest<Object> req = super.toRequest(serviceName, method, args);

        Class<?> rawReturn = method.getReturnType().getRawClass();
        if (rawReturn == void.class || rawReturn == Void.class) {
            // Fire-and-forget control signal: do not block the coordinator waiting
            // for a per-task response. The server still processes the call; its
            // (empty) response is simply discarded by the message transport.
            ApiHeaders.setOneWay(req);
        } else {
            // Request-response: ensure a timeout so callers do not wait forever.
            if (ApiHeaders.getTimeout(req, -1L) <= 0L) {
                ApiHeaders.setTimeout(req, DEFAULT_TIMEOUT_MS);
            }
        }

        // MessageRpcClient reads the request id for response correlation; synthesize
        // one if the caller did not provide it (idempotent for retries that reuse id).
        if (ApiHeaders.getId(req) == null) {
            ApiHeaders.setId(req, "stream-rpc-" + requestIdSeq.incrementAndGet());
        }
        return req;
    }
}
