/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ⑨ — W-09 (MA-18=C8-010, merged C8-013):
 * declared-state-scale snapshot-budget coupling guard
 * ({@code validateSnapshotBudget}, {@code validateUnalignedConfig}-shaped
 * sibling). A deployment that explicitly DECLARES its state scale
 * ({@code snapshotDeclaredEntries}) and the state-size × budget product
 * exceeds half the checkpoint timeout must be rejected typed at config load
 * ("超时→3 次失败→FAIL→重启→重演" loop prevention). An undeclared scale (0,
 * the default) bypasses the guard — no fabricated estimate (opt-in envelope,
 * honest-source discipline).
 *
 * <p>F10 branch binding: the deterministic red is bound to the DECLARED-config
 * branch (direct {@code validate()} call — the
 * {@code TestCheckpointTransportPersistenceValidation} precedent shape); the
 * runtime-prop estimate branch is NOT set (adjudication D.2 —
 * {@code rocksdb.estimate-num-keys} is a runtime gauge property, unusable at
 * config time). Runtime wiring of the guard at the
 * {@code GraphModelCheckpointExecutor} validate funnel is registered as a
 * structural grep in {@code _tmp/red-green-ledger-2536.md}.
 */
class TestCheckpointSnapshotBudgetValidationR403 {

    @Test
    void testUndeclaredScaleBypassesTheGuard() {
        CheckpointConfig config = new CheckpointConfig();
        assertEquals(0L, config.getSnapshotDeclaredEntries(), "undeclared (0) is the default");
        assertDoesNotThrow(config::validateSnapshotBudget,
                "undeclared scale must bypass the guard (no fabricated estimate)");
    }

    @Test
    void testDeclaredScaleWithinBudgetIsAccepted() {
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointTimeout(600_000L)
                .snapshotDeclaredEntries(100_000L)
                .snapshotSerializeMsPerThousandEntries(1000)
                .build();
        // 100k entries × 1000ms/1k = 100s < 600s × 0.5 = 300s — within budget.
        assertDoesNotThrow(config::validateSnapshotBudget,
                "declared scale inside the budget envelope must be accepted");
    }

    @Test
    void testDeclaredScaleBeyondHalfTheTimeoutIsRejectedTyped() {
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointTimeout(600_000L)
                .snapshotDeclaredEntries(1_000_000L)
                .snapshotSerializeMsPerThousandEntries(1000)
                .build();
        // 1M entries × 1000ms/1k = 1000s > 600s × 0.5 = 300s — beyond budget.
        StreamException e = assertThrows(StreamException.class, config::validateSnapshotBudget,
                "declared scale whose estimated snapshot time exceeds half the checkpoint "
                        + "timeout must be rejected at config load");
        assertEquals(io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(),
                e.getErrorCode(), "typed rejection must carry ERR_STREAM_INVALID_ARG");
        String detail = String.valueOf(e.getMessage()) + e;
        assertTrue(detail.contains("snapshotDeclaredEntries") || detail.contains("1000000"),
                "the failure message must name the declared scale: " + detail);
    }

    @Test
    void testBoundaryExactlyAtHalfTheTimeoutIsAccepted() {
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointTimeout(600_000L)
                .snapshotDeclaredEntries(300_000L)
                .snapshotSerializeMsPerThousandEntries(1000)
                .build();
        // 300k × 1000ms/1k = 300s == 600s × 0.5 — exactly at the bound is the
        // envelope's inclusive edge (guard rejects strictly-greater only).
        assertDoesNotThrow(config::validateSnapshotBudget,
                "estimated time exactly at half the timeout sits on the inclusive edge");
    }

    @Test
    void testNegativeDeclaredScaleIsRejectedAtConfigTime() {
        CheckpointConfig config = new CheckpointConfig();
        assertThrows(StreamException.class, () -> config.setSnapshotDeclaredEntries(-1L),
                "a negative declared scale is a nonsensical declaration — fail fast");
        assertThrows(StreamException.class, () -> config.setSnapshotSerializeMsPerThousandEntries(0),
                "a non-positive serialization rate constant is nonsensical — fail fast");
    }
}
