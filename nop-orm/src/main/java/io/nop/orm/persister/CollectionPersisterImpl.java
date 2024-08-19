/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.cache.ICache;
import io.nop.commons.collections.IntArray;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.driver.ICollectionPersistDriver;
import io.nop.orm.loader.IOrmBatchLoadQueueImplementor;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.orm.OrmConfigs.CFG_ENTITY_GLOBAL_CACHE_ENABLED;
import static io.nop.orm.OrmConfigs.CFG_ORM_DEFAULT_ENTITY_BATCH_LOAD_SIZE;

/**
 * @author canonical_entropy@163.com
 */
public class CollectionPersisterImpl implements ICollectionPersister {
    private IEntityRelationModel collectionModel;
    private IPersistEnv env;
    private boolean useGlobalCache;
    private ICache<String, Object> globalCache;
    private ICollectionPersistDriver driver;
    private String defaultQuerySpace;
    private boolean useTenantCache;

    @Override
    public IEntityRelationModel getCollectionModel() {
        return collectionModel;
    }

    @Override
    public void init(IEntityRelationModel relation, IPersistEnv env) {
        useGlobalCache = relation.isUseGlobalCache() && CFG_ENTITY_GLOBAL_CACHE_ENABLED.get();
        if (useGlobalCache) {
            this.globalCache = env.getGlobalCache().getCache(relation.getCollectionName());
        }
        this.env = env;
        this.collectionModel = relation;
        this.defaultQuerySpace = relation.getRefEntityModel().getQuerySpace();

        this.useTenantCache = relation.getRefEntityModel().isUseTenant();

        this.driver = env.createCollectionPersistDriver(collectionModel.getPersistDriver());
        driver.init(relation, env);

    }

    @Override
    public void loadCollection(IOrmEntitySet collection, IntArray propIds, FieldSelectionBean selection,
                               IOrmSessionImplementor session) {
        if (env.getDaoListener() != null)
            env.getDaoListener().onRead(collectionModel.getRefEntityModel());

        if (collectionModel.getRefEntityModel().isUseTenant()) {
            collection.orm_tenantId(ContextProvider.currentTenantId());
        }

        if (useGlobalCache && loadFromGlobalCache(collection, session))
            return;

        ShardSelection shard = getShardSelection(collection);

        driver.loadCollection(shard, collection, propIds, selection, session);

        if (useGlobalCache) {
            updateGlobalCache(collection, session);
        }
    }

    int getMaxBatchLoadSize() {
        Integer size = this.collectionModel.getMaxBatchLoadSize();
        if (size == null || size <= 0)
            return CFG_ORM_DEFAULT_ENTITY_BATCH_LOAD_SIZE.get();
        return size;
    }

    @Override
    public CompletionStage<Void> batchLoadCollectionAsync(Collection<IOrmEntitySet> collections, IntArray propIds,
                                                          FieldSelectionBean selection, IOrmSessionImplementor session) {
        if (env.getDaoListener() != null)
            env.getDaoListener().onRead(collectionModel.getRefEntityModel());

        final Collection<IOrmEntitySet> toLoad = getToLoad(collections, propIds, selection, session);
        if (toLoad.isEmpty())
            return FutureHelper.voidPromise();

        List<CompletionStage<?>> futures = new ArrayList<>();
        for (Map.Entry<ShardSelection, List<IOrmEntitySet>> entry : this.splitForShard(toLoad).entrySet()) {
            _batchLoad(entry.getKey(), entry.getValue(), propIds, selection, session, futures);
        }
        return FutureHelper.waitAll(futures);
    }

    Map<ShardSelection, List<IOrmEntitySet>> splitForShard(Collection<IOrmEntitySet> list) {
        Map<ShardSelection, List<IOrmEntitySet>> ret = new HashMap<>();
        for (IOrmEntitySet item : list) {
            ShardSelection shard = getShardSelection(item);
            List<IOrmEntitySet> shardList = ret.get(shard);
            if (shardList == null) {
                shardList = new ArrayList<>();
                ret.put(shard, shardList);
            }
            shardList.add(item);
        }
        return ret;
    }

    void _batchLoad(ShardSelection shard, Collection<IOrmEntitySet> toLoad, IntArray propIds,
                    FieldSelectionBean selection, IOrmSessionImplementor session, List<CompletionStage<?>> futures) {
        for (final Collection<IOrmEntitySet> colls : CollectionHelper.splitChunk(toLoad, this.getMaxBatchLoadSize())) {

            CompletionStage<?> future = driver.batchLoadCollectionAsync(shard, colls, propIds, selection, session);
            FutureHelper.collectWaiting(future, futures);

            future.thenRun(() -> {
                for (IOrmEntitySet coll : colls) {
                    if (useGlobalCache) {
                        updateGlobalCache(coll, session);
                    }
                }
            });
        }
    }

    ShardSelection getShardSelection(IOrmEntitySet coll) {
        if (!collectionModel.getRefEntityModel().isUseShard())
            return null;

        int shardPropId = collectionModel.getOwnerEntityModel().getShardPropId();
        IColumnModel col = collectionModel.getOwnerEntityModel().getColumnByPropId(shardPropId, false);

        ShardSelection shard = env.getShardSelector().selectShard(collectionModel.getCollectionName(), col.getName(),
                coll.orm_owner().orm_propValue(shardPropId));
        return shard;
    }

