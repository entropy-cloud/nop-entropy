/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-state sidecar that tracks the last-access timestamp of keyed-state entries so that
 * TTL expiry can be enforced without modifying the stored user value.
 *
 * <p>The timestamp key type {@code K} is backend-specific:
 * <ul>
 *   <li>memory backend — {@code TypedNamespaceAndKey}</li>
 *   <li>RocksDB backend — {@code java.nio.ByteBuffer} wrapping the base composite key bytes</li>
 * </ul>
 *
 * <p><b>Storage/value separation</b>: the stored value is always the raw user value. The
 * timestamp lives in this sidecar map. This is required because accumulator-based states
 * (Reducing/Aggregating/Appending) store {@code SimpleAccumulator} or raw ACC values that
 * cannot be wrapped in a {@code TtlValue<T>} without breaking the
 * {@code (ACC) current} casts and fusion logic.
 *
 * <p><b>Restored-entry semantics</b>: after a checkpoint restore the sidecar is empty.
 * A restored entry that has a storage value but no timestamp is granted a fresh TTL
 * window on first access (treated as just-written, {@code OnCreateAndWrite} semantics).
 * This keeps restore + TTL consistent: an entry that was valid when snapshotted does not
 * instantly expire purely because its sidecar was not persisted.
 *
 * <p>The class offers two API layers:
 * <ul>
 *   <li><b>Storage-agnostic decisions</b> ({@link #isExpired}, {@link #hasTimestamp},
 *       {@link #grantFreshWindow}, {@link #removeTimestamp}, {@link #recordWrite},
 *       {@link #recordRead}, {@link #expiredKeys}) — used by the RocksDB backend, whose
 *       storage is a column family, not an in-memory map.</li>
 *   <li><b>Memory convenience</b> ({@link #readEviction}, {@link #writeEviction},
 *       {@link #sweepExpired}) — combine the decision with {@code Map} mutation for the
 *       memory backend.</li>
 * </ul>
 *
 * <p>This class is single-threaded (mailbox model), mirroring the state backends.
 */
public final class TtlContext<K> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final StateTtlConfig config;
    private final TtlTimeProvider clock;
    private final Map<K, Long> timestamps = new HashMap<>();

    public TtlContext(StateTtlConfig config, TtlTimeProvider clock) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.config = config;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public StateTtlConfig getConfig() {
        return config;
    }

    public long now() {
        return clock.currentTimeMillis();
    }

    private boolean isExpired(long ts) {
        return now() - ts > config.ttlMillis();
    }

    // -------------------- storage-agnostic decisions --------------------

    /**
     * Returns {@code true} if a timestamp is recorded for {@code key} and it is past the
     * TTL. Read-only (does not mutate the sidecar). Keys with no timestamp (e.g. restored
     * entries not yet accessed) are NOT expired.
     */
    public boolean isExpired(K key) {
        if (!isEnabled()) {
            return false;
        }
        Long ts = timestamps.get(key);
        return ts != null && isExpired(ts);
    }

    /**
     * Whether the sidecar currently holds a timestamp for {@code key}.
     */
    public boolean hasTimestamp(K key) {
        return isEnabled() && timestamps.containsKey(key);
    }

    /**
     * Grant a fresh TTL window (timestamp = now). Used for restored entries on first
     * access, and equivalently for a write.
     */
    public void grantFreshWindow(K key) {
        if (isEnabled()) {
            timestamps.put(key, now());
        }
    }

    /**
     * Removes the sidecar timestamp. Callers invoke this after removing the storage entry
     * (eviction / clear) so no stale timestamp lingers for an absent entry.
     */
    public void removeTimestamp(K key) {
        if (isEnabled()) {
            timestamps.remove(key);
        }
    }

    /**
     * Refreshes the timestamp on a write (both {@code OnCreateAndWrite} and
     * {@code OnReadAndWrite} refresh on write).
     */
    public void recordWrite(K key) {
        grantFreshWindow(key);
    }

    /**
     * Refreshes the timestamp on a read, but only for {@code OnReadAndWrite}. Callers
     * invoke this only when an entry actually exists.
     */
    public void recordRead(K key) {
        if (isEnabled() && config.getUpdateType().refreshesOnRead()) {
            timestamps.put(key, now());
        }
    }

    /**
     * Snapshot-time check: returns {@code true} if the entry should be excluded from the
     * snapshot because it is expired. Equivalent to {@link #isExpired} but named for
     * call-site clarity.
     */
    public boolean isExpiredForSnapshot(K key) {
        return isExpired(key);
    }

    /**
     * Returns the set of keys whose recorded timestamp is past the TTL. The caller (e.g.
     * a background sweep) removes them from its own storage and the sidecar.
     */
    public Set<K> expiredKeys() {
        if (!isEnabled()) {
            return Collections.emptySet();
        }
        Set<K> out = new HashSet<>();
        long now = now();
        for (Map.Entry<K, Long> e : new HashMap<>(timestamps).entrySet()) {
            if (now - e.getValue() > config.ttlMillis()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /**
     * Removes the sidecar timestamp on {@code clear()}. {@code clear()} is never TTL-gated
     * (it always proceeds), but it must clean the sidecar to avoid leaving a stale
     * timestamp for a now-absent entry.
     */
    public void onClear(K key) {
        removeTimestamp(key);
    }

    // -------------------- memory-backend convenience --------------------

    /**
     * Read-path eviction for the memory backend. Returns {@code true} when the entry at
     * {@code key} is expired; in that case the storage entry and the sidecar timestamp
     * are both removed (double cleanup so no stale data leaks). A restored entry that has
     * a storage value but no timestamp is granted a fresh window and this method returns
     * {@code false}.
     */
    public <V> boolean readEviction(K key, Map<K, V> storage) {
        if (!isEnabled()) {
            return false;
        }
        Long ts = timestamps.get(key);
        if (ts == null) {
            if (storage.containsKey(key)) {
                timestamps.put(key, now());
            }
            return false;
        }
        if (isExpired(ts)) {
            storage.remove(key);
            timestamps.remove(key);
            return true;
        }
        return false;
    }

    /**
     * Write-path eviction for the memory backend. If the entry is expired, removes the
     * storage entry (and sidecar) so a subsequent read-modify-write starts from a clean
     * state rather than mutating stale data.
     */
    public <V> void writeEviction(K key, Map<K, V> storage) {
        if (!isEnabled()) {
            return;
        }
        Long ts = timestamps.get(key);
        if (ts != null && isExpired(ts)) {
            storage.remove(key);
            timestamps.remove(key);
        }
    }

    /**
     * Background sweep for the memory backend: iterate all known timestamps and remove
     * expired entries from {@code storage} and the sidecar. Returns the count removed.
     */
    public <V> int sweepExpired(Map<K, V> storage) {
        if (!isEnabled()) {
            return 0;
        }
        int removed = 0;
        for (K key : expiredKeys()) {
            storage.remove(key);
            timestamps.remove(key);
            removed++;
        }
        return removed;
    }
}
