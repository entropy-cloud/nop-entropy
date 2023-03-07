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
import io.nop.xlang.ast._gen._TypeOfExpression;

public class TypeOfExpression extends _TypeOfExpression {
    public static TypeOfExpression valueOf(SourceLocation loc, Expression value) {
        Guard.notNull(value, "typeOf value is null");
        TypeOfExpression node = new TypeOfExpression();
        node.setLocation(loc);
        node.setArgument(value);
        return node;
    }
}