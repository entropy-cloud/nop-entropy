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
import io.nop.xlang.ast._gen._ForInStatement;

public class ForInStatement extends _ForInStatement {
    public static ForInStatement valueOf(SourceLocation loc, Expression left, Expression right, Expression body) {
        Guard.notNull(left, "left is null");
        Guard.notNull(right, "right is null");
        Guard.notNull(body, "body is null");

        ForInStatement node = new ForInStatement();
        node.setLocation(loc);
        node.setLeft(left);
        node.setRight(right);
        node.setBody(body);
        return node;
    }

}