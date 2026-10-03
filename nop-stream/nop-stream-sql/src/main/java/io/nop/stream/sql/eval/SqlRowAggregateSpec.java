/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * WI17 (plan 25 r2 B1): the structured spec carried by the synthesized composite
 * aggregator entry ({@code fnId="sql-row-agg"}). The SQL compiler emits one entry per
 * aggregate query: the group-key expressions plus the SELECT aggregates in projection
 * order. The spec travels as a JSON string in the {@code expr} attribute of the
 * {@code <aggregator>} registry entry and is parsed by the sql-side
 * {@code IAggregatorFunctionResolver} into a {@link SqlRowCompositeFunction}.
 *
 * <p>JSON shape:
 * <pre>{"keys":["item"],"aggs":[{"fn":"sum","expr":"amount"},{"fn":"count","star":true}]}</pre>
 */
public final class SqlRowAggregateSpec {

    /**
     * The fixed fnId of the synthesized composite aggregator entry (plan 25 r2 B1).
     */
    public static final String FN_ID = "sql-row-agg";

    private final List<String> keys;
    private final List<AggCall> aggs;

    public SqlRowAggregateSpec(List<String> keys, List<AggCall> aggs) {
        this.keys = keys;
        this.aggs = aggs;
    }

    public List<String> getKeys() {
        return keys;
    }

    public List<AggCall> getAggs() {
        return aggs;
    }

    public static final class AggCall {
        private final String fnId;
        private final String expr;
        private final boolean star;

        public AggCall(String fnId, String expr, boolean star) {
            this.fnId = fnId;
            this.expr = expr;
            this.star = star;
        }

        public String getFnId() {
            return fnId;
        }

        /**
         * The aggregate argument expression text; {@code null} for {@code COUNT(*)}.
         */
        public String getExpr() {
            return expr;
        }

        public boolean isStar() {
            return star;
        }
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"keys\":[");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('"').append(escape(keys.get(i))).append('"');
        }
        sb.append("],\"aggs\":[");
        for (int i = 0; i < aggs.size(); i++) {
            AggCall a = aggs.get(i);
            if (i > 0)
                sb.append(',');
            sb.append("{\"fn\":\"").append(escape(a.getFnId())).append('"');
            if (a.getExpr() != null)
                sb.append(",\"expr\":\"").append(escape(a.getExpr())).append('"');
            if (a.isStar())
                sb.append(",\"star\":true");
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String s) {
        // JsonTool round-trips the string; manual escaping keeps the emitter free of
        // a JSON AST while guaranteeing valid JSON for quotes and backslashes.
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\')
                sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static SqlRowAggregateSpec fromJson(String json) {
        Map<String, Object> root;
        try {
            root = JsonTool.parseMap(json);
        } catch (Exception e) {
            throw invalid("composite spec is not valid JSON: " + e.getMessage());
        }
        if (root == null)
            throw invalid("composite spec is blank");
        List<String> keys = new ArrayList<>();
        Object keyNode = root.get("keys");
        if (keyNode instanceof List) {
            for (Object k : (List<Object>) keyNode) {
                if (!(k instanceof String))
                    throw invalid("composite spec key must be a string, got " + k);
                keys.add((String) k);
            }
        } else if (keyNode != null) {
            throw invalid("composite spec 'keys' must be an array");
        }
        List<AggCall> aggs = new ArrayList<>();
        Object aggNode = root.get("aggs");
        if (aggNode instanceof List) {
            for (Object o : (List<Object>) aggNode) {
                if (!(o instanceof Map))
                    throw invalid("composite spec agg entry must be an object");
                Map<String, Object> m = (Map<String, Object>) o;
                Object fn = m.get("fn");
                if (!(fn instanceof String))
                    throw invalid("composite spec agg entry requires a string 'fn'");
                Object expr = m.get("expr");
                boolean star = Boolean.TRUE.equals(m.get("star"));
                aggs.add(new AggCall((String) fn, expr == null ? null : expr.toString(), star));
            }
        } else {
            throw invalid("composite spec requires an 'aggs' array");
        }
        return new SqlRowAggregateSpec(keys, aggs);
    }

    private static NopException invalid(String detail) {
        return new NopException(ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "aggregator")
                .param(ARG_DETAIL, detail);
    }
}
