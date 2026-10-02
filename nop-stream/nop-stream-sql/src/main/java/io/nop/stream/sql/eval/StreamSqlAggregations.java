/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The built-in aggregate catalog (WI9): the five SQL aggregate ids
 * sum/count/avg/min/max and their accumulation semantics (roadmap A4). Resolution
 * order with WI8c's declaration surface is bean-container-first, catalog-second —
 * {@link #resolve} returns {@code null} for unknown ids so that ordering stays
 * intact; unknown ids must surface as the caller's fail-fast, never as a silent
 * fallback.
 */
public final class StreamSqlAggregations {

    private static final Map<String, StreamSqlAggregation> BUILTINS;

    static {
        Map<String, StreamSqlAggregation> m = new HashMap<>();
        m.put("sum", new StreamSqlAggregation("sum", false));
        m.put("count", new StreamSqlAggregation("count", true));
        m.put("avg", new StreamSqlAggregation("avg", false));
        m.put("min", new StreamSqlAggregation("min", false));
        m.put("max", new StreamSqlAggregation("max", false));
        BUILTINS = Collections.unmodifiableMap(m);
    }

    private StreamSqlAggregations() {
    }

    /**
     * Resolves a built-in aggregate id (lowercase, matching the EQL parser's
     * normalized {@code SqlAggregateFunction.getName()}).
     *
     * @param fnId the aggregate id
     * @return the descriptor, or {@code null} when the id is not a built-in (the A4
     * resolution order treats null as "not ours" — a bean lookup happens first)
     */
    public static StreamSqlAggregation resolve(String fnId) {
        return BUILTINS.get(fnId);
    }

    /**
     * @return the unmodifiable set of built-in ids (for diagnostics and WI8c
     * declaration-surface validation)
     */
    public static java.util.Set<String> builtinIds() {
        return BUILTINS.keySet();
    }

    /**
     * WI17 (plan 25 r2 B1): assembles the composite aggregate function for a parsed
     * {@code sql-row-agg} spec — group-key evaluators plus one builtin sub-aggregator
     * per SELECT aggregate (accumulation semantics come from {@link StreamSqlAggregation}
     * instances; nothing is re-implemented here). Unknown sub-aggregate ids and
     * non-count aggregates without an argument fail fast with the stream-side
     * invalid-arg code.
     */
    public static SqlRowCompositeFunction buildComposite(SqlRowAggregateSpec spec) {
        StreamRecordEvaluator[] keyEvals = new StreamRecordEvaluator[spec.getKeys().size()];
        for (int i = 0; i < keyEvals.length; i++) {
            keyEvals[i] = StreamSqlExprCompiler.compileScalar(spec.getKeys().get(i));
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        io.nop.stream.core.common.functions.AggregateFunction<Object, Object, Object>[] subFns =
                new io.nop.stream.core.common.functions.AggregateFunction[spec.getAggs().size()];
        for (int i = 0; i < subFns.length; i++) {
            SqlRowAggregateSpec.AggCall call = spec.getAggs().get(i);
            StreamSqlAggregation desc = resolve(call.getFnId());
            if (desc == null) {
                throw RecordColumnAccess.invalidArg("unknown aggregate fnId '" + call.getFnId()
                        + "' in composite spec: builtin ids are " + builtinIds());
            }
            StreamRecordEvaluator arg = call.getExpr() == null
                    ? null : StreamSqlExprCompiler.compileScalar(call.getExpr());
            if (call.isStar() && desc.allowsNoArg()) {
                arg = null;
            }
            subFns[i] = desc.create(arg);
        }
        return new SqlRowCompositeFunction(spec, keyEvals, subFns);
    }
}
