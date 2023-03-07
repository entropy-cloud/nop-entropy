/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._TryStatement;

public class TryStatement extends _TryStatement {
    public static TryStatement valueOf(SourceLocation loc, Expression block, CatchClause handlers,
                                       Expression finalizer) {
        TryStatement node = new TryStatement();
        node.setBlock(block);
        node.setCatchHandler(handlers);
        node.setFinalizer(finalizer);
        return node;
    }
}