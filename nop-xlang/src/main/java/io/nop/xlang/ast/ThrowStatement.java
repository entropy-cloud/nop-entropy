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
import io.nop.xlang.ast._gen._ThrowStatement;

public class ThrowStatement extends _ThrowStatement {
    public static ThrowStatement valueOf(SourceLocation loc, Expression argument) {
        Guard.notNull(argument, "argument is null");
        ThrowStatement node = new ThrowStatement();
        node.setLocation(loc);
        node.setArgument(argument);
        return node;
    }
}