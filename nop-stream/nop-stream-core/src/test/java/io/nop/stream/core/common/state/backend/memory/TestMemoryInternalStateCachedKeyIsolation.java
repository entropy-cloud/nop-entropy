/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.memory;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.backend.IInternalStateBackend;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * F2 focused tests (plan 01 quality-perf Phase 3): the internal appending/list
 * states now route through the backend's cached
 * {@code cachedNamespaceAndKey(namespace, key)} (the same shape the value and
 * aggregating flavors have used since 360 R1). These tests pin the cache's
 * invalidation contract for those flavors: every key/namespace switch must
 * isolate panes — no cross-namespace or cross-key bleed through the shared
 * cached key instance.
 */
class TestMemoryInternalStateCachedKeyIsolation {

    @Test
    void internalListStateIsolatedAcrossSwitches() throws Exception {
        MemoryKeyedStateBackend<String> keyed = new MemoryKeyedStateBackend<>(String.class);
        try {
            IInternalStateBackend<String> internal = (IInternalStateBackend<String>) keyed;
            InternalListState<String, TimeWindow, Long> listState =
                    internal.getInternalListState(new ListStateDescriptor<>("cached-mem-list", Long.class));

            keyed.setCurrentKey("k1");
            TimeWindow windowA = new TimeWindow(0L, 100L);
            listState.setCurrentNamespace(windowA);
            listState.add(1L);
            listState.add(2L);

            TimeWindow windowB = new TimeWindow(100L, 200L);
            listState.setCurrentNamespace(windowB);
            org.junit.jupiter.api.Assertions.assertFalse(listState.get().iterator().hasNext(),
                    "namespace switch must isolate: window B is empty");

            listState.setCurrentNamespace(windowA);
            assertEquals(2, listState.get().spliterator().getExactSizeIfKnown(),
                    "returning to window A must see the stored pane (cache re-keyed)");

            keyed.setCurrentKey("k2");
            listState.setCurrentNamespace(windowA);
            org.junit.jupiter.api.Assertions.assertFalse(listState.get().iterator().hasNext(),
                    "key switch must isolate: k2 pane is empty");
        } finally {
            keyed.close();
        }
    }
}
