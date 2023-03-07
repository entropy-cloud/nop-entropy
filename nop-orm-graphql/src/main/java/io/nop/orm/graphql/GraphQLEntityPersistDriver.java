/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.graphql;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.collections.IntArray;
import io.nop.dao.api.IEntityDaoExtension;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.utils.DaoHelper;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.utils.GraphQLRequestBuilder;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmTemplate;
import io.nop.orm.OrmConstants;
import io.nop.orm.dao.DaoQueryHelper;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.persister.IBatchAction;
import io.nop.orm.persister.IBatchActionQueue;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.session.IOrmSessionImplementor;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class GraphQLEntityPersistDriver implements IEntityPersistDriver, IEntityDaoExtension<IOrmEntity> {
    private IEntityModel entityModel;
    private String querySpace;
    private IHttpClient httpClient;
    private String serviceUrl;

    private IOrmTemplate ormTemplate;

    @Inject
    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
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

    private GraphQLTask makeTask(IOrmSessionImplementor session, String querySpace, String operationType) {
        IBatchActionQueue queue = session.getBatchActionQueue(querySpace);
        GraphQLTask task = (GraphQLTask) queue.getDelayTask(querySpace);
        if (task == null) {
            task = newTask(operationType);
            queue.addDelayTask(querySpace, task);
        }
        return task;
    }

    private GraphQLTask newTask(String operationType) {
        return new GraphQLTask(httpClient, this::newRequest, null, new GraphQLRequestBuilder(operationType));
    }

    protected HttpRequest newRequest() {
        HttpRequest req = new HttpRequest();
        req.setUrl(serviceUrl);
        req.setMethod(HttpApiConstants.METHOD_POST);
        return req;
    }

    private String newEntityAction(String methodName) {
        return entityModel.getShortName() + "__" + methodName;
    }

    @Override
    public CompletionStage<Void> loadAsync(ShardSelection shard, IOrmEntity entity, IntArray propIds,
                                           FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        GraphQLTask task = newTask(GraphQLRequestBuilder.OPERATION_TYPE_QUERY);
        String operationName = newEntityAction("batchGet");
        FieldSelectionBean selection = newSelection(propIds, subSelection);

        task.getRequest().addOperation(operationName).addArg("id", "String", entity.orm_idString())
                .selection(selection).onSuccess(res -> {
                    bindEntity(entity, (Map<String, Object>) res, propIds);
                });

        return task.getPromise();
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
        GraphQLTask task = makeTask(session, querySpace, GraphQLRequestBuilder.OPERATION_TYPE_MUTATION);
        if (topoAsc) {
            List<Object> data = buildData(saveActions, updateActions);
            if (data == null || data.isEmpty())
                return null;

            task.getRequest().addOperation(newEntityAction("batchModify"))
                    .addArg("data", "[Map]", data);
            return task.getPromise();
        } else {
            if (deleteActions == null || deleteActions.isEmpty())
                return null;

            List<String> ids = deleteActions.stream().map(IBatchAction.EntityDeleteAction::getIdString).collect(Collectors.toList());
            task.getRequest().addOperation(newEntityAction("batchDelete"))
                    .addArg("ids", "[String]", ids);
            return task.getPromise();
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
        GraphQLTask task = makeTask(session, getQuerySpace(shard), GraphQLRequestBuilder.OPERATION_TYPE_QUERY);
        String operationName = newEntityAction("batchGet");
        FieldSelectionBean selection = newSelection(propIds, subSelection);

        List<String> ids = entities.stream().map(IOrmEntity::orm_idString).collect(Collectors.toList());
        task.getRequest().addOperation(operationName).addArg("ids", "[String]", ids)
                .selection(selection).onSuccess(res -> {
                    bindEntities(entities, (List<Map<String, Object>>) res, propIds);
                });
        return task.getPromise();
    }

    protected FieldSelectionBean newSelection(IntArray propIds, FieldSelectionBean subSelection) {
        FieldSelectionBean selection = new FieldSelectionBean();
        for (int propId : propIds) {
            String propName = entityModel.getColumnByPropId(propId, false).getName();
            selection.addField(propName);
        }

        // @TODO subSelection的处理
        return selection;
    }

    private void bindEntities(Collection<IOrmEntity> entities, List<Map<String, Object>> list, IntArray propIds) {
        int i = 0;
        for (IOrmEntity entity : entities) {
            Map<String, Object> map = list.get(i);
            bindEntity(entity, map, propIds);
            i++;
        }
    }

    private void bindEntity(IOrmEntity entity, Map<String, Object> map, IntArray propIds) {

        for (int propId : propIds) {
            String propName = entity.orm_propName(propId);
            Object value = map.get(propName);
            entity.orm_propValue(propId, value);
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
        FieldSelectionBean selection = newSelection(entityModel.getEagerLoadProps(), null);
        GraphQLTask task = newTask(GraphQLRequestBuilder.OPERATION_TYPE_QUERY);
        CompletableFuture<IOrmEntity> ret = new CompletableFuture<>();

        task.getRequest().addOperation(newEntityAction("findFirst"))
                .addArg("query", "QueryBean", query)
                .selection(selection).onSuccess(res -> {
                    try {
                        IOrmEntity entity = OrmAssembly.assemble((Map<String, Object>) res,
                                entityModel, entityModel.getEagerLoadProps(), session);
                        ret.complete(entity);
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }).onFailure(ret::completeExceptionally);
        task.run();
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
        FieldSelectionBean selection = newSelection(entityModel.getEagerLoadProps(), null);
        GraphQLTask task = newTask(GraphQLRequestBuilder.OPERATION_TYPE_QUERY);
        CompletableFuture<List<T>> ret = new CompletableFuture<>();

        task.getRequest().addOperation(newEntityAction("findList"))
                .addArg("query", "QueryBean", query)
                .selection(selection).onSuccess(res -> {
                    List<T> list = new ArrayList<>();
                    try {
                        initEntities(list, (List<Map<String, Object>>) res, session);
                        ret.complete(list);
                    } catch (Exception e) {
                        ret.completeExceptionally(e);
                    }
                }).onFailure(ret::completeExceptionally);
        task.run();
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
        return null;
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
    public long count(QueryBean query) {
        return 0;
    }

    @Override
    public boolean exists(QueryBean query) {
        return false;
    }

    @Override
    public long delete(QueryBean query) {
        return 0;
    }

    @Override
    public long update(QueryBean query, Map<String, Object> props) {
        return 0;
    }
}