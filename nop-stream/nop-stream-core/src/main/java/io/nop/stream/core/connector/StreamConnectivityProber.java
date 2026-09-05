/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector;

import io.nop.stream.core.common.functions.sink.TwoPhaseCommitSinkFunction;
import io.nop.stream.core.source.Source;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTIVITY_CHECK_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_CONNECTIVITY_NOT_SUPPORTED;

/**
 * Item 20 (P-REQ-13) dry-run probe driver (pre-submit-validation-design.md §4):
 * probes ONE resolved endpoint instance by dispatching on probe contracts in the
 * adjudicated order — {@link ConnectivityCheckable} capability interface first, then
 * the FLIP-27 {@link Source} contract (H-1: {@code createEnumerator()} +
 * {@code start()} with a no-op assignment context — directory reachability), then the
 * {@link TwoPhaseCommitSinkFunction} contract (H-5 base form: {@code beginTransaction()}
 * + {@code rollback()} for third-party 2PC sinks) — and reports an explicit
 * {@code SKIP} when the endpoint implements none of them (never a silent pass).
 *
 * <p>The driver depends on core contracts only (zero dependency on concrete connector
 * modules); probe implementations live in the connector modules / endpoint classes.
 */
public final class StreamConnectivityProber {

    private StreamConnectivityProber() {
    }

    /**
     * Probes the given endpoint instance. Never throws — every failure is converted
     * into a {@link ConnectivityProbeOutcome.Status#FAIL} outcome carrying a typed
     * error code and the underlying detail.
     */
    public static ConnectivityProbeOutcome probe(String target, Object endpoint) {
        if (endpoint == null) {
            return ConnectivityProbeOutcome.fail(ERR_STREAM_CONNECTIVITY_CHECK_FAILED.getErrorCode(),
                    "endpoint instance is null");
        }
        // D3 dispatch order: capability interface → FLIP-27 Source → 2PC sink → explicit skip
        if (endpoint instanceof ConnectivityCheckable checkable) {
            try {
                checkable.checkConnection();
                return ConnectivityProbeOutcome.pass(
                        "ConnectivityCheckable.checkConnection passed (" + endpointClassName(endpoint) + ")");
            } catch (Exception e) {
                return ConnectivityProbeOutcome.fail(ERR_STREAM_CONNECTIVITY_CHECK_FAILED.getErrorCode(),
                        endpointClassName(endpoint) + ": " + rootMessage(e));
            }
        }
        if (endpoint instanceof Source<?, ?, ?> source) {
            // H-1: no-op assignment context — start() performs the reachability probe
            // (e.g. directory scan) without delivering any split to any reader.
            try {
                Object enumerator = source.createEnumerator();
                try {
                    ((io.nop.stream.core.source.SplitEnumerator<?, ?>) enumerator)
                            .start(new io.nop.stream.core.source.SplitEnumeratorContext<>(1, null));
                } finally {
                    if (enumerator instanceof AutoCloseable closeable) {
                        closeable.close();
                    }
                }
                return ConnectivityProbeOutcome.pass(
                        "FLIP-27 enumerator start() probe passed (" + endpointClassName(endpoint) + ")");
            } catch (Exception e) {
                return ConnectivityProbeOutcome.fail(ERR_STREAM_CONNECTIVITY_CHECK_FAILED.getErrorCode(),
                        endpointClassName(endpoint) + ": " + rootMessage(e));
            }
        }
        if (endpoint instanceof TwoPhaseCommitSinkFunction<?> sink) {
            // H-5 base form for 2PC sinks without a ConnectivityCheckable implementation
            try {
                sink.beginTransaction();
                sink.rollback();
                return ConnectivityProbeOutcome.pass(
                        "two-phase-commit beginTransaction()+rollback() probe passed ("
                                + endpointClassName(endpoint) + ")");
            } catch (Exception e) {
                return ConnectivityProbeOutcome.fail(ERR_STREAM_CONNECTIVITY_CHECK_FAILED.getErrorCode(),
                        endpointClassName(endpoint) + ": " + rootMessage(e));
            }
        }
        return ConnectivityProbeOutcome.skip(ERR_STREAM_CONNECTIVITY_NOT_SUPPORTED.getErrorCode(),
                endpointClassName(endpoint) + " implements no probe contract "
                        + "(ConnectivityCheckable / FLIP-27 Source / TwoPhaseCommitSinkFunction); "
                        + "connectivity undetermined by dry-run");
    }

    private static String endpointClassName(Object endpoint) {
        return endpoint.getClass().getName();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String message = t.getMessage();
        return (message == null || message.isBlank()) ? t.getClass().getName() : message;
    }
}
