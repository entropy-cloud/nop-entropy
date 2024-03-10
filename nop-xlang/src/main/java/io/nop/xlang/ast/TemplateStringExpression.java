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
import io.nop.xlang.ast._gen._TemplateStringExpression;

public class TemplateStringExpression extends _TemplateStringExpression {
    public static TemplateStringExpression valueOf(SourceLocation loc, Identifier id, TemplateStringLiteral value) {
        Guard.notNull(id, "id is null");
        Guard.notNull(value, "value is null");

        TemplateStringExpression node = new TemplateStringExpression();
        node.setLocation(loc);
        node.setId(id);
        node.setValue(value);
        return node;
    }
}