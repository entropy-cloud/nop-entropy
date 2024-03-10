/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.model.query.FilterOp;
import io.nop.xlang.ast._gen._CompareOpExpression;

public class CompareOpExpression extends _CompareOpExpression {
    public static CompareOpExpression valueOf(SourceLocation loc, Expression left, FilterOp filterOp, Expression right) {
        CompareOpExpression ret = new CompareOpExpression();
        ret.setLocation(loc);
        ret.setLeft(left);
        ret.setOp(filterOp.name());
        ret.setRight(right);
        return ret;
    }
}