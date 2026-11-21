package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentExecStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 2584 Phase 2 (audit Minor): the retained terminal-status cache is
 * LRU-bounded — beyond {@link AgentSessionLifecycle#TERMINAL_STATUSES_MAX}
 * entries the OLDEST terminal status is evicted and
 * {@code getEvictedTerminalStatus} degrades to null for it (observability
 * downgrade, registered in the eviction INFO).
 */
public class TestAgentSessionLifecycleTerminalStatusLru_2584 {

    @Test
    void terminalStatusCacheIsLruBounded() {
        AgentSessionLifecycle lifecycle = new AgentSessionLifecycle(
                null, null, null, "inst-lru", null, null, null, null, null, null, null, null);

        int cap = AgentSessionLifecycle.TERMINAL_STATUSES_MAX;
        // sessionStore is null → the store remove fails and is WARNed, but the
        // terminal-status retention (the face under test) still happens
        for (int i = 0; i < cap + 5; i++) {
            lifecycle.removeTerminalSessionFromStore("lru-sess-" + i, AgentExecStatus.failed);
        }

        assertNull(lifecycle.getEvictedTerminalStatus("lru-sess-0"),
                "the OLDEST terminal status must be LRU-evicted (degrades to null)");
        assertEquals(AgentExecStatus.failed,
                lifecycle.getEvictedTerminalStatus("lru-sess-" + (cap + 4)),
                "the newest terminal status must be retained");
    }
}
