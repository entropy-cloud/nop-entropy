/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.eval;

import io.nop.stream.core.common.typeinfo.BasicTypeInfo;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.sql.parse.EqlExprSupport;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBetweenExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlBooleanLiteral;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlInValuesExpr;
import io.nop.orm.eql.ast.SqlIsNullExpr;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlNullLiteral;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlStringLiteral;
import io.nop.orm.eql.ast.SqlUnaryExpr;
import io.nop.orm.eql.enums.SqlOperator;

import java.util.function.Function;

/**
 * Compiles the stream SQL v1 scalar expression subset (WI9) from EQL ASTs into
 * Serializable record evaluators. Projection and aggregate argument expressions compile
 * here — the EQL-side {@code ERR_EQL_UNSUPPORTED_EVAL_EXPR} is never surfaced; anything
 * outside the subset fails fast with the stream-side {@code ERR_STREAM_INVALID_ARG}
 * instead (no silent approximation).
 *
 * <p>Scalar semantics baseline (adjudicated in plan 14): null operand propagates null;
 * numeric arithmetic promotes to long (integral) / double (any floating operand);
 * division is always double division (D7 has no DECIMAL type — integer division is out
 * of scope); string comparison uses the natural Comparable order; {@code !=} parses as
 * NE; AND/OR/NOT use three-valued logic with short-circuit.
 */
public final class StreamSqlExprCompiler {

    private StreamSqlExprCompiler() {
    }

    /**
     * Compiles a scalar expression AST into a record evaluator.
     *
     * @param expr the parsed expression; a null AST fails fast (blank input from the
     *             parser must not silently evaluate to null)
     * @return the Serializable evaluator
     */
    public static StreamRecordEvaluator compileScalar(SqlExpr expr) {
        if (expr == null) {
            throw RecordColumnAccess.unsupported(null);
        }
        switch (expr.getASTKind()) {
            case SqlColumnName:
                return compileColumn((SqlColumnName) expr);
            case SqlNumberLiteral:
                return compileNumber((SqlNumberLiteral) expr);
            case SqlStringLiteral:
                String s = ((SqlStringLiteral) expr).getValue();
                return r -> s;
            case SqlBooleanLiteral:
                boolean b = ((SqlBooleanLiteral) expr).getValue();
                return r -> b;
            case SqlNullLiteral:
                return r -> null;
            case SqlBinaryExpr:
                return compileBinary((SqlBinaryExpr) expr);
            case SqlAndExpr:
                return compileAnd((SqlAndExpr) expr);
            case SqlOrExpr:
                return compileOr((SqlOrExpr) expr);
            case SqlNotExpr:
                return compileNot((SqlNotExpr) expr);
            case SqlUnaryExpr:
                return compileUnary((SqlUnaryExpr) expr);
            case SqlIsNullExpr:
                return compileIsNull((SqlIsNullExpr) expr);
            case SqlBetweenExpr:
                return compileBetween((SqlBetweenExpr) expr);
            case SqlInValuesExpr:
                return compileIn((SqlInValuesExpr) expr);
            default:
                throw RecordColumnAccess.unsupported(expr);
        }
    }

    /**
     * Convenience overload parsing the expression text first.
     *
     * @see #compileScalar(SqlExpr)
     */
    public static StreamRecordEvaluator compileScalar(String exprText) {
        SqlExpr expr = EqlExprSupport.parseExpr(exprText);
        if (expr == null) {
            throw RecordColumnAccess.unsupported(null);
        }
        return compileScalar(expr);
    }

