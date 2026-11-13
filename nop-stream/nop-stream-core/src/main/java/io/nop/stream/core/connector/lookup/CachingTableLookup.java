/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.lookup;

import io.nop.api.core.time.CoreMetrics;
import io.nop.stream.core.exceptions.StreamException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
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

    /**
     * C4-002 (plan 2510 R2-07, adjudication 3): bounded dispatch queue capacity =
     * {@code refreshMaxConcurrency * 2}. Orthogonal to {@code maxEntries}: that
     * bounds the cache entry count (eviction), this bounds the in-flight refresh
     * task count (dispatch backlog). Not shared, not linked.
     */
    private final int maxDispatchQueueSize;

    /**
     * C4-002: keys currently dispatched and not yet completed. A key in this set is
     * never re-dispatched by a later scan round (in-flight de-duplication); aged
     * keys that were not dispatched are naturally re-scanned by the next round.
     * Transient: rebuilt lazily together with the other background resources.
     */
    private transient volatile Set<Object> inFlightRefreshKeys;

    /**
     * C4-002: count of refresh dispatches dropped because the bounded queue was
     * full (slow/hung delegate). The dropped key stays aged and is re-dispatched
     * by a later round — the degradation surfaces only as refresh delay, never as
     * silent unbounded backlog.
     */
    private final AtomicInteger droppedDispatchCount = new AtomicInteger();

    /** Rate-limit window for the dispatch-drop WARN (nanos of last warning). */
    private transient volatile long lastDropWarnNanos;

    /** WI11 state, all transient: rebuilt lazily by the first synchronous lookup. */
    private transient volatile Map<Object, Entry> cache;
    private transient volatile ScheduledExecutorService scanScheduler;
    /** 测试注入的手动调度器（plan 2510）：非空时扫描调度经它推进，不再自建真实定时线程。 */
    private transient ScheduledExecutorService scanSchedulerOverride;
    private transient volatile ThreadPoolExecutor refreshPool;
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
        this(delegate, ttlMs, refreshIntervalMs, refreshMaxConcurrency, maxEntries, null);
    }

    CachingTableLookup(ITableLookup delegate, long ttlMs, long refreshIntervalMs,
                       int refreshMaxConcurrency, int maxEntries,
                       ScheduledExecutorService scanSchedulerOverride) {
        this.scanSchedulerOverride = scanSchedulerOverride;
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
        this.maxDispatchQueueSize = refreshMaxConcurrency * 2;
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
        long now = CoreMetrics.nanoTime();
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
            inFlightRefreshKeys = ConcurrentHashMap.newKeySet();
            if (closed) {
                return;
            }
            AtomicInteger seq = new AtomicInteger();
            // C4-002: bounded dispatch queue (capacity = refreshMaxConcurrency * 2).
            // The pool size remains the concurrency bound; the bounded queue plus
            // the in-flight key de-duplication replace the unbounded default queue
            // of Executors.newFixedThreadPool (OOM candidate on a hung delegate).
            ThreadPoolExecutor pool = new ThreadPoolExecutor(refreshMaxConcurrency,
                    refreshMaxConcurrency, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(maxDispatchQueueSize), r -> {
                Thread t = new Thread(r, "nop-lookup-refresh-" + System.identityHashCode(this)
                        + "-" + seq.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
            refreshPool = pool;
            ScheduledExecutorService scheduler;
            if (scanSchedulerOverride != null) {
                scheduler = scanSchedulerOverride;
            } else {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "nop-lookup-refresh-scan-" + System.identityHashCode(this));
                    t.setDaemon(true);
                    return t;
                });
            }
            // non-overlapping scan; jobs dispatch to the bounded worker pool
            scheduler.scheduleWithFixedDelay(this::scanAndDispatch,
                    refreshIntervalMs, refreshIntervalMs, TimeUnit.MILLISECONDS);
            this.scanScheduler = scheduler;
        }
    }

    /**
     * C4-002 (plan 2510 R2-07, adjudication 3): bounded, de-duplicated refresh
     * dispatch. Package-private for test visibility. For every aged key: skip keys
     * already in flight (in-flight de-duplication — one dispatch per key until it
     * completes); when the bounded queue is full, drop the dispatch with an
     * explicit counter and a rate-limited WARN (fail-loud degradation — the key
     * stays aged and is re-dispatched by a later round, so no data is lost and no
     * unbounded backlog accumulates).
     */
    void scanAndDispatch() {
        Map<Object, Entry> map = cache();
        if (map == null || closed) {
            return;
        }
        long now = CoreMetrics.nanoTime();
        long refreshAge = TimeUnit.MILLISECONDS.toNanos(refreshIntervalMs);
        Set<Object> inFlight = inFlightRefreshKeys;
        if (inFlight == null) {
            return;
        }
        for (Map.Entry<Object, Entry> e : map.entrySet()) {
            if (closed) {
                return;
            }
            if (now - e.getValue().loadNanos >= refreshAge) {
                Object key = e.getKey();
                // In-flight de-duplication: a dispatched-but-unfinished key is never
                // re-enqueued (the next round re-scans it if still aged).
                if (!inFlight.add(key)) {
                    continue;
                }
                // Re-check freshness AFTER winning the in-flight slot: a concurrent
                // refresh of the same key may have completed (renewed loadNanos and
                // left the in-flight set) between the aged check above and the add —
                // re-dispatching here would probe the delegate twice for one refresh
                // cycle (C4-002 in-flight de-duplication must hold without races).
                Entry refreshed = map.get(key);
                if (refreshed == null || now - refreshed.loadNanos < refreshAge) {
                    inFlight.remove(key);
                    continue;
                }
                try {
                    // the worker pool's size is the concurrency bound (review F-2);
                    // the dispatch queue is bounded (C4-002)
                    refreshPool.execute(() -> refreshOne(key));
                } catch (RejectedExecutionException rex) {
                    inFlight.remove(key);
                    droppedDispatchCount.incrementAndGet();
                    warnDropThrottled(key, rex);
                } catch (RuntimeException rex) {
                    inFlight.remove(key);
                    throw rex;
                }
            }
        }
    }

    /** Rate-limited (>=1s window) WARN for dropped refresh dispatches. */
    private void warnDropThrottled(Object key, RejectedExecutionException cause) {
        long now = CoreMetrics.nanoTime();
        if (now - lastDropWarnNanos >= TimeUnit.SECONDS.toNanos(1)) {
            lastDropWarnNanos = now;
            LOG.warn("CachingTableLookup refresh dispatch queue full (capacity={}); dropping "
                    + "the refresh dispatch for key {} — it stays aged and will be re-dispatched "
                    + "by a later scan round; dropped so far: {}", maxDispatchQueueSize, key,
                    droppedDispatchCount.get(), cause);
        }
    }

    private void refreshOne(Object key) {
        try {
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
                    entry.loadNanos = CoreMetrics.nanoTime();
                }
            } catch (Exception e) {
                // keep the old value and retry on a later round; the TTL expiry falls back
                // to a synchronous requery as the self-heal exit
                LOG.warn("CachingTableLookup background refresh failed for key {}; keeping stale value",
                        key, e);
            }
        } finally {
            // C4-002: the key leaves the in-flight set when its refresh completes
            // (successfully or not), so a later aged scan can re-dispatch it.
            Set<Object> inFlight = inFlightRefreshKeys;
            if (inFlight != null) {
                inFlight.remove(key);
            }
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

    /** Test/ops visibility: pending refresh dispatches in the bounded queue (C4-002). */
    int getPendingRefreshDispatchCountForTest() {
        ThreadPoolExecutor pool = refreshPool;
        return pool == null ? 0 : pool.getQueue().size();
    }

    /** Test/ops visibility: keys currently in-flight (dispatched, not completed). */
    int getInFlightRefreshKeyCountForTest() {
        Set<Object> inFlight = inFlightRefreshKeys;
        return inFlight == null ? 0 : inFlight.size();
    }

    /** Test/ops visibility: refresh dispatches dropped on a full bounded queue (C4-002). */
    int getDroppedRefreshDispatchCountForTest() {
        return droppedDispatchCount.get();
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
