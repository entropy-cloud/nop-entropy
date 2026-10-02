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
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.spi.IAggregatorFunctionResolver;

import java.util.function.Function;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;

/**
 * WI8c: the nop-stream-sql provider of the flow-side aggregate-resolution SPI. The
 * full resolution semantics (adjudicated in plan 15), each failure fail-fast with
 * {@code nop.err.stream.invalid-arg}:
 * <ol>
 *   <li>unknown fnId — not in the WI9 builtin catalog (the A4 bean-first ordering
 *       runs before this SPI in the flow builder)</li>
 *   <li>expression compile failure — outside the scalar subset or malformed</li>
 *   <li>argument count violation — count allows 0..1 args, others exactly 1</li>
 *   <li>argument type violation — sum/avg require numeric column types, min/max
 *       require Comparable types, count any; unknown column types skip the check
 *       (the declaration never promised a schema)</li>
 * </ol>
 * Accumulation semantics are never re-implemented here — {@link StreamSqlAggregation}
 * provides them.
 */
public class StreamAggregatorFunctionResolver implements IAggregatorFunctionResolver {

    private static final long serialVersionUID = 1L;

    @Override
    public AggregateFunction<Object, Object, Object> resolve(String fnId, String expr,
                                                             Function<String, BasicTypeInfo<?>> columnTypes) {
        // WI17 (plan 25 r2 B1): the synthesized composite entry (fnId=sql-row-agg) is
        // resolved here into the composite AggregateFunction — group-key evaluators plus
        // N builtin sub-aggregators. This extends (never rewrites) the WI8c dispatch:
        // regular fnIds keep flowing through the WI9 catalog below.
        if (SqlRowAggregateSpec.FN_ID.equals(fnId)) {
            if (expr == null || expr.isEmpty()) {
                throw invalidArg("composite aggregator entry '" + SqlRowAggregateSpec.FN_ID
                        + "' requires a structured spec in expr");
            }
            return StreamSqlAggregations.buildComposite(SqlRowAggregateSpec.fromJson(expr));
        }
        StreamSqlAggregation spec = StreamSqlAggregations.resolve(fnId);
        if (spec == null) {
            throw invalidArg("unknown aggregate fnId '" + fnId
                    + "': builtin ids are " + StreamSqlAggregations.builtinIds());
        }
        StreamRecordEvaluator evaluator = null;
        if (expr != null && !expr.isEmpty()) {
            io.nop.orm.eql.ast.SqlExpr ast = io.nop.stream.sql.parse.EqlExprSupport.parseExpr(expr);
            if (ast == null) {
                throw invalidArg("aggregate '" + fnId + "': expression '" + expr + "' is blank");
            }
            try {
                evaluator = StreamSqlExprCompiler.compileScalar(ast);
            } catch (RuntimeException e) {
                throw (StreamException) new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG, e)
                        .param(ARG_ARG_NAME, "expr")
                        .param(ARG_DETAIL, "aggregate '" + fnId + "': failed to compile expression '" + expr + "'");
            }
        }
        if (evaluator == null && !spec.allowsNoArg()) {
            throw invalidArg("aggregate '" + fnId + "' requires exactly one argument expression, found none");
        }
        validateArgType(fnId, expr, evaluator, columnTypes);
        return spec.create(evaluator);
    }

    /**
     * Argument-type rule: sum/avg numeric, min/max Comparable, count any. Unknown
     * column types (null from the bridge) skip validation — the declaration did not
     * promise a schema, and guessing would reject legal dynamic payloads.
     */
    private void validateArgType(String fnId, String expr, StreamRecordEvaluator evaluator,
                                 Function<String, BasicTypeInfo<?>> columnTypes) {
        if (evaluator == null || fnId.equals("count") || columnTypes == null) {
            return;
        }
        io.nop.orm.eql.ast.SqlExpr ast = io.nop.stream.sql.parse.EqlExprSupport.parseExpr(expr);
        if (ast == null) {
            return;
        }
        TypeInformation<?> argType = StreamSqlExprCompiler.resolveType(ast, columnTypes);
        if (argType == null || argType instanceof io.nop.stream.core.common.typeinfo.UnknownTypeInformation) {
            // unknown column type (no schema declared / column not in schema) — the
            // declaration never promised a type, so skip validation rather than
            // rejecting legal dynamic payloads
            return;
        }
        boolean numeric = argType == BasicTypeInfo.INT || argType == BasicTypeInfo.LONG
                || argType == BasicTypeInfo.DOUBLE || argType == BasicTypeInfo.FLOAT
                || argType == BasicTypeInfo.SHORT || argType == BasicTypeInfo.BYTE;
        boolean comparable = numeric || argType == BasicTypeInfo.STRING || argType == BasicTypeInfo.BOOLEAN;
        if (fnId.equals("sum") || fnId.equals("avg")) {
            if (!numeric) {
                throw invalidArg("aggregate '" + fnId + "' requires a numeric argument, but '" + expr
                        + "' resolves to " + argType);
            }
        } else if (fnId.equals("min") || fnId.equals("max")) {
            if (!comparable) {
                throw invalidArg("aggregate '" + fnId + "' requires a comparable argument, but '" + expr
                        + "' resolves to " + argType);
            }
        }
    }

    private static StreamException invalidArg(String detail) {
        // param() is declared on NopException; the chain returns this
        return (StreamException) new StreamException(NopStreamErrors.ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "aggregator")
                .param(ARG_DETAIL, detail);
    }
}
