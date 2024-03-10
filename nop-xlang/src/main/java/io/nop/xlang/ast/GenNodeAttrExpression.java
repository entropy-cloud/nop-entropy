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
import io.nop.xlang.ast._gen._GenNodeAttrExpression;

public class GenNodeAttrExpression extends _GenNodeAttrExpression {
    public static GenNodeAttrExpression valueOf(SourceLocation loc, String name, Expression value) {
        Guard.notEmpty(name, "name");
        Guard.notNull(value, "value");

        GenNodeAttrExpression node = new GenNodeAttrExpression();
        node.setLocation(loc);
        node.setName(name);
        node.setValue(value);
        return node;
    }
}