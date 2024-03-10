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
import io.nop.xlang.ast._gen._WhileStatement;

public class WhileStatement extends _WhileStatement {
    public static WhileStatement valueOf(SourceLocation loc, Expression test, Expression body) {
        Guard.notNull(test, "test is null");
        WhileStatement node = new WhileStatement();
        node.setLocation(loc);
        node.setTest(test);
        node.setBody(body);
        return node;
    }
}