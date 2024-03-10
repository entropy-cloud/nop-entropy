/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用于加载builtin schema之外的biz模型定义
 */
public interface IGraphQLSchemaLoader {
    GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name);

    GraphQLObjectDefinition getObjectTypeDefinition(String objName);

    GraphQLTypeDefinition getTypeDefinition(String typeName);

    /**
     * 根据类型得到类型名称，然后再调用getTypeDefinition返回类型定义
     *
     * @param type 类型对象
     * @return 类型定义
     */
    GraphQLTypeDefinition resolveTypeDefinition(GraphQLType type);
    //
    // GraphQLFragment getFragment(String objName, String fragmentName, Map<String, Object> directives);

    List<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType);

    Collection<GraphQLTypeDefinition> getTypeDefinitions();

    GraphQLDocument getGraphQLDocument();

    Set<String> getBizObjNames();

    Map<String, GraphQLFieldDefinition> getBizOperationDefinitions(String bizObjName);
}