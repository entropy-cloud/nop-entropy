/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.impl;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.biz.api.ITenantBizModelProvider;
import io.nop.biz.crud.BizObjectQueryProcessorAdapter;
import io.nop.biz.decorator.IActionDecoratorCollector;
import io.nop.biz.makerchecker.IMakerCheckerProvider;
import io.nop.biz.model.BizActionModel;
import io.nop.biz.model.BizLoaderModel;
import io.nop.biz.model.BizModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreErrors;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.fsm.execution.StateMachine;
import io.nop.fsm.model.StateMachineModel;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.biz.IBizObjectQueryProcessor;
import io.nop.graphql.core.biz.IGraphQLBizInitializer;
import io.nop.graphql.core.reflection.GraphQLBizModel;
import io.nop.graphql.core.reflection.GraphQLBizModels;
import io.nop.graphql.core.reflection.ReflectionGraphQLTypeFactory;
import io.nop.graphql.core.schema.TypeRegistry;
import io.nop.graphql.core.schema.meta.ObjMetaToGraphQLDefinition;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_META_PATH;
import static io.nop.biz.BizErrors.ERR_BIZ_INVALID_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_MISSING_META_FILE_FOR_OBJ;
import static io.nop.biz.BizErrors.ERR_BIZ_STATE_MACHINE_NO_STATE_PROP;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_BIZ_OBJ_NAME;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_RESOURCE_NOT_EXISTS;

/**
 * 对于/nop/auth/model/NopAuthUser_admin.xbiz，允许三种情况 1. 存在NopAuthUser_admin.xbiz文件，可能存在xmeta文件 2.
 * 存在NopAuthUser.xbiz文件，且存在NopAuthUser_admin.xmeta文件 3. 不存在xbiz和xmeta文件，但是存在对应的DataLoader
 */
public class BizObjectBuilder {
    static final Logger LOG = LoggerFactory.getLogger(BizObjectImpl.class);
    private final GraphQLBizModels bizModels;
    private final GraphQLBizModels dynBizModels;

    private final ITenantBizModelProvider tenantBizModelProvider;
    private final TypeRegistry typeRegistry;

    private final List<IActionDecoratorCollector> collectors;

    private final List<IGraphQLBizInitializer> bizInitializers;
    private final IMakerCheckerProvider makerCheckerProvider;

    private final IBizObjectManager bizObjectManager;

