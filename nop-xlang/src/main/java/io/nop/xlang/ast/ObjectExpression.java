/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._ObjectExpression;

import java.util.Collections;
import java.util.List;

public class ObjectExpression extends _ObjectExpression {
    public static ObjectExpression valueOf(SourceLocation loc, List<XLangASTNode> properties) {
        ObjectExpression node = new ObjectExpression();
        node.setLocation(loc);
        node.setProperties(properties == null ? Collections.emptyList() : properties);
        return node;
    }

    /**
     * 是否是属性名固定的Map，例如 {a:1,b:f()}。
     * 对于{[x]:1,...other}这种情况则返回false
     */
    public boolean isPropMap() {
        for (XLangASTNode prop : this.getProperties()) {
            if (prop.getASTKind() != XLangASTKind.PropertyAssignment) {
                return false;
            }

            PropertyAssignment assign = (PropertyAssignment) prop;
            if (assign.getKey().getASTKind() != XLangASTKind.Literal)
                return false;
        }
        return true;
    }
}