    /**
     * Resolves the expression's static type: columns via the caller-supplied D7
     * mapping (column key = last name segment, same rule as {@link #compileScalar}),
     * literals by their parsed value class, aggregates by function rules
     * (count→LONG, avg→DOUBLE, sum promotes, min/max pass through).
     *
     * @param expr        the expression AST
     * @param columnTypes column key → BasicTypeInfo bridge (from the schema registry)
     * @return the resolved type, or {@link UnknownTypeInformation} when not inferable
     */
    public static TypeInformation<?> resolveType(SqlExpr expr, Function<String, BasicTypeInfo<?>> columnTypes) {
        if (expr == null) {
            return UnknownTypeInformation.INSTANCE;
        }
        switch (expr.getASTKind()) {
            case SqlColumnName: {
                BasicTypeInfo<?> t = columnTypes.apply(columnKey(expr));
                return t == null ? UnknownTypeInformation.INSTANCE : t;
            }
            case SqlNumberLiteral:
                return isIntegralLiteral((SqlNumberLiteral) expr)
                        ? BasicTypeInfo.LONG : BasicTypeInfo.DOUBLE;
            case SqlStringLiteral:
                return BasicTypeInfo.STRING;
            case SqlBooleanLiteral:
                return BasicTypeInfo.BOOLEAN;
            case SqlNullLiteral:
                return UnknownTypeInformation.INSTANCE;
            case SqlBinaryExpr: {
                SqlOperator op = ((SqlBinaryExpr) expr).getOperator();
                if (op == SqlOperator.EQ || op == SqlOperator.NE || op == SqlOperator.LT
                        || op == SqlOperator.LE || op == SqlOperator.GT || op == SqlOperator.GE) {
                    return BasicTypeInfo.BOOLEAN;
                }
                TypeInformation<?> l = resolveType(((SqlBinaryExpr) expr).getLeft(), columnTypes);
                TypeInformation<?> r = resolveType(((SqlBinaryExpr) expr).getRight(), columnTypes);
                return promote(l, r);
            }
            case SqlAndExpr:
            case SqlOrExpr:
            case SqlNotExpr:
            case SqlIsNullExpr:
            case SqlBetweenExpr:
            case SqlInValuesExpr:
                return BasicTypeInfo.BOOLEAN;
            case SqlUnaryExpr:
                return resolveType(((SqlUnaryExpr) expr).getExpr(), columnTypes);
            case SqlAggregateFunction: {
                io.nop.orm.eql.ast.SqlAggregateFunction agg = (io.nop.orm.eql.ast.SqlAggregateFunction) expr;
                if (agg.getArgs() == null || agg.getArgs().isEmpty()) {
                    return aggregateResultType(agg.getName(), null);
                }
                return aggregateResultType(agg.getName(),
                        resolveType(agg.getArgs().get(0), columnTypes));
            }
            default:
                return UnknownTypeInformation.INSTANCE;
        }
    }

    /**
     * Aggregate result type rules exposed for the {@code StreamSqlAggregations}
     * catalog: count→LONG, avg→DOUBLE, sum promotes integral to LONG (floating stays),
     * min/max pass the argument type through.
     */
    public static TypeInformation<?> aggregateResultType(String fnId, TypeInformation<?> argType) {
        switch (fnId) {
            case "count":
                return BasicTypeInfo.LONG;
            case "avg":
                return BasicTypeInfo.DOUBLE;
            case "sum":
                return promote(argType, argType);
            default:
                return argType == null ? UnknownTypeInformation.INSTANCE : argType;
        }
    }

    private static TypeInformation<?> promote(TypeInformation<?> l, TypeInformation<?> r) {
        if (isFloating(l) || isFloating(r)) {
            return BasicTypeInfo.DOUBLE;
        }
        if (isIntegral(l) && isIntegral(r)) {
            return BasicTypeInfo.LONG;
        }
        return UnknownTypeInformation.INSTANCE;
    }

    private static boolean isFloating(TypeInformation<?> t) {
        return t == BasicTypeInfo.DOUBLE || t == BasicTypeInfo.FLOAT;
    }

    private static boolean isIntegral(TypeInformation<?> t) {
        return t == BasicTypeInfo.LONG || t == BasicTypeInfo.INT || t == BasicTypeInfo.SHORT
                || t == BasicTypeInfo.BYTE;
    }

    static String columnKey(SqlExpr expr) {
        if (expr instanceof SqlColumnName) {
            return RecordColumnAccess.lastSegment(((SqlColumnName) expr).getName());
        }
        throw RecordColumnAccess.unsupported(expr);
    }

    private static boolean isIntegralLiteral(SqlNumberLiteral lit) {
        String v = lit.getValue();
        return v.indexOf('.') < 0 && v.indexOf('e') < 0 && v.indexOf('E') < 0;
    }

    // ----------------------------------------------------------------
    // scalar compilation
    // ----------------------------------------------------------------

    private static StreamRecordEvaluator compileColumn(SqlColumnName expr) {
        RecordColumnAccess access = new RecordColumnAccess(expr.getName());
        return access::get;
    }

    private static StreamRecordEvaluator compileNumber(SqlNumberLiteral expr) {
        String text = expr.getValue();
        if (isIntegralLiteral(expr)) {
            long v = RecordColumnAccess.toLong(text, "integer literal '" + text + "'");
            return r -> v;
        }
        double v = RecordColumnAccess.toDouble(text, "number literal '" + text + "'");
        return r -> v;
    }

