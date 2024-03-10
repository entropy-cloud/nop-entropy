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
import io.nop.xlang.ast._gen._UpdateExpression;

public class UpdateExpression extends _UpdateExpression {
    public static UpdateExpression valueOf(SourceLocation loc, boolean prefix, XLangOperator op, Expression argument) {
        Guard.notNull(op, "op is null");
        Guard.notNull(argument, "argument is null");
        UpdateExpression node = new UpdateExpression();
        node.setArgument(argument);
        node.setOperator(op);
        node.setPrefix(prefix);
        return node;
    }
}