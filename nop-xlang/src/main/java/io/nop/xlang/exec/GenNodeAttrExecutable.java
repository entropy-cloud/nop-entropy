/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.api.core.util.Guard;
import io.nop.core.lang.eval.IExecutableExpression;

public class GenNodeAttrExecutable {
    private final String name;
    private final IExecutableExpression valueExpr;

    public GenNodeAttrExecutable(String name, IExecutableExpression valueExpr) {
        this.name = Guard.notEmpty(name, "name");
        this.valueExpr = Guard.notNull(valueExpr, "valueExpr");
    }

    public String getName() {
        return name;
    }

    public IExecutableExpression getValueExpr() {
        return valueExpr;
    }
}