    private static StreamRecordEvaluator compileBinary(SqlBinaryExpr expr) {
        SqlOperator op = expr.getOperator();
        StreamRecordEvaluator left = compileScalar(expr.getLeft());
        StreamRecordEvaluator right = compileScalar(expr.getRight());
        switch (op) {
            case ADD:
                return r -> arith(left.eval(r), right.eval(r), op, false);
            case MINUS:
                return r -> arith(left.eval(r), right.eval(r), op, false);
            case MULTIPLY:
                return r -> arith(left.eval(r), right.eval(r), op, false);
            case DIVIDE:
                return r -> arith(left.eval(r), right.eval(r), op, true);
            case MOD:
                return r -> arith(left.eval(r), right.eval(r), op, false);
            case EQ:
                return r -> compareOp(left.eval(r), right.eval(r), op);
            case NE:
                return r -> compareOp(left.eval(r), right.eval(r), op);
            case LT:
                return r -> compareOp(left.eval(r), right.eval(r), op);
            case LE:
                return r -> compareOp(left.eval(r), right.eval(r), op);
            case GT:
                return r -> compareOp(left.eval(r), right.eval(r), op);
            case GE:
                return r -> compareOp(left.eval(r), right.eval(r), op);
            default:
                throw RecordColumnAccess.unsupported(expr);
        }
    }

    private static StreamRecordEvaluator compileAnd(SqlAndExpr expr) {
        StreamRecordEvaluator left = compileScalar(expr.getLeft());
        StreamRecordEvaluator right = compileScalar(expr.getRight());
        return r -> logicalAnd(left.eval(r), right.eval(r));
    }

    private static StreamRecordEvaluator compileOr(SqlOrExpr expr) {
        StreamRecordEvaluator left = compileScalar(expr.getLeft());
        StreamRecordEvaluator right = compileScalar(expr.getRight());
        return r -> logicalOr(left.eval(r), right.eval(r));
    }

    private static StreamRecordEvaluator compileNot(SqlNotExpr expr) {
        StreamRecordEvaluator inner = compileScalar(expr.getExpr());
        return r -> not(inner.eval(r));
    }

    private static StreamRecordEvaluator compileUnary(SqlUnaryExpr expr) {
        StreamRecordEvaluator inner = compileScalar(expr.getExpr());
        if (expr.getOperator() == SqlOperator.MINUS) {
            return r -> negate(inner.eval(r));
        }
        throw RecordColumnAccess.unsupported(expr);
    }

    private static StreamRecordEvaluator compileIsNull(SqlIsNullExpr expr) {
        StreamRecordEvaluator inner = compileScalar(expr.getExpr());
        boolean not = expr.getNot();
        return r -> {
            boolean isNull = inner.eval(r) == null;
            return not != isNull;
        };
    }

    private static StreamRecordEvaluator compileBetween(SqlBetweenExpr expr) {
        StreamRecordEvaluator test = compileScalar(expr.getTest());
        StreamRecordEvaluator begin = compileScalar(expr.getBegin());
        StreamRecordEvaluator end = compileScalar(expr.getEnd());
        boolean not = expr.getNot();
        return r -> {
            Object geBegin = compareOp(test.eval(r), begin.eval(r), SqlOperator.GE);
            Object leEnd = compareOp(test.eval(r), end.eval(r), SqlOperator.LE);
            Boolean inRange = logicalAnd(geBegin, leEnd);
            return inRange == null ? null : (not != inRange);
        };
    }

    private static StreamRecordEvaluator compileIn(SqlInValuesExpr expr) {
        StreamRecordEvaluator test = compileScalar(expr.getExpr());
        java.util.List<StreamRecordEvaluator> values = new java.util.ArrayList<>();
        if (expr.getValues() != null) {
            for (SqlExpr v : expr.getValues()) {
                values.add(compileScalar(v));
            }
        }
        boolean not = expr.getNot();
        return r -> {
            Object probe = test.eval(r);
            if (probe == null) {
                return null;
            }
            boolean sawNull = false;
            for (StreamRecordEvaluator v : values) {
                Object candidate = v.eval(r);
                if (candidate == null) {
                    sawNull = true;
                } else if (Boolean.TRUE.equals(compareOp(probe, candidate, SqlOperator.EQ))) {
                    return !not;
                }
            }
            if (sawNull) {
                return null;
            }
            return not;
        };
    }

