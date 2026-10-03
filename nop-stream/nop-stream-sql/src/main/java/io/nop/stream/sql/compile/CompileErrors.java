/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.compile;

import io.nop.orm.eql.ast.SqlExpr;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.sql.eval.RecordColumnAccess;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * WI17 compile-phase failure helpers. Every unsupported construct fails fast — the
 * fail-fast matrix (plan 25 M4) maps to exactly two codes:
 * <ul>
 *   <li>{@code nop.err.stream.invalid-arg} — the default-reject for every construct
 *       outside the §4b supported surface (HAVING, CTE, DISTINCT, INTERSECT/EXCEPT,
 *       parenthesized selects, LATERAL, global aggregation, non-equi join, ...)</li>
 *   <li>{@code nop.err.eql.dialect-not-support-feature} — the single pinned code for
 *       ORDER BY / LIMIT (4a#1 primary choice, existing consumer EqlTransformVisitor)</li>
 * </ul>
 * No new codes are introduced (WI16 §4d zero-new-codes) and nothing is silently
 * dropped: an AST walk has no default-pass branch.
 */
public final class CompileErrors {

    private CompileErrors() {
    }

    static StreamException invalidArg(String detail) {
        return (StreamException) new StreamException(ERR_STREAM_INVALID_ARG)
                .param(ARG_ARG_NAME, "sql")
                .param(ARG_DETAIL, detail);
    }

    static StreamException unsupported(SqlExpr expr) {
        return RecordColumnAccess.unsupported(expr);
    }

    static StreamException dialectNotSupport(String feature) {
        return (StreamException) new StreamException(
                io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE)
                .param(io.nop.orm.eql.OrmEqlErrors.ARG_DIALECT, "stream-sql")
                .param(io.nop.orm.eql.OrmEqlErrors.ARG_FEATURE, feature);
    }
}
