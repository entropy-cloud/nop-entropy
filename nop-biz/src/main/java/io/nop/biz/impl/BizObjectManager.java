/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.impl;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancellable;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.biz.makerchecker.IMakerCheckerProvider;
import io.nop.commons.cache.GlobalCacheRegistry;
import io.nop.commons.collections.SafeOrderedComparator;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.resource.cache.ResourceLoadingCache;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.ast.GraphQLTypeDefinition;
import io.nop.graphql.core.biz.IGraphQLBizInitializer;
import io.nop.graphql.core.biz.IGraphQLSchemaInitializer;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.schema.IGraphQLSchemaLoader;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_EAGER_INIT_BIZ_OBJECT;
import static io.nop.graphql.core.GraphQLConstants.GRAPHQL_CONNECTION_PREFIX;
import static io.nop.graphql.core.GraphQLConstants.OBJ_ACTION_SEPARATOR;
import static io.nop.graphql.core.GraphQLConstants.PAGE_BEAN_PREFIX;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_NOT_OBJ_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNDEFINED_OBJECT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_BUILTIN_TYPE;

public class BizObjectManager implements IBizObjectManager, IGraphQLSchemaLoader {
    private List<Object> bizModelBeans;

    private TypeRegistry typeRegistry;

    private ICancellable cancellable;

    private GraphQLBizModels bizModels;

    private List<IActionDecoratorCollector> actionDecoratorCollectors;

    private List<IGraphQLSchemaInitializer> schemaInitializers;
    private List<IGraphQLBizInitializer> bizInitializers;

    private IMakerCheckerProvider makerCheckerProvider;

    private Map<String, GraphQLBizModel> dynBizModels = Collections.emptyMap();

    private final ResourceLoadingCache<IBizObject> bizObjCache = new ResourceLoadingCache<>("biz-object-cache",
            this::buildBizObject, null);

    public void setBizModelBeans(List<Object> bizModelBeans) {
        this.bizModelBeans = bizModelBeans;
    }

    public void setDynBizModels(Map<String, GraphQLBizModel> dynBizModels) {
        this.dynBizModels = Guard.notNull(dynBizModels, "dynBizModels");
    }

    public void setBizInitializers(List<IGraphQLBizInitializer> bizInitializers) {
        if (bizInitializers != null) {
            bizInitializers.sort(SafeOrderedComparator.DEFAULT);
        }
        this.bizInitializers = bizInitializers;
    }

    public void setSchemaInitializers(List<IGraphQLSchemaInitializer> schemaInitializers) {
        this.schemaInitializers = schemaInitializers;
    }

    public void setActionDecoratorCollectors(List<IActionDecoratorCollector> actionDecoratorCollectors) {
        this.actionDecoratorCollectors = actionDecoratorCollectors;
    }

