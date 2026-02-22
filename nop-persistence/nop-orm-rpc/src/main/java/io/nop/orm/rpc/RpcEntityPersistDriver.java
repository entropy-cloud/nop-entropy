/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.collections.IntArray;
import io.nop.dao.api.IEntityDaoExtension;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.utils.DaoHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.session.IOrmSessionImplementor;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * 基于 IRpcServiceInvoker 的实体持久化驱动。
 * 通过 RPC 调用远程服务来加载和保存实体数据。
 */
public class RpcEntityPersistDriver implements IEntityPersistDriver, IEntityDaoExtension<IOrmEntity> {
    private IEntityModel entityModel;
    private String querySpace;
    private IRpcServiceInvoker rpcServiceInvoker;
    private String serviceName;

    private IOrmTemplate ormTemplate;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    @Inject
    public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
        this.rpcServiceInvoker = rpcServiceInvoker;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void init(IEntityModel entityModel, IPersistEnv env) {
        this.entityModel = entityModel;
        this.querySpace = DaoHelper.normalizeQuerySpace(entityModel.getQuerySpace());
    }

    @Override
    public <T> T getExtension(Class<T> clazz) {
        if (clazz == IEntityDaoExtension.class)
            return (T) this;
        return null;
    }

    @Override
    public IOrmEntity findLatest(ShardSelection selection, IOrmEntity entity, IOrmSessionImplementor session) {
        return null;
    }

    /**
     * 构建实体操作的方法名
     */
    private String newEntityAction(String methodName) {
        return entityModel.getShortName() + "__" + methodName;
    }

