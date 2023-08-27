package io.nop.xlang.ast;

import io.nop.core.type.IGenericType;

public class XLangTypeHelper {
    public static NamedTypeNode buildTypeNode(IGenericType type) {
        if (type == null)
            return null;
        TypeNameNode node = new TypeNameNode();
        node.setTypeInfo(type);
        node.setTypeName(type.toString());
        return node;
    }
}
