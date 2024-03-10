/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLNamedType;
import io.nop.graphql.core.schema.GraphQLScalarType;

public class GraphQLNamedType extends _GraphQLNamedType {
    private GraphQLScalarType scalarType;

    private GraphQLDefinition resolvedType;

    @Override
    public boolean isNamedType() {
        return true;
    }

    public String toString() {
        return getName();
    }

    @Override
    public boolean isScalarType() {
        return scalarType != null;
    }

    @Override
    public boolean needFieldSelection() {
        return scalarType == null && !isEnumType();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        scalarType = GraphQLScalarType.fromText(name);
    }

    public GraphQLScalarType getScalarType() {
        return scalarType;
    }

    public boolean isObjectType() {
        return resolvedType != null && resolvedType.isObjectDefinition();
    }

    public boolean isEnumType() {
        return resolvedType != null && resolvedType.isEnumDefinition();
    }

    @Override
    public String getNamedTypeName() {
        return getName();
    }

    public GraphQLDefinition getResolvedType() {
        return resolvedType;
    }

    public void setResolvedType(GraphQLDefinition resolvedType) {
        this.resolvedType = resolvedType;
    }

    public GraphQLType mergeType(GraphQLType type) {
        if (this == type)
            return this;

        if (type instanceof GraphQLNonNullType) {
            return type.mergeType(this);
        }

        if (!(type instanceof GraphQLNamedType))
            return null;

        if (getName().equals(((GraphQLNamedType) type).getName()))
            return this;

        return null;
    }

    @Override
    public GraphQLType getNullableType() {
        return this;
    }
}