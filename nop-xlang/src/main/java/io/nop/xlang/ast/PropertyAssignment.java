/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._PropertyAssignment;

public class PropertyAssignment extends _PropertyAssignment {

    public static PropertyAssignment valueOf(SourceLocation loc, Literal key, Expression value) {
        PropertyAssignment node = new PropertyAssignment();
        node.setLocation(loc);
        node.setKey(key);
        node.setValue(value);
        return node;
    }

    @Override
    public void normalize() {
        if (!computed) {
            if (key instanceof Identifier) {
                Identifier id = (Identifier) key;
                key = Literal.valueOf(id.getLocation(), id.getName());
            }
        }
        // shorthand情况
        if (value == null && key instanceof Literal) {
            value = Identifier.valueOf(key.getLocation(), ((Literal) key).getStringValue());
        }
        if (value == null)
            value = Literal.nullValue(getLocation());
    }
}