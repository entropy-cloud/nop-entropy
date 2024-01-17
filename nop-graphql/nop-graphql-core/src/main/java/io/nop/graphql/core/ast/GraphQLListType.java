/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLListType;
import io.nop.graphql.core.utils.GraphQLTypeHelper;

public class GraphQLListType extends _GraphQLListType {
    public boolean isListType() {
        return true;
    }

    public String toString() {
        return "[" + getType() + "]";
    }

    public String getNamedTypeName() {
        return getType().getNamedTypeName();
    }

    @Override
    public boolean needFieldSelection() {
        return getType().needFieldSelection();
    }

    public GraphQLType mergeType(GraphQLType type) {
        if (this == type)
            return this;

        if (type instanceof GraphQLNonNullType) {
            return type.mergeType(this);
        }
        if (!(type instanceof GraphQLListType))
            return null;

        GraphQLType elementType = getType().mergeType(((GraphQLListType) type).getType());
        if (elementType == null)
            return null;

        if (elementType == getType())
            return this;

        return GraphQLTypeHelper.listType(elementType.deepClone());
    }

    @Override
    public GraphQLType getItemType() {
        return getType();
    }

    @Override
    public GraphQLType getNullableType() {
        GraphQLType elementType = getType().getNullableType();
        if (elementType == getType())
            return this;
        return GraphQLTypeHelper.listType(elementType);
    }
}
