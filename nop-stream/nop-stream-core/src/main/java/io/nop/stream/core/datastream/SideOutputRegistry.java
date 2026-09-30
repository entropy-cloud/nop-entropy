/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.datastream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.nop.stream.core.streamrecord.StreamRecord;

/**
 * G-2+09e① (plan 369 Phase 2): registry bridging plan-time side-output consumption
 * ({@code getSideOutput(tag).sink(...)} / {@code print()} / {@code collect(...)}) to the
 * runtime output protocol. Producers emit tagged records via
 * {@code Output#collect(OutputTag, record)}; consumers ({@code ChainingOutput}, the
 * cross-task {@code StreamTaskInvokable} routing) look the consumer up here when their
 * task-local registration map has no entry for the tag.
 *
 * <p>The registry is JVM-local — exactly the scope of the embedded/local execution model
 * where the plan-time {@code getSideOutput} and the producing task share a process.
 * Registering again for the same tag id replaces the previous consumer. Entries are
 * plain function references and small; tests can clear the registry between jobs via
 * {@link #clear()}.
 */
public final class SideOutputRegistry {

    private static final Map<String, Consumer<StreamRecord<?>>> CONSUMERS = new ConcurrentHashMap<>();

    private SideOutputRegistry() {
    }

    /**
     * Registers (or replaces) the consumer for the given side-output tag id.
     */
    public static void register(String tagId, Consumer<StreamRecord<?>> consumer) {
        if (tagId == null || tagId.isEmpty()) {
            throw new IllegalArgumentException("Side-output tag id must not be null or empty");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("Side-output consumer must not be null");
        }
        CONSUMERS.put(tagId, consumer);
    }

    /**
     * Returns the consumer registered for the given tag id, or {@code null} when none
     * is registered (the caller keeps its own no-consumer fail-fast semantics).
     */
    public static Consumer<StreamRecord<?>> get(String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            return null;
        }
        return CONSUMERS.get(tagId);
    }

    /**
     * Removes the registration for the given tag id.
     */
    public static void remove(String tagId) {
        if (tagId != null) {
            CONSUMERS.remove(tagId);
        }
    }

    /**
     * Removes all registrations (test isolation helper).
     */
    public static void clear() {
        CONSUMERS.clear();
    }
}
