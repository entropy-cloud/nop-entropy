/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IOrdered;
import io.nop.core.reflect.IFunctionModel;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.fetcher.BeanMethodAction;

import jakarta.annotation.Priority;
import java.util.HashMap;
import java.util.Map;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_ACTION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_CLASS;
import static io.nop.graphql.core.GraphQLErrors.ARG_LOADER_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_METHOD;
import static io.nop.graphql.core.GraphQLErrors.ARG_OLD_CLASS;
import static io.nop.graphql.core.GraphQLErrors.ARG_OLD_METHOD;
import static io.nop.graphql.core.GraphQLErrors.ARG_PATH_A;
import static io.nop.graphql.core.GraphQLErrors.ARG_PATH_B;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATED_LOADER;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_DUPLICATE_ACTION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_MULTI_BIZ_FILE_FOR_BIZ_OBJ;

@DataBean
public class GraphQLBizModel {
    private final String bizObjName;
    private final Map<String, GraphQLFieldDefinition> queryActions = new HashMap<>();
    private final Map<String, GraphQLFieldDefinition> mutationActions = new HashMap<>();

    private final Map<String, GraphQLFieldDefinition> loaders = new HashMap<>();

    // 在后台使用的内部调用函数，不参与GraphQL类型包装，不直接向前台返回数据
    private final Map<String, BeanMethodAction> bizActions = new HashMap<>();

    private String metaPath;
    private String bizPath;

    public GraphQLBizModel(String bizObjName) {
        this.bizObjName = Guard.notEmpty(bizObjName, "bizObjName");
    }

    public GraphQLBizModel cloneInstance() {
        GraphQLBizModel ret = new GraphQLBizModel(bizObjName);
        ret.queryActions.putAll(queryActions);
        ret.mutationActions.putAll(mutationActions);
        ret.loaders.putAll(loaders);
        ret.bizActions.putAll(bizActions);
        ret.metaPath = metaPath;
        ret.bizPath = bizPath;
        return ret;
    }

    public String getMetaPath() {
        return metaPath;
    }

