/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._BetweenOpExpression;

public class BetweenOpExpression extends _BetweenOpExpression {

    public static BetweenOpExpression valueOf(SourceLocation loc, Expression value, String op,
                                              Expression min, Expression max, boolean excludeMin, boolean excludeMax) {
        BetweenOpExpression node = new BetweenOpExpression();
        node.setLocation(loc);
        node.setValue(value);
        node.setOp(op);
        node.setMin(min);
        node.setMax(max);
        node.setExcludeMin(excludeMin);
        node.setExcludeMax(excludeMax);
        return node;
    }
}