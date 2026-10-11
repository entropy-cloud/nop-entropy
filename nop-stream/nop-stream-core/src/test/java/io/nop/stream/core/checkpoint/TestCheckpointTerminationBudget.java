/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI10 (plan 2426): the nullable termination-budget knob. {@code null} resolves
 * to the general checkpoint timeout (pre-WI10 behavior, byte-identical); an
 * explicit value overrides it. Default-value adjudication across driver faces
 * is deferred to WI11 on differential evidence (WI9 §4.3 1c).
 */
class TestCheckpointTerminationBudget {

    @Test
    void nullFallsBackToCheckpointTimeout() {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointTimeout(1234L);
        assertNull(config.getTerminationCheckpointTimeoutMs(), "default must be null (no behavior change)");
        assertEquals(1234L, config.resolveTerminationCheckpointTimeout(),
                "null termination knob resolves to the checkpoint timeout");
    }

    @Test
    void explicitValueOverrides() {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointTimeout(600_000L);
        config.setTerminationCheckpointTimeoutMs(60_000L);
        assertEquals(60_000L, config.resolveTerminationCheckpointTimeout(),
                "explicit termination budget overrides the checkpoint timeout");
        // independent of the general timeout
        config.setCheckpointTimeout(999L);
        assertEquals(60_000L, config.resolveTerminationCheckpointTimeout());
    }

    @Test
    void resetToNullRestoresFallback() {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointTimeout(555L);
        config.setTerminationCheckpointTimeoutMs(11L);
        config.setTerminationCheckpointTimeoutMs(null);
        assertEquals(555L, config.resolveTerminationCheckpointTimeout());
    }

    @Test
    void builderCarriesOverride() {
        CheckpointConfig config = CheckpointConfig.builder()
                .checkpointEnabled(true)
                .terminationCheckpointTimeoutMs(7_000L)
                .build();
        assertEquals(7_000L, config.resolveTerminationCheckpointTimeout());
    }
}
