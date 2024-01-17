/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLNonNullType;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.utils.GraphQLTypeHelper;

public class GraphQLNonNullType extends _GraphQLNonNullType {
    public boolean isNonNullType() {
        return true;
    }

    public String toString() {
        return getType() + "!";
    }

    public boolean isScalarType() {
        return getType().isScalarType();
    }

    public boolean isListType() {
        return getType().isListType();
    }

    public boolean isEnumType() {
        return getType().isEnumType();
    }

    @Override
    public GraphQLScalarType getScalarType() {
        return getType().getScalarType();
    }

    @Override
    public GraphQLType getItemType() {
        return getType().getItemType();
    }

    @Override
    public boolean needFieldSelection() {
        return getType().needFieldSelection();
    }

    public boolean isObjectType() {
        return getType().isObjectType();
    }

    public String getNamedTypeName() {
        return getType().getNamedTypeName();
    }

    @Override
    public GraphQLType mergeType(GraphQLType type) {
        if (this == type)
            return this;

        GraphQLType baseType = getType().mergeType(type);
        if (baseType == null)
            return null;

        return GraphQLTypeHelper.nonNullType(baseType);
    }

    @Override
    public GraphQLType getNullableType() {
        return getType().getNullableType();
    }
}
