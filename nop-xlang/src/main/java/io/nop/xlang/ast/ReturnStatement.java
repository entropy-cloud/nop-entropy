/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._ReturnStatement;

public class ReturnStatement extends _ReturnStatement {
    public static ReturnStatement valueOf(SourceLocation loc, Expression argument) {
        ReturnStatement node = new ReturnStatement();
        node.setLocation(loc);
        node.setArgument(argument);
        return node;
    }
}