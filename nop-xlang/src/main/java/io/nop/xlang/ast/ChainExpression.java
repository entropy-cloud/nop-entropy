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
import io.nop.xlang.ast._gen._ChainExpression;

public class ChainExpression extends _ChainExpression {
    public static ChainExpression valueOf(SourceLocation loc, Expression value, boolean optional) {
        Guard.notNull(value, "value is null");
        ChainExpression node = new ChainExpression();
        node.setLocation(loc);
        node.setExpr(value);
        node.setOptional(optional);
        return node;
    }
}