/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizMakerChecker;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.graphql.GraphQLReturn;
import io.nop.api.core.auth.IDataAuthChecker;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.utils.AuthHelper;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.action.IServiceAction;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.api.IQueryTransformer;
import io.nop.dao.exceptions.UnknownEntityException;
import io.nop.dao.utils.DaoHelper;
import io.nop.fsm.execution.IStateMachine;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.utils.OrmQueryHelper;
import io.nop.xlang.filter.BizExprHelper;
import io.nop.xlang.filter.BizFilterEvaluator;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.impl.ObjKeyModel;
import io.nop.xlang.xmeta.impl.ObjTreeModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH;
import static io.nop.auth.api.AuthApiErrors.ERR_AUTH_NO_DATA_AUTH_AFTER_UPDATE;
import static io.nop.biz.BizConstants.ACTION_ARG_ENTITY;
import static io.nop.biz.BizConstants.BIZ_OBJ_NAME_THIS_OBJ;
import static io.nop.biz.BizConstants.METHOD_FIND_FIRST;
import static io.nop.biz.BizConstants.METHOD_FIND_LIST;
import static io.nop.biz.BizConstants.METHOD_FIND_PAGE;
import static io.nop.biz.BizConstants.METHOD_TRY_DELETE;
import static io.nop.biz.BizConstants.METHOD_TRY_SAVE;
import static io.nop.biz.BizConstants.METHOD_TRY_UPDATE;
import static io.nop.biz.BizConstants.TAG_DICT;
import static io.nop.biz.BizErrors.ARG_ACTION_NAME;
import static io.nop.biz.BizErrors.ARG_CLASS_NAME;
import static io.nop.biz.BizErrors.ARG_ENTITY_NAME;
import static io.nop.biz.BizErrors.ARG_ID;
import static io.nop.biz.BizErrors.ARG_KEY;
import static io.nop.biz.BizErrors.ARG_OBJ_LABEL;
import static io.nop.biz.BizErrors.ARG_PARAM_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_EMPTY_DATA_FOR_SAVE;
import static io.nop.biz.BizErrors.ERR_BIZ_EMPTY_DATA_FOR_UPDATE;
import static io.nop.biz.BizErrors.ERR_BIZ_ENTITY_ALREADY_EXISTS;
import static io.nop.biz.BizErrors.ERR_BIZ_ENTITY_NOT_MATCH_FILTER_CONDITION;
import static io.nop.biz.BizErrors.ERR_BIZ_ENTITY_NOT_SUPPORT_LOGICAL_DELETE;
import static io.nop.biz.BizErrors.ERR_BIZ_ENTITY_WITH_SAME_KEY_ALREADY_EXISTS;
import static io.nop.biz.BizErrors.ERR_BIZ_NOT_ALLOW_DELETE_PARENT_WHEN_CHILDREN_IS_NOT_EMPTY;
import static io.nop.biz.BizErrors.ERR_BIZ_NO_BIZ_MODEL_ANNOTATION;
import static io.nop.biz.BizErrors.ERR_BIZ_NO_MANDATORY_PARAM;
import static io.nop.biz.BizErrors.ERR_BIZ_OBJ_NO_DICT_TAG;
import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_MAX_PAGE_SIZE;

@Locale("zh-CN")
public abstract class CrudBizModel<T extends IOrmEntity> {
    static final Logger LOG = LoggerFactory.getLogger(CrudBizModel.class);

    private IDaoProvider daoProvider;
    private String entityName;
    private String bizObjName;

    private IBizObjectManager bizObjectManager;

    /**
     * 自定义的query转换
     */
    private IQueryTransformer queryTransformer;

    private List<CascadePropMeta> cascadeProps;

    public void setQueryTransformer(IQueryTransformer queryTransformer) {
        this.queryTransformer = queryTransformer;
    }

    @Inject
    public void setBizObjectManager(IBizObjectManager bizObjectManager) {
        this.bizObjectManager = bizObjectManager;
    }

    public String getBizObjName() {
        if (bizObjName == null) {
            BizModel bizModel = getClass().getAnnotation(BizModel.class);
            if (bizModel == null)
                throw new NopException(ERR_BIZ_NO_BIZ_MODEL_ANNOTATION).param(ARG_CLASS_NAME, getClass().getName());

            bizObjName = bizModel.value();
        }
        return bizObjName;
    }

    public IBizObject getThisObj() {
        return bizObjectManager.getBizObject(getBizObjName());
    }

