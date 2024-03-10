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
import io.nop.xlang.ast._gen._OutputXmlAttrExpression;

public class OutputXmlAttrExpression extends _OutputXmlAttrExpression {
    public static OutputXmlAttrExpression valueOf(SourceLocation loc, String name, Expression expr) {
        Guard.notEmpty(name, "name");
        Guard.notNull(expr, "value");

        OutputXmlAttrExpression ret = new OutputXmlAttrExpression();
        ret.setLocation(loc);
        ret.setName(name);
        ret.setValue(expr);
        return ret;
    }
}