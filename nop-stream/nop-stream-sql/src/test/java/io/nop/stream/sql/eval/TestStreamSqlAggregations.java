/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.sql.eval.StreamRecordEvaluator;
import io.nop.stream.sql.eval.StreamSqlAggregation;
import io.nop.stream.sql.eval.StreamSqlAggregations;
import io.nop.stream.sql.eval.StreamSqlExprCompiler;
import io.nop.stream.sql.parse.EqlExprSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9: the five built-in aggregate ids (sum/count/avg/min/max) accumulate over stream
 * records with SQL semantics — nulls skipped, empty-set result null (count: 0), sum of
 * integral inputs promoted to long, avg always double, COUNT(*) counts records.
 */
public class TestStreamSqlAggregations {

    private static AggregateFunction<Object, Object, Object> agg(String fnId, String argExpr) {
        StreamSqlAggregation spec = StreamSqlAggregations.resolve(fnId);
        StreamRecordEvaluator arg = argExpr == null ? null
                : StreamSqlExprCompiler.compileScalar(EqlExprSupport.parseExpr(argExpr));
        return spec.create(arg);
    }

    @SafeVarargs
    private static Object run(AggregateFunction<Object, Object, Object> fn, Map<String, Object>... rows) {
        Object acc = fn.createAccumulator();
        for (Map<String, Object> row : rows) {
            acc = fn.add(row, acc);
        }
        return fn.getResult(acc);
    }

    private static Map<String, Object> row(String k1, Object v1) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        return m;
    }

    @Test
    public void resolveKnownIdsAndUnknownReturnsNull() {
        for (String id : new String[]{"sum", "count", "avg", "min", "max"}) {
            assertTrue(StreamSqlAggregations.resolve(id) != null, "builtin id must resolve: " + id);
        }
        assertNull(StreamSqlAggregations.resolve("median"), "unknown fnId must return null (A4: bean-first ordering)");
    }

    @Test
    public void sumSkipsNullsPromotesIntegralToLongAndEmptyIsNull() {
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        // 1 + 2 + null skipped = 3 (long promotion)
        assertEquals(3L, run(sum, row("amount", 1), row("amount", 2), row("amount", null)));
        // integral inputs promote to Long
        Object r = run(sum, row("amount", 1), row("amount", 2));
        assertTrue(r instanceof Long, "integral sum must promote to Long, got " + r.getClass());
        // empty set (all null) -> null
        assertNull(run(sum, row("amount", null)));
    }

    @Test
    public void sumOfFloatingInputsStaysDouble() {
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object r = run(sum, row("amount", 1.5), row("amount", 2.5));
        assertEquals(4.0, r);
        assertTrue(r instanceof Double);
    }

    @Test
    public void countSkipsNullsCountsRecordsOnStarAndEmptyIsZero() {
        assertEquals(2L, run(agg("count", "v"), row("v", 1), row("v", null), row("v", 3)),
                "count(expr) skips nulls");
        assertEquals(3L, run(agg("count", null), row("v", 1), row("v", null), row("v", 3)),
                "COUNT(*) form counts every record");
        assertEquals(0L, run(agg("count", "v")), "empty set count is 0");
    }

    @Test
    public void avgYieldsDoubleAndEmptyIsNull() {
        assertEquals(2.0, run(agg("avg", "v"), row("v", 1), row("v", 3)));
        assertNull(run(agg("avg", "v"), row("v", null)), "empty set avg is null");
    }

    @Test
    public void minMaxSkipNullsAndCompareNaturally() {
        assertEquals(1, run(agg("min", "v"), row("v", 3), row("v", 1), row("v", null), row("v", 2)));
        assertEquals(3, run(agg("max", "v"), row("v", 3), row("v", 1), row("v", null), row("v", 2)));
        assertEquals("a", run(agg("min", "s"), row("s", "b"), row("s", "a")),
                "string min uses natural Comparable order");
        assertNull(run(agg("min", "v")), "empty set min is null");
    }

    @Test
    public void mergeCombinesAccumulators() {
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object left = sum.add(row("amount", 1), sum.createAccumulator());
        Object right = sum.add(row("amount", 2), sum.createAccumulator());
        assertEquals(3L, sum.getResult(sum.merge(left, right)));
    }

    @Test
    public void distinctFailsFast() {
        StreamSqlAggregation spec = StreamSqlAggregations.resolve("sum");
        StreamRecordEvaluator arg = StreamSqlExprCompiler.compileScalar(EqlExprSupport.parseExpr("amount"));
        StreamException e = assertThrows(StreamException.class, () -> spec.withDistinct(true).create(arg),
                "DISTINCT aggregates are outside the v1 subset — fail fast, no silent approximation");
        assertEquals("nop.err.stream.invalid-arg", e.getErrorCode());
    }

    @Test
    public void nonPositiveArityValidated() {
        // count: 0..1 args; everything else exactly 1
        assertTrue(StreamSqlAggregations.resolve("count").allowsNoArg());
        assertTrue(!StreamSqlAggregations.resolve("sum").allowsNoArg());
    }

    @Test
    public void resultTypesFollowD7StyleRules() {
        assertEquals(BasicTypeInfo.LONG, StreamSqlAggregations.resolve("count").resultType(BasicTypeInfo.INT));
        assertEquals(BasicTypeInfo.DOUBLE, StreamSqlAggregations.resolve("avg").resultType(BasicTypeInfo.INT));
        assertEquals(BasicTypeInfo.LONG, StreamSqlAggregations.resolve("sum").resultType(BasicTypeInfo.INT));
        assertEquals(BasicTypeInfo.DOUBLE, StreamSqlAggregations.resolve("sum").resultType(BasicTypeInfo.DOUBLE));
        assertEquals(BasicTypeInfo.STRING, StreamSqlAggregations.resolve("max").resultType(BasicTypeInfo.STRING));
    }

    @Test
    public void serializationPreservesAggregation() throws Exception {
        AggregateFunction<Object, Object, Object> fn = agg("sum", "amount");
        Object acc = fn.add(row("amount", 2), fn.createAccumulator());
        byte[] bytes;
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos)) {
            oos.writeObject(fn);
            oos.writeObject(acc);
            bytes = baos.toByteArray();
        }
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes))) {
            AggregateFunction<Object, Object, Object> copy =
                    (AggregateFunction<Object, Object, Object>) ois.readObject();
            Object accCopy = ois.readObject();
            assertEquals(2L, copy.getResult(accCopy),
                    "aggregation + accumulator must survive Java serialization (operator copy contract)");
        }
    }
}
