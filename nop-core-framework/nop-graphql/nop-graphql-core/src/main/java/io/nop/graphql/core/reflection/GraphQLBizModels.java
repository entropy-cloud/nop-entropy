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
import io.nop.commons.util.StringHelper;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.schema.TypeRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.nop.graphql.core.GraphQLErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_INVALID_OPERATION_NAME;

/**
 * 通过java注解可以为GraphQLObjectDefinition补充loader等加载器定义。
 * 在Nop平台中，一个ObjMeta一般对应一个GraphQL对象，所有GraphQL能访问的字段都应在ObjMeta中定义。具体字段如何加载，可以通过 Java服务方法或者BizModel模型来提供。
 */
@DataBean
public class GraphQLBizModels {
    private final Map<String, GraphQLBizModel> bizModels = new HashMap<>();

    public Map<String, GraphQLBizModel> getBizModels() {
        return bizModels;
    }

    public static GraphQLBizModels fromBizModels(Map<String, GraphQLBizModel> bizModels) {
        GraphQLBizModels ret = new GraphQLBizModels();
        ret.bizModels.putAll(bizModels);
        return ret;
    }

    public void build(TypeRegistry typeRegistry, Collection<?> beans) {
        if (beans != null) {
            for (Object bean : beans) {
                GraphQLBizModel bizModel = ReflectionBizModelBuilder.INSTANCE.build(bean, typeRegistry, this);
                GraphQLBizModel oldModel = bizModels.putIfAbsent(bizModel.getBizObjName(), bizModel);
                if (oldModel != null)
                    oldModel.merge(bizModel);
            }
        }

        findBizResources();
    }

    private void findBizResources() {
        for (IResource resource : ModuleManager.instance().findModuleResources(false, "model", ".xbiz")) {
            discoverBiz(bizModels, resource);
        }

        for (IResource resource : ModuleManager.instance().findModuleResources(false, "model", ".xmeta")) {
            discoverMeta(bizModels, resource);
        }
    }

    public GraphQLBizModel getBizModel(String bizObjName) {
        return bizModels.get(bizObjName);
    }

    public GraphQLBizModel makeBizModel(String bizObjName) {
        return bizModels.computeIfAbsent(bizObjName, GraphQLBizModel::new);
    }

    public static void discoverBizModel(Map<String, GraphQLBizModel> bizModels, IResource resource) {
        String fileName = resource.getName();
        if (fileName.endsWith(".xmeta")) {
            discoverMeta(bizModels, resource);
        } else if (fileName.endsWith(".xbiz")) {
            discoverBiz(bizModels, resource);
        }
    }

    private static void discoverBiz(Map<String, GraphQLBizModel> bizModels, IResource resource) {
        String fileName = resource.getName();
        if (fileName.startsWith("_"))
            return;

        // 例如: /nop/auth/model/NopAuthDept/NopAuthDept.xbiz
        int count = StringHelper.countChar(resource.getStdPath(), '/');
        if (count != 5)
            return;

        String bizObjName = fileName.substring(0, fileName.length() - ".xbiz".length());
        makeBizModel(bizModels, bizObjName).setBizPath(resource.getStdPath());
    }

    private static void discoverMeta(Map<String, GraphQLBizModel> bizModels, IResource resource) {
        String fileName = resource.getName();
        if (fileName.startsWith("_"))
            return;

        int count = StringHelper.countChar(resource.getStdPath(), '/');
        if (count != 5)
            return;

        String bizObjName = fileName.substring(0, fileName.length() - ".xmeta".length());
        makeBizModel(bizModels, bizObjName).setMetaPath(resource.getStdPath());
    }

    private static GraphQLBizModel makeBizModel(Map<String, GraphQLBizModel> bizModels, String bizObjName) {
        return bizModels.computeIfAbsent(bizObjName, k -> new GraphQLBizModel(bizObjName));
    }

    public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String action) {
        int pos = action.indexOf(GraphQLConstants.OBJ_ACTION_SEPARATOR);
        if (pos <= 0)
            throw new NopException(ERR_GRAPHQL_INVALID_OPERATION_NAME).param(ARG_OPERATION_NAME, action);

        String bizObjName = action.substring(0, pos);
        String bizAction = action.substring(pos + GraphQLConstants.OBJ_ACTION_SEPARATOR.length());

        GraphQLBizModel bizModel = bizModels.get(bizObjName);
        if (bizModel == null)
            return null;

        return bizModel.getOperationDefinition(opType, bizAction);
    }

    public Set<String> getBizObjNames() {
        return bizModels.keySet();
    }

    public void customize(GraphQLObjectDefinition objDef) {
        GraphQLBizModel bizModel = bizModels.get(objDef.getName());
        if (bizModel != null) {
            bizModel.mergeLoaderTo(objDef, false);
        }
    }
}