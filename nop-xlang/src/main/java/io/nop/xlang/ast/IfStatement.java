/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._IfStatement;

import java.util.List;

import static io.nop.xlang.ast.XLangASTBuilder.initConditional;

public class IfStatement extends _IfStatement implements IConditionalExpression {
    @Override
    public IConditionalExpression createInstance() {
        IfStatement expr = new IfStatement();
        return expr;
    }

    public static IfStatement valueOf(SourceLocation loc, List<Expression> exprs) {
        IfStatement node = new IfStatement();
        node.setLocation(loc);
        node.setTest(exprs.get(0));
        initConditional(node, exprs, 1);
        return node;
    }

    public static IfStatement valueOf(SourceLocation loc, Expression test, Expression consequence,
                                      Expression alternate) {
        Guard.notNull(test, "test is null");
        IfStatement node = new IfStatement();
        node.setLocation(loc);
        node.setTest(test);
        node.setConsequent(consequence);
        node.setAlternate(alternate);
        return node;
    }
}