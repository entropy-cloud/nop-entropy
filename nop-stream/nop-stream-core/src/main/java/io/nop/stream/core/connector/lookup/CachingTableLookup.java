/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.lookup;

import io.nop.stream.core.exceptions.StreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;

/**
 * WI11 (plan 2338): local materialization layer over an existing {@link ITableLookup}
 * — the roadmap delta that removes the one-DB-query-per-record pattern while keeping
 * the per-record path fully synchronous (adjudication §三 R5: no async operator, the
 * asynchrony lives in the refresh layer inside the function's own background
 * resources).
 *
 * <p><strong>Per-record path (synchronous)</strong>: cache hit within TTL returns the
 * cached value (zero DB IO); a miss or an expired entry delegates synchronously to the
 * wrapped lookup and loads the cache. A null result is cached too (penetration
 * guard) with the same TTL semantics as a positive entry.
 *
 * <p><strong>Background refresh (scan/execute separation)</strong>: a lazily started
 * single-thread daemon scheduler scans (fixed-delay, non-overlapping) for entries aged
 * past {@code refreshIntervalMs} and dispatches a refresh job per entry to a fixed
 * worker pool of size {@code refreshMaxConcurrency} — the pool size IS the concurrency
 * bound, so delegate calls genuinely overlap when the pool is larger than one.
 *
 * <p><strong>Refresh failure</strong>: a failing refresh keeps the old value, logs a
 * warning, and retries on a later round (the entry age keeps growing); once the entry
 * exceeds the TTL the next per-record access falls back to a synchronous requery —
 * the self-heal exit. Removing entries on failure is deliberately avoided: DB jitter
 * must not turn into a synchronous query storm.
 *
 * <p><strong>Eviction</strong>: loading beyond {@code maxEntries} evicts the
 * entry with the oldest load time (background refresh renews the load time, so
 * refreshed entries are considered fresh).
 *
 * <p><strong>Lifecycle</strong>: all mutable resources (cache, pools) are transient —
 * after Java-deserialization the first synchronous lookup lazily rebuilds everything
 * (thread-safe). {@link #close()} is idempotent: it stops the pools, clears the cache,
 * and any later {@link #lookup(Object)} fails typed (a silent DB fallback after close
 * would mask lifecycle bugs). The production close call site is the host function's
 * {@code close()} override (WI17 wiring) or a bean destroy-method — this class only
 * provides the capability.
 *
 * <p><strong>Delegate thread-safety contract</strong>: the refresh pool and the task
 * thread call the delegate concurrently — a wrapped adapter must be safe for
 * concurrent {@code lookup} calls.
 */
