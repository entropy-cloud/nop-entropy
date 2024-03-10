/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._SuperExpression;

public class SuperExpression extends _SuperExpression {
    public static SuperExpression valueOf(SourceLocation loc) {
        SuperExpression node = new SuperExpression();
        node.setLocation(loc);
        return node;
    }
}