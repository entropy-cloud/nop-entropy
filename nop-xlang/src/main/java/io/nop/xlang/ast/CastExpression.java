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
import io.nop.xlang.ast._gen._CastExpression;

public class CastExpression extends _CastExpression {
    public static CastExpression valueOf(SourceLocation loc, Expression value, NamedTypeNode asType) {
        Guard.notNull(value, "value is null");
        Guard.notNull(asType, "asType is null");

        CastExpression node = new CastExpression();
        node.setLocation(loc);
        node.setValue(value);
        node.setAsType(asType);
        return node;
    }
}