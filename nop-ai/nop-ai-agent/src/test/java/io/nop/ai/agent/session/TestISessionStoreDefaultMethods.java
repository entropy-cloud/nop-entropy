package io.nop.ai.agent.session;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestISessionStoreDefaultMethods {

    private final InMemorySessionStore store = new InMemorySessionStore();

    @Test
    void forkSessionThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.forkSession("s1", true, Collections.emptyMap()));
        assertEquals("forkSession requires VfsSessionStore", ex.getMessage());
    }

    @Test
    void appendEventThrowsUOE() {
        VfsEvent event = new VfsEvent("test", Collections.emptyMap(), System.currentTimeMillis());
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.appendEvent("s1", event));
        assertEquals("appendEvent requires VfsSessionStore", ex.getMessage());
    }

    @Test
    void compactThrowsUOE() {
        CompactConfig config = new CompactConfig(1000, "truncate", true);
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.compact("s1", config));
        assertEquals("compact requires VfsSessionStore", ex.getMessage());
    }

    @Test
    void loadSnapshotThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.loadSnapshot("s1", "snap-1"));
        assertEquals("loadSnapshot requires VfsSessionStore", ex.getMessage());
    }

    @Test
    void setPlanRefThrowsUOE() {
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> store.setPlanRef("s1", "plan-1"));
        assertEquals("setPlanRef requires VfsSessionStore", ex.getMessage());
    }
}
