/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * F1 focused tests (plan 01 quality-perf Phase 3): the internal window states
 * must produce byte-identical storage keys through the new cached encode
 * {@code cachedStorageKeyFor(namespace, key)} as the direct
 * {@code buildStorageKey} call they replaced — and the single-slot cache must
 * invalidate correctly on every key/namespace switch (including the
 * internal-state namespace duality: the state's own namespace, the backend's
 * current key).
 */
class TestRocksDBInternalStateCachedStorageKey {

    @TempDir
    File tempDir;

    @Test
    void cachedKeyMatchesDirectEncodeForArbitraryPairs() throws Exception {
        RocksDBStateBackend backend = new RocksDBStateBackend(tempDir.getAbsolutePath());
        @SuppressWarnings("unchecked")
        RocksDBKeyedStateBackend<String> keyed =
                (RocksDBKeyedStateBackend<String>) backend.createKeyedStateBackend(String.class);
        try {
            TimeWindow ns1 = new TimeWindow(0L, 100L);
            TimeWindow ns2 = new TimeWindow(100L, 200L);

            keyed.setCurrentKey("k1");
            byte[] direct = keyed.buildStorageKey(ns1, keyed.getCurrentKey());
            byte[] cached = keyed.cachedStorageKeyFor(ns1, keyed.getCurrentKey());
            assertArrayEquals(direct, cached, "cached encode must equal the direct encode");

            // Cache hit with the SAME pair after touching another pair: the slot
            // now holds (ns2,k2); re-asking (ns1,k1) must re-encode identically.
            keyed.setCurrentKey("k2");
            keyed.cachedStorageKeyFor(ns2, keyed.getCurrentKey());
            keyed.setCurrentKey("k1");
            byte[] again = keyed.cachedStorageKeyFor(ns1, keyed.getCurrentKey());
            assertArrayEquals(direct, again,
                    "re-encoding a previously cached pair must reproduce the same bytes");
        } finally {
            keyed.close();
        }
    }

    @Test
    void internalStateIsolatedAcrossKeyAndNamespaceSwitches() throws Exception {
        RocksDBStateBackend backend = new RocksDBStateBackend(tempDir.getAbsolutePath());
        IKeyedStateBackend<String> keyed = backend.createKeyedStateBackend(String.class);
        try {
            IInternalStateBackend<String> internal = (IInternalStateBackend<String>) keyed;
            InternalListState<String, TimeWindow, Long> listState =
                    internal.getInternalListState(new ListStateDescriptor<>("cached-key-list", Long.class));

            ValueState<Long> valueState = keyed.getState(new ValueStateDescriptor<>("cached-key-value", Long.class));

            // Pane A under key k1
            keyed.setCurrentKey("k1");
            TimeWindow windowA = new TimeWindow(0L, 100L);
            listState.setCurrentNamespace(windowA);
            listState.add(1L);

            // Same key, different window namespace → must NOT see pane A
            TimeWindow windowB = new TimeWindow(100L, 200L);
            listState.setCurrentNamespace(windowB);
            assertFalse(listState.get().iterator().hasNext(),
                    "namespace switch must invalidate: window B is empty");

            // Back to window A → value still there (cache invalidated, re-encoded)
            listState.setCurrentNamespace(windowA);
            assertEquals(1L, listState.get().iterator().next(),
                    "returning to window A must re-read the stored pane");

            // Different key → isolated; public value state on the same key must not
            // collide with the internal list state either (namespace duality).
            keyed.setCurrentKey("k2");
            listState.setCurrentNamespace(windowA);
            assertFalse(listState.get().iterator().hasNext(),
                    "key switch must invalidate: k2 pane is empty");
            valueState.update(42L);
            listState.setCurrentNamespace(windowA);
            keyed.setCurrentKey("k2");
            InternalListState<String, TimeWindow, Long> listState2 =
                    internal.getInternalListState(new ListStateDescriptor<>("cached-key-list", Long.class));
            listState2.setCurrentNamespace(windowA);
            assertFalse(listState2.get().iterator().hasNext(),
                    "public value state must not leak into the internal list state of the same key");
        } finally {
            keyed.close();
        }
    }

    @Test
    void bytesRoundTripThroughCacheSharedArray() throws Exception {
        // The cached byte[] is handed to RocksDB by reference; encode twice for
        // the same pair and mutate nothing — arrays must stay equal.
        RocksDBStateBackend backend = new RocksDBStateBackend(tempDir.getAbsolutePath());
        @SuppressWarnings("unchecked")
        RocksDBKeyedStateBackend<String> keyed =
                (RocksDBKeyedStateBackend<String>) backend.createKeyedStateBackend(String.class);
        try {
            keyed.setCurrentKey("shared");
            TimeWindow ns = new TimeWindow(0L, 100L);
            byte[] first = keyed.cachedStorageKeyFor(ns, keyed.getCurrentKey());
            byte[] second = keyed.cachedStorageKeyFor(ns, keyed.getCurrentKey());
            assertArrayEquals(first, second);
            assertEquals(-1, Arrays.mismatch(first, second), "no mismatch means equal");
        } finally {
            keyed.close();
        }
    }
}
