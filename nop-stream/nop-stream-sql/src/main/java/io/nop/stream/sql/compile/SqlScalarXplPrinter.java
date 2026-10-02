/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.orm.eql.ast.SqlAggregateFunction;
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

import java.util.List;
import java.util.function.Function;

/**
 * WI17: prints the WI9 scalar-expression subset from an EQL AST into an XLang
 * (xpl-fn) expression text for the compiled model's inline map/filter bodies.
 *
 * <p><b>Null-semantics pinning</b> — XLang's native operators diverge from the WI9
 * evaluator on nulls (comparison with null yields false, arithmetic yields NaN, NOT
 * yields true, AND with a null and a true side yields null). Every binary/unary form
 * below therefore emits an explicit three-valued guard so the compiled body is
 * semantically EQUAL to the WI9 evaluator — verified by the equivalence pin test
 * (same record matrix through both engines: null propagation, double division,
 * three-valued logic). XLang division is already double division (5/2 → 2.5), matching
 * the WI9 rule.
 *
 * <p>Anything outside the scalar subset (subqueries, CASE, CAST, window expressions,
 * regular function calls, ...) fails fast with the stream-side invalid-arg code — the
 * EQL-side unsupported-eval-expr code is never surfaced (same contract as WI9).
 */
public final class SqlScalarXplPrinter {

    /**
     * Resolves a column reference to its xpl text, e.g. {@code event['amount']} for the
     * single-input form or {@code event.left['id']} / {@code event.right['id']} after a
     * join. Returning null means the reference cannot be resolved (fail fast).
     */
    public interface ColumnResolver {
        String ref(SqlColumnName col);
    }

    /**
     * Receives every aggregate function node encountered (projection scan); its result
     * replaces the node when non-null (the aggregate-over-row reference, e.g.
     * {@code event[1]}).
     */
    public interface AggregateRef {
        String ref(SqlAggregateFunction agg);
    }

    private final ColumnResolver columns;
    private final AggregateRef aggregates;

    public SqlScalarXplPrinter(ColumnResolver columns, AggregateRef aggregates) {
        this.columns = columns;
        this.aggregates = aggregates;
    }

    /**
     * Prints {@code expr} as an XLang expression; fails fast outside the scalar subset.
     */
    public String print(SqlExpr expr) {
        StringBuilder sb = new StringBuilder(64);
        printTo(expr, sb);
        return sb.toString();
    }

    public void printTo(SqlExpr expr, StringBuilder sb) {
        if (expr == null) {
            throw CompileErrors.invalidArg("expression is blank");
        }
        switch (expr.getASTKind()) {
            case SqlColumnName: {
                String ref = columns.ref((SqlColumnName) expr);
                if (ref == null) {
                    throw CompileErrors.invalidArg("unresolvable column reference '"
                            + ((SqlColumnName) expr).getName() + "'");
                }
                sb.append(ref);
                break;
            }
            case SqlNumberLiteral:
                sb.append(((SqlNumberLiteral) expr).getValue());
                break;
            case SqlStringLiteral:
                sb.append(xlangString(((SqlStringLiteral) expr).getValue()));
                break;
            case SqlBooleanLiteral:
                sb.append(((SqlBooleanLiteral) expr).getValue());
                break;
            case SqlNullLiteral:
                sb.append("null");
                break;
            case SqlBinaryExpr:
                printBinary((SqlBinaryExpr) expr, sb);
                break;
            case SqlAndExpr:
                printAnd((SqlAndExpr) expr, sb);
                break;
            case SqlOrExpr:
                printOr((SqlOrExpr) expr, sb);
                break;
            case SqlNotExpr:
                printNot((SqlNotExpr) expr, sb);
                break;
            case SqlUnaryExpr: {
                SqlUnaryExpr unary = (SqlUnaryExpr) expr;
                if (unary.getOperator() != SqlOperator.MINUS) {
                    throw CompileErrors.unsupported(expr);
                }
                sb.append("(((").append(print(unary.getExpr())).append(") == null) ? null : -(")
                        .append(print(unary.getExpr())).append("))");
                break;
            }
            case SqlIsNullExpr: {
                SqlIsNullExpr isNull = (SqlIsNullExpr) expr;
                sb.append("((").append(print(isNull.getExpr())).append(") == null");
                if (isNull.getNot()) {
                    // NOT NULL: total (no third value) — mirrors the WI9 evaluator
                    sb.append(" ? false : true");
                } else {
                    sb.append(" ? true : false");
                }
                sb.append(')');
                break;
            }
            case SqlBetweenExpr:
                printBetween((SqlBetweenExpr) expr, sb);
                break;
            case SqlInValuesExpr:
                printIn((SqlInValuesExpr) expr, sb);
                break;
            case SqlAggregateFunction: {
                SqlAggregateFunction agg = (SqlAggregateFunction) expr;
                String ref = aggregates == null ? null : aggregates.ref(agg);
                if (ref == null) {
                    throw CompileErrors.invalidArg("aggregate '" + agg.getName()
                            + "' is not part of the compiled aggregate set");
                }
                sb.append(ref);
                break;
            }
            default:
                throw CompileErrors.unsupported(expr);
        }
    }

