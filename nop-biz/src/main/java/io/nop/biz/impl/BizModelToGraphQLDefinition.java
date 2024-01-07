/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.impl;

import io.nop.api.core.annotations.biz.BizActionArgKind;
import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.OrderedComparator;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.biz.model.BizActionArgModel;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizLoaderModel;
import io.nop.biz.model.BizReturnModel;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLInputFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.fetcher.ServiceActionFetcher;
import io.nop.graphql.core.reflection.ArgBuilders;
import io.nop.graphql.core.reflection.EvalGraphQLArgsNormalizer;
import io.nop.graphql.core.reflection.IServiceActionArgBuilder;
import io.nop.graphql.core.schema.GraphQLScalarType;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.schema.meta.ObjMetaToGraphQLDefinition;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.graphql.core.utils.GraphQLTypeHelper;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.graphql.core.GraphQLErrors.ARG_ACTION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_ACTION_ARG_TYPE_NOT_OBJ_TYPE;

public class BizModelToGraphQLDefinition {
    public static final BizModelToGraphQLDefinition INSTANCE = new BizModelToGraphQLDefinition();

    public GraphQLFieldDefinition toOperationDefinition(String bizObjName, BizActionModel actionModel,
                                                        TypeRegistry typeRegistry, List<IActionDecoratorCollector> collectors,
                                                        IServiceActionArgBuilder thisObjBuilder) {
        List<IServiceActionDecorator> decorators = new ArrayList<>();
        if (collectors != null) {
            for (IActionDecoratorCollector collector : collectors) {
                collector.collectDecorator(actionModel, decorators);
            }
            Collections.sort(decorators, OrderedComparator.instance());
        }

        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setLocation(actionModel.getLocation());
        String operationName = GraphQLNameHelper.getOperationName(bizObjName, actionModel.getName());
        field.setName(operationName);
        field.setOperationName(operationName);

        field.setDescription(actionModel.getDisplayName());
        field.setType(getType(bizObjName, actionModel, typeRegistry));
        field.setOperationType(actionModel.getOperationType());
        field.setAuth(actionModel.getAuth());
        List<GraphQLArgumentDefinition> args = getArgs(bizObjName, actionModel.getName(), actionModel.getArgs(),
                typeRegistry);
        if (args != null)
            field.setArguments(args);

        IServiceAction action = buildAction(actionModel, thisObjBuilder);
        for (IServiceActionDecorator decorator : decorators) {
            action = decorator.decorate(action);
        }
        field.setServiceAction(action);
        field.setFetcher(new ServiceActionFetcher(action));

        if (actionModel.getMakerChecker() != null) {
            String tryMethod = actionModel.getMakerChecker().getTryMethod();
            String cancelMethod = actionModel.getMakerChecker().getCancelMethod();
            BizMakerCheckerMeta meta = new BizMakerCheckerMeta(tryMethod, cancelMethod);
            field.setMakerCheckerMeta(meta);
        }

        if (actionModel.getArgsNormalizer() != null) {
            field.setArgsNormalizer(new EvalGraphQLArgsNormalizer(actionModel.getArgsNormalizer()));
        }
        return field;
    }

    public GraphQLFieldDefinition toBuilder(String thisObjName, BizLoaderModel loaderModel, TypeRegistry typeRegistry) {
        GraphQLFieldDefinition field = new GraphQLFieldDefinition();
        field.setLocation(loaderModel.getLocation());
        field.setName(loaderModel.getName());
        field.setType(getLoaderType(thisObjName, loaderModel, typeRegistry));
        List<GraphQLArgumentDefinition> args = getArgs(thisObjName, loaderModel.getName(), loaderModel.getArgs(),
                typeRegistry);
        if (args != null)
            field.setArguments(args);

        IDataFetcher fetcher = buildFetcher(loaderModel, typeRegistry);
        field.setFetcher(fetcher);
        return field;
    }

    public IServiceAction buildAction(BizActionModel actionModel, IServiceActionArgBuilder thisObjBuilder) {
        IEvalAction source = actionModel.getSource();
        if (source == null)
            source = ctx -> null;

        // 保持参数获取顺序
        Map<String, IServiceActionArgBuilder> argBuilders = new LinkedHashMap<>();
        for (BizActionArgModel arg : actionModel.getArgs()) {
            IServiceActionArgBuilder argBuilder = getArgBuilder(arg.getKind(), arg.getName(), arg.getType());
            argBuilders.put(arg.getName(), argBuilder);
        }
        argBuilders.put(GraphQLConstants.VAR_THIS_OBJ, thisObjBuilder);
        return new EvalServiceAction(source, argBuilders);
    }

    IDataFetcher buildFetcher(BizLoaderModel loaderModel, TypeRegistry typeRegistry) {
        IEvalAction source = loaderModel.getSource();
        if (source == null)
            source = ctx -> null;

        Map<String, Function<IDataFetchingEnvironment, Object>> argBuilders = new LinkedHashMap<>();
        for (BizActionArgModel arg : loaderModel.getArgs()) {
            Function<IDataFetchingEnvironment, Object> argBuilder = getFetcherArg(arg.getKind(), arg.getName(),
                    arg.getType());
            argBuilders.put(arg.getName(), argBuilder);
        }
        return new EvalActionDataFetcher(source, argBuilders);
    }

    IServiceActionArgBuilder getArgBuilder(BizActionArgKind kind, String name, IGenericType type) {
        if (kind == BizActionArgKind.RequestBean) {
            return ArgBuilders.getActionRequest(type);
        } else if (kind == BizActionArgKind.FieldSelection) {
            return ArgBuilders.getSelection();
        } else if (kind == BizActionArgKind.ServiceContext) {
            return ArgBuilders.getContext();
        } else {
            return ArgBuilders.getActionArgFromRequest(name, type);
        }
    }

