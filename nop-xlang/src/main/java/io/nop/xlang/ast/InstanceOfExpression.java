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
import io.nop.xlang.ast._gen._InstanceOfExpression;

public class InstanceOfExpression extends _InstanceOfExpression {
    public static InstanceOfExpression valueOf(SourceLocation loc, Expression value, NamedTypeNode refType) {
        Guard.notNull(value, "value is null");
        Guard.notNull(value, "refType is null");

        InstanceOfExpression node = new InstanceOfExpression();
        node.setLocation(loc);
        node.setValue(value);
        node.setRefType(refType);
        return node;
    }
}