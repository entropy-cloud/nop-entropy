/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.annotations.biz.BizActionArgKind;
import io.nop.api.core.annotations.biz.BizMakerCheckerMeta;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.biz.model.BizActionArgModel;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizLoaderModel;
import io.nop.biz.model.BizReturnModel;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.type.IGenericType;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLArgumentDefinition;
import io.nop.graphql.core.ast.GraphQLDefinition;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLInputDefinition;
import io.nop.graphql.core.ast.GraphQLInputFieldDefinition;
import io.nop.graphql.core.ast.GraphQLNamedType;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLType;
import io.nop.graphql.core.fetcher.BeanMethodBatchFetcher;
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
import io.nop.xlang.xmeta.reflect.ReflectObjMetaParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.nop.graphql.core.GraphQLErrors.ARG_ACTION_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_ARG_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_METHOD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_ACTION_ARG_TYPE_NOT_OBJ_TYPE;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_BATCH_LOAD_METHOD_MUST_RETURN_LIST;

public class BizModelToGraphQLDefinition {
    public static final BizModelToGraphQLDefinition INSTANCE = new BizModelToGraphQLDefinition();

    public GraphQLFieldDefinition toOperationDefinition(String bizObjName, BizActionModel actionModel,
                                                        TypeRegistry typeRegistry, List<IActionDecoratorCollector> collectors,
                                                        IServiceActionArgBuilder thisObjBuilder) {

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
        action = BizObjectBuildHelper.decorateAction(action, actionModel, collectors);

        field.setServiceAction(action);
        if (action != null)
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

        IDataFetcher fetcher = buildFetcher(thisObjName, loaderModel, typeRegistry);
        field.setFetcher(fetcher);
        field.setAutoCreate(loaderModel.isAutoCreateField());
        return field;
    }

    public IServiceAction buildAction(BizActionModel actionModel, IServiceActionArgBuilder thisObjBuilder) {
        IEvalAction source = actionModel.getSource();
        if (source == null)
            return null;

        // 保持参数获取顺序
        Map<String, IServiceActionArgBuilder> argBuilders = new LinkedHashMap<>();
        for (BizActionArgModel arg : actionModel.getArgs()) {
            IServiceActionArgBuilder argBuilder = getArgBuilder(arg.getKind(), arg.getName(), arg.getType());
            argBuilders.put(arg.getName(), argBuilder);
        }
        argBuilders.put(GraphQLConstants.VAR_THIS_OBJ, thisObjBuilder);
        return new EvalServiceAction(source, argBuilders);
    }

    IDataFetcher buildFetcher(String bizObjName, BizLoaderModel loaderModel, TypeRegistry typeRegistry) {
        IEvalAction source = loaderModel.getSource();
        if (source == null)
            source = ctx -> null;

        Map<String, Function<IDataFetchingEnvironment, Object>> argBuilders = new LinkedHashMap<>();
        BizActionArgModel contextSourceArg = null;
        for (BizActionArgModel arg : loaderModel.getArgs()) {
            if (arg.getKind() == BizActionArgKind.ContextSource)
                contextSourceArg = arg;

            Function<IDataFetchingEnvironment, Object> argBuilder = getFetcherArg(arg.getKind(), arg.getName(),
                    arg.getType());
            argBuilders.put(arg.getName(), argBuilder);
        }

        if (contextSourceArg != null && contextSourceArg.getType() != null && contextSourceArg.getType().isCollectionLike()) {
            // 如果是集合类型的参数，则表示这是一个BatchLoader
            if (!loaderModel.isReturnList()) {
                throw new NopException(ERR_GRAPHQL_BATCH_LOAD_METHOD_MUST_RETURN_LIST).source(loaderModel)
                        .param(ARG_OBJ_NAME, bizObjName).param(ARG_METHOD_NAME, loaderModel.getName());
            }
            int sourceIndex = loaderModel.getArgs().indexOf(contextSourceArg);
            String loaderName = bizObjName + "@" + loaderModel.getName();

            List<Function<IDataFetchingEnvironment, Object>> argsList = loaderModel.getArgs()
                    .stream().map(arg -> argBuilders.get(arg.getName()))
                    .collect(Collectors.toList());

            return new BeanMethodBatchFetcher(loaderName, newFetcher(source, loaderModel), argsList, sourceIndex);
        }
        return new EvalActionDataFetcher(source, argBuilders);
    }