    @Override
    public void flushCollectionChange(IOrmEntitySet collection, IOrmSessionImplementor session) {
        ShardSelection shard = getShardSelection(collection);

        IBatchAction.CollectionBatchAction action = new IBatchAction.CollectionBatchAction(collection, shard,
                (ret, err) -> {
                    if (err != null) {
                        evictGlobalCache(shard, collection);
                    }
                });
        session.getBatchActionQueue(shard == null ? defaultQuerySpace : shard.getQuerySpace()).enqueueCollection(action);
    }

    boolean loadFromGlobalCache(IOrmEntitySet coll, IOrmSessionImplementor session) {
        Object[] elementIds = convertCacheValues(globalCache.get(getCacheKey(coll.orm_owner())));
        if (elementIds == null) {
            return false;
        }

        IOrmBatchLoadQueue queue = session.getBatchLoadQueue();

        String entityName = collectionModel.getRefEntityName();

        coll.orm_beginLoad();
        for (Object id : elementIds) {
            IOrmEntity entity = session.load(entityName, id);
            coll.orm_internalAdd(entity);
            queue.enqueue(entity);
        }
        coll.orm_endLoad();

        queue.flush();
        return true;
    }

    void evictGlobalCache(ShardSelection shard, IOrmEntitySet coll) {
        if (!useGlobalCache)
            return;

        String querySpace = shard == null ? null : shard.getQuerySpace();

        if (env.txn().isTransactionOpened(querySpace)) {
            env.txn().addTransactionListener(querySpace, new ITransactionListener() {
                @Override
                public void onAfterCommit(ITransaction txn) {
                    globalCache.remove(getCacheKey(coll.orm_owner()));
                }
            });
        } else {
            globalCache.remove(getCacheKey(coll.orm_owner()));
        }
    }

    void updateGlobalCache(IOrmEntitySet coll, IOrmSessionImplementor session) {

        Object[] elementIds = getElementIds(coll);

        globalCache.put(getCacheKey(coll.orm_owner()), elementIds);
    }

    private String getCacheKey(IOrmEntity entity) {
        if (!useTenantCache)
            return entity.orm_idString();

        Object tenantId = null;
        int tenantPropId = entity.orm_entityModel().getTenantPropId();
        if (tenantPropId > 0) {
            // 存在一种可能，非租户表引用租户表的集合
            // 第一次按照主键进行load的时候没有设置tenantId。如果直接读取则导致proxy加载
            if (!entity.orm_propInited(tenantPropId)) {
                tenantId = ContextProvider.currentTenantId();
            } else {
                tenantId = entity.orm_propValue(tenantPropId);
                if (tenantId == null)
                    tenantId = ContextProvider.currentTenantId();
            }
        }

        if (tenantId == null)
            tenantId = ContextProvider.currentTenantId();
        return tenantId + ":" + entity.orm_id();
    }

    public static Object[] getElementIds(Collection<IOrmEntity> collection) {
        Object[] elementIds = new Object[collection.size()];
        int i = 0;
        for (IOrmEntity entity : collection) {
            elementIds[i] = entity.get_id();
            i++;
        }
        return elementIds;
    }

    Collection<IOrmEntitySet> getToLoad(Collection<IOrmEntitySet> colls, IntArray propIds, FieldSelectionBean selection,
                                        IOrmSessionImplementor session) {
        if (!useGlobalCache)
            return colls;

        Set<String> keys = new HashSet<>(colls.size());
        Set<IOrmEntitySet> ret = new HashSet<>();
        for (IOrmEntitySet coll : colls) {
            if (!coll.orm_proxy())
                continue;
            ret.add(coll);
            keys.add(getCacheKey(coll.orm_owner()));
        }

        Map<String, Object> values = globalCache.getAll(keys);

        String entityName = collectionModel.getRefEntityName();

        IOrmBatchLoadQueueImplementor queue = session.getBatchLoadQueue();

        Iterator<IOrmEntitySet> it = ret.iterator();
        while (it.hasNext()) {
            IOrmEntitySet coll = it.next();
            String ownerId = coll.orm_owner().orm_idString();
            Object[] elmIds = convertCacheValues(values.get(ownerId));
            if (elmIds != null) {
                coll.orm_beginLoad();
                for (Object elmId : elmIds) {
                    IOrmEntity entity = session.load(entityName, elmId);
                    coll.orm_internalAdd(entity);
                }
                coll.orm_endLoad();
                queue.internalEnqueueCollection(coll, propIds, selection);
                it.remove();
            }
        }

        queue.flush();
        return ret;
    }

    Object[] convertCacheValues(Object value) {
        if (value == null)
            return null;

        if (value instanceof Object[]) {
            return (Object[]) value;
        }
        List<Object> list = (List<Object>) value;
        return list.toArray();
    }

    @Override
    public void close() throws Exception {
        IoHelper.safeClose(driver);
    }
}