    // ----------------------------------------------------------------
    // value semantics
    // ----------------------------------------------------------------

    private static Object arith(Object l, Object rr, SqlOperator op, boolean forceDouble) {
        if (l == null || rr == null) {
            return null;
        }
        if (forceDouble) {
            double a = RecordColumnAccess.toDouble(l, "dividend");
            double b = RecordColumnAccess.toDouble(rr, "divisor");
            return a / b;
        }
        if (RecordColumnAccess.isFloating(l) || RecordColumnAccess.isFloating(rr)) {
            double a = RecordColumnAccess.toDouble(l, "operand");
            double b = RecordColumnAccess.toDouble(rr, "operand");
            switch (op) {
                case ADD:
                    return a + b;
                case MINUS:
                    return a - b;
                case MULTIPLY:
                    return a * b;
                case MOD:
                    return a % b;
                default:
                    throw RecordColumnAccess.invalidArg("unsupported arithmetic operator " + op);
            }
        }
        long a = RecordColumnAccess.toLong(l, "operand");
        long b = RecordColumnAccess.toLong(rr, "operand");
        switch (op) {
            case ADD:
                return a + b;
            case MINUS:
                return a - b;
            case MULTIPLY:
                return a * b;
            case MOD:
                return a % b;
            default:
                throw RecordColumnAccess.invalidArg("unsupported arithmetic operator " + op);
        }
    }

    private static Object compareOp(Object l, Object rr, SqlOperator op) {
        if (l == null || rr == null) {
            return null;
        }
        int cmp = compareValues(l, rr, op);
        switch (op) {
            case EQ:
                return cmp == 0;
            case NE:
                return cmp != 0;
            case LT:
                return cmp < 0;
            case LE:
                return cmp <= 0;
            case GT:
                return cmp > 0;
            case GE:
                return cmp >= 0;
            default:
                throw RecordColumnAccess.invalidArg("unsupported comparison operator " + op);
        }
    }

    private static int compareValues(Object l, Object rr, SqlOperator op) {
        if (l instanceof Number && rr instanceof Number) {
            if (RecordColumnAccess.isFloating(l) || RecordColumnAccess.isFloating(rr)) {
                return Double.compare(RecordColumnAccess.toDouble(l, "comparison operand"),
                        RecordColumnAccess.toDouble(rr, "comparison operand"));
            }
            return Long.compare(RecordColumnAccess.toLong(l, "comparison operand"),
                    RecordColumnAccess.toLong(rr, "comparison operand"));
        }
        if (l instanceof Boolean && rr instanceof Boolean) {
            return Boolean.compare((Boolean) l, (Boolean) rr);
        }
        if (l instanceof Comparable && rr instanceof Comparable) {
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                int cmp = ((Comparable) l).compareTo(rr);
                return cmp;
            } catch (ClassCastException e) {
                throw RecordColumnAccess.invalidArg("cannot compare " + typeName(l) + " with "
                        + typeName(rr) + " using " + op);
            }
        }
        if (l.equals(rr)) {
            return 0;
        }
        throw RecordColumnAccess.invalidArg("cannot compare " + typeName(l) + " with "
                + typeName(rr) + " using " + op);
    }

    private static Boolean logicalAnd(Object l, Object rr) {
        if (Boolean.FALSE.equals(l) || Boolean.FALSE.equals(rr)) {
            return Boolean.FALSE;
        }
        if (l == null || rr == null) {
            return null;
        }
        return requireBoolean(l) && requireBoolean(rr);
    }

    private static Boolean logicalOr(Object l, Object rr) {
        if (Boolean.TRUE.equals(l) || Boolean.TRUE.equals(rr)) {
            return Boolean.TRUE;
        }
        if (l == null || rr == null) {
            return null;
        }
        return requireBoolean(l) || requireBoolean(rr);
    }

    private static Boolean not(Object v) {
        return v == null ? null : !requireBoolean(v);
    }

    private static Object negate(Object v) {
        if (v == null) {
            return null;
        }
        if (RecordColumnAccess.isFloating(v)) {
            return -RecordColumnAccess.toDouble(v, "negation operand");
        }
        return -RecordColumnAccess.toLong(v, "negation operand");
    }

    private static boolean requireBoolean(Object v) {
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        throw RecordColumnAccess.invalidArg("logical operator requires a boolean operand, got "
                + typeName(v));
    }

    private static String typeName(Object v) {
        return v == null ? "null" : v.getClass().getSimpleName();
    }
}
