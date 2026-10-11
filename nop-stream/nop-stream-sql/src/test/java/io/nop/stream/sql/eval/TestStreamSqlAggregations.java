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
import java.math.BigDecimal;
import java.math.BigInteger;

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

    // WI17 (plan 2342): DISTINCT is now supported via DistinctAggregateWrapper
// ========== plan 2327（P0-2.8-001 混型累加修复 + dim10-10 BigDecimal/BigInteger）回归钉 ==========

    @Test
    public void sumMixedIntegralAndFloatingKeepsIntegralPart() {
        // P0-2.8-001 probe ①: old behavior silently dropped integral part (3.5)
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object r = run(sum, row("amount", 1), row("amount", 2), row("amount", 3.5));
        assertEquals(6.5, r);
        assertTrue(r instanceof Double);
    }

    @Test
    public void sumMixedOrdersAllAgree() {
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        assertEquals(3.5, run(sum, row("amount", 0.5), row("amount", 1), row("amount", 2)));
        assertEquals(6.0, run(sum, row("amount", 3), row("amount", 4), row("amount", 5),
                row("amount", -6.0)));
        assertEquals(2.5, run(sum, row("amount", 1.5), row("amount", -1.0), row("amount", 2)));
    }

    @Test
    public void sumFloatingCollapseEliminated() {
        // P0-2.8-001 probe ⑤: 1.5 + (-1.5) + 2 must stay Double (flag monotonic, no 0.0 collapse)
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object r = run(sum, row("amount", 1.5), row("amount", -1.5), row("amount", 2));
        assertEquals(2.0, r);
        assertTrue(r instanceof Double, "floating input must pin result to Double, got "
                + r.getClass());
    }

    @Test
    public void sumBigDecimalStreamIsExact() {
        // dim10-10: old behavior truncated every record via longValue() (1.5+2.25 -> 3)
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object r = run(sum, row("amount", new BigDecimal("1.5")),
                row("amount", new BigDecimal("2.25")));
        assertEquals(new BigDecimal("3.75"), r);
    }

    @Test
    public void sumBigIntegerDoesNotRewind() {
        // dim10-10: old behavior wound 2^70 back to 0 via longValue()
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object r = run(sum, row("amount", BigInteger.TWO.pow(70)));
        // ladder: sawDecimal -> BigDecimal (exact, no rewind); type is BigDecimal by contract
        assertEquals(new BigDecimal(BigInteger.TWO.pow(70)), r);
    }

    @Test
    public void sumLongPlusBigDecimalMixRuling() {
        // design fork ②: long + BigDecimal mixes -> BigDecimal channel (sawDecimal wins)
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object r = run(sum, row("amount", 1), row("amount", new BigDecimal("2.25")));
        assertEquals(new BigDecimal("3.25"), r);
    }

    @Test
    public void sumMixedMergeCombinesAllSlotsAndFlags() {
        // merge regression pin: three slots + monotonic flag OR
        AggregateFunction<Object, Object, Object> sum = agg("sum", "amount");
        Object left = sum.createAccumulator();
        left = sum.add(row("amount", 1), left);
        left = sum.add(row("amount", 2.5), left);
        Object right = sum.createAccumulator();
        right = sum.add(row("amount", new BigDecimal("0.25")), right);
        right = sum.add(row("amount", null), right);
        Object merged = sum.merge(left, right);
        Object r = sum.getResult(merged);
        // sawDecimal wins the ladder: 1 + 2.5 + 0.25 = 3.75 as BigDecimal
        assertEquals(new BigDecimal("3.75"), r);
    }

    @Test
    void distinctFiltersDuplicates() {
        StreamSqlAggregation agg = StreamSqlAggregations.resolve("count").withDistinct(true);
        io.nop.stream.core.common.functions.AggregateFunction<Object, Object, Object> fn =
                agg.create(null);

        Object acc = fn.createAccumulator();
        acc = fn.add("a", acc);
        acc = fn.add("a", acc); // duplicate — must be filtered
        acc = fn.add("b", acc);
        assertEquals(2L, fn.getResult(acc), "COUNT(DISTINCT) must deduplicate values");
    }

    @Test
    void distinctNullValuesNotCounted() {
        StreamSqlAggregation agg = StreamSqlAggregations.resolve("count").withDistinct(true);
        io.nop.stream.core.common.functions.AggregateFunction<Object, Object, Object> fn =
                agg.create(null);

        Object acc = fn.createAccumulator();
        acc = fn.add(null, acc);
        acc = fn.add(null, acc);
        assertEquals(0L, fn.getResult(acc), "null values must not be counted");
    }

    /** DISTINCT aggregate over an evaluated argument (the COUNT(DISTINCT v) shape). */
    private static AggregateFunction<Object, Object, Object> distinctAgg(String fnId, String argExpr) {
        StreamRecordEvaluator arg = argExpr == null ? null
                : StreamSqlExprCompiler.compileScalar(EqlExprSupport.parseExpr(argExpr));
        return StreamSqlAggregations.resolve(fnId).withDistinct(true).create(arg);
    }

    private static Map<String, Object> row2(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    /**
     * WI17 (plan 2342): the continuous reduce path merges a state accumulator with a
     * fresh single-record accumulator (SqlRowAggregateOps.merge) — a value both sides
     * have seen must count once. merge replays only the unseen records; a plain inner
     * merge would double-count.
     */
    @Test
    void distinctMergeReplaysOnlyUnseenValues() {
        AggregateFunction<Object, Object, Object> count = distinctAgg("count", "v");
        Object state = count.createAccumulator();
        state = count.add(row("v", 1), state);
        state = count.add(row("v", 2), state);

        Object fresh = count.createAccumulator();
        fresh = count.add(row("v", 2), fresh); // duplicate across sides
        fresh = count.add(row("v", 3), fresh);

        assertEquals(3L, count.getResult(count.merge(state, fresh)),
                "COUNT(DISTINCT) over {1,2} ⊕ {2,3} must be 3, not 4");

        AggregateFunction<Object, Object, Object> sum = distinctAgg("sum", "v");
        Object sumState = sum.createAccumulator();
        sumState = sum.add(row("v", 1), sumState);
        sumState = sum.add(row("v", 2), sumState);
        Object sumFresh = sum.createAccumulator();
        sumFresh = sum.add(row("v", 2), sumFresh); // duplicate across sides
        sumFresh = sum.add(row("v", 3), sumFresh);
        assertEquals(6L, sum.getResult(sum.merge(sumState, sumFresh)),
                "SUM(DISTINCT) over {1,2} ⊕ {2,3} must be 6, not 8");
    }

    /**
     * WI17 (plan 2342) audit pin: min/max DISTINCT is semantically identical to the
     * non-distinct form (dedup is idempotent for extremum aggregation) — through both
     * the sequential add path and the reduce-path merge (state ⊕ fresh replays only
     * unseen records, so the result equals aggregating all values once).
     */
    @Test
    void distinctMinMaxMatchesNonDistinct() {
        for (String fnId : new String[]{"min", "max"}) {
            Map<String, Object> r1 = row2("item", "a", "v", 3);
            Map<String, Object> r2 = row2("item", "a", "v", 1);
            Map<String, Object> r3 = row2("item", "a", "v", 1);
            Map<String, Object> r4 = row2("item", "a", "v", 2);

            assertEquals(run(agg(fnId, "v"), r1, r2, r3, r4),
                    run(distinctAgg(fnId, "v"), r1, r2, r3, r4),
                    fnId + "(DISTINCT) must equal " + fnId + "() on add path");

            AggregateFunction<Object, Object, Object> distinctFn = distinctAgg(fnId, "v");
            Object left = distinctFn.createAccumulator();
            left = distinctFn.add(r1, left);
            left = distinctFn.add(r2, left);
            Object right = distinctFn.createAccumulator();
            right = distinctFn.add(r3, right);
            right = distinctFn.add(r4, right);
            assertEquals(run(agg(fnId, "v"), r1, r2, r3, r4),
                    distinctFn.getResult(distinctFn.merge(left, right)),
                    fnId + "(DISTINCT) must equal " + fnId + "() on merge-replay path");
        }
    }

    /**
     * WI17 (plan 2342, M-3): continuous accumulator rows round-trip through
     * SnapshotCopyUtil.typedCopy on checkpoint snapshot — the DISTINCT seen-map must
     * survive the JSON round trip and keep deduplicating after restore.
     */
    @Test
    void distinctCompositeAccumulatorSurvivesSnapshotCopyAndContinues() {
        SqlRowAggregateSpec spec = new SqlRowAggregateSpec(
                Arrays.asList("item"),
                Arrays.asList(new SqlRowAggregateSpec.AggCall("count", "v", false, true)));
        AggregateFunction<Object, Object, Object> composite = StreamSqlAggregations.buildComposite(spec);

        Object acc = composite.createAccumulator();
        Map<String, Object> r1 = row2("item", "a", "v", 1);
        acc = composite.add(r1, acc);
        acc = composite.add(r1, acc); // same record twice pre-snapshot
        acc = composite.add(row2("item", "a", "v", 2), acc);
        assertEquals(2L, ((java.util.List<?>) composite.getResult(acc)).get(1),
                "pre-snapshot COUNT(DISTINCT v) = {1,2}");

        Object restored = io.nop.stream.core.util.SnapshotCopyUtil.typedCopy(acc);

        restored = composite.add(r1, restored); // duplicate after restore — dedup must hold
        restored = composite.add(row2("item", "a", "v", 3), restored);
        assertEquals(3L, ((java.util.List<?>) composite.getResult(restored)).get(1),
                "post-restore COUNT(DISTINCT v) = {1,2,3} — seen-map survived the JSON round trip");
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

    @Test
    public void serializationPreservesMixedTypeAccumulator() throws Exception {
        // plan 2327 advisory: BigDecimal-carrying accumulator must survive Java serialization
        AggregateFunction<Object, Object, Object> fn = agg("sum", "amount");
        Object acc = fn.createAccumulator();
        acc = fn.add(row("amount", 1), acc);
        acc = fn.add(row("amount", new BigDecimal("2.5")), acc);
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
            Object r = copy.getResult(accCopy);
            assertEquals(new BigDecimal("3.5"), r,
                    "mixed-type accumulator must survive Java serialization with exact decimal sum");
            assertTrue(r instanceof BigDecimal, "ladder must stay on BigDecimal channel after round-trip");
        }
    }
}