    public IDaoProvider daoProvider() {
        return daoProvider;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityName() {
        return entityName;
    }

    public IEntityDao<T> dao() {
        return daoProvider.dao(getEntityName());
    }

    public <R extends IOrmEntity> IEntityDao<R> daoFor(Class<R> clazz) {
        return daoProvider.daoFor(clazz);
    }

    public IOrmTemplate orm() {
        IEntityDao<T> dao = dao();
        return ((IOrmEntityDao<T>) dao).getOrmTemplate();
    }

    @Description("@i18n:biz.findPage|分页查询")
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<T> findPage(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                                FieldSelectionBean selection, IServiceContext context) {
        if (query != null)
            query.setDisableLogicalDelete(false);

        return doFindPage(query, this::defaultPrepareQuery, selection, context);
    }

    @BizAction
    public PageBean<T> doFindPage(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                                  @Name("prepareQuery") BiConsumer<QueryBean, IServiceContext> prepareQuery, FieldSelectionBean selection,
                                  IServiceContext context) {
        query = prepareFindPageQuery(query, METHOD_FIND_PAGE, context);
        if (prepareQuery != null) {
            prepareQuery.accept(query, context);
        }

        IEntityDao<T> dao = dao();

        PageBean<T> pageBean = new PageBean<>();
        pageBean.setLimit(query.getLimit());
        pageBean.setOffset(query.getOffset());
        pageBean.setTotal(-1L);

        if (selection != null && selection.hasField(GraphQLConstants.FIELD_TOTAL)) {
            long total = dao.countByQuery(query);
            pageBean.setTotal(total);
        }

        if (selection == null || selection.hasField(GraphQLConstants.FIELD_ITEMS)
                || selection.hasField(GraphQLConstants.FIELD_NEXT_CURSOR)) {
            if (!StringHelper.isEmpty(query.getCursor())) {
                dao.findPageAndReturnCursor(query, pageBean);
            } else {
                List<T> ret = dao.findPageByQuery(query);
                pageBean.setItems(ret);
            }
        }
        return pageBean;
    }

    @BizAction
    protected QueryBean prepareFindPageQuery(@Name("query") QueryBean query, @Name("action") String action,
                                             IServiceContext context) {
        checkAllowQuery(query, getThisObj().getObjMeta());

        query = AuthHelper.appendFilter(context.getDataAuthChecker(), query, bizObjName, action,
                context);
        if (query == null)
            query = new QueryBean();

        if (query.getName() == null) {
            query.setName(getBizObjName() + '.' + action);
        }

        IObjMeta objMeta = getThisObj().getObjMeta();
        int maxPageSize = CFG_GRAPHQL_MAX_PAGE_SIZE.get();
        if (objMeta != null) {
            if (objMeta.getFilter() != null) {
                query.addFilter(objMeta.getFilter().cloneInstance());
            }
            maxPageSize = ConvertHelper.toPrimitiveInt(objMeta.prop_get(BizConstants.EXT_MAX_PAGE_SIZE), maxPageSize,
                    NopException::new);

            if (objMeta.getOrderBy() != null) {
                query.addOrderBy(objMeta.getOrderBy());
            }
        }

        if (query.getLimit() <= 0) {
            query.setLimit(10);
        }

        if (query.getLimit() > maxPageSize) {
            query.setLimit(maxPageSize);
        }

        appendOrderByPk(query);

        if (queryTransformer != null)
            queryTransformer.transform(query);

        BizExprHelper.resolveBizExpr(query.getFilter(), context);
        return query;
    }

    protected void checkAllowQuery(QueryBean query, IObjMeta objMeta) {
        if (objMeta != null && query != null) {
            if (query.getFilter() != null)
                new ObjMetaBasedFilterValidator(objMeta, bizObjectManager).visit(query.getFilter(), DisabledEvalScope.INSTANCE);

            if (query.getOrderBy() != null) {
                for (OrderFieldBean field : query.getOrderBy()) {
                    String name = field.getName();
                    BizObjMetaHelper.checkPropSortable(getBizObjName(), objMeta, name, bizObjectManager);
                }
            }
        }
    }

    protected void appendOrderByPk(QueryBean query) {
        IEntityDao<T> dao = dao();
        List<OrderFieldBean> orderBy = query.getOrderBy();
        IObjMeta objMeta = getThisObj().getObjMeta();
        // 总是追加具有唯一性的排序条件，保证分页结果的确定性
        if (objMeta == null || !OrmQueryHelper.containsAnyKey(orderBy, objMeta.getKeys())) {
            boolean desc = false;
            orderBy = OrmQueryHelper.appendOrderByPk(orderBy, dao.getPkColumnNames(), desc);
            query.setOrderBy(orderBy);
        }
    }

    @Description("@i18n:biz.findFirst|返回符合条件的第一条数据")
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T findFirst(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                       @Name("selection") FieldSelectionBean selection, IServiceContext context) {
        if (query != null)
            query.setDisableLogicalDelete(false);
        return doFindFirst(query, this::defaultPrepareQuery, selection, context);
    }

    @BizAction
    public T doFindFirst(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                         @Name("prepareQuery") BiConsumer<QueryBean, IServiceContext> prepareQuery,
                         FieldSelectionBean selection, IServiceContext context) {
        query = prepareFindFirstQuery(query, METHOD_FIND_FIRST, context);
        if (prepareQuery != null) {
            prepareQuery.accept(query, context);
        }
        T ret = dao().findFirstByQuery(query);
        return ret;
    }

    @BizAction
    protected QueryBean prepareFindFirstQuery(@Name("query") QueryBean query, @Name("action") String action,
                                              IServiceContext context) {
        query = prepareFindPageQuery(query, action, context);
        query.setLimit(1);
        return query;
    }

    @BizAction
    protected void defaultPrepareQuery(@Name("query") QueryBean query, IServiceContext context) {

    }

    @Description("@i18n:biz.save|保存数据")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    @BizMakerChecker(tryMethod = METHOD_TRY_SAVE)
    public T save(@Name("data") Map<String, Object> data, IServiceContext context) {
        return doSave(data, null, this::defaultPrepareSave, context);
    }

    @BizAction
    public T doSave(@Name("data") Map<String, Object> data, @Name("inputSelection") FieldSelectionBean inputSelection,
                    @Name("prepareSave") BiConsumer<EntityData<T>, IServiceContext> prepareSave, IServiceContext context) {
        if (CollectionHelper.isEmptyMap(data))
            throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_SAVE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        EntityData<T> entityData = buildEntityDataForSave(data, inputSelection, context);
        checkUniqueForSave(entityData);

        new OrmEntityCopier(daoProvider, bizObjectManager).copyToEntity(entityData.getValidatedData(),
                entityData.getEntity(), null, entityData.getObjMeta(), getBizObjName(),
                BizConstants.METHOD_SAVE, context.getEvalScope());

        checkDataAuth(BizConstants.METHOD_SAVE, entityData.getEntity(), context);

        if (prepareSave != null)
            prepareSave.accept(entityData, context);

        doSaveEntity(entityData, context);

        return entityData.getEntity();
    }

    @BizAction
    protected void checkUniqueForSave(@Name("entityData") EntityData<T> entityData) {
        IObjMeta objMeta = entityData.getObjMeta();
        if (objMeta.getKeys() != null) {
            Map<String, Object> data = entityData.getValidatedData();
            IEntityDao<T> dao = dao();
            for (ObjKeyModel keyModel : objMeta.getKeys()) {
                Set<String> props = keyModel.getProps();
                if (data.keySet().containsAll(props)) {
                    T example = dao.newEntity();
                    List<Object> keys = new ArrayList<>();
                    for (String propName : props) {
                        Object value = data.get(propName);
                        example.orm_propValueByName(propName, value);
                        keys.add(value);
                    }
                    T existing = dao.findFirstByExample(example);
                    if (existing != null && existing != entityData.getEntity()) {
                        throw new NopException(ERR_BIZ_ENTITY_WITH_SAME_KEY_ALREADY_EXISTS)
                                .param(ARG_KEY, StringHelper.join(keys, ",")).param(ARG_ENTITY_NAME, getEntityName())
                                .param(ARG_BIZ_OBJ_NAME, getBizObjName());
                    }
                }
            }
        }
    }

    @BizAction
    public void trySave(@Name("data") Map<String, Object> data, FieldSelectionBean selection, IServiceContext context) {
        if (CollectionHelper.isEmptyMap(data))
            throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_SAVE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        EntityData<T> entityData = buildEntityDataForSave(data, selection, context);

        defaultPrepareSave(entityData, context);
    }

    @BizAction
    protected EntityData<T> buildEntityDataForSave(@Name("data") Map<String, Object> data,
                                                   @Name("inputSelection") FieldSelectionBean inputSelection, IServiceContext context) {
        IBizObject bizObj = getThisObj();
        IObjMeta objMeta = bizObj.requireObjMeta();

        ObjMetaBasedValidator validator = new ObjMetaBasedValidator(bizObjectManager, bizObj.getBizObjName(), objMeta,
                context, true);

        Map<String, Object> validated = validator.validateForSave(data, inputSelection);

        IEntityDao<T> dao = dao();
        T entity = null;
        boolean recover = false;
        if (dao.isUseLogicalDelete()) {
            // 如果是逻辑删除后再次添加同样主键的对象
            Object id = getId(data, dao);
            if (id != null) {
                id = dao.castId(id);
                entity = dao.getEntityById(id);
                if (entity != null) {
                    int delFlag = ConvertHelper.toPrimitiveInt(entity.orm_propValueByName(dao.getDeleteFlagProp()), 0,
                            NopException::new);
                    if (delFlag != 1) {
                        throw new NopException(ERR_BIZ_ENTITY_ALREADY_EXISTS).param(ARG_ENTITY_NAME, getEntityName())
                                .param(ARG_ID, id);
                    }

                    dao.resetToDefaultValues(entity);
                    recover = true;
                }
            }
        }
        if (entity == null) {
            entity = dao.newEntity();
        }
        EntityData entityData = new EntityData<>(data, validated, entity, objMeta);
        entityData.setRecoverDeleted(recover);
        return entityData;
    }

    Object getId(Map<String, Object> data, IEntityDao<T> dao) {
        List<String> colNames = dao.getPkColumnNames();
        if (colNames.size() == 1) {
            return data.get(colNames.get(0));
        }

        List<Object> values = new ArrayList<>(colNames.size());
        for (String colName : colNames) {
            Object value = data.get(colName);
            if (StringHelper.isEmptyObject(value))
                return null;
            values.add(value);
        }
        return values;
    }

    @BizAction
    protected void checkDataAuth(@Name("action") String action, @Name("entity") T entity, IServiceContext context) {
        IDataAuthChecker dataAuthChecker = context.getDataAuthChecker();
        if (dataAuthChecker == null)
            return;

        String bizObjName = getBizObjName();
        if (!dataAuthChecker.isPermitted(bizObjName, action, entity, context)) {
            throw new NopException(ERR_AUTH_NO_DATA_AUTH).param(ARG_BIZ_OBJ_NAME, bizObjName);
        }
    }

    @BizAction
    protected void checkDataAuthAfterUpdate(@Name("entity") T entity, IServiceContext context) {
        IDataAuthChecker dataAuthChecker = context.getDataAuthChecker();
        if (dataAuthChecker == null)
            return;

        String bizObjName = getBizObjName();
        if (!dataAuthChecker.isPermitted(bizObjName, BizConstants.METHOD_UPDATE, entity, context)) {
            throw new NopException(ERR_AUTH_NO_DATA_AUTH_AFTER_UPDATE).param(ARG_BIZ_OBJ_NAME, bizObjName);
        }
    }

    @BizAction
    protected void defaultPrepareSave(@Name("entityData") EntityData<T> entityData, IServiceContext context) {
        IStateMachine stm = getThisObj().getStateMachine();
        if (stm != null) {
            stm.initState(entityData.getEntity());
        }
    }

    @BizAction
    protected void doSaveEntity(@Name("entityData") EntityData<T> entityData, IServiceContext context) {
        if (entityData.isRecoverDeleted()) {
            dao().updateEntity(entityData.getEntity());
        } else {
            dao().saveEntity(entityData.getEntity());
        }
        afterEntityChange(entityData.getEntity(), context);
    }

    @BizAction
    protected void afterEntityChange(@Name("entity") T entity, IServiceContext context) {

    }

    protected void invokeAction(String actionName, Map<String, Object> args, IServiceContext context,
                                BizInvocation inv) {
        IBizObject bizObject = getThisObj();
        IServiceAction action = bizObject.getAction(actionName);
        if (action != null) {
            args.put(BizConstants.ACTION_ARG_INVOCATION, inv);
            action.invoke(args, null, context);
        } else {
            inv.proceed();
        }
    }

    @Description("@i18n:biz.update|更新数据")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    @BizMakerChecker(tryMethod = METHOD_TRY_UPDATE)
    public T update(@Name("data") Map<String, Object> data, IServiceContext context) {
        return doUpdate(data, null, this::defaultPrepareUpdate, context);
    }

    @BizAction
    public T doUpdate(@Name("data") Map<String, Object> data,
                      @Name("inputSelection") FieldSelectionBean inputSelection,
                      @Name("prepareUpdate") BiConsumer<EntityData<T>, IServiceContext> prepareUpdate, IServiceContext context) {
        if (CollectionHelper.isEmptyMap(data))
            throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_UPDATE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        EntityData<T> entityData = buildEntityDataForUpdate(data, inputSelection, context);

        new OrmEntityCopier(daoProvider, bizObjectManager).copyToEntity(entityData.getValidatedData(),
                entityData.getEntity(), null, entityData.getObjMeta(), getBizObjName(),
                BizConstants.METHOD_UPDATE, context.getEvalScope());

        if (prepareUpdate != null)
            prepareUpdate.accept(entityData, context);

        checkDataAuthAfterUpdate(entityData.getEntity(), context);

        doUpdateEntity(entityData, context);

        return entityData.getEntity();
    }

    @BizAction
    public void tryUpdate(@Name("data") Map<String, Object> data,
                          @Name("inputSelection") FieldSelectionBean inputSelection, IServiceContext context) {
        if (CollectionHelper.isEmptyMap(data))
            throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_UPDATE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        buildEntityDataForUpdate(data, inputSelection, context);
    }

    @BizAction
    protected T requireEntity(String id, String action, IServiceContext context) {
        return getEntity(id, action, false, context);
    }

    @BizAction
    protected T getEntity(String id, String action, boolean ignoreUnknown,
                          IServiceContext context) {
        IBizObject bizObj = getThisObj();
        IObjMeta objMeta = bizObj.requireObjMeta();

        IEntityDao<T> dao = dao();
        T entity = dao.getEntityById(id);
        if (entity == null) {
            if (ignoreUnknown)
                return null;
            throw new UnknownEntityException(dao.getEntityName(), id);
        }

        checkDataAuth(action, entity, context);
        checkMetaFilter(entity, objMeta, context);
        return entity;
    }

    @BizAction
    protected EntityData<T> buildEntityDataForUpdate(@Name("data") Map<String, Object> data,
                                                     @Name("inputSelection") FieldSelectionBean inputSelection, IServiceContext context) {
        IBizObject bizObj = getThisObj();
        IObjMeta objMeta = bizObj.requireObjMeta();

        Object id = data.get(OrmConstants.PROP_ID);

        T entity = requireEntity(ConvertHelper.toString(id), BizConstants.METHOD_UPDATE, context);

        ObjMetaBasedValidator validator = new ObjMetaBasedValidator(bizObjectManager, bizObj.getBizObjName(), objMeta,
                context, true);

        Map<String, Object> validated = validator.validateForUpdate(data, inputSelection);

        return new EntityData<>(data, validated, entity, objMeta);
    }

    protected void checkMetaFilter(T entity, IObjMeta objMeta, IServiceContext context) {
        if (objMeta != null && objMeta.getFilter() != null) {
            boolean b = new BizFilterEvaluator(context).testForEntity(objMeta.getFilter(), entity);

            if (!b)
                throw new NopException(ERR_BIZ_ENTITY_NOT_MATCH_FILTER_CONDITION)
                        .param(ARG_BIZ_OBJ_NAME, getBizObjName()).param(ARG_ID, entity.orm_id())
                        .param(ACTION_ARG_ENTITY, entity);
        }
    }

    @BizAction
    protected void defaultPrepareUpdate(@Name("entityData") EntityData<T> entityData, IServiceContext context) {
    }

    @BizAction
    protected void doUpdateEntity(@Name("entityData") EntityData<T> entityData, IServiceContext context) {
        dao().updateEntity(entityData.getEntity());
        afterEntityChange(entityData.getEntity(), context);
    }

    @Description("@i18n:biz.get|根据id获取单条数据")
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T get(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id,
                 @Name("ignoreUnknown") @Description("@i18n:biz.ignoreUnknown|未找到对象时是返回null还是抛出异常") boolean ignoreUnknown,
                 IServiceContext context) {
        checkMandatoryParam("get", "id", id);

        T entity = getEntity(id, BizConstants.METHOD_GET, ignoreUnknown, context);
        return entity;
    }

    @Description("@i18n:biz.batchGet|根据主键批量获取对象")
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<T> batchGet(@Name("ids") Set<String> ids, IServiceContext context) {
        if (CollectionHelper.isEmpty(ids))
            return Collections.emptyList();

        IEntityDao<T> dao = dao();
        List<T> list = dao.batchGetEntitiesByIds(ids);
        if (list.isEmpty()) {
            return list;
        }

        for (T entity : list) {
            checkMetaFilter(entity, getThisObj().getObjMeta(), context);
            checkDataAuth(BizConstants.METHOD_GET, entity, context);
        }
        return list;
    }

    protected void checkMandatoryParam(String actionName, String paramName, Object value) {
        if (StringHelper.isEmptyObject(value))
            throw new NopException(ERR_BIZ_NO_MANDATORY_PARAM).param(ARG_ACTION_NAME, actionName)
                    .param(ARG_PARAM_NAME, paramName).param(ARG_BIZ_OBJ_NAME, getBizObjName());
    }

    @Description("@i18n:biz.delete|根据主键删除指定对象")
    @BizMutation
    @BizMakerChecker(tryMethod = METHOD_TRY_DELETE)
    public boolean delete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id, IServiceContext context) {
        return doDelete(id, this::defaultPrepareDelete, context);
    }

