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
import io.nop.xlang.ast._gen._MemberExpression;

public class MemberExpression extends _MemberExpression {
    public static MemberExpression valueOf(SourceLocation loc, Expression object, Expression property, boolean computed,
                                           boolean optional) {
        Guard.notNull(object, "object is null");
        Guard.notNull(property, "property is null");

        MemberExpression node = new MemberExpression();
        node.setLocation(loc);
        node.setObject(object);
        node.setProperty(property);
        node.setComputed(computed);
        node.setOptional(optional);
        return node;
    }

    public static MemberExpression valueOf(SourceLocation loc, Expression object, Expression property,
                                           boolean computed) {
        return valueOf(loc, object, property, computed, true);
    }
}