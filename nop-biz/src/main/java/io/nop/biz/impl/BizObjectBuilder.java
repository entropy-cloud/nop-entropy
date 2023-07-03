/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.impl;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.OrderedComparator;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.biz.makerchecker.IMakerCheckerProvider;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizLoaderModel;
import io.nop.biz.model.BizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.context.action.IServiceActionDecorator;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.fsm.execution.StateMachine;
import io.nop.fsm.model.StateMachineModel;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.biz.IGraphQLBizInitializer;
import io.nop.graphql.core.fetcher.ServiceActionFetcher;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.reflection.ReflectionGraphQLTypeFactory;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.schema.meta.ObjMetaToGraphQLDefinition;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.xlang.filter.BizExprHelper;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_META_PATH;
import static io.nop.biz.BizErrors.ERR_BIZ_INVALID_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_MISSING_META_FILE_FOR_OBJ;
import static io.nop.biz.BizErrors.ERR_BIZ_STATE_MACHINE_NO_STATE_PROP;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_BIZ_OBJ_NAME;

/**
 * 对于/nop/auth/model/NopAuthUser_admin.xbiz，允许三种情况 1. 存在NopAuthUser_admin.xbiz文件，可能存在xmeta文件 2.
 * 存在NopAuthUser.xbiz文件，且存在NopAuthUser_admin.xmeta文件 3. 不存在xbiz和xmeta文件，但是存在对应的DataLoader
 */
public class BizObjectBuilder {
    private final GraphQLBizModels bizModels;
    private final TypeRegistry typeRegistry;

    private final List<IActionDecoratorCollector> collectors;

    private final List<IGraphQLBizInitializer> bizInitializers;
    private final IMakerCheckerProvider makerCheckerProvider;

    public BizObjectBuilder(GraphQLBizModels bizModels, TypeRegistry typeRegistry,
                            List<IActionDecoratorCollector> collectors,
                            List<IGraphQLBizInitializer> bizInitializers,
                            IMakerCheckerProvider makerCheckerProvider) {
        this.bizModels = bizModels;
        this.typeRegistry = typeRegistry;
        this.collectors = collectors;
        this.bizInitializers = bizInitializers;
        this.makerCheckerProvider = makerCheckerProvider;
    }

    public IBizObject buildBizObject(String bizObjName) {
        BizObjectImpl bizObj = loadBizObjFromModel(bizObjName);
        GraphQLObjectDefinition objDef;
        if (bizObj.getObjMeta() != null) {
            objDef = new ObjMetaToGraphQLDefinition().toObjectDefinition(bizObj.getObjMeta(), bizObj.getBizObjName(),
                    typeRegistry);
        } else {
            Class<?> bizClass = typeRegistry.getBizObjClass(bizObj.getBizObjName());
            if (bizClass != null) {
                objDef = ReflectionGraphQLTypeFactory.INSTANCE.buildObjectDefinition(bizObj.getBizObjName(), bizClass,
                        typeRegistry);
            } else {
                objDef = new GraphQLObjectDefinition();
                objDef.setName(bizObj.getBizObjName());
            }
        }

        GraphQLBizModel bizModel = null;
        if (bizModels != null) {
            bizModel = bizModels.getBizModel(bizObj.getBizObjName());
            if (bizModel != null) {
                bizModel.mergeTo(objDef);
            }
        }

        if (objDef.getFields() == null || objDef.getFields().isEmpty()) {
            if (bizObj.getBizModel() == null && bizModel == null)
                throw new NopException(ERR_BIZ_UNKNOWN_BIZ_OBJ_NAME).param(ARG_BIZ_OBJ_NAME, bizObj.getBizObjName());
        }

        buildLoaders(objDef, bizObj.getBizModel());
        bizObj.setObjectDefinition(objDef);

        buildOperations(bizObj, bizModel);

        // 删除所有meta中没有定义的字段。如果存在meta，则所有GraphQL返回数据以meta为准。
        objDef.removeFieldsNotInMeta();

        if (objDef.getFields() == null)
            objDef.setFields(new ArrayList<>(0));

        String entityName = null;
        if (bizObj.getObjMeta() != null)
            entityName = bizObj.getObjMeta().getEntityName();

        if (bizInitializers != null) {
            for (IGraphQLBizInitializer initializer : bizInitializers) {
                initializer.initialize(objDef, entityName, this::resolveBizExpr);
            }
        }

        ObjectDefinitionExtProcessor.provideFetchers(objDef);
        ObjectDefinitionExtProcessor.initMakerChecker(bizObj, makerCheckerProvider);

        if (bizObj.getBizModel() != null && bizObj.getBizModel().getStateMachine() != null) {
            StateMachineModel stateMachineModel = bizObj.getBizModel().getStateMachine();
            if (stateMachineModel.getStateProp() != null) {
                bizObj.setStateMachine(new StateMachine(bizObj.getBizModel().getStateMachine()));
            } else {
                throw new NopException(ERR_BIZ_STATE_MACHINE_NO_STATE_PROP).source(stateMachineModel)
                        .param(ARG_BIZ_OBJ_NAME, bizObj.getBizObjName());
            }
        }

        // 如果没有字段定义，则不支持GraphQL selection语法返回数据
        if (objDef.getFields().isEmpty())
            bizObj.setObjectDefinition(null);

        return bizObj;
    }

