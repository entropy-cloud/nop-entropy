/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 360 R1 focused test (Minimum Rules #25): the (currentKey, currentNamespace)
 * → storage-key cache must invalidate on every key or namespace switch — a stale
 * prefix would silently read/write another key's state.
 *
 * <p>Interleaves keys and namespaces and verifies every read observes the value
 * written under the exact (key, namespace) pair, with cache-hits (same pair
 * repeated) and cache-misses (switches) alternating.
 */
class TestPlan360KeyCacheInvalidation {

    private Path dir;
    private RocksDBKeyedStateBackend<Long> backend;
    private ValueState<String> state;

    @BeforeEach
    void setUp() throws IOException {
        dir = Files.createTempDirectory("p360-key-cache");
        backend = new RocksDBKeyedStateBackend<>(dir.toAbsolutePath().toString(), Long.class, 128, null);
        state = backend.getState(new io.nop.stream.core.common.state.ValueStateDescriptor<>("p360cache", String.class));
    }

    @AfterEach
    void tearDown() throws IOException {
        backend.close();
        deleteRecursively(dir);
    }

    @Test
    void cacheInvalidatesOnEveryKeyAndNamespaceSwitch() throws Exception {
        // write under (k=1, ns=A) — miss
        backend.setCurrentKey(1L);
        backend.setCurrentNamespace("A");
        state.update("1A");
        // cache hit path: repeat the same pair, must read the same value
        assertEquals("1A", state.value());
        assertEquals("1A", state.value());

        // switch namespace only — miss, must NOT see 1A
        backend.setCurrentNamespace("B");
        assertNull(state.value(), "namespace switch must invalidate the cached storage key");
        state.update("1B");

        // switch key only — miss, must NOT see 1B
        backend.setCurrentKey(2L);
        backend.setCurrentNamespace("B");
        assertNull(state.value(), "key switch must invalidate the cached storage key");
        state.update("2B");

        // return to the original pair — miss (evicted), must read its own value
        backend.setCurrentKey(1L);
        backend.setCurrentNamespace("A");
        assertEquals("1A", state.value());
        // and the intermediate pair is intact
        backend.setCurrentKey(2L);
        backend.setCurrentNamespace("B");
        assertEquals("2B", state.value());
    }

    @Test
    void interleavedPairsAlwaysReadTheirOwnValue() throws Exception {
        for (long key = 0; key < 8; key++) {
            for (String ns : new String[]{"A", "B"}) {
                backend.setCurrentKey(key);
                backend.setCurrentNamespace(ns);
                state.update(key + ns);
            }
        }
        // second pass hits the cache for the (key, ns) written last in each key group
        for (long key = 0; key < 8; key++) {
            for (String ns : new String[]{"A", "B"}) {
                backend.setCurrentKey(key);
                backend.setCurrentNamespace(ns);
                assertEquals(key + ns, state.value(),
                        "each (key, namespace) pair must read its own value after cache warm");
            }
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new io.nop.stream.core.exceptions.StreamException(
                            io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR, e);
                }
            });
        }
    }
}
