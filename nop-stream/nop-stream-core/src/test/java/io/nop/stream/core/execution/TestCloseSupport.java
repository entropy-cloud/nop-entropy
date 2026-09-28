/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * S1 regression (plan 366 Phase 2): {@link CloseSupport#accumulate} produces a
 * FLAT suppressed tree matching the pre-CloseSupport inline teardown form —
 * {@code firstError.suppressed = [X, Y, ...]} in close order. The plan-01
 * refactor briefly nested the chain ({@code firstError → X → Y}), changing the
 * shape monitoring/log tooling observes on triple teardown failures.
 */
class TestCloseSupport {

    @Test
    void accumulateFlattensCloseAllSuppressionChain() {
        Exception firstError = new IllegalStateException("emit failed");
        // closeAll returns its first close error carrying later ones as suppressed:
        IOException closeX = new IOException("chain close failed");
        IOException closeY = new IOException("gate close failed");
        closeX.addSuppressed(closeY);

        Exception result = CloseSupport.accumulate(firstError, closeX);

        assertSame(firstError, result, "first error wins");
        assertEquals(2, firstError.getSuppressed().length,
                "suppressed tree must be flat: [X, Y] in close order");
        assertSame(closeX, firstError.getSuppressed()[0]);
        assertSame(closeY, firstError.getSuppressed()[1]);
    }

    @Test
    void accumulatePlainErrorStaysSingleSuppressed() {
        Exception firstError = new IllegalStateException("emit failed");
        IOException closeError = new IOException("close failed");

        Exception result = CloseSupport.accumulate(firstError, closeError);

        assertSame(firstError, result);
        assertEquals(1, firstError.getSuppressed().length);
        assertSame(closeError, firstError.getSuppressed()[0]);
    }

    @Test
    void accumulateNullCases() {
        IOException error = new IOException("only");
        assertSame(error, CloseSupport.accumulate(null, error));
        Exception firstError = new IllegalStateException("emit failed");
        assertSame(firstError, CloseSupport.accumulate(firstError, null));
        assertSame(firstError, CloseSupport.accumulate(firstError, firstError),
                "self-accumulation is a no-op");
        assertEquals(0, firstError.getSuppressed().length);
    }
}