    private void resolveBizExpr(QueryBean query, IDataFetchingEnvironment env) {
        BizExprHelper.resolveBizExpr(query.getFilter(), env.getExecutionContext());
    }

    BizObjectImpl loadBizObjFromModel(String bizObjName) {
        GraphQLBizModel gqlBizModel = bizModels.getBizModel(bizObjName);
        if (gqlBizModel == null)
            throw new NopException(ERR_BIZ_UNKNOWN_BIZ_OBJ_NAME).param(ARG_BIZ_OBJ_NAME, bizObjName);

        String basePath = gqlBizModel.getModelBasePath();

        String baseObjName = getBaseObjName(bizObjName);

        String bizPath = gqlBizModel.getBizPath();
        if (bizPath == null)
            bizPath = basePath + bizObjName + ".xbiz";

        String metaPath = gqlBizModel.getMetaPath();
        if (metaPath == null)
            metaPath = basePath + bizObjName + ".xmeta";

        // 当bizObjName对应的biz或者meta不存在的时候，会查找baseObjName对应的文件

        BizModel bizModel = loadBizModel(bizPath);
        IObjMeta objMeta = loadObjMeta(metaPath);
        if (bizModel != null) {
            if (objMeta == null && baseObjName != null) {
                metaPath = basePath + baseObjName + ".xmeta";
                objMeta = loadObjMeta(metaPath);
            }
        } else if (baseObjName != null) {
            // 如果bizObjName对应的biz文件不存在，检查是否存在基础对象的biz文件
            String baseModelPath = basePath + baseObjName + '.' + BizConstants.FILE_EXT_XBIZ;
            bizModel = loadBizModel(baseModelPath);
            if (bizModel != null && objMeta == null) {
                // 如果使用基础对象的biz模型，则必须存在对应于bizObjName的objMeta
                if (bizModel.getMetaDir() != null) {
                    metaPath = StringHelper.appendPath(bizModel.getMetaDir(), bizObjName + ".xmeta");
                    objMeta = loadObjMeta(metaPath);
                } else {
                    metaPath = basePath + bizObjName + ".xmeta";
                    objMeta = loadObjMeta(metaPath);
                }
                if (objMeta == null)
                    throw new NopException(ERR_BIZ_MISSING_META_FILE_FOR_OBJ).param(ARG_BIZ_OBJ_NAME, bizObjName)
                            .param(ARG_META_PATH, metaPath);
            }
        }
        return newBizObject(bizObjName, bizModel, objMeta);
    }

    /**
     * baseObjName一般对应数据库中的实体名。同一个数据库实体在不同的应用场景下可能分化为多个业务对象
     */
    String getBaseObjName(String bizObjName) {
        String baseName = GraphQLNameHelper.getBaseObjName(bizObjName);
        if (!StringHelper.isValidSimpleVarName(baseName))
            throw new NopException(ERR_BIZ_INVALID_BIZ_OBJ_NAME).param(ARG_BIZ_OBJ_NAME, bizObjName);
        return baseName;
    }

