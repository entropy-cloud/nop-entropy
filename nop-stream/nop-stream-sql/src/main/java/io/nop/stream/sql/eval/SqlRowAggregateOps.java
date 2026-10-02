/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WI17 (plan 25 r2 B3): static operations the SQL compiler's emitted xpl bodies call
 * for the windowless continuous GROUP BY pipeline
 * ({@code map(record→accumulator-row) + keyBy + reduce(merge) + map(row)}). The xpl
 * bodies embed the composite spec JSON and dispatch here
 * ({@code import io.nop.stream.sql.eval.SqlRowAggregateOps; ...}), so the accumulation
 * semantics stay single-sourced in {@link SqlRowCompositeFunction} — the generated xpl
 * never re-implements aggregation.
 *
 * <p>Specs are parsed once per JSON text and cached (the emitted body text is constant
 * per compiled pipeline, so the cache is effectively unbounded by one entry per
 * pipeline).
 */
public final class SqlRowAggregateOps {

    private static final ConcurrentHashMap<String, SqlRowCompositeFunction> CACHE =
            new ConcurrentHashMap<>();

    private SqlRowAggregateOps() {
    }

    static SqlRowCompositeFunction function(String specJson) {
        return CACHE.computeIfAbsent(specJson, s -> {
            SqlRowAggregateSpec spec = SqlRowAggregateSpec.fromJson(s);
            return StreamSqlAggregations.buildComposite(spec);
        });
    }

    /**
     * map body: one raw record → one accumulator row (create + first add).
     */
    public static Object begin(String specJson, Object record) {
        return function(specJson).add(record, function(specJson).createAccumulator());
    }

    /**
     * reduce body: merge two accumulator rows (the merge output is emitted downstream —
     * last-value-wins final-value semantics per D1).
     */
    public static Object merge(String specJson, Object a, Object b) {
        return function(specJson).merge(a, b);
    }

    /**
     * final map body: merged accumulator row → the SQL row (keys first, aggregates in
     * SELECT order).
     */
    public static Object toRow(String specJson, Object acc) {
        return function(specJson).getResult(acc);
    }
}
