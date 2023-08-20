/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.utils;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLListType;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLNonNullType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.parse.GraphQLDocumentParser;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.TypeRegistry;

public class GraphQLTypeHelper {
    public static GraphQLType parseType(SourceLocation loc, String type, TypeRegistry registry) {
        if (StringHelper.isEmpty(type))
            return null;

        GraphQLType graphQLType = new GraphQLDocumentParser().parseType(loc, type);
        if (graphQLType == null)
            return null;

        registry.processSpecialType(graphQLType);
        return graphQLType;
    }

    public static boolean isRootType(String name) {
        return name.equals(GraphQLConstants.TYPE_QUERY) || name.equals(GraphQLConstants.TYPE_MUTATION)
                || name.equals(GraphQLConstants.TYPE_SUBSCRIPTION);

    }

    public static boolean isQuery(String name) {
        return GraphQLConstants.TYPE_QUERY.equals(name);
    }

    public static boolean isMutation(String name) {
        return GraphQLConstants.TYPE_MUTATION.equals(name);
    }

    public static GraphQLListType listType(GraphQLType type) {
        GraphQLListType list = new GraphQLListType();
        if (type.getASTParent() != null || type.frozen())
            type = type.deepClone();
        list.setType(type);
        return list;
    }

    public static GraphQLNamedType scalarType(GraphQLScalarType type) {
        GraphQLNamedType ret = new GraphQLNamedType();
        ret.setName(type.name());
        return ret;
    }

    public static GraphQLNonNullType nonNullType(GraphQLType type) {
        if (type instanceof GraphQLNonNullType)
            return ((GraphQLNonNullType) type);

        GraphQLNonNullType ret = new GraphQLNonNullType();
        if (type.getASTParent() != null || type.frozen())
            type = type.deepClone();
        ret.setType(type);
        return ret;
    }

    public static GraphQLNamedType namedType(String name) {
        GraphQLNamedType type = new GraphQLNamedType();
        type.setName(name);
        return type;
    }

}
