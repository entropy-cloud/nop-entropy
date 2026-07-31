package io.nop.ai.agent.session;

import io.nop.ai.agent.NopAiAgentErrors;
import io.nop.ai.agent.engine.NopAiAgentException;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestISessionStoreDefaultMethods {

    private final InMemorySessionStore store = new InMemorySessionStore();

    @Test
    void appendEventThrowsNopAiAgentException() {
        VfsEvent event = new VfsEvent("test", Collections.emptyMap(), System.currentTimeMillis());
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.appendEvent("s1", event));
        assertEquals(NopAiAgentErrors.ERR_AGENT_SESSION_APPEND_EVENT_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("appendEvent requires VfsSessionStore", ex.getDescription());
    }

    @Test
    void compactThrowsNopAiAgentException() {
        CompactConfig config = new CompactConfig(1000, "truncate", true);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.compact("s1", config));
        assertEquals(NopAiAgentErrors.ERR_AGENT_SESSION_COMPACT_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("compact requires VfsSessionStore", ex.getDescription());
    }

    @Test
    void loadSnapshotThrowsNopAiAgentException() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.loadSnapshot("s1", "snap-1"));
        assertEquals(NopAiAgentErrors.ERR_AGENT_SESSION_LOAD_SNAPSHOT_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("loadSnapshot requires VfsSessionStore", ex.getDescription());
    }

    @Test
    void setPlanRefThrowsNopAiAgentException() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> store.setPlanRef("s1", "plan-1"));
        assertEquals(NopAiAgentErrors.ERR_AGENT_SESSION_SET_PLAN_REF_NOT_SUPPORTED.getErrorCode(), ex.getErrorCode());
        assertEquals("setPlanRef requires VfsSessionStore", ex.getDescription());
    }
}
