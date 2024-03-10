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
import io.nop.xlang.ast._gen._CollectOutputExpression;

public class CollectOutputExpression extends _CollectOutputExpression {
    public static CollectOutputExpression valueOf(SourceLocation loc, XLangOutputMode outputMode, boolean singleNode,
                                                  Expression body) {
        Guard.notNull(outputMode, "outputMode is null");
        Guard.notNull(body, "body is null");

        CollectOutputExpression node = new CollectOutputExpression();
        node.setLocation(loc);
        node.setOutputMode(outputMode);
        node.setSingleNode(singleNode);
        node.setBody(body);
        return node;
    }
}