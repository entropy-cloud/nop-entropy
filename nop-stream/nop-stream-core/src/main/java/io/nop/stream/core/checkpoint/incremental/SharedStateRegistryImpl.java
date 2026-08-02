/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link SharedStateRegistry} backed by a {@link ConcurrentHashMap} keyed
 * on content hash. Both {@link #register} and {@link #unregister} use
 * {@link ConcurrentHashMap#compute} so that, for any given content hash, the
 * read-modify-write of the reference count and the canonical handle is fully
 * serialized. As a result an entry is <em>never</em> observable in the map with a
 * reference count of zero: it is inserted with count 1 and removed in the same
 * critical section that decrements it to zero.
 *
 * <p>The canonical handle stored alongside is returned on every (re-)registration
 * so callers always observe a single handle identity per content hash.
 *
 * <p>Concurrency contract: {@link #register} and {@link #unregister} are safe to
 * call from multiple threads. The registry does not perform file I/O.
 *
 * <p>Stale-unregister note: because a content hash is removed as soon as its count
 * reaches zero, a strictly-unbalanced stale {@code unregister} that races with a
 * fresh {@code register} for the same hash could release a logically-new
 * registration. In this codebase the registry is driven exclusively by the single
 * {@code CheckpointCoordinator} under a controlled register/unregister pairing
 * (each checkpoint registers its segments and, on subsumption, unregisters them
 * exactly once), so the pairing is balanced and the race does not arise. Orphan
 * files left by crashes are reconciled at coordinator (re)start, not via this registry.
 */
public class SharedStateRegistryImpl implements SharedStateRegistry {

    private static final class Entry {
        final AtomicInteger refCount;
        final SharedStateHandle handle;

        Entry(SharedStateHandle handle) {
            this.refCount = new AtomicInteger(0);
            this.handle = handle;
        }
    }

    private final ConcurrentHashMap<String, Entry> registry = new ConcurrentHashMap<>();

    @Override
    public SharedStateHandle register(SharedStateHandle handle) {
        if (handle == null) {
            throw new IllegalArgumentException("handle must not be null");
        }
        // Per-key atomic: create+increment (or increment existing) happen under the
        // segment lock so an entry is never visible in the map with count 0.
        final SharedStateHandle[] canonical = new SharedStateHandle[1];
        registry.compute(handle.getStateObjectId(), (key, entry) -> {
            if (entry == null) {
                entry = new Entry(handle);
            }
            entry.refCount.incrementAndGet();
            canonical[0] = entry.handle;
            return entry;
        });
        return canonical[0];
    }

    @Override
    public List<SharedStateHandle> unregister(String stateObjectId) {
        if (stateObjectId == null) {
            return Collections.emptyList();
        }
        // Per-key atomic: decrement and (on zero) remove happen under the same segment
        // lock as register, so the decrement→0→remove window cannot be raced by another
        // unregister for the same key (which previously could drive the count negative).
        final SharedStateHandle[] discarded = new SharedStateHandle[1];
        registry.computeIfPresent(stateObjectId, (key, entry) -> {
            int nc = entry.refCount.decrementAndGet();
            if (nc <= 0) {
                // Count reached zero (the only path, since entries never sit at 0): remove
                // the entry and hand the handle back so the caller can physically discard it.
                discarded[0] = entry.handle;
                return null;
            }
            return entry;
        });
        return discarded[0] != null ? Collections.singletonList(discarded[0]) : Collections.emptyList();
    }

    @Override
    public int getReferenceCount(String stateObjectId) {
        if (stateObjectId == null) {
            return 0;
        }
        Entry entry = registry.get(stateObjectId);
        return entry != null ? entry.refCount.get() : 0;
    }

    /**
     * @return the number of distinct state objects currently tracked (diagnostic).
     */
    public int size() {
        return registry.size();
    }
}
