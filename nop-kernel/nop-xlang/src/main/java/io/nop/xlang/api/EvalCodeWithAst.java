/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangASTHelper;

public class EvalCodeWithAst extends EvalCode {
    private final Expression ast;

    public EvalCodeWithAst(IExecutableExpression action, String code, Expression ast) {
        super(action, code);
        this.ast = XLangASTHelper.getDetachedExpr(ast);
    }

    public Expression getAst() {
        return ast;
    }
}
