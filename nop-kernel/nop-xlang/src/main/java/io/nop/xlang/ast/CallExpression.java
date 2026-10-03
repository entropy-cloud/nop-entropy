/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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

    /**
     * 取第 i 个参数；超出实际参数个数时返回 null 而非抛越界异常，
     * 以支持 between 等可选尾随参数的 3/4 参数调用形式（回归覆盖 wi3#2）
     */
    public Expression getArgument(int i) {
        if (arguments == null || i >= arguments.size())
            return null;
        return arguments.get(i);
    }
}