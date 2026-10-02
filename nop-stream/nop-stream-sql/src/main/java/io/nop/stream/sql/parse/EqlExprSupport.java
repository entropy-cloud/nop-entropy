/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.sql.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.parse.EqlExprASTParser;

/**
 * Thin entry over the shared EQL expression parser ({@link EqlExprASTParser}) for the
 * stream SQL side (WI9). One parse entry keeps a single grammar surface — no local
 * parser copies (D13 (c) rejected exactly that).
 */
public final class EqlExprSupport {

    private EqlExprSupport() {
    }

    /**
     * Parses an EQL/SQL expression text into an AST. Returns {@code null} for blank
     * input (the underlying parser's contract) — callers that require an expression
     * must fail fast on the null return.
     *
     * @param text the expression text, e.g. {@code sum(amount)} or {@code a + 1}
     * @return the parsed expression AST, or null when the text is blank
     */
    public static SqlExpr parseExpr(String text) {
        return new EqlExprASTParser().parseFromText(SourceLocation.fromPath("stream-sql:/expr"), text);
    }
}