public class CachingTableLookup implements ITableLookup, AutoCloseable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(CachingTableLookup.class);

    private final ITableLookup delegate;
    private final long ttlMs;
    private final long refreshIntervalMs;
    private final int refreshMaxConcurrency;
    private final int maxEntries;

    /** WI11 state, all transient: rebuilt lazily by the first synchronous lookup. */
    private transient volatile Map<Object, Entry> cache;
    private transient volatile ScheduledExecutorService scanScheduler;
    private transient volatile java.util.concurrent.ExecutorService refreshPool;
    private transient volatile boolean closed;

    /** One lookup key currently materialized (value may be the NULL_SENTINEL). */
    static final class Entry {
        /** Volatile: written by refresh workers, read by the task thread. */
        volatile Object value;
        long loadNanos;

        Entry(Object value, long loadNanos) {
            this.value = value;
            this.loadNanos = loadNanos;
        }
    }

    /** Null-penetration sentinel: ConcurrentHashMap values must not be null. */
    private static final Object NULL_SENTINEL = new Object();

    public CachingTableLookup(ITableLookup delegate, long ttlMs, long refreshIntervalMs,
                              int refreshMaxConcurrency, int maxEntries) {
        if (delegate == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param("argName", (Object) "delegate");
        }
        if (ttlMs <= 0) {
            throw typedConfig("ttlMs must be positive, got: " + ttlMs);
        }
        if (refreshIntervalMs <= 0) {
            throw typedConfig("refreshIntervalMs must be positive, got: " + refreshIntervalMs);
        }
        if (refreshMaxConcurrency < 1) {
            throw typedConfig("refreshMaxConcurrency must be >= 1, got: " + refreshMaxConcurrency);
        }
        if (maxEntries < 1) {
            throw typedConfig("maxEntries must be >= 1, got: " + maxEntries);
        }
        this.delegate = delegate;
        this.ttlMs = ttlMs;
        this.refreshIntervalMs = refreshIntervalMs;
        this.refreshMaxConcurrency = refreshMaxConcurrency;
        this.maxEntries = maxEntries;
    }

    private static StreamException typedConfig(String detail) {
        StreamException ex = new StreamException(ERR_STREAM_INVALID_ARG);
        ex.param(ARG_DETAIL, "CachingTableLookup config: " + detail);
        return ex;
    }

    // ==================== per-record path (synchronous) ====================

    @Override
    public Object lookup(Object key) {
        ensureInit();
        if (closed) {
            throw new StreamException(ERR_STREAM_INVALID_STATE)
                    .param(ARG_DETAIL, "CachingTableLookup is closed — further lookups are rejected "
                            + "instead of silently falling back to the wrapped lookup");
        }
        long now = System.nanoTime();
        Entry entry = cache().get(key);
        if (entry != null && now - entry.loadNanos < TimeUnit.MILLISECONDS.toNanos(ttlMs)) {
            return unwrap(entry.value);
        }
        Object value = delegate.lookup(key);
        putEntry(key, value, now);
        return value;
    }

    private void putEntry(Object key, Object value, long now) {
        Map<Object, Entry> map = cache();
        map.put(key, new Entry(value == null ? NULL_SENTINEL : value, now));
        evictOldestIfNeeded(now);
    }

    /** Loads beyond maxEntries evict the entry with the oldest load time. */
    private void evictOldestIfNeeded(long now) {
        Map<Object, Entry> map = cache();
        while (map.size() > maxEntries) {
            Object oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Object, Entry> e : map.entrySet()) {
                if (e.getValue().loadNanos < oldest) {
                    oldest = e.getValue().loadNanos;
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            map.remove(oldestKey);
        }
    }

    private static Object unwrap(Object stored) {
        return stored == NULL_SENTINEL ? null : stored;
    }

    // ==================== background refresh ====================

    private void ensureInit() {
        if (cache != null) {
            return;
        }
        synchronized (this) {
            if (cache != null) {
                return;
            }
            cache = new ConcurrentHashMap<>();
            if (closed) {
                return;
            }
            AtomicInteger seq = new AtomicInteger();
            refreshPool = Executors.newFixedThreadPool(refreshMaxConcurrency, r -> {
                Thread t = new Thread(r, "nop-lookup-refresh-" + System.identityHashCode(this)
                        + "-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "nop-lookup-refresh-scan-" + System.identityHashCode(this));
                t.setDaemon(true);
                return t;
            });
            // non-overlapping scan; jobs dispatch to the bounded worker pool
            scheduler.scheduleWithFixedDelay(this::scanAndDispatch,
                    refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
            this.scanScheduler = scheduler;
        }
    }

    private void scanAndDispatch() {
        Map<Object, Entry> map = cache();
        if (map == null || closed) {
            return;
        }
        long now = System.nanoTime();
        long refreshAge = TimeUnit.MILLISECONDS.toNanos(refreshIntervalMs);
        for (Map.Entry<Object, Entry> e : map.entrySet()) {
            if (closed) {
                return;
            }
            if (now - e.getValue().loadNanos >= refreshAge) {
                // the worker pool's size is the concurrency bound (review F-2)
                refreshPool.execute(() -> refreshOne(e.getKey()));
            }
        }
    }

    private void refreshOne(Object key) {
        if (closed) {
            return;
        }
        Map<Object, Entry> map = cache();
        Entry current = map.get(key);
        if (current == null) {
            return;
        }
        try {
            Object value = delegate.lookup(key);
            Entry entry = map.get(key);
            if (entry != null) {
                // refresh renews the load time: refreshed entries count as fresh for
                // eviction ordering (documented semantics)
                entry.value = value == null ? NULL_SENTINEL : value;
                entry.loadNanos = System.nanoTime();
            }
        } catch (Exception e) {
            // keep the old value and retry on a later round; the TTL expiry falls back
            // to a synchronous requery as the self-heal exit
            LOG.warn("CachingTableLookup background refresh failed for key {}; keeping stale value",
                    key, e);
        }
    }

    private Map<Object, Entry> cache() {
        Map<Object, Entry> map = cache;
        if (map == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = new ConcurrentHashMap<>();
                }
                map = cache;
            }
        }
        return map;
    }

    // ==================== lifecycle ====================

    /**
     * WI11: idempotent shutdown — stops the scheduler and the refresh pool, clears the
     * cache. Further lookups fail typed instead of silently hitting the DB.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        ScheduledExecutorService scan = scanScheduler;
        if (scan != null) {
            scan.shutdownNow();
        }
        java.util.concurrent.ExecutorService pool = refreshPool;
        if (pool != null) {
            pool.shutdownNow();
        }
        Map<Object, Entry> map = cache;
        if (map != null) {
            map.clear();
        }
    }

    /** Test/ops visibility: the materialized key count. */
    public int materializedSize() {
        Map<Object, Entry> map = cache;
        return map == null ? 0 : map.size();
    }

    /** Test visibility: whether the background resources are started. */
    boolean isRefreshStartedForTest() {
        return scanScheduler != null;
    }

    /** Test visibility: whether the background pools are terminated after close. */
    boolean isTerminatedForTest() {
        ScheduledExecutorService scan = scanScheduler;
        java.util.concurrent.ExecutorService pool = refreshPool;
        return scan != null && pool != null && scan.isShutdown() && pool.isShutdown();
    }
}
