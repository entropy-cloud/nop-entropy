/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.stream.core.exceptions.StreamException;

/**
 * Shared teardown helpers giving the task-teardown sites' common
 * "first failure wins, later ones attach as suppressed" skeleton one
 * implementation.
 *
 * <p>Note: this is NOT a universal close policy. Sites with a different error
 * policy keep their own loops — e.g. {@code RecordWriter.close()} converts
 * {@code InterruptedException} to a typed StreamException per element, and
 * {@code InputGate.close()} logs and continues (best-effort teardown).
 */
public final class CloseSupport {

    private CloseSupport() {
    }

    /**
     * Closes every resource in order; the FIRST failure wins and later ones
     * attach as suppressed exceptions. Every closer is attempted.
     *
     * @return the first failure, or {@code null} when every close succeeded
     */
    public static Exception closeAll(Iterable<? extends AutoCloseable> closers) {
        Exception firstError = null;
        for (AutoCloseable closer : closers) {
            try {
                closer.close();
            } catch (Exception e) {
                if (firstError == null) {
                    firstError = e;
                } else {
                    firstError.addSuppressed(e);
                }
            }
        }
        return firstError;
    }

    /**
     * Same accumulate-suppress contract for element types that expose a
     * {@code close()}-shaped method without implementing {@link AutoCloseable}
     * (e.g. {@code RecordWriter}, {@code OperatorChain}, {@code Output}).
     */
    public static <T> Exception closeAll(Iterable<T> items, ThrowingClose<T> closer) {
        Exception firstError = null;
        for (T item : items) {
            try {
                closer.close(item);
            } catch (Exception e) {
                if (firstError == null) {
                    firstError = e;
                } else {
                    firstError.addSuppressed(e);
                }
            }
        }
        return firstError;
    }

    /** Close-shaped operation on one element, possibly throwing. */
    @FunctionalInterface
    public interface ThrowingClose<T> {
        void close(T item) throws Exception;
    }

    /**
     * Chains {@code error} behind {@code firstError}: null-aware, later errors
     * are attached as suppressed.
     *
     * <p>Shape contract: the result tree is FLAT —
     * {@code firstError.suppressed = [error, error.suppressed...]} in close
     * order — matching the pre-CloseSupport inline teardown form that
     * monitoring/log tooling sees. When {@code error} itself carries suppressed
     * errors (e.g. it is the first-error returned by {@link #closeAll}, with
     * later close errors attached), that chain is hoisted onto
     * {@code firstError}; the original nested attachment on {@code error}
     * cannot be removed, so the hoisted throwables remain reachable through
     * both paths (same instances, zero loss, printed twice by deep tree
     * walkers).
     *
     * @return the error to keep propagating
     */
    public static Exception accumulate(Exception firstError, Exception error) {
        if (firstError == null) {
            return error;
        }
        if (error != null && error != firstError) {
            firstError.addSuppressed(error);
            for (Throwable nested : error.getSuppressed()) {
                firstError.addSuppressed(nested);
            }
        }
        return firstError;
    }

    /**
     * Typed close-error rethrow shared by the writer/chain teardown sites:
     * {@link StreamException} rethrown as-is, {@link RuntimeException} as-is,
     * anything else wrapped in a StreamException with {@code code}.
     */
    public static void throwAsCloseError(Exception error, ErrorCode code) {
        if (error == null) {
            return;
        }
        if (error instanceof StreamException) {
            throw (StreamException) error;
        }
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        throw new StreamException(code, error);
    }
}