    BizModel loadBizModel(String bizPath) {
        IResource resource = VirtualFileSystem.instance().getResource(bizPath);
        return (BizModel) new DslModelParser(BizConstants.XDEF_BIZ).parseFromResource(resource, true);
    }

    IObjMeta loadObjMeta(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (resource.exists()) {
            return SchemaLoader.loadXMeta(path);
        }
        return null;
    }

    BizObjectImpl newBizObject(String bizObjName, BizModel bizModel, IObjMeta objMeta) {
        BizObjectImpl bo = new BizObjectImpl(bizObjName);
        bo.setBizModel(bizModel);
        bo.setObjMeta(objMeta);
        if (bizModel != null) {
            bo.setLocation(bizModel.getLocation());
        } else if (objMeta != null) {
            bo.setLocation(objMeta.getLocation());
        }
        return bo;
    }

    private void buildLoaders(GraphQLObjectDefinition objDef, BizModel bizModel) {
        if (bizModel == null)
            return;

        List<BizLoaderModel> loaders = bizModel.getLoaders();
        if (loaders != null) {
            for (BizLoaderModel loader : loaders) {
                GraphQLFieldDefinition field = BizModelToGraphQLDefinition.INSTANCE.toBuilder(objDef.getName(), loader,
                        typeRegistry);
                objDef.mergeField(field);
            }
        }
    }

    private void buildOperations(BizObjectImpl bizObj, GraphQLBizModel gqlBizModel) {
        Map<String, GraphQLFieldDefinition> operations = new HashMap<>();
        Map<String, IServiceAction> actions = new HashMap<>();
        BizModel bizModel = bizObj.getBizModel();
        if (bizModel != null) {
            for (BizActionModel actionModel : bizModel.getActions()) {
                GraphQLOperationType opType = actionModel.getOperationType();
                if (opType == GraphQLOperationType.action) {
                    actions.put(actionModel.getName(), buildServiceAction(bizObj, actionModel));
                } else {
                    GraphQLFieldDefinition op = buildActionOperation(bizObj, actionModel);
                    operations.put(actionModel.getName(), op);
                }
            }
        }

        if (gqlBizModel != null) {
            gqlBizModel.getMutationActions().forEach((name, action) -> {
                buildFetcher(action);
                // 如果xbiz文件中已经定义，则忽略java类上的定义
                operations.putIfAbsent(name, action);
            });

            gqlBizModel.getQueryActions().forEach((name, action) -> {
                buildFetcher(action);
                operations.putIfAbsent(name, action);
            });

            gqlBizModel.getBizActions().forEach((name, action) -> {
                // 如果xbiz文件中已经定义，则忽略java类上的定义
                actions.putIfAbsent(name, action);
            });
        }

        bizObj.setActions(actions);
        bizObj.setOperations(operations);
    }

    private void buildFetcher(GraphQLFieldDefinition field) {
        List<IServiceActionDecorator> decorators = buildDecorators(field.getFunctionModel());
        IServiceAction action = field.getServiceAction();
        for (IServiceActionDecorator decorator : decorators) {
            action = decorator.decorate(action);
        }
        field.setFetcher(new ServiceActionFetcher(action));
    }

    GraphQLFieldDefinition buildActionOperation(BizObjectImpl bizObj, BizActionModel actionModel) {
        return BizModelToGraphQLDefinition.INSTANCE.toOperationDefinition(bizObj.getBizObjName(), actionModel,
                typeRegistry, collectors, (req, sel, ctx) -> bizObj);
    }

    IServiceAction buildServiceAction(BizObjectImpl bizObj, BizActionModel actionModel) {
        return BizModelToGraphQLDefinition.INSTANCE.buildAction(actionModel, (req, sel, ctx) -> bizObj);
    }

    private List<IServiceActionDecorator> buildDecorators(IFunctionModel func) {
        List<IServiceActionDecorator> decorators = new ArrayList<>();
        if (collectors != null) {
            for (IActionDecoratorCollector collector : collectors) {
                collector.collectDecorator(func, decorators);
            }
            Collections.sort(decorators, OrderedComparator.instance());
        }
        return decorators;
    }
}