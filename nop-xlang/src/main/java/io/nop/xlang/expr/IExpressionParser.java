package io.nop.xlang.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast.Expression;

public interface IExpressionParser {
    Expression parseTemplateExpr(SourceLocation loc, String source, boolean singleExpr, ExprPhase phase);

    Expression parseExpr(SourceLocation loc, String source);
}