    private static BiFunction<Object[], IGraphQLExecutionContext, Object> newFetcher(IEvalAction source, BizLoaderModel loaderModel) {
        return (args, ctx) -> {
            IEvalScope scope = loaderModel.newEvalScope(args, ctx.getEvalScope());
            return source.invoke(scope);
        };
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
                return IDataFetchingEnvironment::getServiceContext;
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
                argDef.setMandatory(bizArg.isMandatory());
                argDef.setSchema(bizArg.getSchema());
                argDef.setJavaType(bizArg.getType());
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
                GraphQLArgumentDefinition argDef = buildArgDef(propName, graphqlType);
                ISchema schema = getFieldSchema(field);
                argDef.setMandatory(isFieldMandatory(field));
                argDef.setSchema(schema);
                argDef.setJavaType(field.getJavaType());
                argDefs.add(argDef);
            }
        } else {
            GraphQLInputDefinition objDef = (GraphQLInputDefinition) def;
            for (GraphQLInputFieldDefinition field : objDef.getFields()) {
                String propName = field.getName();
                GraphQLType graphQLType = field.getType().deepClone();
                GraphQLArgumentDefinition argDef = buildArgDef(propName, graphQLType);
                ISchema schema = getInputSchema(field);
                argDef.setMandatory(isInputMandatory(field));
                argDef.setSchema(schema);
                argDef.setJavaType(field.getJavaType());
                argDefs.add(argDef);
            }
        }
        return argDefs;
    }

    private boolean isFieldMandatory(GraphQLFieldDefinition field) {
        if (field.getPropMeta() != null) {
            return field.getPropMeta().isMandatory();
        }
        if (field.getBeanPropMeta() != null)
            return field.getBeanPropMeta().mandatory();
        return false;
    }

    ISchema getFieldSchema(GraphQLFieldDefinition field) {
        if (field.getPropMeta() != null) {
            ISchema schema = field.getPropMeta().getSchema();
            if (schema != null)
                return schema;
        }

        if (field.getBeanPropMeta() != null)
            return ReflectObjMetaParser.INSTANCE.buildSchemaFromPropMeta(field.getBeanPropMeta());

        return null;
    }

    private boolean isInputMandatory(GraphQLInputFieldDefinition field) {
        if (field.getPropMeta() != null) {
            return field.getPropMeta().isMandatory();
        }
        if (field.getBeanPropMeta() != null)
            return field.getBeanPropMeta().mandatory();
        return false;
    }

    ISchema getInputSchema(GraphQLInputFieldDefinition field) {
        if (field.getPropMeta() != null) {
            ISchema schema = field.getPropMeta().getSchema();
            if (schema != null)
                return schema;
        }

        if (field.getBeanPropMeta() != null)
            return ReflectObjMetaParser.INSTANCE.buildSchemaFromPropMeta(field.getBeanPropMeta());
        return null;
    }

    GraphQLType getType(String thisObjName, BizActionModel actionModel, TypeRegistry typeRegistry) {
        BizReturnModel returnModel = actionModel.getReturn();
        if (returnModel == null)
            return GraphQLTypeHelper.scalarType(GraphQLScalarType.Void);

        GraphQLType type = GraphQLTypeHelper.parseType(returnModel.getLocation(),
                (String) returnModel.prop_get(GraphQLConstants.ATTR_GRAPHQL_TYPE), typeRegistry);

        if (type != null) {
            if (returnModel.isMandatory())
                type = GraphQLTypeHelper.nonNullType(type);
            return type;
        }

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
        if (type != null) {
            if (argModel.isMandatory()) {
                type = GraphQLTypeHelper.nonNullType(type);
            }
            return type;
        }

        ISchema schema = argModel.getSchema();
        if (schema == null)
            return null;
        return ObjMetaToGraphQLDefinition.INSTANCE.toGraphQLType(thisObjName, schema, argModel.isMandatory(), registry, true);
    }
}