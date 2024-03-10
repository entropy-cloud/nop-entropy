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
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.xlang.ast._gen._TypeNameNode;

import java.lang.reflect.Type;

public class TypeNameNode extends _TypeNameNode {

    public static TypeNameNode valueOf(SourceLocation loc, String typeName) {
        Guard.notEmpty(typeName, "typeName is empty");
        TypeNameNode node = new TypeNameNode();
        node.setLocation(loc);
        node.setTypeName(typeName);
        return node;
    }

    public static TypeNameNode fromTypeInfo(SourceLocation loc, IGenericType type) {
        TypeNameNode node = valueOf(loc, type.getTypeName());
        node.setTypeInfo(type);
        return node;
    }

    public static TypeNameNode fromType(SourceLocation loc, Type type) {
        return fromTypeInfo(loc, ReflectionManager.instance().buildGenericType(type));
    }
}