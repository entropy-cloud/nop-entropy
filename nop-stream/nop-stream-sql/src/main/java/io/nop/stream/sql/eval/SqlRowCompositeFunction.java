/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.stream.core.common.functions.AggregateFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * WI17 (plan 25 r2 B1): the composite aggregate function a synthesized
 * {@code sql-row-agg} aggregator entry resolves to. Internally it holds
 * <ul>
 *   <li>the group-key evaluators (WI9 {@link StreamRecordEvaluator}s re-evaluated per
 *       record) and</li>
 *   <li>N sub-aggregators ({@link StreamSqlAggregation} instances — accumulation
 *       semantics are never re-implemented here)</li>
 * </ul>
 * {@link #getResult} produces one SQL row: an ordered {@code List<Object>} with the
 * group keys first (last seen values — within one keyed/windowed partition they are
 * constant) and the aggregate results in SELECT order.
 *
 * <p>Accumulator shape: {@code Object[]{ Object[] keys, Object[] subAccumulators }}.
 */
public final class SqlRowCompositeFunction
        implements AggregateFunction<Object, Object, Object> {

    private static final long serialVersionUID = 1L;

    private final SqlRowAggregateSpec spec;
    private final StreamRecordEvaluator[] keyEvals;
    private final AggregateFunction<Object, Object, Object>[] subFns;

    @SuppressWarnings({"unchecked", "rawtypes"})
    SqlRowCompositeFunction(SqlRowAggregateSpec spec, StreamRecordEvaluator[] keyEvals,
                            AggregateFunction[] subFns) {
        this.spec = spec;
        this.keyEvals = keyEvals;
        this.subFns = subFns;
    }

    public SqlRowAggregateSpec getSpec() {
        return spec;
    }

    @Override
    public Object createAccumulator() {
        Object[] subAccs = new Object[subFns.length];
        for (int i = 0; i < subFns.length; i++) {
            subAccs[i] = subFns[i].createAccumulator();
        }
        return new Object[]{new Object[keyEvals.length], subAccs};
    }

    @Override
    public Object add(Object record, Object accumulator) {
        Object[] acc = (Object[]) accumulator;
        Object[] keys = (Object[]) acc[0];
        for (int i = 0; i < keyEvals.length; i++) {
            keys[i] = keyEvals[i].eval(record);
        }
        Object[] subAccs = (Object[]) acc[1];
        for (int i = 0; i < subFns.length; i++) {
            subAccs[i] = subFns[i].add(record, subAccs[i]);
        }
        return acc;
    }

    @Override
    public Object getResult(Object accumulator) {
        Object[] acc = (Object[]) accumulator;
        Object[] keys = (Object[]) acc[0];
        Object[] subAccs = (Object[]) acc[1];
        List<Object> row = new ArrayList<>(keys.length + subAccs.length);
        for (Object key : keys) {
            row.add(key);
        }
        for (int i = 0; i < subFns.length; i++) {
            row.add(subFns[i].getResult(subAccs[i]));
        }
        return row;
    }

    @Override
    public Object merge(Object a, Object b) {
        Object[] x = (Object[]) a;
        Object[] y = (Object[]) b;
        Object[] xKeys = (Object[]) x[0];
        Object[] yKeys = (Object[]) y[0];
        // first-seen keys win: within a keyed partition both sides carry the same key
        for (int i = 0; i < xKeys.length; i++) {
            if (xKeys[i] == null && yKeys[i] != null)
                xKeys[i] = yKeys[i];
        }
        Object[] xSub = (Object[]) x[1];
        Object[] ySub = (Object[]) y[1];
        for (int i = 0; i < subFns.length; i++) {
            xSub[i] = subFns[i].merge(xSub[i], ySub[i]);
        }
        return x;
    }
}
