/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.commons.type.StdDataType;
import io.nop.graphql.core.ast._gen._GraphQLType;
import io.nop.graphql.core.schema.GraphQLScalarType;

public abstract class GraphQLType extends _GraphQLType {
    public boolean isListType() {
        return false;
    }

    public boolean isNonNullType() {
        return false;
    }

    public boolean isNamedType() {
        return false;
    }

    public boolean isEnumType() {
        return false;
    }

    public boolean isScalarType() {
        return false;
    }

    public boolean isVoidType() {
        return getScalarType() == GraphQLScalarType.Void;
    }

    public boolean needFieldSelection() {
        return true;
    }

    public boolean isObjectType() {
        return false;
    }

    public String getNamedTypeName() {
        return null;
    }

    public GraphQLType getItemType() {
        return null;
    }

    public GraphQLScalarType getScalarType() {
        return null;
    }

    public StdDataType getStdDataType() {
        GraphQLScalarType scalarType = getScalarType();
        return scalarType == null ? null : scalarType.getStdDataType();
    }

    /**
     * 如果非空类型和可空类型合并，则返回非空类型。如果不是能够匹配的类型，则返回null
     *
     * @param type
     * @return 如果不是可空类型与非空类型的区别，则返回null
     */
    public abstract GraphQLType mergeType(GraphQLType type);

    public abstract GraphQLType getNullableType();
}