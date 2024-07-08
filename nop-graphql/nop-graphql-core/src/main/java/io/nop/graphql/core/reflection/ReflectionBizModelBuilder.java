/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.reflection;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizArgsNormalizer;
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizMakerChecker;
import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextRoot;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.LazyLoad;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.auth.ActionAuthMeta;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.MultiCsvSet;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.ArrayHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.IAopProxy;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IBizModelImpl;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.fetcher.BeanMethodAction;
import io.nop.graphql.core.fetcher.BeanMethodBatchFetcher;
import io.nop.graphql.core.fetcher.BeanMethodFetcher;
import io.nop.graphql.core.fetcher.ServiceActionFetcher;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.utils.GraphQLNameHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_CLASS;
import static io.nop.graphql.core.GraphQLErrors.ARG_METHOD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_RETURN_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_ACTION_RETURN_TYPE_MUST_NOT_BE_API_RESPONSE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_BATCH_LOAD_METHOD_MUST_RETURN_LIST;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_ONLY_ALLOW_ONE_CONTEXT_SOURCE_PARAM;
import static io.nop.graphql.core.reflection.ArgBuilders.getArg;
import static io.nop.graphql.core.reflection.ArgBuilders.getArgsAsBean;
import static io.nop.graphql.core.reflection.ArgBuilders.getArgsAsRequest;
import static io.nop.graphql.core.reflection.ArgBuilders.getEnv;

public class ReflectionBizModelBuilder {
    public static final ReflectionBizModelBuilder INSTANCE = new ReflectionBizModelBuilder();

    public GraphQLBizModel build(Object bean, TypeRegistry registry) {
        Class<?> clazz = bean.getClass();
        if (IAopProxy.class.isAssignableFrom(clazz))
            clazz = clazz.getSuperclass();

        IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
        BizModel bizModel = getBizModel(classModel);
        String[] disabledActions = bizModel.disabledActions();
        String[] inheritActions = bizModel.inheritActions();

        String bizObjName = getBizObjName(bizModel, classModel);
        if (bean instanceof IBizModelImpl) {
            String name = ((IBizModelImpl) bean).getBizObjName();
            if (!StringHelper.isEmpty(name))
                bizObjName = name;
        }

        if (StringHelper.isEmpty(bizObjName))
            throw new IllegalArgumentException("nop.err.graphql.empty-bizObjName:" + bean);

        GraphQLBizModel ret = new GraphQLBizModel(bizObjName);

        SourceLocation loc = SourceLocation.fromClass(clazz);
        for (IFunctionModel func : classModel.getMethods()) {
            BizMutation mutation = func.getAnnotation(BizMutation.class);
            if (mutation != null) {
                String action = getMutationName(mutation, func);
                if (!isLocalMethod(classModel, func) && !isAllowed(action, disabledActions, inheritActions))
                    continue;
                GraphQLFieldDefinition field = buildActionField(bizObjName, bean, GraphQLOperationType.mutation,
                        loc, action, func, registry);
                field.setSourceClassModel(classModel);
                field.setOperationName(GraphQLNameHelper.getOperationName(bizObjName, action));
//                if (field.getAuth() == null) {
//                    String permission = GraphQLNameHelper.getPermission(bizObjName, action);
//                    field.setAuth(new ActionAuthMeta(Collections.emptySet(), MultiCsvSet.fromText(permission)));
//                }
                ret.addMutationAction(action, field);
                continue;
            }

            BizQuery query = func.getAnnotation(BizQuery.class);
            if (query != null) {
                String action = getQueryName(query, func);
                if (!isLocalMethod(classModel, func) && !isAllowed(action, disabledActions, inheritActions))
                    continue;
                GraphQLFieldDefinition field = buildActionField(bizObjName, bean, GraphQLOperationType.query,
                        loc, action, func, registry);
                field.setSourceClassModel(classModel);
                field.setOperationName(GraphQLNameHelper.getOperationName(bizObjName, action));
//                if (field.getAuth() == null) {
//                    String permission = GraphQLNameHelper.getPermission(bizObjName, action);
//                    field.setAuth(new ActionAuthMeta(Collections.emptySet(), MultiCsvSet.fromText(permission)));
//                }

                ret.addQueryAction(action, field);
                continue;
            }

            BizAction bizAction = func.getAnnotation(BizAction.class);
            if (bizAction != null) {
                String action = getBizActionName(bizAction, func);
                if (!isLocalMethod(classModel, func) && !isAllowed(action, disabledActions, inheritActions))
                    continue;
                BeanMethodAction gqlAction = buildAction(bean, loc, action, func);
                gqlAction.setSourceClassModel(classModel);
                ret.addBizAction(action, gqlAction);
                continue;
            }

            BizLoader bizLoader = func.getAnnotation(BizLoader.class);
            if (bizLoader != null) {
                String name = getLoaderName(bizLoader, func);
                if (!isLocalMethod(classModel, func) && !isAllowed(name, disabledActions, inheritActions))
                    continue;

                GraphQLFieldDefinition field = buildFetcherField(bean, loc, name, func, registry);
                field.setSourceClassModel(classModel);

                IGenericType returnType = func.getReturnType();
                if (field.getFetcher() instanceof BeanMethodBatchFetcher) {
                    returnType = returnType.getTypeParameters().get(0);
                }
                field.setType(ReflectionGraphQLTypeFactory.INSTANCE.buildGraphQLType(returnType, bizObjName,
                        getReturnBizObjName(func), registry, false));
                field.setAutoCreate(bizLoader.autoCreateField());

                GraphQLObjectDefinition loaderType = getLoaderForType(bizLoader, registry);
                if (loaderType != null) {
                    loaderType.mergeField(field, false);
                } else {
                    ret.addLoader(name, field);
                }
            }
        }

        return ret;
    }

