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
import io.nop.xlang.ast._gen._GenNodeExpression;

import java.util.List;

public class GenNodeExpression extends _GenNodeExpression {
    public static GenNodeExpression genTextNode(Expression value) {
        GenNodeExpression node = new GenNodeExpression();
        node.setLocation(value.getLocation());
        node.setBody(value);
        node.setTextNode(true);
        return node;
    }

    public static GenNodeExpression valueOf(SourceLocation loc, Expression tagName, List<GenNodeAttrExpression> attrs,
                                            Expression extAttrs, Expression body) {
        Guard.notEmpty(tagName, "tagName");
        GenNodeExpression node = new GenNodeExpression();
        node.setLocation(loc);
        node.setTagName(tagName);
        node.setAttrs(attrs);
        node.setExtAttrs(extAttrs);
        node.setBody(body);
        return node;
    }
}