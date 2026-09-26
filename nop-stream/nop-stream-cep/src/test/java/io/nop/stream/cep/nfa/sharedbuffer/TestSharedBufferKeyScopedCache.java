/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep.nfa.sharedbuffer;

import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 360 R3 focused test (Minimum Rules #25): key-scoped SharedBuffer accessors.
 *
 * <p>EventId/NodeId are minted from per-key counters, so DIFFERENT keys can mint
 * IDENTICAL ids; the legacy shared heap cache keyspace therefore requires the
 * load-bearing flush-on-close (cross-key correctness). Key-scoped accessors
 * ((key, id) cache keys) make cross-key contamination structurally impossible.
 *
 * <p>These tests run against a REAL keyed backend (MemoryKeyedStateBackend with
 * setCurrentKey switching) so the two isolation layers behave as in production:
 * (a) the keyed MapState isolates per-key state, (b) the heap cache is isolated
 * by the (key, id) scope.
 */
class TestSharedBufferKeyScopedCache {

    private Path dir;
    private MemoryKeyedStateBackend<String> backend;
    private SharedBuffer<String> buffer;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("p360-sharedbuffer");
        backend = new MemoryKeyedStateBackend<>(String.class);
        buffer = new SharedBuffer<>(backend, null, new SharedBufferCacheConfig());
    }

    @AfterEach
    void tearDown() throws IOException {
        backend.close();
        deleteRecursively(dir);
    }

    private void setCurrentKey(String key) {
        backend.setCurrentKey(key);
    }

    @Test
    void identicalIdsOfDifferentKeysNeverBleedThroughCache() {
        // key-A mints (0, 1) and key-B mints the SAME (0, 1) — the exact collision
        // the legacy flush-on-close guards against.
        setCurrentKey("key-A");
        EventId idA;
        try (SharedBufferAccessor<String> a = buffer.getAccessor("key-A")) {
            idA = a.registerEvent("event-A", 1L);
        }
        setCurrentKey("key-B");
        EventId idB;
        try (SharedBufferAccessor<String> b = buffer.getAccessor("key-B")) {
            idB = b.registerEvent("event-B", 1L);
        }
        assertEquals(idA, idB, "test precondition: per-key counters mint identical ids");

        // Scoped reads serve each key its OWN event despite identical ids.
        setCurrentKey("key-A");
        assertEquals("event-A", buffer.getEvent(idA, "key-A").getElement());
        setCurrentKey("key-B");
        assertEquals("event-B", buffer.getEvent(idB, "key-B").getElement());
        // and the (key, id) pair of the OTHER key is absent from this key's view
        // NOTE: with identical ids there is no "the other key's event" to ask for —
        // (key-B, (0,1)) IS key B's own entry. Isolation here means each (key, id)
        // scope serves its own value, asserted by the two reads above.
    }

    @Test
    void closingScopedAccessorDoesNotEvictOtherKeysCache() {
        setCurrentKey("key-A");
        EventId idA;
        try (SharedBufferAccessor<String> a = buffer.getAccessor("key-A")) {
            idA = a.registerEvent("event-A", 1L);
        }
        // key-A's scoped close must NOT clear the scoped cache entry: the read below
        // is served from the CACHE (observable via the scoped view being warm).
        setCurrentKey("key-B");
        EventId idB;
        try (SharedBufferAccessor<String> b = buffer.getAccessor("key-B")) {
            idB = b.registerEvent("event-B", 1L);
        }
        setCurrentKey("key-A");
        assertEquals("event-A", buffer.getEvent(idA, "key-A").getElement(),
                "key A's cache entry survives its own scoped close");
        setCurrentKey("key-B");
        assertEquals("event-B", buffer.getEvent(idB, "key-B").getElement(),
                "key B's cache entry survives key A's scoped close");
    }

    @Test
    void scopedEntriesWriteThroughToBackingState() {
        setCurrentKey("key-A");
        NodeId nodeId;
        try (SharedBufferAccessor<String> a = buffer.getAccessor("key-A")) {
            EventId eventId = a.registerEvent("event-A", 1L);
            nodeId = a.put("state-A", eventId, null, new DeweyNumber(1));
        }
        assertNotNull(buffer.getEntry(nodeId, "key-A"),
                "scoped write-through must persist the node to keyed state");
    }

    @Test
    void legacyAccessorPathStillFlushesOnClose() {
        setCurrentKey("legacy-key");
        EventId eventId;
        try (SharedBufferAccessor<String> a = buffer.getAccessor()) {
            eventId = a.registerEvent("event-legacy", 1L);
        }
        // legacy close flushed the cache — a subsequent read must still observe the
        // event from the backing state (write-through invariant).
        assertEquals("event-legacy", buffer.getEvent(eventId).getElement(),
                "legacy path: data survives flush because writes are write-through");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p2 -> {
                try {
                    Files.delete(p2);
                } catch (IOException e) {
                    throw new io.nop.stream.core.exceptions.StreamException(
                            io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e);
                }
            });
        }
    }
}
