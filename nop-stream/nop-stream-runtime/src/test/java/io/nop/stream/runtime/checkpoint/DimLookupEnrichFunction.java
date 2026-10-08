/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.common.functions.KeyedProcessFunction;
import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.state.KeyedStateStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.connector.lookup.CachingTableLookup;
import io.nop.stream.core.connector.lookup.ITableLookup;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WI14: the dimension-table lookup function for the process path — the enrichment
 * form of a static dim-table lookup join. Two cache layers cooperate:
 *
 * <ul>
 *   <li>the per-key {@code ValueState} cache (one slot per lookup key) — the
 *       checkpoint-restorable fast path; a restored cache hit must not reload;</li>
 *   <li>the WI11 {@link CachingTableLookup} materialization layer wrapped around the
 *       application delegate — cross-key TTL cache, bounded background refresh, and
 *       null-penetration guard for the miss/reload path. WI17 (plan 2342) wires the
 *       lifecycle: this function holds the wrapper and its {@link #close()} override
 *       propagates (the wrapper never closes itself; a silent DB fallback after close
 *       would mask lifecycle bugs).</li>
 * </ul>
 *
 * <p>The lookup key is taken from the record field ({@code dimKey}) —
 * {@code KeyedProcessFunction.Context.getCurrentKey()} is not a wired API on the
 * process path's context.
 */
public class DimLookupEnrichFunction extends KeyedProcessFunction<String, Map<String, Object>, Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    private static final ValueStateDescriptor<Object> DIM_CACHE_DESC =
            new ValueStateDescriptor<>("dim-cache", Object.class);

    /** WI17 sample defaults: 1h TTL, 10min refresh sweep, 2 refresh workers, 10k entries. */
    static final long DEFAULT_TTL_MS = 3600_000L;
    static final long DEFAULT_REFRESH_INTERVAL_MS = 600_000L;
    static final int DEFAULT_REFRESH_CONCURRENCY = 2;
    static final int DEFAULT_MAX_ENTRIES = 10_000;

    private ITableLookup tableLookup;

    /** transient: lazily built by the first miss lookup, also after Java-deserialization. */
    private transient volatile CachingTableLookup cachingLookup;

    /** test/prog wiring: setter form (Nop IoC forbids private-field injection). */
    public void setTableLookup(ITableLookup tableLookup) {
        if (tableLookup == null)
            throw new IllegalArgumentException("tableLookup is required");
        this.tableLookup = tableLookup;
        this.cachingLookup = null;
    }

    /**
     * WI17 wiring: the app delegate is fronted by the WI11 materialization layer. An
     * already-caching delegate is used as-is (no double wrap).
     */
    private CachingTableLookup cachingLookup() {
        CachingTableLookup lookup = this.cachingLookup;
        if (lookup == null) {
            if (tableLookup instanceof CachingTableLookup) {
                lookup = (CachingTableLookup) tableLookup;
            } else {
                lookup = new CachingTableLookup(tableLookup, DEFAULT_TTL_MS,
                        DEFAULT_REFRESH_INTERVAL_MS, DEFAULT_REFRESH_CONCURRENCY, DEFAULT_MAX_ENTRIES);
            }
            this.cachingLookup = lookup;
        }
        return lookup;
    }

    private transient ValueState<Object> cacheState;

    private ValueState<Object> cacheState() {
        if (cacheState == null) {
            KeyedStateStore store = getRuntimeContext().getKeyedStateStore();
            cacheState = store.getState(DIM_CACHE_DESC);
        }
        return cacheState;
    }

    @Override
    public void processElement(Map<String, Object> record,
                               ProcessFunction<Map<String, Object>, Map<String, Object>>.Context ctx,
                               Collector<Map<String, Object>> out) throws Exception {
        Object dimKey = record.get("dimKey");
        ValueState<Object> cache = cacheState();
        Object dimValue = cache.value();
        if (dimValue == null) {
            dimValue = cachingLookup().lookup(dimKey);
            cache.update(dimValue);
        }
        Map<String, Object> enriched = new HashMap<>(record);
        enriched.put("dimValue", dimValue);
        out.collect(enriched);
    }

    /**
     * WI17 wiring: the WI11 wrapper's documented production close call site — the host
     * function's {@code close()} override. Idempotent (the wrapper guards double
     * close); after close, a cache-miss lookup fails typed instead of silently
     * hitting the DB.
     */
    @Override
    public void close() {
        CachingTableLookup lookup = this.cachingLookup;
        if (lookup != null) {
            lookup.close();
        }
    }

    /** Serializable stub of the dimension table with per-generation load counting. */
    public static final class VersionedTableLookup implements ITableLookup {

        private static final long serialVersionUID = 1L;

        private final Map<String, String> rows;
        private final String generation;
        private final AtomicInteger loadCount;

        public VersionedTableLookup(Map<String, String> rows, String generation, AtomicInteger loadCount) {
            this.rows = new HashMap<>(rows);
            this.generation = generation;
            this.loadCount = loadCount;
        }

        @Override
        public Object lookup(Object key) {
            loadCount.incrementAndGet();
            String value = rows.get(key);
            return value == null ? null : value + "@" + generation;
        }
    }

    /** builds a record carrying the lookup key. */
    public static StreamRecord<Map<String, Object>> record(String dimKey) {
        Map<String, Object> m = new HashMap<>();
        m.put("dimKey", dimKey);
        return new StreamRecord<>(m);
    }
}
