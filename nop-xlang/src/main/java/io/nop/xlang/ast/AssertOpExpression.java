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
import io.nop.xlang.ast._gen._AssertOpExpression;

public class AssertOpExpression extends _AssertOpExpression {
    public static AssertOpExpression valueOf(SourceLocation loc, FilterOp op, Expression value) {
        AssertOpExpression ret = new AssertOpExpression();
        ret.setLocation(loc);
        ret.setOp(op.name());
        ret.setValue(value);
        return ret;
    }
}