    boolean isLocalMethod(IClassModel classModel, IFunctionModel func) {
        return func.getDeclaringClass() == classModel.getRawClass();
    }

    boolean isAllowed(String actionName, String[] disabledActions, String[] inheritedActions) {
        if (disabledActions.length > 0) {
            if (ArrayHelper.indexOf(disabledActions, actionName) >= 0)
                return false;
        }

        if (inheritedActions.length > 0) {
            return ArrayHelper.indexOf(inheritedActions, actionName) >= 0;
        }

        return true;
    }

    String getReturnBizObjName(IFunctionModel fn) {
        GraphQLReturn returnAnn = fn.getAnnotation(GraphQLReturn.class);
        return returnAnn == null ? null : returnAnn.bizObjName();
    }

    GraphQLObjectDefinition getLoaderForType(BizLoader loader, TypeRegistry registry) {
        if (loader.forType() != Object.class)
            return ReflectionGraphQLTypeFactory.INSTANCE.buildDef(loader.forType(), registry);
        return null;
    }

    private BizModel getBizModel(IClassModel classModel) {
        BizModel bizModel = classModel.getAnnotation(BizModel.class);
        if (bizModel == null)
            throw new IllegalArgumentException("class no @BizModel annotation:" + classModel.getClassName());

        return bizModel;
    }

    private String getBizObjName(BizModel bizModel, IClassModel classModel) {
        String bizObjName = bizModel.value();
        if (StringHelper.isEmpty(bizObjName)) {
            bizObjName = classModel.getSimpleName();
            if (bizObjName.endsWith(GraphQLConstants.POSTFIX_BIZ_MODEL)) {
                bizObjName = StringHelper.removeTail(bizObjName, GraphQLConstants.POSTFIX_BIZ_MODEL);
            }
        }
        return bizObjName;
    }

    private String getQueryName(BizQuery query, IFunctionModel funcModel) {
        String name = query.value();
        if (StringHelper.isEmpty(name))
            name = getActionName(funcModel);
        return name; // GraphQLNameHelper.getOperationName(bizObjName, name);
    }

    private String getMutationName(BizMutation mutation, IFunctionModel funcModel) {
        String name = mutation.value();
        if (StringHelper.isEmpty(name)) {
            name = getActionName(funcModel);
        }
        return name;// GraphQLNameHelper.getOperationName(bizObjName, name);
    }

    private String getLoaderName(BizLoader loader, IFunctionModel funcModel) {
        String name = loader.value();
        if (StringHelper.isEmpty(name))
            name = getActionName(funcModel);
        return name;
    }

    private String getBizActionName(BizAction action, IFunctionModel funcModel) {
        String name = action.value();
        if (StringHelper.isEmpty(name))
            name = getActionName(funcModel);
        return name;
    }

    private String getActionName(IFunctionModel funcModel) {
        String name = funcModel.getName();
        if (funcModel.isAsync()) {
            if (name.endsWith("Async")) {
                name = StringHelper.removeTail(name, "Async");
            }
        }
        return name;
    }

