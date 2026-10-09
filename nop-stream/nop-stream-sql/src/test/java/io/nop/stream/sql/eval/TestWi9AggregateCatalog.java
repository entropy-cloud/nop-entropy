/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9 (plan 2355): the three new catalog faces — maxBy (whole-record retention),
 * distinct (window-scoped exact dedup count), composite (SqlRowAggregateSpec JSON
 * payload in expr, resolver-level special case before the catalog and the scalar
 * expression path — the payload is not a legal scalar expression).
 */
public class TestWi9AggregateCatalog {

    private static final Map<String, Object> NO_SCHEMA = new HashMap<>();

    private static StreamAggregatorFunctionResolver resolver() {
        return new StreamAggregatorFunctionResolver();
    }

    private static AggregateFunction<Object, Object, Object> aggOf(String fnId, String expr) {
        return resolver().resolve(fnId, expr, k -> null);
    }

    private static StreamException assertCompositeThrows(String expr) {
        return org.junit.jupiter.api.Assertions.assertThrows(StreamException.class,
                () -> aggOf("composite", expr));
    }

    // ---------------- maxBy ----------------

    @Test
    public void maxByKeepsTheWholeRecordWithTheMaxArgumentValue() {
        AggregateFunction<Object, Object, Object> fn = aggOf("maxBy", "price");
        fn.createAccumulator();
        Object acc = fn.createAccumulator();
        acc = fn.add(mapOf("price", 10, "id", "a"), acc);
        acc = fn.add(mapOf("price", 30, "id", "c"), acc);
        acc = fn.add(mapOf("price", 20, "id", "b"), acc);
        Object result = fn.getResult(acc);
        assertTrue(result instanceof Map, () -> "maxBy returns the whole record: " + result);
        assertEquals("c", ((Map<?, ?>) result).get("id"));
        assertEquals(30, ((Map<?, ?>) result).get("price"));
    }

    @Test
    public void maxByTieBreakIsFirstWins() {
        AggregateFunction<Object, Object, Object> fn = aggOf("maxBy", "price");
        Object acc = fn.createAccumulator();
        acc = fn.add(mapOf("price", 30, "id", "first"), acc);
        acc = fn.add(mapOf("price", 30, "id", "second"), acc);
        assertEquals("first", ((Map<?, ?>) fn.getResult(acc)).get("id"));
    }

    @Test
    public void maxBySkipsNullArgumentsAndReturnsNullOnEmptyWindow() {
        AggregateFunction<Object, Object, Object> fn = aggOf("maxBy", "price");
        Object acc = fn.createAccumulator();
        acc = fn.add(mapOf("price", null, "id", "n"), acc);
        assertEquals(null, fn.getResult(acc));
        acc = fn.add(mapOf("price", 5, "id", "a"), acc);
        assertEquals("a", ((Map<?, ?>) fn.getResult(acc)).get("id"));
    }

    // ---------------- distinct ----------------

    @Test
    public void distinctCountsExactWindowScopedDedupValues() {
        AggregateFunction<Object, Object, Object> fn = aggOf("distinct", "userId");
        Object acc = fn.createAccumulator();
        for (String u : new String[]{"u1", "u2", "u1", "u3", "u2"}) {
            acc = fn.add(mapOf("userId", u), acc);
        }
        assertEquals(3L, fn.getResult(acc));
        acc = fn.merge(acc, fn.getResult(acc) == null ? fn.createAccumulator() : acc);
        assertEquals(3L, fn.getResult(acc));
    }

    // ---------------- composite ----------------

    @Test
    public void compositeJsonSpecProducesPositionOrderedValues() {
        String spec = "{\"aggs\":[{\"fn\":\"sum\",\"expr\":\"amount\"},"
                + "{\"fn\":\"distinct\",\"expr\":\"userId\"},{\"fn\":\"maxBy\",\"expr\":\"price\"}]}";
        AggregateFunction<Object, Object, Object> fn = aggOf("composite", spec);
        Object acc = fn.createAccumulator();
        acc = fn.add(mapOf("amount", 10, "userId", "u1", "price", 5), acc);
        acc = fn.add(mapOf("amount", 4, "userId", "u2", "price", 9), acc);
        acc = fn.add(mapOf("amount", 6, "userId", "u1", "price", 7), acc);
        Object result = fn.getResult(acc);
        assertTrue(result instanceof java.util.List, () -> "composite result: " + result);
        java.util.List<?> row = (java.util.List<?>) result;
        // sum=20, distinct=2, maxBy record = the price=9 record
        assertEquals(20L, ((Number) row.get(0)).longValue());
        assertEquals(2L, ((Number) row.get(1)).longValue());
        assertEquals(9, ((Map<?, ?>) row.get(2)).get("price"));
    }

    @Test
    public void compositeRejectsNonJsonAndMissingAggs() {
        io.nop.api.core.exceptions.NopException e1 = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> aggOf("composite", "not-json"));
        assertTrue(e1.getMessage().contains("JSON"), () -> e1.getMessage());
        io.nop.api.core.exceptions.NopException e2 = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> aggOf("composite", "{\"keys\":[]}"));
        assertTrue(e2.getMessage().contains("aggs") || e2.getMessage().contains("composite"),
                () -> e2.getMessage());
    }

    // ---------------- grammar spec pins ----------------

    @Test
    public void bareCountStaysLegalAndStarFormIsJsonSpecFaceOnly() {
        // bare count (no expr) is the 0..1-arg form; the '*' star form belongs to the
        // SqlRowAggregateSpec JSON face (isStar) — the plain expr path cannot parse it.
        assertNotNull(aggOf("count", null));
        assertThrows(Exception.class, () -> aggOf("count", "*"));
    }

    @Test
    public void upperCaseFnIdStaysRejected() {
        StreamException e = assertThrows(StreamException.class,
                () -> aggOf("SUM", "amount"));
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
