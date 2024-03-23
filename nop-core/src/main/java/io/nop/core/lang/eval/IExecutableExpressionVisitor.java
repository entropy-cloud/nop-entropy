package io.nop.core.lang.eval;

import io.nop.api.core.util.SourceLocation;

public interface IExecutableExpressionVisitor {
    boolean onVisitExpr(IExecutableExpression expr);

    default void onVisitSimpleExpr(IExecutableExpression expr) {
        if (onVisitExpr(expr)) {
            onEndVisitExpr(expr);
        }
    }

    default void onEndVisitExpr(IExecutableExpression expr) {

    }

    void visitIdentifier(SourceLocation loc, String id);
}
