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
     * Marks this descriptor as a DISTINCT aggregation. The v1 stream SQL subset does
     * not implement DISTINCT — {@link #create} fails fast on a marked descriptor so a
     * declaration never silently degrades to the non-distinct aggregate (the
     * out-of-subset confirmation belongs to WI16).
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
        if (distinct) {
            throw invalidArg("DISTINCT " + fnId + "() is outside the stream SQL v1 subset; "
                    + "declare the aggregate without DISTINCT (WI16 confirms the supported list)");
        }
        if (argEvaluator == null && !allowsNoArg) {
            throw invalidArg("aggregate '" + fnId + "' requires exactly one argument expression");
        }
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
            return (Long) accumulator + 1L;
        }

        @Override
        public Object getResult(Object accumulator) {
            return accumulator;
        }

        @Override
        public Object merge(Object a, Object b) {
            return (Long) a + (Long) b;
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
            return arg.eval(record) == null ? accumulator : (Long) accumulator + 1L;
        }

        @Override
        public Object getResult(Object accumulator) {
            return accumulator;
        }

        @Override
        public Object merge(Object a, Object b) {
            return (Long) a + (Long) b;
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

        /** Accumulator: long[0]=integral sum, double[1]=floating sum, Boolean[0]=saw value. */
        @Override
        public Object createAccumulator() {
            return new Object[]{0L, 0.0, Boolean.FALSE};
        }

        @Override
        public Object add(Object record, Object accumulator) {
            Object v = arg.eval(record);
            if (v == null) {
                return accumulator;
            }
            Object[] acc = (Object[]) accumulator;
            acc[2] = Boolean.TRUE;
            if (v instanceof Double || v instanceof Float) {
                acc[1] = (Double) acc[1] + ((Number) v).doubleValue();
            } else {
                Number n = RecordColumnAccess.toNumber(v, "sum operand");
                acc[0] = (Long) acc[0] + n.longValue();
            }
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = (Object[]) accumulator;
            if (!(Boolean) acc[2]) {
                return null;
            }
            return (Double) acc[1] != 0.0 ? acc[1] : acc[0];
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = (Object[]) a;
            Object[] y = (Object[]) b;
            x[0] = (Long) x[0] + (Long) y[0];
            x[1] = (Double) x[1] + (Double) y[1];
            x[2] = (Boolean) x[2] || (Boolean) y[2];
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
            Object[] acc = (Object[]) accumulator;
            acc[0] = (Double) acc[0] + RecordColumnAccess.toDouble(v, "avg operand");
            acc[1] = (Long) acc[1] + 1L;
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = (Object[]) accumulator;
            long count = (Long) acc[1];
            return count == 0 ? null : (Double) acc[0] / count;
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = (Object[]) a;
            Object[] y = (Object[]) b;
            x[0] = (Double) x[0] + (Double) y[0];
            x[1] = (Long) x[1] + (Long) y[1];
            return x;
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
            Object[] acc = (Object[]) accumulator;
            if (!(Boolean) acc[1]) {
                acc[0] = v;
                acc[1] = Boolean.TRUE;
                return acc;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            int cmp = ((Comparable) v).compareTo(acc[0]);
            if (min ? cmp < 0 : cmp > 0) {
                acc[0] = v;
            }
            return acc;
        }

        @Override
        public Object getResult(Object accumulator) {
            Object[] acc = (Object[]) accumulator;
            return (Boolean) acc[1] ? acc[0] : null;
        }

        @Override
        public Object merge(Object a, Object b) {
            Object[] x = (Object[]) a;
            Object[] y = (Object[]) b;
            if (!(Boolean) y[1]) {
                return x;
            }
            if (!(Boolean) x[1]) {
                x[0] = y[0];
                x[1] = Boolean.TRUE;
                return x;
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            int cmp = ((Comparable) y[0]).compareTo(x[0]);
            if (min ? cmp < 0 : cmp > 0) {
                x[0] = y[0];
            }
            return x;
        }
    }
}
