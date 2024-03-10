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
import io.nop.xlang.ast._gen._NewExpression;

import java.util.List;

public class NewExpression extends _NewExpression {
    public static NewExpression valueOf(SourceLocation loc, NamedTypeNode type, List<Expression> args) {
        Guard.notNull(type, "type is null");
        NewExpression node = new NewExpression();
        node.setLocation(loc);
        node.setCallee(type);
        node.setArguments(args);
        return node;
    }
}