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
import io.nop.stream.core.connector.lookup.ITableLookup;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WI14: the dimension-table lookup function for the process path — the enrichment
 * form of a static dim-table lookup join. Dimension entries are cached in keyed
 * {@code ValueState} (one cache slot per lookup key); a cache miss delegates to the
 * {@link ITableLookup} bridge (which applications wire to IJdbcTemplate /
 * IBatchLoader — never a self-managed data source) and fills the cache.
 *
 * <p>The lookup key is taken from the record field ({@code dimKey}) —
 * {@code KeyedProcessFunction.Context.getCurrentKey()} is not a wired API on the
 * process path's context.
 */
public class DimLookupEnrichFunction extends KeyedProcessFunction<String, Map<String, Object>, Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    private static final ValueStateDescriptor<Object> DIM_CACHE_DESC =
            new ValueStateDescriptor<>("dim-cache", Object.class);

    private ITableLookup tableLookup;

    /** test/prog wiring: setter form (Nop IoC forbids private-field injection). */
    public void setTableLookup(ITableLookup tableLookup) {
        if (tableLookup == null)
            throw new IllegalArgumentException("tableLookup is required");
        this.tableLookup = tableLookup;
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
            dimValue = tableLookup.lookup(dimKey);
            cache.update(dimValue);
        }
        Map<String, Object> enriched = new HashMap<>(record);
        enriched.put("dimValue", dimValue);
        out.collect(enriched);
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
