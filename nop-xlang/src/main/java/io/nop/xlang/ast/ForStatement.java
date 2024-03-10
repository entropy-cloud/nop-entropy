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
import io.nop.xlang.ast._gen._ForStatement;

public class ForStatement extends _ForStatement {
    public static ForStatement valueOf(SourceLocation loc, Expression init, Expression test, Expression update,
                                       Expression body) {
        Guard.notNull(body, "body is null");

        ForStatement node = new ForStatement();
        node.setLocation(loc);
        node.setInit(init);
        node.setTest(test);
        node.setUpdate(test);
        node.setBody(body);
        return node;
    }
}