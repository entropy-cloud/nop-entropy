/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.operators.TimerStateKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI21 (§八 9): timer state is REQUIRED state for window and CEP exactly-once.
 * The snapshot-key contract is now carried by shared constants
 * ({@link TimerStateKeys}) instead of per-operator private literals, so the
 * durable-snapshot key names are pinned in one place:
 *
 * <ul>
 *   <li>{@code WindowOperator} snapshots its timers under
 *       {@code WINDOW_INTERNAL_TIMERS} ("internal-timers") — runtime behavior
 *       (timers survive checkpoint→restore) is proven end-to-end by
 *       {@code TestTimerCheckpointRestoreE2E};</li>
 *   <li>{@code CepOperator} snapshots its per-key event-time timer ledger under
 *       {@code CEP_EVENT_TIME_TIMERS} ("cep-event-time-timers") — runtime
 *       behavior proven by {@code TestCepCheckpointRestoreE2E} /
 *       {@code TestCepEventTimeTimerRegistry}.</li>
 * </ul>
 *
 * <p>The values are historical literals preserved verbatim: existing durable
 * snapshots on disk key their timer state under these names, so renaming would
 * silently orphan restored timers.
 */
public class TestTimerStateSnapshotContract {

    @Test
    public void timerSnapshotKeysArePinnedSharedConstants() {
        // historical literal compat — existing durable snapshots key timer state
        // under exactly these names
        assertEquals("internal-timers", TimerStateKeys.WINDOW_INTERNAL_TIMERS,
                "the window timer key is a durable-snapshot contract — do not rename");
        assertEquals("cep-event-time-timers", TimerStateKeys.CEP_EVENT_TIME_TIMERS,
                "the CEP timer ledger key is a durable-snapshot contract — do not rename");
        // the two contracts must never collide (a shared key would make one
        // operator's snapshotState read the other's timer payload)
        org.junit.jupiter.api.Assertions
                .assertNotEquals(TimerStateKeys.WINDOW_INTERNAL_TIMERS, TimerStateKeys.CEP_EVENT_TIME_TIMERS);
    }
}