    public void setTypeRegistry(TypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    @Inject
    @Nullable
    public void setMakerCheckerProvider(IMakerCheckerProvider makerCheckerProvider) {
        this.makerCheckerProvider = makerCheckerProvider;
    }

    @PostConstruct
    public void init() {
        if (typeRegistry == null)
            typeRegistry = new TypeRegistry();

        cancellable = new Cancellable();

        GlobalCacheRegistry.instance().register(bizObjCache);
        cancellable.appendOnCancelTask(() -> {
            GlobalCacheRegistry.instance().unregister(bizObjCache);
        });

        if (schemaInitializers != null) {
            for (IGraphQLSchemaInitializer initializer : schemaInitializers) {
                initializer.initialize(typeRegistry);
            }
        }

        bizModels = new GraphQLBizModels();
        bizModels.build(typeRegistry, bizModelBeans);
    }

    public void delayInit() {
        if (CFG_GRAPHQL_EAGER_INIT_BIZ_OBJECT.get()) {
            for (String bizObjName : getBizObjNames()) {
                getBizObject(bizObjName);
            }
        }
    }

    public void clearCache() {
        bizObjCache.clear();
    }

    public void removeCache(String bizObjName) {
        bizObjCache.remove(bizObjName);
    }

    @PreDestroy
    public void destroy() {
        if (cancellable != null)
            cancellable.cancel();
        if (typeRegistry != null)
            typeRegistry.clear();
    }

    private IBizObject buildBizObject(String bizObjName) {
        try {
            return new BizObjectBuilder(bizModels, dynBizModels, typeRegistry, actionDecoratorCollectors, bizInitializers,
                    makerCheckerProvider).buildBizObject(bizObjName);
        } catch (NopException e) {
            e.addXplStack("buildBizObject:" + bizObjName);
            throw e;
        }
    }

    @Override
    public IBizObject getBizObject(String bizObjName) throws NopException {
        return bizObjCache.get(bizObjName);
    }

    @Override
    public ApiResponse<?> buildResponse(String locale, Object result, IServiceContext rt) {
        if (result instanceof ApiResponse)
            return (ApiResponse<?>) result;

        ApiResponse<?> response;

        if (rt.getError() != null) {
            response = ErrorMessageManager.instance().buildResponse(locale, rt.getError());
        } else if (rt.getErrorBeans() != null && !rt.getErrorBeans().isEmpty()) {
            Collections.sort(rt.getErrorBeans(), Comparator.comparing(ErrorBean::getSeverity));
            response = ErrorMessageManager.instance().buildResponse(locale, rt.getErrorBeans().get(0));
        } else {
            response = ApiResponse.buildSuccess(result);
        }
        response.setHeaders(rt.getResponseHeaders());

        return response;
    }

    @Override
    public GraphQLFieldDefinition getOperationDefinition(GraphQLOperationType opType, String name) {
        String bizObjName;
        String bizAction = name;
        int pos = name.indexOf(OBJ_ACTION_SEPARATOR);
        if (pos < 0) {
            bizObjName = GraphQLConstants.BIZ_OBJ_NAME_ROOT;
        } else {
            bizObjName = name.substring(0, pos);
            bizAction = name.substring(pos + OBJ_ACTION_SEPARATOR.length());
        }

        IBizObject bizObj = getBizObject(bizObjName);
        if (bizObj == null)
            throw new NopException(ERR_GRAPHQL_UNDEFINED_OBJECT).param(ARG_OBJ_NAME, bizObjName);

        return bizObj.getOperationDefinition(opType, bizAction);
    }

    @Override
    public GraphQLObjectDefinition getObjectTypeDefinition(String objName) {
        return (GraphQLObjectDefinition) getTypeDefinition(objName);
    }

    @Override
    public GraphQLTypeDefinition getTypeDefinition(String objName) {
        if (GraphQLNameHelper.isGeneratedTypeName(objName) || objName.startsWith(PAGE_BEAN_PREFIX)
                || objName.startsWith(GRAPHQL_CONNECTION_PREFIX))
            return typeRegistry.getType(objName);

        GraphQLTypeDefinition def = typeRegistry.getType(objName);
        if (def != null)
            return def;

        if (objName.startsWith("i_") || objName.startsWith("g_") || objName.startsWith("e_"))
            throw new NopException(ERR_GRAPHQL_UNKNOWN_BUILTIN_TYPE).param(ARG_TYPE_NAME, objName);

        IBizObject bizObj = getBizObject(objName);
        if (bizObj == null)
            return null;
        return bizObj.getObjectDefinition();
    }

    @Override
    public GraphQLObjectDefinition resolveTypeDefinition(GraphQLType type) {
        GraphQLType baseType = type.getNullableType();
        if (!(baseType instanceof GraphQLNamedType)) {
            throw new NopException(ERR_GRAPHQL_NOT_OBJ_TYPE).param(ARG_TYPE, type);
        }

        GraphQLNamedType namedType = (GraphQLNamedType) baseType;
        if (namedType.getResolvedType() != null) {
            GraphQLDefinition def = namedType.getResolvedType();
            if (!(def instanceof GraphQLObjectDefinition))
                throw new NopException(ERR_GRAPHQL_NOT_OBJ_TYPE).param(ARG_TYPE, type);

            return (GraphQLObjectDefinition) def;
        }

        return getObjectTypeDefinition(namedType.getName());
    }

    @Override
    public Set<String> getBizObjNames() {
        return bizModels.getBizObjNames();
    }

    @Override
    public List<GraphQLFieldDefinition> getOperationDefinitions(GraphQLOperationType opType) {
        Set<String> bizObjNames = getBizObjNames();
        List<GraphQLFieldDefinition> defs = new ArrayList<>();
        for (String bizObjName : bizObjNames) {
            IBizObject bizObj = getBizObject(bizObjName);
            Collection<GraphQLFieldDefinition> ops = bizObj.getOperationDefinitions(opType);
            defs.addAll(ops);
        }
        return defs;
    }

    @Override
    public Collection<GraphQLTypeDefinition> getTypeDefinitions() {
        Map<String, GraphQLTypeDefinition> types = new TreeMap<>();
        for (String bizObjName : getBizObjNames()) {
            IBizObject bizObj = getBizObject(bizObjName);
            if (bizObj.getObjectDefinition() != null) {
                types.put(bizObjName, bizObj.getObjectDefinition());
            }
        }
        // 加载BizObject的时候有可能会注册新的类型，因此最后再从typeRegistry获取
        CollectionHelper.putAllIfAbsent(types, typeRegistry.getTypes());
        return types.values();
    }

    @Override
    public GraphQLDocument getGraphQLDocument() {
        GraphQLDocument doc = new GraphQLDocument();
        List<GraphQLDefinition> defs = new ArrayList<>();
        defs.addAll(getTypeDefinitions().stream().map(def -> def.deepClone()).collect(Collectors.toList()));

        GraphQLObjectDefinition queryType = getObjDef(GraphQLOperationType.query);
        if (queryType != null) {
            defs.add(queryType);
        }

        GraphQLObjectDefinition mutationType = getObjDef(GraphQLOperationType.mutation);
        if (mutationType != null) {
            defs.add(mutationType);
        }

        GraphQLObjectDefinition subscriptionType = getObjDef(GraphQLOperationType.subscription);
        if (subscriptionType != null) {
            defs.add(subscriptionType);
        }

        doc.setDefinitions(defs);
        return doc;
    }

    private GraphQLObjectDefinition getObjDef(GraphQLOperationType opType) {
        List<GraphQLFieldDefinition> fields = getOperationDefinitions(opType);
        if (fields.isEmpty())
            return null;

        fields = fields.stream().map(f -> f.deepClone()).collect(Collectors.toList());

        GraphQLObjectDefinition objDef = new GraphQLObjectDefinition();
        objDef.setExtension(true);
        objDef.setName(opType.getTypeName());
        objDef.setFields(fields);
        return objDef;
    }

}