    /**
     * 调用 RPC 服务，支持传递 selection
     */
    private CompletionStage<ApiResponse<?>> invokeRpc(String serviceMethod, Map<String, Object> data,
                                                      FieldSelectionBean selection) {
        ApiRequest<Map<String, Object>> request = ApiRequest.build(data);
        if (selection != null && selection.hasField()) {
            request.setSelection(selection);
        }
        return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null);
    }

    /**
     * 调用 RPC 服务（无 selection）
     */
    private CompletionStage<ApiResponse<?>> invokeRpc(String serviceMethod, Map<String, Object> data) {
        return invokeRpc(serviceMethod, data, null);
    }

    /**
     * 检查响应，如果不是成功则抛出异常
     */
    private void checkResponse(ApiResponse<?> response) {
        if (!response.isOk()) {
            throw NopRebuildException.rebuild(response);
        }
    }

    protected FieldSelectionBean newSelection(IntArray propIds, FieldSelectionBean subSelection) {
        FieldSelectionBean selection = new FieldSelectionBean();
        if (propIds != null) {
            for (int propId : propIds) {
                String propName = entityModel.getColumnByPropId(propId, false).getName();
                selection.addField(propName);
            }
        }

        // 合并 subSelection，但只包含使用相同 driver 的关联属性
        if (subSelection != null && subSelection.hasField()) {
            filterSubSelection(selection, entityModel, subSelection);
        }
        return selection;
    }

    /**
     * 递归过滤 subSelection，只保留使用相同 driver 的关联属性
     */
    private void filterSubSelection(FieldSelectionBean selection, IEntityModel currentEntityModel,
                                    FieldSelectionBean subSelection) {
        String currentDriver = currentEntityModel.getPersistDriver();

        for (Map.Entry<String, FieldSelectionBean> entry : subSelection.getFields().entrySet()) {
            String propName = entry.getKey();
            FieldSelectionBean fieldSelection = entry.getValue();

            IEntityPropModel propModel = currentEntityModel.getProp(propName, true);
            if (propModel != null && propModel.isRelationModel()) {
                // 关联属性：检查关联实体的 driver 是否与当前 driver 一致
                IEntityRelationModel relModel = (IEntityRelationModel) propModel;
                IEntityModel refEntityModel = relModel.getRefEntityModel();
                if (refEntityModel != null) {
                    String refDriver = refEntityModel.getPersistDriver();
                    if (isSameDriver(currentDriver, refDriver)) {
                        // driver 一致，递归过滤子字段后添加
                        FieldSelectionBean filteredField = filterFieldSelection(refEntityModel, fieldSelection);
                        if (filteredField != null) {
                            selection.addField(propName, filteredField);
                        }
                    }
                    // driver 不一致，不下推，由 ORM 框架通过各自的 driver 加载
                }
            } else {
                // 非关联属性，直接添加
                selection.addField(propName, fieldSelection);
            }
        }
    }

    /**
     * 递归过滤单个字段的 selection
     * 
     * @param refEntityModel 关联实体模型
     * @param fieldSelection 字段选择
     * @return 过滤后的字段选择，如果没有有效字段则返回 null
     */
    private FieldSelectionBean filterFieldSelection(IEntityModel refEntityModel, FieldSelectionBean fieldSelection) {
        if (fieldSelection == null || !fieldSelection.hasField()) {
            // 没有子字段，返回原始选择（表示只选择该属性本身）
            return fieldSelection;
        }

        // 创建新的过滤后的选择
        FieldSelectionBean filtered = new FieldSelectionBean();

        // 复制 args 和 directives
        if (fieldSelection.getArgs() != null && !fieldSelection.getArgs().isEmpty()) {
            filtered.addArgs(fieldSelection.getArgs());
        }
        if (fieldSelection.getDirectives() != null && !fieldSelection.getDirectives().isEmpty()) {
            filtered.addDirectives(fieldSelection.getDirectives());
        }
        if (fieldSelection.getName() != null) {
            filtered.setName(fieldSelection.getName());
        }

        // 递归过滤子字段
        String currentDriver = refEntityModel.getPersistDriver();
        for (Map.Entry<String, FieldSelectionBean> entry : fieldSelection.getFields().entrySet()) {
            String subPropName = entry.getKey();
            FieldSelectionBean subFieldSelection = entry.getValue();

            IEntityPropModel subPropModel = refEntityModel.getProp(subPropName, true);
            if (subPropModel != null && subPropModel.isRelationModel()) {
                // 子属性也是关联属性，检查 driver
                IEntityRelationModel subRelModel = (IEntityRelationModel) subPropModel;
                IEntityModel subRefEntityModel = subRelModel.getRefEntityModel();
                if (subRefEntityModel != null) {
                    String subRefDriver = subRefEntityModel.getPersistDriver();
                    if (isSameDriver(currentDriver, subRefDriver)) {
                        // driver 一致，递归过滤
                        FieldSelectionBean filteredSubField = filterFieldSelection(subRefEntityModel, subFieldSelection);
                        if (filteredSubField != null) {
                            filtered.addField(subPropName, filteredSubField);
                        }
                    }
                    // driver 不一致，跳过
                }
            } else {
                // 非关联属性，直接添加
                filtered.addField(subPropName, subFieldSelection);
            }
        }

        // 如果过滤后没有任何字段，返回原始 fieldSelection（保持选择该属性本身）
        if (!filtered.hasField()) {
            return fieldSelection.isSimpleField() ? fieldSelection : new FieldSelectionBean();
        }

        return filtered;
    }

    /**
     * 判断两个 driver 是否相同
     */
    private boolean isSameDriver(String driver1, String driver2) {
        if (driver1 == null && driver2 == null)
            return true;
        if (driver1 == null || driver2 == null)
            return false;
        return driver1.equals(driver2);
    }

    protected FieldSelectionBean newSelection(IntArray propIds) {
        return newSelection(propIds, null);
    }

    @Override
    public CompletionStage<Void> loadAsync(ShardSelection shard, IOrmEntity entity, IntArray propIds,
                                           FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        String operationName = newEntityAction("batchGet");
        FieldSelectionBean selection = newSelection(propIds, subSelection);

        Map<String, Object> data = new HashMap<>();
        data.put("id", entity.orm_idString());

        return invokeRpc(operationName, data, selection).thenAccept(response -> {
            checkResponse(response);
            Map<String, Object> result = (Map<String, Object>) response.getData();
            if (result != null) {
                bindEntity(entity, result, propIds, session);
                // 处理 subSelection 中的子表数据
                if (subSelection != null && subSelection.hasField()) {
                    bindSubSelection(entity, result, subSelection, session);
                }
            }
        });
    }

    @Override
    public boolean lock(ShardSelection shard, IOrmEntity entity, IntArray propIds, Runnable unlockCallback, IOrmSessionImplementor session) {
        return false;
    }

    @Override
    public CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace,
                                                   List<IBatchAction.EntitySaveAction> saveActions,
                                                   List<IBatchAction.EntityUpdateAction> updateActions,
                                                   List<IBatchAction.EntityDeleteAction> deleteActions,
                                                   IOrmSessionImplementor session) {
        if (topoAsc) {
            List<Object> data = buildData(saveActions, updateActions);
            if (data == null || data.isEmpty())
                return null;

            Map<String, Object> args = new HashMap<>();
            args.put("data", data);

            return invokeRpc(newEntityAction("batchModify"), args).thenApply(response -> null);
        } else {
            if (deleteActions == null || deleteActions.isEmpty())
                return null;

            List<String> ids = deleteActions.stream()
                    .map(IBatchAction.EntityDeleteAction::getIdString)
                    .collect(Collectors.toList());

            Map<String, Object> args = new HashMap<>();
            args.put("ids", ids);

            return invokeRpc(newEntityAction("batchDelete"), args).thenApply(response -> null);
        }
    }

    private List<Object> buildData(List<IBatchAction.EntitySaveAction> saveActions,
                                   List<IBatchAction.EntityUpdateAction> updateActions) {
        if (saveActions == null && updateActions == null)
            return null;

        List<Object> data = new ArrayList<>();
        if (saveActions != null) {
            for (IBatchAction.EntitySaveAction action : saveActions) {
                data.add(action.getEntity().orm_initedValues());
            }
        }

        if (updateActions != null) {
            for (IBatchAction.EntityUpdateAction action : updateActions) {
                Map<String, Object> map = action.getEntity().orm_dirtyNewValues();
                map.put(OrmConstants.PROP_ID, action.getEntity().orm_idString());
                data.add(map);
            }
        }
        return data;
    }

    private String getQuerySpace(ShardSelection shard) {
        if (shard == null)
            return querySpace;
        return shard.getQuerySpace();
    }

    @Override
    public CompletionStage<Void> batchLoadAsync(ShardSelection shard, Collection<IOrmEntity> entities, IntArray propIds,
                                                FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        String operationName = newEntityAction("batchGet");
        FieldSelectionBean selection = newSelection(propIds, subSelection);

        List<String> ids = entities.stream().map(IOrmEntity::orm_idString).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("ids", ids);

        return invokeRpc(operationName, data, selection).thenAccept(response -> {
            checkResponse(response);
            List<Map<String, Object>> list = (List<Map<String, Object>>) response.getData();
            if (list != null) {
                int i = 0;
                for (IOrmEntity entity : entities) {
                    Map<String, Object> map = list.get(i++);
                    bindEntity(entity, map, propIds, session);
                    // 处理 subSelection 中的子表数据
                    if (subSelection != null && subSelection.hasField()) {
                        bindSubSelection(entity, map, subSelection, session);
                    }
                }
            }
        });
    }

    /**
     * 绑定实体属性值，使用 session.internalAssemble 确保正确设置实体状态
     */
    private void bindEntity(IOrmEntity entity, Map<String, Object> map, IntArray propIds,
                            IOrmSessionImplementor session) {
        Object[] values = new Object[propIds.size()];
        int i = 0;
        for (int propId : propIds) {
            String propName = entity.orm_propName(propId);
            values[i++] = map.get(propName);
        }
        session.internalAssemble(entity, values, propIds);
    }

    /**
     * 处理 subSelection 中的子表/关联数据
     * 只有当关联实体使用相同的 driver 时才处理
     */
    private void bindSubSelection(IOrmEntity entity, Map<String, Object> map,
                                  FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        bindSubSelection(entity, entityModel, map, subSelection, session);
    }

    /**
     * 递归处理 subSelection 中的子表/关联数据
     */
    private void bindSubSelection(IOrmEntity entity, IEntityModel currentEntityModel, Map<String, Object> map,
                                  FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        if (subSelection == null || !subSelection.hasField() || map == null)
            return;

        String currentDriver = currentEntityModel.getPersistDriver();

        for (Map.Entry<String, FieldSelectionBean> entry : subSelection.getFields().entrySet()) {
            String propName = entry.getKey();
            FieldSelectionBean fieldSelection = entry.getValue();

            Object value = map.get(propName);
            if (value == null)
                continue;

            // 获取属性模型，判断是否为关联属性
            IEntityPropModel propModel = currentEntityModel.getProp(propName, true);
            if (propModel == null || !propModel.isRelationModel())
                continue;

            // 检查关联实体的 driver 是否与当前 driver 一致
            IEntityRelationModel relModel = (IEntityRelationModel) propModel;
            IEntityModel refEntityModel = relModel.getRefEntityModel();
            if (refEntityModel == null)
                continue;

            String refDriver = refEntityModel.getPersistDriver();
            if (!isSameDriver(currentDriver, refDriver)) {
                // driver 不一致，不处理，由 ORM 框架通过各自的 driver 加载
                continue;
            }

            // driver 一致，处理关联数据（递归传递 fieldSelection）
            if (relModel.isToManyRelation()) {
                // ToMany 关联：数据是 List<Map>
                bindToManyRelation(entity, propName, refEntityModel, value, fieldSelection, session);
            } else {
                // ToOne 关联：数据是单个 Map
                bindToOneRelation(entity, propName, refEntityModel, value, fieldSelection, session);
            }
        }
    }

    /**
     * 处理 ToOne 关联数据（支持递归处理子字段）
     */
    private void bindToOneRelation(IOrmEntity entity, String propName, IEntityModel refEntityModel,
                                   Object value, FieldSelectionBean fieldSelection, IOrmSessionImplementor session) {
        if (!(value instanceof Map))
            return;

        Map<String, Object> refMap = (Map<String, Object>) value;
        IntArray refPropIds = refEntityModel.getEagerLoadProps();

        // 从 Map 中读取关联实体数据并组装
        IOrmEntity refEntity = OrmAssembly.assemble(refMap, refEntityModel, refPropIds, session);
        if (refEntity != null) {
            entity.orm_propValueByName(propName, refEntity);

            // 递归处理子字段
            if (fieldSelection != null && fieldSelection.hasField()) {
                bindSubSelection(refEntity, refEntityModel, refMap, fieldSelection, session);
            }
        }
    }

    /**
     * 处理 ToMany 关联数据（支持递归处理子字段）
     */
    private void bindToManyRelation(IOrmEntity entity, String propName, IEntityModel refEntityModel,
                                    Object value, FieldSelectionBean fieldSelection, IOrmSessionImplementor session) {
        if (!(value instanceof List))
            return;

        List<Map<String, Object>> refList = (List<Map<String, Object>>) value;
        if (refList.isEmpty())
            return;

        // 获取关联集合
        IOrmEntitySet<IOrmEntity> coll = entity.orm_refEntitySet(propName);
        IntArray refPropIds = refEntityModel.getEagerLoadProps();

        coll.orm_beginLoad();
        try {
            for (Map<String, Object> refMap : refList) {
                // 从 Map 中读取关联实体数据并组装
                IOrmEntity refEntity = OrmAssembly.assemble(refMap, refEntityModel, refPropIds, session);
                if (refEntity != null) {
                    coll.orm_internalAdd(refEntity);

                    // 递归处理子字段
                    if (fieldSelection != null && fieldSelection.hasField()) {
                        bindSubSelection(refEntity, refEntityModel, refMap, fieldSelection, session);
                    }
                }
            }
        } finally {
            coll.orm_endLoad();
        }
    }

    @Override
    public <T extends IOrmEntity> List<T> findPageByExample(ShardSelection shard, T example,
                                                            List<OrderFieldBean> orderBy,
                                                            long offset, int limit, IOrmSessionImplementor session) {

        QueryBean query = DaoQueryHelper.buildQueryFromExample(example);
        query.setOrderBy(orderBy);
        query.setOffset(offset);
        query.setLimit(limit);

        return findList(query, session);
    }

    private <T extends IOrmEntity> void initEntities(List<T> ret, List<Map<String, Object>> list,
                                                     IOrmSessionImplementor session) {
        IntArray propIds = entityModel.getEagerLoadProps();

        for (Map<String, Object> map : list) {
            IOrmEntity entity = OrmAssembly.assemble(map, entityModel, propIds, session);
            ret.add((T) entity);
        }
    }

    @Override
    public <T extends IOrmEntity> List<T> findAllByExample(ShardSelection shard, T example,
                                                           List<OrderFieldBean> orderBy, IOrmSessionImplementor session) {
        QueryBean query = DaoQueryHelper.buildQueryFromExample(example);
        query.setOrderBy(orderBy);
        return findAll(query);
    }

    @Override
    public long deleteByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        QueryBean query = DaoQueryHelper.buildQueryFromExample(example);
        return delete(query);
    }

    @Override
    public IOrmEntity findFirstByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        QueryBean query = DaoQueryHelper.buildQueryFromExample(example);
        return findFirst(query, session);
    }

    public IOrmEntity findFirst(QueryBean query, IOrmSessionImplementor session) {
        String operationName = newEntityAction("findFirst");
        FieldSelectionBean selection = newSelection(entityModel.getEagerLoadProps());

        Map<String, Object> data = new HashMap<>();
        data.put("query", query);

        CompletableFuture<IOrmEntity> ret = new CompletableFuture<>();

        invokeRpc(operationName, data, selection).whenComplete((response, err) -> {
            if (err != null) {
                ret.completeExceptionally(err);
            } else {
                try {
                    checkResponse(response);
                    Map<String, Object> result = (Map<String, Object>) response.getData();
                    if (result != null) {
                        IOrmEntity entity = OrmAssembly.assemble(result, entityModel,
                                entityModel.getEagerLoadProps(), session);
                        ret.complete(entity);
                    } else {
                        ret.complete(null);
                    }
                } catch (Exception e) {
                    ret.completeExceptionally(e);
                }
            }
        });

        return FutureHelper.syncGet(ret);
    }

    @Override
    public long countByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
        QueryBean query = DaoQueryHelper.buildQueryFromExample(example);

        return count(query);
    }

    @Override
    public long updateByExample(ShardSelection shard, IOrmEntity example,
                                IOrmEntity updated, IOrmSessionImplementor session) {
        QueryBean query = DaoQueryHelper.buildQueryFromExample(example);
        Map<String, Object> propValues = updated.orm_initedValues();

        return update(query, propValues);
    }

    @Override
    public boolean supportReadQuery(QueryBean query) {
        return true;
    }

    @Override
    public boolean supportUpdateQuery(QueryBean query) {
        return true;
    }

    @Override
    public boolean supportDeleteQuery(QueryBean query) {
        return true;
    }

    @Override
    public boolean supportForEachQuery(QueryBean query) {
        return true;
    }

    @Override
    public boolean supportFindNext(ITreeBean filter, List orderBy) {
        return true;
    }

    private <T extends IOrmEntity> List<T> findList(QueryBean query, IOrmSessionImplementor session) {
        String operationName = newEntityAction("findList");
        FieldSelectionBean selection = newSelection(entityModel.getEagerLoadProps());

        Map<String, Object> data = new HashMap<>();
        data.put("query", query);

        CompletableFuture<List<T>> ret = new CompletableFuture<>();

        invokeRpc(operationName, data, selection).whenComplete((response, err) -> {
            if (err != null) {
                ret.completeExceptionally(err);
            } else {
                try {
                    checkResponse(response);
                    List<T> list = new ArrayList<>();
                    List<Map<String, Object>> result = (List<Map<String, Object>>) response.getData();
                    if (result != null) {
                        initEntities(list, result, session);
                    }
                    ret.complete(list);
                } catch (Exception e) {
                    ret.completeExceptionally(e);
                }
            }
        });

        return FutureHelper.syncGet(ret);
    }

    @Override
    public List<IOrmEntity> findPage(QueryBean query) {
        return ormTemplate.runInSession(session -> {
            return findList(query, (IOrmSessionImplementor) session);
        });
    }

    @Override
    public IOrmEntity findFirst(QueryBean query) {
        return ormTemplate.runInSession(session -> {
            return findFirst(query, (IOrmSessionImplementor) session);
        });
    }

    @Override
    public <V> List<V> findAll(QueryBean query) {
        return ormTemplate.runInSession(session -> {
            String operationName = newEntityAction("findAll");
            FieldSelectionBean selection = newSelection(entityModel.getEagerLoadProps());

            Map<String, Object> data = new HashMap<>();
            data.put("query", query);

            CompletableFuture<List<V>> ret = new CompletableFuture<>();

            invokeRpc(operationName, data, selection).whenComplete((response, err) -> {
                if (err != null) {
                    ret.completeExceptionally(err);
                } else {
                    try {
                        checkResponse(response);
                        List<V> list = new ArrayList<>();
                        List<Map<String, Object>> result = (List<Map<String, Object>>) response.getData();
                        if (result != null) {
                            initEntities((List<IOrmEntity>) list, result, (IOrmSessionImplementor) session);
                        }
                        ret.complete(list);
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }
            });

            return FutureHelper.syncGet(ret);
        });
    }

    @Override
    public List<IOrmEntity> findNext(IOrmEntity lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit) {
        QueryBean query = new QueryBean();
        if (lastEntity != null)
            query.setCursor(lastEntity.orm_idString());
        if (filter != null)
            query.setFilter(filter.toTreeBean());
        query.setOrderBy(orderBy);
        query.setLimit(limit);

        return findPage(query);
    }

    @Override
    public List<IOrmEntity> findPrev(IOrmEntity lastEntity, ITreeBean filter, List<OrderFieldBean> orderBy, int limit) {
        QueryBean query = new QueryBean();
        if (lastEntity != null)
            query.setCursor(lastEntity.orm_idString());
        if (filter != null)
            query.setFilter(filter.toTreeBean());
        query.setFindPrev(true);
        query.setOrderBy(orderBy);
        query.setLimit(limit);

        return findPage(query);
    }


    @Override
    public long count(QueryBean query) {
        return ormTemplate.runInSession(session -> {
            String operationName = newEntityAction("count");

            Map<String, Object> data = new HashMap<>();
            data.put("query", query);

            CompletableFuture<Long> ret = new CompletableFuture<>();

            invokeRpc(operationName, data).whenComplete((response, err) -> {
                if (err != null) {
                    ret.completeExceptionally(err);
                } else {
                    try {
                        checkResponse(response);
                        Number result = (Number) response.getData();
                        ret.complete(result != null ? result.longValue() : 0L);
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }
            });

            return FutureHelper.syncGet(ret);
        });
    }

    @Override
    public boolean exists(QueryBean query) {
        return ormTemplate.runInSession(session -> {
            String operationName = newEntityAction("exists");

            Map<String, Object> data = new HashMap<>();
            data.put("query", query);

            CompletableFuture<Boolean> ret = new CompletableFuture<>();

            invokeRpc(operationName, data).whenComplete((response, err) -> {
                if (err != null) {
                    ret.completeExceptionally(err);
                } else {
                    try {
                        checkResponse(response);
                        Boolean result = (Boolean) response.getData();
                        ret.complete(Boolean.TRUE.equals(result));
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }
            });

            return FutureHelper.syncGet(ret);
        });
    }

    @Override
    public long delete(QueryBean query) {
        return ormTemplate.runInSession(session -> {
            String operationName = newEntityAction("delete");

            Map<String, Object> data = new HashMap<>();
            data.put("query", query);

            CompletableFuture<Long> ret = new CompletableFuture<>();

            invokeRpc(operationName, data).whenComplete((response, err) -> {
                if (err != null) {
                    ret.completeExceptionally(err);
                } else {
                    try {
                        checkResponse(response);
                        Number result = (Number) response.getData();
                        ret.complete(result != null ? result.longValue() : 0L);
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }
            });

            return FutureHelper.syncGet(ret);
        });
    }

    @Override
    public long update(QueryBean query, Map<String, Object> props) {
        return ormTemplate.runInSession(session -> {
            String operationName = newEntityAction("update");

            Map<String, Object> data = new HashMap<>();
            data.put("query", query);
            data.put("data", props);

            CompletableFuture<Long> ret = new CompletableFuture<>();

            invokeRpc(operationName, data).whenComplete((response, err) -> {
                if (err != null) {
                    ret.completeExceptionally(err);
                } else {
                    try {
                        checkResponse(response);
                        Number result = (Number) response.getData();
                        ret.complete(result != null ? result.longValue() : 0L);
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }
            });

            return FutureHelper.syncGet(ret);
        });
    }
}
