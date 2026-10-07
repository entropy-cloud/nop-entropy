/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WI8 Phase 2 (plan 2332) shared static store behind the side-output pipeline E2E
 * sinks ({@code SideOutputMainSink} / {@code SideOutputTagSink}). Keyed buckets let
 * one store serve both sinks while keeping their collections separately assertable.
 */
final class SideOutputSinkStore {

    private static final ConcurrentHashMap<String, List<Integer>> BUCKETS =
            new ConcurrentHashMap<>();

    private SideOutputSinkStore() {
    }

    static void add(String key, Integer value) {
        BUCKETS.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(value);
    }

    static List<Integer> get(String key) {
        List<Integer> bucket = BUCKETS.get(key);
        return bucket == null ? List.of() : new ArrayList<>(bucket);
    }

    static void clear() {
        BUCKETS.clear();
    }
}
