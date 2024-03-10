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
import io.nop.xlang.ast._gen._BlockStatement;

import java.util.List;

public class BlockStatement extends _BlockStatement {
    public static BlockStatement valueOf(SourceLocation loc, List<Expression> body) {
        Guard.notNull(body, "body is null");

        BlockStatement node = new BlockStatement();
        node.setLocation(loc);
        node.setBody(body);
        return node;
    }
}