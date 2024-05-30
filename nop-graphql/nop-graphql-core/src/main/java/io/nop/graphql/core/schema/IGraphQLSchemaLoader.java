/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.schema;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
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

import static io.nop.graphql.core.GraphQLErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_FRAGMENT_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_FRAGMENT_SELECTION;

/**
 * 用于加载builtin schema之外的biz模型定义
 */
public interface IGraphQLSchemaLoader {
    GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name);

    GraphQLObjectDefinition getObjectTypeDefinition(String objName);

    GraphQLTypeDefinition getTypeDefinition(String typeName);

    FieldSelectionBean getFragmentDefinition(String objName, String fragmentName);

    default FieldSelectionBean requireFragmentDefinition(String objName, String fragmentName) {
        FieldSelectionBean fragment = getFragmentDefinition(objName, fragmentName);
        if (fragment == null)
            throw new NopException(ERR_GRAPHQL_UNKNOWN_FRAGMENT_SELECTION)
                    .param(ARG_BIZ_OBJ_NAME, objName)
                    .param(ARG_FRAGMENT_NAME, fragmentName);
        return fragment;
    }

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