    private void printBinary(SqlBinaryExpr expr, StringBuilder sb) {
        SqlOperator op = expr.getOperator();
        String xlang = xlangOp(op);
        if (xlang == null) {
            throw CompileErrors.unsupported(expr);
        }
        String l = print(expr.getLeft());
        String r = print(expr.getRight());
        boolean comparison = isComparison(op);
        if (comparison) {
            // three-valued comparison: any null operand → null
            sb.append("(((").append(l).append(") == null || (").append(r)
                    .append(") == null) ? null : ((").append(l).append(") ").append(xlang)
                    .append(" (").append(r).append(")))");
        } else {
            // arithmetic: null propagation (XLang would yield NaN for null+1)
            sb.append("(((").append(l).append(") == null || (").append(r)
                    .append(") == null) ? null : ((").append(l).append(") ").append(xlang)
                    .append(" (").append(r).append(")))");
        }
    }

    private void printAnd(SqlAndExpr expr, StringBuilder sb) {
        String l = print(expr.getLeft());
        String r = print(expr.getRight());
        // three-valued AND: FALSE dominates, then null, else conjunction
        sb.append("(((").append(l).append(") == false || (").append(r)
                .append(") == false) ? false : (((").append(l).append(") == null || (")
                .append(r).append(") == null) ? null : ((").append(l).append(") && (")
                .append(r).append("))))");
    }

    private void printOr(SqlOrExpr expr, StringBuilder sb) {
        String l = print(expr.getLeft());
        String r = print(expr.getRight());
        // three-valued OR: TRUE dominates, then null, else disjunction
        sb.append("(((").append(l).append(") == true || (").append(r)
                .append(") == true) ? true : (((").append(l).append(") == null || (")
                .append(r).append(") == null) ? null : ((").append(l).append(") || (")
                .append(r).append("))))");
    }

    private void printNot(SqlNotExpr expr, StringBuilder sb) {
        String x = print(expr.getExpr());
        sb.append("((").append(x).append(") == null ? null : !((").append(x).append(")))");
    }

    private void printBetween(SqlBetweenExpr expr, StringBuilder sb) {
        // x BETWEEN a AND b  ==  (x >= a) AND (x <= b) — composed from the three-valued
        // comparison and AND emitters, so null semantics match the WI9 evaluator.
        StringBuilder ge = new StringBuilder(64);
        printBinaryOp(expr.getTest(), SqlOperator.GE, expr.getBegin(), ge);
        StringBuilder le = new StringBuilder(64);
        printBinaryOp(expr.getTest(), SqlOperator.LE, expr.getEnd(), le);
        String and = "((" + ge + ") == false || (" + le
                + ") == false) ? false : (((" + ge + ") == null || (" + le + ") == null) ? null : ((" + ge + ") && (" + le + ")))";
        if (expr.getNot()) {
            sb.append("((").append(and).append(") == null ? null : !((").append(and).append(")))");
        } else {
            sb.append(and);
        }
    }

    private void printBinaryOp(SqlExpr left, SqlOperator op, SqlExpr right, StringBuilder sb) {
        String xlang = xlangOp(op);
        String l = print(left);
        String r = print(right);
        sb.append("(((").append(l).append(") == null || (").append(r)
                .append(") == null) ? null : ((").append(l).append(") ").append(xlang)
                .append(" (").append(r).append(")))");
    }

    private void printIn(SqlInValuesExpr expr, StringBuilder sb) {
        String probe = print(expr.getExpr());
        List<SqlExpr> values = expr.getValues();
        StringBuilder eqs = new StringBuilder(64);
        StringBuilder nulls = new StringBuilder(64);
        int count = values == null ? 0 : values.size();
        for (int i = 0; i < count; i++) {
            String v = print(values.get(i));
            if (i > 0) {
                eqs.append(" || ");
                nulls.append(" || ");
            }
            eqs.append("((").append(probe).append(") == (").append(v).append("))");
            nulls.append("((").append(v).append(") == null)");
        }
        sb.append("((").append(probe).append(") == null ? null : (");
        if (count == 0) {
            sb.append(expr.getNot()).append(')');
        } else {
            sb.append(eqs).append(" ? ").append(!expr.getNot()).append(" : ((")
                    .append(nulls).append(") ? null : ").append(expr.getNot()).append("))");
        }
        sb.append(')');
    }

    private static boolean isComparison(SqlOperator op) {
        switch (op) {
            case EQ:
            case NE:
            case LT:
            case LE:
            case GT:
            case GE:
                return true;
            default:
                return false;
        }
    }

    private static String xlangOp(SqlOperator op) {
        switch (op) {
            case EQ:
                return "==";
            case NE:
                return "!=";
            case LT:
                return "<";
            case LE:
                return "<=";
            case GT:
                return ">";
            case GE:
                return ">=";
            case ADD:
                return "+";
            case MINUS:
                return "-";
            case MULTIPLY:
                return "*";
            case DIVIDE:
                // XLang division is already double division (5/2 -> 2.5), matching WI9
                return "/";
            case MOD:
                return "%";
            default:
                return null;
        }
    }

    private static String xlangString(String value) {
        // XLang string literals follow Java escaping; emit double-quoted so single
        // quotes inside SQL strings need no special casing beyond the backslash.
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\')
                sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }
}
