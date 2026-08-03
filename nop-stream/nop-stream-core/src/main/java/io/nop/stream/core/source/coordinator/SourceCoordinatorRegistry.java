/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.source.coordinator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.nop.api.core.annotations.core.Internal;

import io.nop.stream.core.source.SourceSplit;

/**
 * Stage 49 D3 (LOCAL-mode landing): process-wide registry of {@link LocalSourceCoordinator}s
 * keyed by source vertex id. Used by {@code SourceReaderOperator} to look up the
 * coordinator that owns its source vertex's enumerator.
 *
 * <p>The registry is populated lazily: the first subtask to open a {@code SourceReaderOperator}
 * for a vertex creates and registers the coordinator; subsequent subtasks reuse it. The
 * execution path (LOCAL: {@code StreamExecutionEnvironment.execute()}) is responsible for
 * {@link #unregister(Integer) unregistering} after the job finishes to avoid leaks across
 * executions of the same vertex id.
 *
 * <p><strong>v1 limitation</strong>: keyed by vertex id only (not jobId+vertexId). This
 * means at most one active execution per source vertex id in the JVM at a time — fine for
 * LOCAL-mode test execution where jobs run sequentially. DISTRIBUTED mode would key by
 * jobId+vertexId and route over Stage 39 control-plane RPC (not in v1).
 */
@Internal
public final class SourceCoordinatorRegistry {

    private static final ConcurrentMap<Integer, LocalSourceCoordinator<? extends SourceSplit, ?>> COORDINATORS =
            new ConcurrentHashMap<>();

    private SourceCoordinatorRegistry() {
    }

    /**
     * Looks up an existing coordinator for {@code vertexId}, or {@code null} if none registered.
     */
    public static <T extends SourceSplit> LocalSourceCoordinator<T, ?> get(Integer vertexId) {
        @SuppressWarnings("unchecked")
        LocalSourceCoordinator<T, ?> cast = (LocalSourceCoordinator<T, ?>) COORDINATORS.get(vertexId);
        return cast;
    }

    /**
     * Registers a coordinator for {@code vertexId}. Returns the registered coordinator
     * (which may be a previously-registered one if a concurrent first-subtask race happened).
     */
    public static <T extends SourceSplit, StateT> LocalSourceCoordinator<T, StateT> registerIfAbsent(
            Integer vertexId,
            java.util.function.Function<Integer, LocalSourceCoordinator<T, StateT>> factory) {
        @SuppressWarnings("unchecked")
        LocalSourceCoordinator<T, StateT> existing =
                (LocalSourceCoordinator<T, StateT>) COORDINATORS.get(vertexId);
        if (existing != null) {
            return existing;
        }
        LocalSourceCoordinator<T, StateT> created = factory.apply(vertexId);
        @SuppressWarnings("unchecked")
        LocalSourceCoordinator<T, StateT> prev =
                (LocalSourceCoordinator<T, StateT>) COORDINATORS.putIfAbsent(vertexId, created);
        return prev != null ? prev : created;
    }

    /**
     * Unregisters (and closes) the coordinator for {@code vertexId}. Safe to call after
     * job completion; idempotent.
     */
    public static void unregister(Integer vertexId) {
        LocalSourceCoordinator<?, ?> removed = COORDINATORS.remove(vertexId);
        if (removed != null) {
            try {
                removed.close();
            } catch (Exception ignored) {
                // close() already logs internally; ignore here
            }
        }
    }

    /** Test helper: clears all registered coordinators without closing (test sweep). */
    public static void clearForTest() {
        COORDINATORS.clear();
    }

    /** Test helper: returns true if a coordinator is registered for {@code vertexId}. */
    public static boolean isRegistered(Integer vertexId) {
        return COORDINATORS.containsKey(vertexId);
    }

    private static final long serialVersionUID = 1L;
}