    public void setMetaPath(String metaPath) {
        if (this.metaPath != null && !this.metaPath.equals(metaPath))
            throw new NopException(ERR_GRAPHQL_MULTI_BIZ_FILE_FOR_BIZ_OBJ)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_PATH_A, this.metaPath)
                    .param(ARG_PATH_B, metaPath);
        this.metaPath = metaPath;
    }

    public String getBizPath() {
        return bizPath;
    }

    public void setBizPath(String bizPath) {
        if (this.bizPath != null && !this.bizPath.equals(bizPath))
            throw new NopException(ERR_GRAPHQL_MULTI_BIZ_FILE_FOR_BIZ_OBJ)
                    .param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_PATH_A, this.bizPath)
                    .param(ARG_PATH_B, bizPath);
        this.bizPath = bizPath;
    }

    public String getModelBasePath() {
        if (bizPath == null)
            return "/biz/" + bizObjName + "/";
        int pos = bizPath.lastIndexOf('/');
        return bizPath.substring(0, pos + 1);
    }

    public String getBizObjName() {
        return bizObjName;
    }

    public Map<String, GraphQLFieldDefinition> getQueryActions() {
        return queryActions;
    }

    public Map<String, GraphQLFieldDefinition> getMutationActions() {
        return mutationActions;
    }

    public Map<String, GraphQLFieldDefinition> getLoaders() {
        return loaders;
    }

    public Map<String, BeanMethodAction> getBizActions() {
        return bizActions;
    }

    public GraphQLFieldDefinition getQueryAction(String action) {
        return queryActions.get(action);
    }

    public GraphQLFieldDefinition getMutationAction(String action) {
        return mutationActions.get(action);
    }

    public BeanMethodAction getBizAction(String action) {
        return bizActions.get(action);
    }

    public GraphQLFieldDefinition getLoader(String name) {
        return loaders.get(name);
    }

    public void addQueryAction(String action, GraphQLFieldDefinition field) {
        Guard.notNull(field.getServiceAction(), "field.serviceAction");

        field.setOperationType(GraphQLOperationType.query);

        GraphQLFieldDefinition old = queryActions.get(action);
        int cmp = comparePriority(old, field);
        if (cmp < 0)
            return;

        if (cmp > 0) {
            queryActions.put(action, field);
            return;
        }

        throw new NopException(ERR_GRAPHQL_DUPLICATE_ACTION).param(ARG_BIZ_OBJ_NAME, bizObjName)
                .param(ARG_ACTION_NAME, action).param(ARG_METHOD, field.getFunctionModel())
                .param(ARG_CLASS, field.getSourceClassModel())
                .param(ARG_OLD_METHOD, old.getFunctionModel())
                .param(ARG_OLD_CLASS, old.getSourceClassModel());
    }

    public void addMutationAction(String action, GraphQLFieldDefinition field) {
        Guard.notNull(field.getServiceAction(), "field.serviceAction");

        field.setOperationType(GraphQLOperationType.mutation);
        GraphQLFieldDefinition old = mutationActions.get(action);
        int cmp = comparePriority(old, field);
        if (cmp < 0)
            return;

        if (cmp > 0) {
            mutationActions.put(action, field);
            return;
        }
        throw new NopException(ERR_GRAPHQL_DUPLICATE_ACTION).param(ARG_BIZ_OBJ_NAME, bizObjName)
                .param(ARG_ACTION_NAME, action).param(ARG_METHOD, field.getFunctionModel())
                .param(ARG_CLASS, field.getSourceClassModel())
                .param(ARG_OLD_METHOD, old.getFunctionModel())
                .param(ARG_OLD_CLASS, old.getSourceClassModel());
    }

    public void addBizAction(String action, BeanMethodAction fn) {
        Guard.notNull(fn, "serviceAction");

        BeanMethodAction old = bizActions.get(action);
        int cmp = comparePriority(old, fn);
        if (cmp < 0)
            return;

        if (cmp > 0) {
            bizActions.put(action, fn);
            return;
        }

        throw new NopException(ERR_GRAPHQL_DUPLICATE_ACTION).param(ARG_BIZ_OBJ_NAME, bizObjName)
                .param(ARG_ACTION_NAME, action).param(ARG_METHOD, fn).param(ARG_OLD_METHOD, old)
                .param(ARG_CLASS, fn.getSourceClassModel())
                .param(ARG_OLD_CLASS, old.getSourceClassModel());
    }

    int comparePriority(GraphQLFieldDefinition old, GraphQLFieldDefinition field) {
        if (old == null)
            return 1;
        return comparePriority(old.getFunctionModel(), field.getFunctionModel());
    }

    int comparePriority(BeanMethodAction old, BeanMethodAction field) {
        if (old == null)
            return 1;
        return comparePriority(old.getFunctionModel(), field.getFunctionModel());
    }

    int comparePriority(IFunctionModel old, IFunctionModel fn) {
        if (old == null)
            return 1;
        int p1 = getPriority(old);
        int p2 = getPriority(fn);
        return Integer.compare(p1, p2);
    }

    int getPriority(IFunctionModel fn) {
        Priority priority = fn.getAnnotation(Priority.class);
        return priority == null ? IOrdered.NORMAL_PRIORITY : priority.value();
    }

    public void addLoader(String loaderName, GraphQLFieldDefinition field) {
        GraphQLFieldDefinition old = loaders.get(loaderName);
        int cmp = comparePriority(old, field);
        if (cmp < 0)
            return;

        if (cmp > 0) {
            loaders.put(loaderName, field);
            return;
        }

        throw new NopException(ERR_GRAPHQL_DUPLICATED_LOADER).param(ARG_BIZ_OBJ_NAME, bizObjName)
                .param(ARG_LOADER_NAME, loaderName).param(ARG_METHOD, field.getFunctionModel())
                .param(ARG_OLD_METHOD, old.getFunctionModel())
                .param(ARG_CLASS, field.getSourceClassModel())
                .param(ARG_OLD_CLASS, old.getSourceClassModel());
    }

    public void merge(GraphQLBizModel bizModel) {
        for (Map.Entry<String, GraphQLFieldDefinition> entry : bizModel.queryActions.entrySet()) {
            addQueryAction(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, GraphQLFieldDefinition> entry : bizModel.mutationActions.entrySet()) {
            addMutationAction(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, GraphQLFieldDefinition> entry : bizModel.loaders.entrySet()) {
            addLoader(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, BeanMethodAction> entry : bizModel.bizActions.entrySet()) {
            addBizAction(entry.getKey(), entry.getValue());
        }
    }

    public void mergeLoaderTo(GraphQLObjectDefinition objDef, boolean force) {
        for (GraphQLFieldDefinition fieldDef : this.loaders.values()) {
            objDef.mergeField(fieldDef,force);
        }
    }

    public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String action) {
        if (opType == GraphQLOperationType.query) {
            return getQueryAction(action);
        } else if (opType == GraphQLOperationType.mutation) {
            return getMutationAction(action);
        }else{
            GraphQLFieldDefinition field = getQueryAction(action);
            if(field == null)
                field = getMutationAction(action);
            return field;
        }
    }
}