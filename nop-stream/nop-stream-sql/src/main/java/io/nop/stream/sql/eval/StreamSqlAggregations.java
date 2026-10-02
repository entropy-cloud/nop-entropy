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
}