    private GraphQLFieldDefinition buildActionField(String bizObjName, Object bean, GraphQLOperationType opType,
                                                    SourceLocation loc, String name,
                                                    IFunctionModel func, TypeRegistry registry) {
        IServiceAction action = buildAction(bean, loc, name, func);
        IDataFetcher fetcher = new ServiceActionFetcher(action);

        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setFunctionModel(func);

        ReflectionGraphQLTypeFactory.INSTANCE.getArgDefinitions(field, func,
                registry);

        Description description = func.getAnnotation(Description.class);
        if (description != null)
            field.setDescription(description.value());

        Auth auth = func.getAnnotation(Auth.class);
        if (auth != null) {
            field.setAuth(new ActionAuthMeta(auth.publicAccess(), ConvertHelper.toCsvSet(auth.roles()), MultiCsvSet.fromText(auth.permissions())));
        } else {
            String permission = bizObjName + ':' + opType + "|" + bizObjName + ':' + name;
            field.setAuth(new ActionAuthMeta(false, Collections.emptySet(), MultiCsvSet.fromText(permission)));
        }

        BizMakerChecker makerChecker = func.getAnnotation(BizMakerChecker.class);
        if (makerChecker != null) {
            field.setMakerCheckerMeta(new BizMakerCheckerMeta(makerChecker.tryMethod(), makerChecker.cancelMethod()));
        }

        BizArgsNormalizer argsNormalizer = func.getAnnotation(BizArgsNormalizer.class);
        if (argsNormalizer != null) {
            field.setArgsNormalizer(new LazyGraphQLArgsNormalizer(argsNormalizer.value()));
        }

        field.setLocation(loc);
        field.setName(GraphQLNameHelper.getOperationName(bizObjName, name));
        field.setServiceAction(action);
        field.setFetcher(fetcher);

        if (func.getAsyncReturnType().getRawClass() == ApiResponse.class)
            throw new NopException(ERR_GRAPHQL_ACTION_RETURN_TYPE_MUST_NOT_BE_API_RESPONSE)
                    .param(ARG_METHOD_NAME, func.getName())
                    .param(ARG_CLASS, func.getDeclaringClass().getName())
                    .param(ARG_RETURN_TYPE, func.getReturnType());

        try {
            field.setType(ReflectionGraphQLTypeFactory.INSTANCE.buildGraphQLType(func.getReturnType(), bizObjName,
                    getReturnBizObjName(func), registry, false));
        } catch (NopException e) {
            e.addXplStack("buildActionField:" + func.getName());
            throw e;
        }
        return field;
    }

    private BeanMethodAction buildAction(Object bean, SourceLocation loc, String name, IFunctionModel func) {
        List<IServiceActionArgBuilder> argBuilders = new ArrayList<>(func.getArgCount());

        for (int i = 0, n = func.getArgCount(); i < n; i++) {
            IFunctionArgument arg = func.getArgs().get(i);
            if (arg.isAnnotationPresent(ContextRoot.class)) {
                argBuilders.add((req, selection, ctx) -> ctx.getRequest());
            } else if (arg.getRawClass() == ApiRequest.class || arg.isAnnotationPresent(RequestBean.class)) {
                argBuilders.add(ArgBuilders.getActionRequest(arg.getType()));
            } else if (IServiceContext.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add((req, selection, ctx) -> ctx);
            } else if (IEvalScope.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add((req, selection, ctx) -> ctx.getEvalScope());
            } else if (IUserContext.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add((req, selection, ctx) -> ctx.getUserContext());
            } else if (IContext.class == arg.getRawClass()) {
                argBuilders.add((req, selection, ctx) -> ctx.getContext());
            } else if (ICache.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add((req, selection, ctx) -> ctx.getCache());
            } else if (FieldSelectionBean.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add((req, selection, ctx) -> selection);
            } else {
                if (!arg.isAnnotationPresent(Name.class))
                    throw new NopException(ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION).loc(loc)
                            .param(ARG_OBJ_NAME, name).param(ARG_METHOD_NAME, func.getName())
                            .param(ARG_ARG_NAME, arg.getName());
                argBuilders.add(ArgBuilders.getActionArgFromRequest(arg.getName(), arg.getType()));
            }
        }

        return new BeanMethodAction(bean, func, argBuilders);
    }