    Function<IDataFetchingEnvironment, Object> getFetcherArg(BizActionArgKind kind, String name, IGenericType type) {
        if (kind == null) {
            return ArgBuilders.getArg(name, type);
        }
        switch (kind) {
            case ContextRoot:
                return IDataFetchingEnvironment::getRoot;
            case ContextSource:
                return IDataFetchingEnvironment::getSource;
            case Cache:
                return IDataFetchingEnvironment::getCache;
            case ServiceContext:
                return IDataFetchingEnvironment::getExecutionContext;
            case FieldSelection:
                return IDataFetchingEnvironment::getSelectionBean;
            case RequestBean:
                return ArgBuilders.getArgsAsBean(type);
        }
        return ArgBuilders.getArg(name, type);
    }

    List<GraphQLArgumentDefinition> getArgs(String thisObjName, String actionName, List<BizActionArgModel> bizArgs,
                                            TypeRegistry typeRegistry) {
        if (bizArgs == null || bizArgs.isEmpty())
            return null;

        List<GraphQLArgumentDefinition> args = new ArrayList<>(bizArgs.size());
        for (BizActionArgModel bizArg : bizArgs) {
            BizActionArgKind kind = bizArg.getKind();
            if (kind == BizActionArgKind.RequestBean) {
                return getRequestBeanArgTypes(thisObjName, actionName, bizArg, typeRegistry);
            } else if (kind == null) {
                GraphQLType type = getArgType(thisObjName, bizArg, typeRegistry);
                GraphQLArgumentDefinition argDef = new GraphQLArgumentDefinition();
                argDef.setName(bizArg.getName());
                argDef.setType(type);
                args.add(argDef);
            }
        }
        return args;
    }

    private List<GraphQLArgumentDefinition> getRequestBeanArgTypes(String thisObjName, String actionName,
                                                                   BizActionArgModel argModel, TypeRegistry registry) {
        GraphQLType type = getArgType(thisObjName, argModel, registry);
        if (!(type instanceof GraphQLNamedType)) {
            throw new NopException(ERR_GRAPHQL_ACTION_ARG_TYPE_NOT_OBJ_TYPE).source(argModel).param(ARG_ACTION_NAME, actionName)
                    .param(ARG_ARG_NAME, argModel.getName()).param(ARG_TYPE, type);
        }

        GraphQLDefinition def = registry.getType(type.toString());
        if (!(def instanceof GraphQLObjectDefinition)) {
            throw new NopException(ERR_GRAPHQL_ACTION_ARG_TYPE_NOT_OBJ_TYPE).source(argModel).param(ARG_ACTION_NAME, actionName)
                    .param(ARG_ARG_NAME, argModel.getName()).param(ARG_TYPE, type);
        }

        List<GraphQLArgumentDefinition> argDefs = new ArrayList<>();
        if (def instanceof GraphQLObjectDefinition) {
            GraphQLObjectDefinition objDef = (GraphQLObjectDefinition) def;
            for (GraphQLFieldDefinition field : objDef.getFields()) {
                String propName = field.getName();
                GraphQLType graphqlType = field.getType().deepClone();
                argDefs.add(buildArgDef(propName, graphqlType));
            }
        } else {
            GraphQLInputDefinition objDef = (GraphQLInputDefinition) def;
            for (GraphQLInputFieldDefinition field : objDef.getFields()) {
                String propName = field.getName();
                GraphQLType graphQLType = field.getType().deepClone();
                argDefs.add(buildArgDef(propName, graphQLType));
            }
        }
        return argDefs;
    }

    GraphQLType getType(String thisObjName, BizActionModel actionModel, TypeRegistry typeRegistry) {
        BizReturnModel returnModel = actionModel.getReturn();
        if (returnModel == null)
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.String);

        ISchema schema = returnModel.getSchema();
        if (schema == null)
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.String);

        return ObjMetaToGraphQLDefinition.INSTANCE.toGraphQLType(thisObjName, schema, returnModel.isMandatory(),
                typeRegistry, false);
    }

    GraphQLType getLoaderType(String thisObjName, BizLoaderModel loaderModel, TypeRegistry typeRegistry) {
        BizReturnModel returnModel = loaderModel.getReturn();
        if (returnModel == null)
            return null;

        GraphQLType type = GraphQLTypeHelper.parseType(returnModel.getLocation(),
                (String) returnModel.prop_get(GraphQLConstants.ATTR_GRAPHQL_TYPE), typeRegistry);

        if (type != null)
            return type;

        ISchema schema = returnModel.getSchema();
        if (schema == null)
            return null;

        return ObjMetaToGraphQLDefinition.INSTANCE.toGraphQLType(thisObjName, schema, returnModel.isMandatory(),
                typeRegistry, false);
    }

    private GraphQLArgumentDefinition buildArgDef(String name, GraphQLType type) {
        GraphQLArgumentDefinition argDef = new GraphQLArgumentDefinition();
        argDef.setName(name);
        argDef.setType(type);
        return argDef;
    }

    GraphQLType getArgType(String thisObjName, BizActionArgModel argModel, TypeRegistry registry) {
        GraphQLType type = GraphQLTypeHelper.parseType(argModel.getLocation(),
                (String) argModel.prop_get(GraphQLConstants.ATTR_GRAPHQL_TYPE), registry);
        if (type != null)
            return type;

        ISchema schema = argModel.getSchema();
        if (schema == null)
            return null;
        return ObjMetaToGraphQLDefinition.INSTANCE.toGraphQLType(thisObjName, schema, argModel.isMandatory(), registry, true);
    }
}