    public BizObjectBuilder(IBizObjectManager bizObjectManager, GraphQLBizModels bizModels,
                            GraphQLBizModels dynBizModels,
                            ITenantBizModelProvider tenantBizModelProvider, TypeRegistry typeRegistry,
                            List<IActionDecoratorCollector> collectors,
                            List<IGraphQLBizInitializer> bizInitializers,
                            IMakerCheckerProvider makerCheckerProvider) {
        this.bizObjectManager = bizObjectManager;
        this.bizModels = bizModels;
        this.dynBizModels = dynBizModels;
        this.tenantBizModelProvider = tenantBizModelProvider;
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

        GraphQLObjectDefinition bizObjDef = null;
        GraphQLBizModel bizModel = null;
        if (bizModels != null) {
            bizModel = bizModels.getBizModel(bizObj.getBizObjName());
            if (bizModel != null) {
                bizObjDef = new GraphQLObjectDefinition();
                bizObjDef.setName(bizObj.getBizObjName());
                bizModel.mergeLoaderTo(bizObjDef, false);
            }
        }

        GraphQLObjectDefinition dslObjDef = buildBizLoaders(objDef.getName(), bizObj.getBizModel());
        if (dslObjDef != null) {
            if (bizObjDef == null) {
                bizObjDef = dslObjDef;
            } else {
                bizObjDef.merge(dslObjDef, true);
            }
        }

        if (bizObjDef != null) {
            // 以meta上的信息为准，不会覆盖meta中的配置
            objDef.merge(bizObjDef, false);
        }

        if (objDef.getFields() == null || objDef.getFields().isEmpty()) {
            if (bizObj.getBizModel() == null && bizModel == null)
                throw new NopException(ERR_BIZ_UNKNOWN_BIZ_OBJ_NAME).param(ARG_BIZ_OBJ_NAME, bizObj.getBizObjName());
        }

        bizObj.setObjectDefinition(objDef);
        if(bizModel != null && bizModel.getBizModelBeans() != null)
            bizObj.setBizModelBeans(bizModel.getBizModelBeans());

        buildOperations(bizObj, bizModel);
        // 删除所有meta中没有定义的字段。如果存在meta，则所有GraphQL返回数据以meta为准。
        objDef.removeFieldsNotInMeta();

        // 如果meta标记了not-pub，则直接清空类型字段定义，不对外暴露
        if (bizObj.getObjMeta() != null && bizObj.getObjMeta().containsTag(OrmModelConstants.TAG_NOT_PUB)) {
            objDef.setFields(new ArrayList<>(0));
        }

        if (objDef.getFields() == null)
            objDef.setFields(new ArrayList<>(0));

        if (bizInitializers != null) {
            for (IGraphQLBizInitializer initializer : bizInitializers) {
                initializer.initialize(bizObj, this::buildQueryProcessor, typeRegistry, bizModels);
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

        checkOperations(bizObj);

        // 如果没有字段定义，则不支持GraphQL selection语法返回数据
        if (objDef.getFields().isEmpty())
            bizObj.setObjectDefinition(null);

        return bizObj;
    }

    private void checkOperations(BizObjectImpl bizObj) {
        for (GraphQLFieldDefinition fieldDef : bizObj.getOperations().values()) {
            if (fieldDef.getServiceAction() == null) {
                LOG.info("nop.biz.operation-no-impl-action:bizObjName={},operationName={}",
                        bizObj.getBizObjName(), fieldDef.getOperationName());
            }
//                throw new NopException(ERR_BIZ_OPERATION_NO_IMPL_ACTION)
//                        .source(fieldDef).param(ARG_BIZ_OBJ_NAME, bizObj.getBizObjName())
//                        .param(ARG_OPERATION_NAME, fieldDef.getOperationName());
        }
    }

    private <T> IBizObjectQueryProcessor<T> buildQueryProcessor(String bizObjName) {
        return new BizObjectQueryProcessorAdapter<>(bizObjectManager, bizObjName);
    }

    BizObjectImpl loadBizObjFromModel(String bizObjName) {
        GraphQLBizModel gqlBizModel = bizModels.getBizModel(bizObjName);
        if (gqlBizModel == null && dynBizModels != null)
            gqlBizModel = dynBizModels.getBizModel(bizObjName);

        if (gqlBizModel == null && tenantBizModelProvider != null && ContextProvider.currentTenantId() != null)
            gqlBizModel = tenantBizModelProvider.getTenantBizModel(bizObjName);
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
        if (bizModel == null && gqlBizModel.getBizPath() != null)
            throw new NopException(CoreErrors.ERR_RESOURCE_NOT_EXISTS).param(ARG_RESOURCE_PATH, gqlBizModel.getBizPath());

        if (objMeta == null && gqlBizModel.getMetaPath() != null)
            throw new NopException(ERR_RESOURCE_NOT_EXISTS).param(ARG_RESOURCE_PATH, gqlBizModel.getMetaPath());
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

    protected BizModel loadBizModel(String bizPath) {
        IResource resource = VirtualFileSystem.instance().getResource(bizPath);
        return (BizModel) new DslModelParser(BizConstants.XDEF_BIZ).parseFromResource(resource, true);
    }

    protected IObjMeta loadObjMeta(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (resource.exists()) {
            return SchemaLoader.loadXMeta(path);
        }
        return null;
    }

    protected BizObjectImpl newBizObject(String bizObjName, BizModel bizModel, IObjMeta objMeta) {
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

    private GraphQLObjectDefinition buildBizLoaders(String bizObjName, BizModel bizModel) {
        if (bizModel == null)
            return null;

        GraphQLObjectDefinition objDef = new GraphQLObjectDefinition();
        objDef.setName(bizObjName);
        List<BizLoaderModel> loaders = bizModel.getLoaders();
        if (loaders != null) {
            for (BizLoaderModel loader : loaders) {
                GraphQLFieldDefinition field = BizModelToGraphQLDefinition.INSTANCE.toBuilder(objDef.getName(), loader,
                        typeRegistry);
                objDef.addField(field);
            }
        }
        return objDef;
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


        bizObj.setActions(actions);
        bizObj.setOperations(operations);

        if (gqlBizModel != null) {
            BizObjectBuildHelper.addDefaultAction(bizObj, gqlBizModel, collectors);
        }
    }

    GraphQLFieldDefinition buildActionOperation(BizObjectImpl bizObj, BizActionModel actionModel) {
        return BizModelToGraphQLDefinition.INSTANCE.toOperationDefinition(bizObj.getBizObjName(), actionModel,
                typeRegistry, collectors, (req, sel, ctx) -> bizObj);
    }

    IServiceAction buildServiceAction(BizObjectImpl bizObj, BizActionModel actionModel) {
        IServiceAction action = BizModelToGraphQLDefinition.INSTANCE.buildAction(actionModel, (req, sel, ctx) -> bizObj);
        if (action == null)
            return null;

        return BizObjectBuildHelper.decorateAction(action, actionModel, collectors);
    }
}