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
import io.nop.xlang.ast._gen._CallExpression;

import java.util.List;

public class CallExpression extends _CallExpression {

    public static CallExpression valueOf(SourceLocation loc, Expression callee, List<Expression> arguments) {
        Guard.notNull(callee, "callee is null");
        CallExpression node = new CallExpression();
        node.setLocation(loc);
        node.setCallee(callee);
        node.setArguments(arguments);
        return node;
    }

    public Expression getArgument(int i) {
        return arguments == null ? null : arguments.get(i);
    }
}