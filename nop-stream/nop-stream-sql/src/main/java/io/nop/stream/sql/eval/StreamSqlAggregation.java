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
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.exceptions.StreamException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * The description of one built-in aggregate id (WI9, roadmap A4): a pure parameter
 * descriptor — NOT a bean. Consumers resolve in the A4 order (bean container first,
 * then {@link StreamSqlAggregations#resolve}), so WI8c's declaration surface only
 * needs the id and the argument shape; the accumulation semantics live here and are
 * never duplicated in WI8c.
 *
 * <p>{@link #create} produces a full
 * {@link AggregateFunction}{@code <Object,Object,Object>} over stream records: the
 * evaluator extracts the argument value inside {@code add}, so it plugs into
 * {@code WindowedStream.aggregate} with zero adaptation.
 */
public final class StreamSqlAggregation {

    private final String fnId;
    private final boolean allowsNoArg;
    private final boolean distinct;

    StreamSqlAggregation(String fnId, boolean allowsNoArg) {
        this(fnId, allowsNoArg, false);
    }

    private StreamSqlAggregation(String fnId, boolean allowsNoArg, boolean distinct) {
        this.fnId = fnId;
        this.allowsNoArg = allowsNoArg;
        this.distinct = distinct;
    }

    public String getFnId() {
        return fnId;
    }

    /**
     * Whether the function may be declared without an argument expression
     * ({@code COUNT(*)}); false for sum/avg/min/max (exactly one argument).
     */
    public boolean allowsNoArg() {
        return allowsNoArg;
    }

    /**
     * Marks this descriptor as a DISTINCT aggregation. Since WI17 (plan 2342,
     * superseding the v1 exclusion 4a#6) {@link #create} returns a
     * {@link DistinctAggregateWrapper} that deduplicates argument values before they
     * reach the inner aggregate — a marked declaration never silently degrades to the
     * non-distinct aggregate.
     */
    public StreamSqlAggregation withDistinct(boolean distinct) {
        return new StreamSqlAggregation(fnId, allowsNoArg, distinct);
    }

    public boolean isDistinct() {
        return distinct;
    }

    /**
     * Result type rule: count→LONG, avg→DOUBLE, sum promotes integral to LONG and
     * floating stays, min/max pass the argument type through.
     *
     * @param argType the resolved argument type (D7-mapped), may be null/unknown
     */
    public TypeInformation<?> resultType(TypeInformation<?> argType) {
        return StreamSqlExprCompiler.aggregateResultType(fnId, argType);
    }

    /**
     * Instantiates the accumulation semantics over the given argument evaluator.
     *
     * @param argEvaluator the compiled argument expression, or null only for
     *                     {@code COUNT(*)} (a no-arg-allowed function)
     * @return a Serializable AggregateFunction whose IN is the raw stream record
     */
    public AggregateFunction<Object, Object, Object> create(StreamRecordEvaluator argEvaluator) {
        if (argEvaluator == null && !allowsNoArg) {
            throw invalidArg("aggregate '" + fnId + "' requires exactly one argument expression");
        }
        AggregateFunction<Object, Object, Object> aggregate = doCreate(argEvaluator);
        // WI17 (plan 2342): DISTINCT is implemented as a wrapper that deduplicates the
        // evaluated argument values before they reach the inner aggregate (supersedes
        // 4a#6). min/max DISTINCT stay semantically identical to the non-distinct form.
        if (distinct) {
            return new DistinctAggregateWrapper(aggregate, argEvaluator);
        }
        return aggregate;
    }

    private AggregateFunction<Object, Object, Object> doCreate(StreamRecordEvaluator argEvaluator) {
        switch (fnId) {
            case "count":
                return argEvaluator == null ? new CountStarAgg() : new CountAgg(argEvaluator);
            case "sum":
                return new SumAgg(argEvaluator);
            case "avg":
                return new AvgAgg(argEvaluator);
            case "min":
                return new MinMaxAgg(argEvaluator, true);
            case "max":
                return new MinMaxAgg(argEvaluator, false);
            case "maxBy":
                return new MaxByAgg(argEvaluator);
            case "distinct":
                return new DistinctCountAgg(argEvaluator);
            default:
                throw invalidArg("unknown builtin aggregate id '" + fnId + "'");
        }
    }

    private static StreamException invalidArg(String detail) {
        // param() is declared on NopException; the chain returns this, so the cast is safe
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "aggregation")
                .param(ARG_DETAIL, detail);
    }

    // ----------------------------------------------------------------
    // accumulation semantics (nulls skipped; empty set: count=0, others null)
    // ----------------------------------------------------------------

    abstract static class BaseAgg implements AggregateFunction<Object, Object, Object> {
        private static final long serialVersionUID = 1L;

        final StreamRecordEvaluator arg;

        BaseAgg(StreamRecordEvaluator arg) {
            this.arg = arg;
        }

        StreamException invalid(String detail) {
            return invalidArg(fnName() + ": " + detail);
        }

        abstract String fnName();
    }

    static final class CountStarAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        CountStarAgg() {
            super(null);
        }

        @Override
        String fnName() {
            return "count(*)";
        }

        @Override
        public Object createAccumulator() {
            return 0L;
        }

        @Override
        public Object add(Object value, Object accumulator) {
            return SqlAccShapeUtil.asLong(accumulator) + 1L;
        }

        @Override
        public Object getResult(Object accumulator) {
            return SqlAccShapeUtil.asLong(accumulator);
        }

        @Override
        public Object merge(Object a, Object b) {
            return SqlAccShapeUtil.asLong(a) + SqlAccShapeUtil.asLong(b);
        }
    }

    static final class CountAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        CountAgg(StreamRecordEvaluator arg) {
            super(arg);
        }

        @Override
        String fnName() {
            return "count";
        }

        @Override
        public Object createAccumulator() {
            return 0L;
        }

        @Override
        public Object add(Object record, Object accumulator) {
            return arg.eval(record) == null ? accumulator : SqlAccShapeUtil.asLong(accumulator) + 1L;
        }

        @Override
        public Object getResult(Object accumulator) {
            return SqlAccShapeUtil.asLong(accumulator);
        }

        @Override
        public Object merge(Object a, Object b) {
            return SqlAccShapeUtil.asLong(a) + SqlAccShapeUtil.asLong(b);
        }
    }

    static final class SumAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        SumAgg(StreamRecordEvaluator arg) {
            super(arg);
        }

        @Override
        String fnName() {
            return "sum";
        }

        /**
         * Accumulator slots (plan 2327, P0-2.8-001): [0]=integral sum, [1]=floating sum,
         * [2]=decimal sum, [3]=saw floating flag, [4]=saw decimal flag, [5]=saw value.
         * Result ladder: sawDecimal -> BigDecimal, else sawFloating -> Double, else Long,
         * else null. Flags are monotonic so the result type never collapses back.
         */
        @Override
        public Object createAccumulator() {
            return new Object[]{0L, 0.0, BigDecimal.ZERO, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE};
        }

        @Override
        public Object add(Object record, Object accumulator) {
            Object v = arg.eval(record);
            if (v == null) {
                return accumulator;
            }
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            acc[5] = Boolean.TRUE;
            if (v instanceof Double || v instanceof Float) {
                acc[3] = Boolean.TRUE;
                acc[1] = SqlAccShapeUtil.asDouble(acc[1]) + ((Number) v).doubleValue();
            } else if (v instanceof BigDecimal || v instanceof BigInteger) {
                acc[4] = Boolean.TRUE;
                BigDecimal dec = (v instanceof BigDecimal) ? (BigDecimal) v
                        : new BigDecimal((BigInteger) v);
                acc[2] = ((BigDecimal) acc[2]).add(dec);
            } else {
                Number n = RecordColumnAccess.toNumber(v, "sum operand");
                acc[0] = SqlAccShapeUtil.asLong(acc[0]) + n.longValue();
            }
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            if (!(Boolean) acc[5]) {
                return null;
            }
            if ((Boolean) acc[4]) {
                BigDecimal sum = (BigDecimal) acc[2];
                if ((Boolean) acc[3])
                    sum = sum.add(BigDecimal.valueOf(SqlAccShapeUtil.asDouble(acc[1])));
                return sum.add(BigDecimal.valueOf(SqlAccShapeUtil.asLong(acc[0])));
            }
            if ((Boolean) acc[3]) {
                return SqlAccShapeUtil.asDouble(acc[1]) + SqlAccShapeUtil.asLong(acc[0]);
            }
            return acc[0];
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = SqlAccShapeUtil.asArray(a);
            Object[] y = SqlAccShapeUtil.asArray(b);
            x[0] = SqlAccShapeUtil.asLong(x[0]) + SqlAccShapeUtil.asLong(y[0]);
            x[1] = SqlAccShapeUtil.asDouble(x[1]) + SqlAccShapeUtil.asDouble(y[1]);
            x[2] = ((BigDecimal) x[2]).add((BigDecimal) y[2]);
            x[3] = (Boolean) x[3] || (Boolean) y[3];
            x[4] = (Boolean) x[4] || (Boolean) y[4];
            x[5] = (Boolean) x[5] || (Boolean) y[5];
            return x;
        }
    }

    static final class AvgAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        AvgAgg(StreamRecordEvaluator arg) {
            super(arg);
        }

        @Override
        String fnName() {
            return "avg";
        }

        /** Accumulator: [0]=sum (Double), [1]=count (Long). */
        @Override
        public Object createAccumulator() {
            return new Object[]{0.0, 0L};
        }

        @Override
        public Object add(Object record, Object accumulator) {
            Object v = arg.eval(record);
            if (v == null) {
                return accumulator;
            }
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            acc[0] = SqlAccShapeUtil.asDouble(acc[0]) + RecordColumnAccess.toDouble(v, "avg operand");
            acc[1] = SqlAccShapeUtil.asLong(acc[1]) + 1L;
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            long count = SqlAccShapeUtil.asLong(acc[1]);
            return count == 0 ? null : SqlAccShapeUtil.asDouble(acc[0]) / count;
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = SqlAccShapeUtil.asArray(a);
            Object[] y = SqlAccShapeUtil.asArray(b);
            x[0] = SqlAccShapeUtil.asDouble(x[0]) + SqlAccShapeUtil.asDouble(y[0]);
            x[1] = SqlAccShapeUtil.asLong(x[1]) + SqlAccShapeUtil.asLong(y[1]);
            return x;
        }
    }

    /**
     * WI9 (plan 2355): keeps the WHOLE record whose argument expression evaluates to
     * the maximum — accumulator shape = Object[3]{extremeKey, record, saw-flag}.
     * Tie-break = strict '>' (first-wins, MinMaxAgg precedent); null argument values
     * are skipped; an empty window yields null. The comparison reuses the
     * numeric-aware {@code compareValues} (JSON round-trip Integer/Long safety).
     */
    static final class MaxByAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        MaxByAgg(StreamRecordEvaluator arg) {
            super(arg);
        }

        @Override
        String fnName() {
            return "maxBy";
        }

        @Override
        public Object createAccumulator() {
            return new Object[]{null, null, Boolean.FALSE};
        }

        @Override
        public Object add(Object record, Object accumulator) {
            Object key = arg.eval(record);
            if (key == null) {
                return accumulator;
            }
            if (!(key instanceof Comparable)) {
                throw invalid("maxBy requires a comparable operand, got "
                        + key.getClass().getName());
            }
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            if (!(Boolean) acc[2]) {
                acc[0] = key;
                acc[1] = record;
                acc[2] = Boolean.TRUE;
                return acc;
            }
            if (MinMaxAgg.compareValues(key, acc[0]) > 0) {
                acc[0] = key;
                acc[1] = record;
            }
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            return (Boolean) acc[2] ? acc[1] : null;
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = SqlAccShapeUtil.asArray(a);
            Object[] y = SqlAccShapeUtil.asArray(b);
            if (!(Boolean) y[2]) {
                return x;
            }
            if (!(Boolean) x[2]) {
                x[0] = y[0];
                x[1] = y[1];
                x[2] = Boolean.TRUE;
                return x;
            }
            if (MinMaxAgg.compareValues(y[0], x[0]) > 0) {
                x[0] = y[0];
                x[1] = y[1];
            }
            return x;
        }
    }

    /**
     * WI9 (plan 2355): the declaration-face DISTINCT-count aggregate — window-scoped
     * EXACT dedup semantics (the WI0(d) "approximate" wording was imprecise; the
     * difference from the SQL-face DISTINCT aggregate is scope/lifecycle, not
     * precision). Accumulator = HashSet of argument values (null skipped, mirroring
     * DistinctAggregateWrapper); result = the set cardinality as a Long. Memory
     * bound = the number of distinct argument values in the window, cleared with the
     * window.
     */
    static final class DistinctCountAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        DistinctCountAgg(StreamRecordEvaluator arg) {
            super(arg);
        }

        @Override
        String fnName() {
            return "distinct";
        }

        @Override
        public Object createAccumulator() {
            return new java.util.LinkedHashSet<>();
        }

        @Override
        public Object add(Object record, Object accumulator) {
            Object v = arg.eval(record);
            if (v == null) {
                return accumulator;
            }
            ((java.util.Set<Object>) accumulator).add(v);
            return accumulator;
        }

        @Override
        public Object getResult(Object accumulator) {
            return (long) ((java.util.Set<?>) accumulator).size();
        }

        @Override
        public Object merge(Object a, Object b) {
            ((java.util.Set<Object>) a).addAll((java.util.Set<?>) b);
            return a;
        }
    }

    static final class MinMaxAgg extends BaseAgg {
        private static final long serialVersionUID = 1L;

        private final boolean min;

        MinMaxAgg(StreamRecordEvaluator arg, boolean min) {
            super(arg);
            this.min = min;
        }

        @Override
        String fnName() {
            return min ? "min" : "max";
        }

        @Override
        public Object createAccumulator() {
            // [0]=current extreme, [1]=saw-value flag (null results are skipped, so
            // the flag distinguishes "empty" from "extreme is null")
            return new Object[]{null, Boolean.FALSE};
        }

        @Override
        public Object add(Object record, Object accumulator) {
            Object v = arg.eval(record);
            if (v == null) {
                return accumulator;
            }
            if (!(v instanceof Comparable)) {
                throw invalid("min/max require a comparable operand, got "
                        + (v == null ? "null" : v.getClass().getName()));
            }
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            if (!(Boolean) acc[1]) {
                acc[0] = v;
                acc[1] = Boolean.TRUE;
                return acc;
            }
            if (min ? compareValues(v, acc[0]) < 0 : compareValues(v, acc[0]) > 0) {
                acc[0] = v;
            }
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            return (Boolean) acc[1] ? acc[0] : null;
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = SqlAccShapeUtil.asArray(a);
            Object[] y = SqlAccShapeUtil.asArray(b);
            if (!(Boolean) y[1]) {
                return x;
            }
            if (!(Boolean) x[1]) {
                x[0] = y[0];
                x[1] = Boolean.TRUE;
                return x;
            }
            if (min ? compareValues(y[0], x[0]) < 0 : compareValues(y[0], x[0]) > 0) {
                x[0] = y[0];
            }
            return x;
        }

        /**
         * JSON round trips rebuild integral scalars as Integer regardless of the
         * original Long — a restored extreme and a fresh record value can then be
         * different Number subtypes, where raw {@code Comparable} would throw
         * ClassCastException. Integral pairs compare as long, mixed numeric pairs as
         * double, everything else stays on natural Comparable order.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        static int compareValues(Object a, Object b) {
            if (a instanceof Number && b instanceof Number) {
                if (isIntegral(a) && isIntegral(b)) {
                    return Long.compare(((Number) a).longValue(), ((Number) b).longValue());
                }
                return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
            }
            return ((Comparable) a).compareTo(b);
        }

        private static boolean isIntegral(Object v) {
            return v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long;
        }
    }
    /**
     * WI17 (plan 2342): DISTINCT wrapper for any AggregateFunction — deduplicates the
     * evaluated argument values before they reach the inner aggregate. Accumulator
     * shape = Object[2]{inner_acc, LinkedHashMap&lt;String,Object&gt;} where the map key is
     * {@code String.valueOf(argValue)} (JSON round-trip stable) and the value is the
     * record that introduced it: {@link #merge} replays the losing side's unseen
     * records through the inner aggregate's {@code add}, so a value shared by both
     * sides is counted exactly once. The continuous SQL pipeline merges a state
     * accumulator with a fresh single-record accumulator
     * ({@code SqlRowAggregateOps.merge}) — a plain inner merge would double-count
     * values the state side has already seen, which exact DISTINCT forbids.
     *
     * <p>NULL argument values never enter the seen-set (SQL DISTINCT ignores nulls).
     * Exact dedup is memory-bounded by the number of distinct argument values per
     * group — for windowed aggregates state clears at window close; a continuous
     * DISTINCT aggregate carries it for the job's lifetime (declared cost, no
     * approximation).
     */
    static final class DistinctAggregateWrapper implements AggregateFunction<Object, Object, Object> {
        private static final long serialVersionUID = 1L;

        private final AggregateFunction<Object, Object, Object> inner;

        /** null only for the COUNT(*) form: the record itself is the dedup domain. */
        private final StreamRecordEvaluator arg;

        DistinctAggregateWrapper(AggregateFunction<Object, Object, Object> inner, StreamRecordEvaluator arg) {
            this.inner = inner;
            this.arg = arg;
        }

        /** dedup key for the argument extracted from {@code record}; null = skip value. */
        private String dedupKey(Object record) {
            Object v = arg == null ? record : arg.eval(record);
            return v == null ? null : String.valueOf(v);
        }

        @Override
        public Object createAccumulator() {
            return new Object[]{inner.createAccumulator(), new LinkedHashMap<String, Object>()};
        }

        @Override
        public Object add(Object record, Object accumulator) {
            String key = dedupKey(record);
            if (key == null) {
                return accumulator; // SQL DISTINCT ignores null values
            }
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            Map<String, Object> seen = (Map<String, Object>) acc[1];
            if (seen.containsKey(key)) {
                return accumulator;
            }
            seen.put(key, record);
            acc[0] = inner.add(record, acc[0]);
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = SqlAccShapeUtil.asArray(accumulator);
            return inner.getResult(acc[0]);
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] accA = SqlAccShapeUtil.asArray(a);
            Object[] accB = SqlAccShapeUtil.asArray(b);
            Map<String, Object> seenA = (Map<String, Object>) accA[1];
            Map<String, Object> seenB = (Map<String, Object>) accB[1];
            for (Map.Entry<String, Object> entry : seenB.entrySet()) {
                if (!seenA.containsKey(entry.getKey())) {
                    seenA.put(entry.getKey(), entry.getValue());
                    accA[0] = inner.add(entry.getValue(), accA[0]);
                }
            }
            return accA;
        }
    }
}