    @BizAction
    protected boolean doDelete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id,
                               @Name("prepareDelete") BiConsumer<T, IServiceContext> prepareDelete, IServiceContext context) {
        checkMandatoryParam("delete", "id", id);

        T entity = dao().getEntityById(id);
        if (entity == null)
            return true;

        checkMetaFilter(entity, getThisObj().getObjMeta(), context);
        checkDataAuth(BizConstants.METHOD_DELETE, entity, context);

        if (prepareDelete != null) {
            prepareDelete.accept(entity, context);
        }

        doDeleteEntity(entity, context);
        return true;
    }

    @BizAction
    protected void defaultPrepareDelete(@Name("entity") T entity, IServiceContext context) {
        IObjMeta objMeta = getThisObj().getObjMeta();
        if (objMeta != null) {
            ObjTreeModel tree = objMeta.getTree();
            if (tree != null) {
                if (tree.getChildrenProp() != null) {
                    IObjPropMeta prop = objMeta.getProp(tree.getChildrenProp());
                    // 如果不是递归删除children，则需要检查children不存在
                    if (prop != null && !prop.containsTag(BizConstants.TAG_CASCADE_DELETE)) {
                        Collection<?> children = (Collection<?>) entity.orm_propValueByName(prop.getName());
                        if (children != null && !children.isEmpty()) {
                            String dispProp = objMeta.getDisplayProp();
                            if (dispProp == null) {
                                dispProp = OrmConstants.PROP_ID;
                            }
                            throw new NopException(ERR_BIZ_NOT_ALLOW_DELETE_PARENT_WHEN_CHILDREN_IS_NOT_EMPTY)
                                    .param(ARG_BIZ_OBJ_NAME, getBizObjName()).param(ARG_ID, entity.orm_idString())
                                    .param(ARG_OBJ_LABEL, entity.orm_propValueByName(dispProp));
                        }
                    }
                }
            }
        }
    }

    @BizAction
    protected void doDeleteEntity(@Name("entity") T entity, IServiceContext context) {
        // 先标记实体被删除，避免递归删除的时候出现死循环
        dao().deleteEntity(entity);
        deleteReferences(entity, context);
        afterEntityChange(entity, context);
    }

    /**
     * 删除所有关联对象
     */
    protected void deleteReferences(@Name("entity") T entity, IServiceContext context) {
        IOrmBatchLoadQueue loadQueue = orm().requireSession().getBatchLoadQueue();
        boolean empty = loadQueue.isEmpty();

        for (CascadePropMeta prop : getCascadeProps()) {
            if (prop.isCascadeDelete()) {
                Object value = BizObjHelper.getProp(entity, prop.getPropMeta(), context);
                if (value == null)
                    continue;

                IBizObject refBizObj = bizObjectManager.getBizObject(prop.getRefBizObjName());

                if (value instanceof IOrmEntity) {
                    IOrmEntity refEntity = (IOrmEntity) value;
                    // 已经标记为被删除或者不存在的记录不需要再进行进一步的处理
                    if (refEntity.orm_state().isGone()) {
                        LOG.info("nop.orm.delete-skip-entity-already-gone:entity={}", refEntity);
                        continue;
                    }

                    loadQueue.enqueue(refEntity);
                    loadQueue.afterFlush(() -> {
                        Map<String, Object> req = new HashMap<>();
                        req.put(GraphQLConstants.ARG_ID, refEntity.orm_idString());
                        refBizObj.invoke(BizConstants.METHOD_DELETE, req, null, context);
                    });
                } else if (value instanceof IOrmEntitySet) {
                    Collection<IOrmEntity> c = (Collection<IOrmEntity>) value;
                    loadQueue.enqueueMany(c);
                    loadQueue.afterFlush(() -> {
                        for (IOrmEntity refEntity : c) {
                            if (refEntity.orm_state().isGone())
                                continue;

                            Map<String, Object> req = new HashMap<>();
                            req.put(GraphQLConstants.ARG_ID, refEntity.orm_idString());
                            refBizObj.invoke(BizConstants.METHOD_DELETE, req, null, context);
                        }
                    });
                }
            }
        }

        // 如果不为空，则表示由外部调用者负责flush
        if (empty)
            loadQueue.flush();
    }

    protected List<CascadePropMeta> getCascadeProps() {
        if (cascadeProps == null) {
            this.cascadeProps = BizSchemaHelper.getCascadeProps(getThisObj().getObjMeta());
        }
        return cascadeProps;
    }

    @BizAction
    public void tryDelete(@Name("id") String id, IServiceContext context) {
        checkMandatoryParam("tryDelete", "id", id);

        this.requireEntity(id, BizConstants.METHOD_DELETE, context);
    }

    @Description("@i18n:biz.batchUpdate|批量修改")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data,
                            IServiceContext context) {
        if (CollectionHelper.isEmpty(ids) || CollectionHelper.isEmptyMap(data))
            return;

        dao().batchGetEntitiesByIds(ids);
        for (String id : ids) {
            Map<String, Object> copy = new LinkedHashMap<>(data);
            copy.put(GraphQLConstants.PROP_ID, id);
            update(copy, context);
        }
    }

    @Description("@i18n:biz.batchDelete|根据主键批量删除对象")
    @BizMutation
    public Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context) {
        if (CollectionHelper.isEmpty(ids))
            return Collections.emptySet();

        List<T> entities = dao().batchGetEntitiesByIds(ids);
        Set<String> ret = new LinkedHashSet<>();
        for (T entity : entities) {
            if (entity.orm_state().isMissing()) {
                ret.add(entity.orm_idString());
            } else {
                delete(entity.orm_idString(), context);
            }
        }
        return ret;
    }

    @Description("@i18n:biz.batchModify|批量增删改")
    @BizMutation
    public void batchModify(@Name("data") List<Map<String, Object>> data,
                            @Name("delIds") @Description("@i18n:biz.delIds|待删除的实体主键列表") Set<String> delIds, IServiceContext context) {
        if (data != null) {
            List<Object> idList = new ArrayList<>();
            for (Map<String, Object> item : data) {
                Object id = item.get(OrmConstants.PROP_ID);
                if (!StringHelper.isEmptyObject(id)) {
                    idList.add(id);
                }
            }
            // 预加载数据
            dao().batchGetEntitiesByIds(idList);

            for (Map<String, Object> item : data) {
                Object id = item.get(OrmConstants.PROP_ID);
                String chgType = DaoHelper.getChangeType(item);
                if (StringHelper.isEmptyObject(id) || DaoConstants.CHANGE_TYPE_ADD.equals(chgType)) {
                    save(item, context);
                } else if (DaoConstants.CHANGE_TYPE_DELETE.equals(chgType)) {
                    delete(StringHelper.toString(id, null), context);
                } else {
                    // 具有id属性，且没有标记为A/U，则是update
                    update(item, context);
                }
            }
        }
        batchDelete(delIds, context);
    }

    @Description("@i18n:biz.saveOrUpdate|如果没有id就修改记录，否则就新增记录")
    @BizMutation
    public T save_update(@Name("data") Map<String, Object> data, IServiceContext context) {
        if (CollectionHelper.isEmptyMap(data))
            throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_UPDATE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        T result;
        Object id = data.get(OrmConstants.PROP_ID);
        if (StringHelper.isEmptyObject(id) || DaoConstants.CHANGE_TYPE_ADD.equals(DaoHelper.getChangeType(data))) {
            result = save(data, context);
        } else {
            // 具有id属性，且没有标记为_forAdd，则是update
            result = update(data, context);
        }
        return result;
    }

    @Description("@i18n:biz.deleted_findPage|分页查询已删除记录")
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<T> deleted_findPage(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                                        FieldSelectionBean selection, IServiceContext context) {
        if (query == null) {
            query = new QueryBean();
        }
        query.setDisableLogicalDelete(true);
        String deleteFlagProp = dao().getDeleteFlagProp();
        if (StringHelper.isEmpty(deleteFlagProp))
            throw new NopException(ERR_BIZ_ENTITY_NOT_SUPPORT_LOGICAL_DELETE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        query.addFilter(FilterBeans.eq(deleteFlagProp, 1));

        return doFindPage(query, this::defaultPrepareQuery, selection, context);
    }

    @Description("@i18n:biz.recoverDeleted|恢复已删除记录")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T recoverDeleted(@Name("id") String id, IServiceContext context) {
        String deleteFlagProp = dao().getDeleteFlagProp();
        if (StringHelper.isEmpty(deleteFlagProp))
            throw new NopException(ERR_BIZ_ENTITY_NOT_SUPPORT_LOGICAL_DELETE).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        T entity = dao().requireEntityById(id);
        checkMetaFilter(entity, getThisObj().getObjMeta(), context);

        checkDataAuth(BizConstants.METHOD_UPDATE, entity, context);

        entity.orm_propValueByName(deleteFlagProp, 0);

        dao().updateEntity(entity);
        return entity;
    }

    @Description("@i18n:biz.asDict|将实体记录作为字典项返回")
    @BizQuery
    public DictBean asDict(IServiceContext context) {
        IObjMeta objMeta = getThisObj().requireObjMeta();
        if (!objMeta.containsTag(TAG_DICT))
            throw new NopException(ERR_BIZ_OBJ_NO_DICT_TAG).param(ARG_BIZ_OBJ_NAME, getBizObjName());

        DictBean dict = new DictBean();
        dict.setNormalized(true);
        QueryBean query = new QueryBean();
        query.setLimit(CFG_GRAPHQL_MAX_PAGE_SIZE.get());
        PageBean<T> pageBean = findPage(query, FieldSelectionBean.fromProp(GraphQLConstants.FIELD_ITEMS), context);
        List<DictOptionBean> options = new ArrayList<>(pageBean.getItems().size());

        String labelProp = objMeta.getDisplayProp();
        if (labelProp == null) {
            labelProp = GraphQLConstants.PROP_ID;
        }
        for (T entity : pageBean.getItems()) {
            DictOptionBean option = new DictOptionBean();
            option.setValue(entity.orm_idString());
            Object value = entity.orm_propValueByName(labelProp);
            option.setLabel(StringHelper.toString(value, ""));
            options.add(option);
        }
        dict.setOptions(options);
        return dict;
    }

    @Description("@i18n:biz.findList|根据查询条件返回列表数据。与findPage的不同在于,findPage返回PageBean类型，支持分页，而这个函数返回List类型，而且缺省不分页")
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<T> findList(@Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context) {
        if (query != null)
            query.setDisableLogicalDelete(false);
        return doFindList(query, this::defaultPrepareQuery, selection, context);
    }

    @BizAction
    public List<T> doFindList(@Name("query") QueryBean query,
                              @Name("prepareQuery") BiConsumer<QueryBean, IServiceContext> prepareQuery,
                              FieldSelectionBean selection,
                              IServiceContext context) {
        if (query == null)
            query = new QueryBean();

        if (query.getLimit() <= 0)
            query.setLimit(CFG_GRAPHQL_MAX_PAGE_SIZE.get());

        query = prepareFindPageQuery(query, METHOD_FIND_LIST, context);
        if (prepareQuery != null) {
            prepareQuery.accept(query, context);
        }

        List<T> ret = dao().findPageByQuery(query);
        return ret;
    }

    @Description("@i18n:biz.copyForNew|复制新建")
    @BizMutation
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public T copyForNew(@Name("data") Map<String, Object> data, IServiceContext context) {
        if (CollectionHelper.isEmptyMap(data))
            throw new NopException(ERR_BIZ_EMPTY_DATA_FOR_SAVE).param(ARG_BIZ_OBJ_NAME, getBizObjName());
        return doCopyForNew(data, BizConstants.SELECTION_COPY_FOR_NEW, context);
    }

    protected T doCopyForNew(@Name("data") Map<String, Object> data, @Name("copySelection") String copySelection, IServiceContext context) {
        Object id = data.get(OrmConstants.PROP_ID);

        IEntityDao<T> dao = dao();
        T entity = dao.requireEntityById(id);
        IObjMeta objMeta = getThisObj().requireObjMeta();
        checkMetaFilter(entity, objMeta, context);

        FieldSelectionBean inputSelection = objMeta.getFieldSelection(copySelection);
        EntityData<T> entityData = buildEntityDataForSave(data, inputSelection, context);
        entityData.getValidatedData().remove(OrmConstants.PROP_ID);

        T newEntity;
        if (inputSelection != null) {
            newEntity = dao.newEntity();
            new OrmEntityCopier(daoProvider, bizObjectManager).copyToEntity(entity,
                    newEntity, inputSelection, entityData.getObjMeta(), getBizObjName(),
                    BizConstants.METHOD_SAVE, context.getEvalScope());
        } else {
            newEntity = (T) entity.cloneInstance();
        }

        new OrmEntityCopier(daoProvider, bizObjectManager).copyToEntity(entityData.getValidatedData(),
                newEntity, inputSelection, entityData.getObjMeta(), getBizObjName(),
                BizConstants.METHOD_SAVE, context.getEvalScope());

        this.doSaveEntity(entityData, context);
        return newEntity;
    }

    protected <R extends IOrmEntity> void removeRelation(Class<R> relationClass,
                                                         String leftProp, String rightProp,
                                                         Object leftValue, Object rightValue) {
        IEntityDao<R> dao = daoFor(relationClass);

        R example = dao.newEntity();
        example.orm_propValueByName(leftProp, leftValue);
        example.orm_propValueByName(rightProp, rightValue);
        R rel = dao.findFirstByExample(example);
        if (rel != null) {
            dao.deleteEntity(rel);
        }
    }

    protected <R extends IOrmEntity> void removeRelations(Class<R> relationClass,
                                                          String leftProp, String rightProp,
                                                          Object leftValue, Collection<?> rightValues) {
        IEntityDao<R> dao = daoFor(relationClass);

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(leftProp, leftValue));
        query.addFilter(FilterBeans.in(rightProp, rightValues));
        List<R> relations = dao.findAllByQuery(query);
        dao.batchDeleteEntities(relations);
    }

    protected <R extends IOrmEntity> void addRelations(Class<R> relationClass,
                                                       String leftProp, String rightProp,
                                                       Object leftValue, Collection<?> rightValues) {
        Map<String, Object> fixedProps = new HashMap<>();
        fixedProps.put(leftProp, leftValue);
        updateRelations(relationClass, fixedProps, null, false, rightProp, rightValues);
    }

    protected <R extends IOrmEntity> void updateRelations(Class<R> relationClass,
                                                          String leftProp, String rightProp,
                                                          Object leftValue, Collection<?> rightValues) {
        Map<String, Object> fixedProps = new HashMap<>();
        fixedProps.put(leftProp, leftValue);
        updateRelations(relationClass, fixedProps, null, true, rightProp, rightValues);
    }

    protected <R extends IOrmEntity> void updateRelations(Class<R> relationClass,
                                                          Map<String, Object> fixedProps,
                                                          Predicate<R> filter,
                                                          boolean deleteUnknown,
                                                          String relProp, Collection<?> relValues) {
        IEntityDao<R> dao = daoFor(relationClass);

        if (relValues == null) {
            relValues = Collections.emptyList();
        }

        R example = dao.newEntity();
        for (Map.Entry<String, Object> entry : fixedProps.entrySet()) {
            example.orm_propValueByName(entry.getKey(), entry.getValue());
        }

        List<R> relations = dao.findAllByExample(example);
        relValues = CollectionHelper.toStringList(relValues);

        for (R relation : relations) {
            if (filter != null && !filter.test(relation))
                continue;

            String relValue = ConvertHelper.toString(relation.orm_propValueByName(relProp));
            if (!relValues.remove(relValue)) {
                if (deleteUnknown)
                    dao.deleteEntity(relation);
            }
        }

        for (Object relValue : relValues) {
            R relation = dao.newEntity();
            for (Map.Entry<String, Object> entry : fixedProps.entrySet()) {
                relation.orm_propValueByName(entry.getKey(), entry.getValue());
            }
            relation.orm_propValueByName(relProp, relValue);
            dao.saveEntity(relation);
        }
    }
}