    /**
     * 识别服务方法上的参数，并把它们对应到GraphQL的loader参数上
     */
    public GraphQLFieldDefinition buildFetcherField(Object bean, SourceLocation loc, String name, IFunctionModel func,
                                                    TypeRegistry registry) {
        GraphQLFieldDefinition def = new GraphQLFieldDefinition();
        def.setName(name);
        def.setLocation(loc);
        def.setFunctionModel(func);

        ReflectionGraphQLTypeFactory.INSTANCE.getArgDefinitions(def, func,
                registry);

        IDataFetcher fetcher = buildFetcher(bean, loc, name, func);
        def.setFetcher(fetcher);
        if(func.isAnnotationPresent(LazyLoad.class))
            def.setLazy(true);

        return def;
    }

    private IDataFetcher buildFetcher(Object bean, SourceLocation loc, String name, IFunctionModel func) {
        List<Function<IDataFetchingEnvironment, Object>> argBuilders = new ArrayList<>(func.getArgCount());

        int sourceIndex = -1;
        for (int i = 0, n = func.getArgCount(); i < n; i++) {
            IFunctionArgument arg = func.getArgs().get(i);
            boolean source = arg.isAnnotationPresent(ContextSource.class);
            if (source) {
                if (sourceIndex >= 0)
                    throw new NopException(ERR_GRAPHQL_ONLY_ALLOW_ONE_CONTEXT_SOURCE_PARAM).param(ARG_OBJ_NAME, name)
                            .param(ARG_METHOD_NAME, func.getName());
                sourceIndex = i;
                argBuilders.add(IDataFetchingEnvironment::getSource);
            } else if (IServiceContext.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add(IDataFetchingEnvironment::getServiceContext);
            } else if (IDataFetchingEnvironment.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add(getEnv());
            } else if (IEvalScope.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add(IDataFetchingEnvironment::getEvalScope);
            } else if (IUserContext.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add(env -> env.getServiceContext().getUserContext());
            } else if (IContext.class == arg.getRawClass()) {
                argBuilders.add(IDataFetchingEnvironment::getContext);
            } else if (arg.isAnnotationPresent(ContextRoot.class)) {
                argBuilders.add(IDataFetchingEnvironment::getRoot);
            } else if (arg.isAnnotationPresent(RequestBean.class)) {
                argBuilders.add(getArgsAsBean(arg.getType()));
            } else if (ICache.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add(IDataFetchingEnvironment::getCache);
            } else if (FieldSelectionBean.class.isAssignableFrom(arg.getRawClass())) {
                argBuilders.add(IDataFetchingEnvironment::getSelectionBean);
            } else if (ApiRequest.class == arg.getRawClass()) {
                argBuilders.add(getArgsAsRequest(arg.getType().getTypeParameters().get(0)));
            } else {
                if (!arg.isAnnotationPresent(Name.class))
                    throw new NopException(ERR_GRAPHQL_METHOD_PARAM_NO_REFLECTION_NAME_ANNOTATION).loc(loc)
                            .param(ARG_OBJ_NAME, name).param(ARG_METHOD_NAME, func.getName())
                            .param(ARG_ARG_NAME, arg.getName());
                argBuilders.add(getArg(arg.getName(), arg.getType()));
            }
        }

        IDataFetcher fetcher = buildFetcher(bean, loc, name, func, argBuilders, sourceIndex);
        return fetcher;
    }

    private IDataFetcher buildFetcher(Object bean, SourceLocation loc, String name, IFunctionModel func,
                                      List<Function<IDataFetchingEnvironment, Object>> argBuilders, int sourceIndex) {
        if (sourceIndex >= 0) {
            IFunctionArgument sourceArg = func.getArgs().get(sourceIndex);
            if (sourceArg.getType().isCollectionLike()) {
                // 如果是集合类型的参数，则表示这是一个BatchLoader
                if (!func.getReturnType().isListLike()) {
                    throw new NopException(ERR_GRAPHQL_BATCH_LOAD_METHOD_MUST_RETURN_LIST).loc(loc)
                            .param(ARG_OBJ_NAME, name).param(ARG_METHOD_NAME, func.getName());
                }
                String loaderName = bean.getClass().getTypeName() + "@" + func.getName();
                return new BeanMethodBatchFetcher(loaderName, bean, func, argBuilders, sourceIndex);
            }
        }
        return new BeanMethodFetcher(bean, func, argBuilders);
    }
}