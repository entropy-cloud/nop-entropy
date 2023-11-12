package io.nop.xlang.api;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangASTHelper;

public class EvalCodeWithAst extends EvalCode {
    private final Expression expr;

    public EvalCodeWithAst(Expression expr, String code, IEvalAction action) {
        super(expr.getLocation(), code, action);
        this.expr = XLangASTHelper.getDetachedExpr(expr);
    }

    public Expression getExpr() {
        return expr;
    }
}
