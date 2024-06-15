/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.persister;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.cache.ICache;
import io.nop.commons.collections.IntArray;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.IBeanConstructor;
import io.nop.dao.DaoConstants;
import io.nop.dao.shard.ShardSelection;
import io.nop.dao.txn.ITransaction;
import io.nop.dao.txn.ITransactionListener;
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmDaoListener;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConstants;
import io.nop.orm.driver.IEntityPersistDriver;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.id.IEntityIdGenerator;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityComponentModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmEntityFilterModel;
import io.nop.orm.model.utils.OrmModelHelper;
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
import static io.nop.orm.OrmConfigs.CFG_ORM_CHECK_MANDATORY_WHEN_SAVE;
import static io.nop.orm.OrmConfigs.CFG_ORM_CHECK_MANDATORY_WHEN_UPDATE;
import static io.nop.orm.OrmConfigs.CFG_ORM_DEFAULT_ENTITY_BATCH_LOAD_SIZE;
import static io.nop.orm.OrmErrors.ARG_CURRENT_TENANT;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_TENANT_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_MANDATORY_PROP_IS_NULL;
import static io.nop.orm.OrmErrors.ERR_ORM_MISSING_TENANT_ID;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT;
import static io.nop.orm.OrmErrors.ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS;
import static io.nop.orm.OrmErrors.ERR_ORM_UPDATE_ENTITY_NOT_FOUND;

/**
 * @author canonical_entropy@163.com
 */
public class EntityPersisterImpl implements IEntityPersister {
    private IEntityModel entityModel;
    private IPersistEnv env;
    private IEntityIdGenerator idGenerator;
    private IEntityPersistDriver driver;
    private boolean useGlobalCache;
    private ICache<String, Object> globalCache;
    private IBeanConstructor constructor;

    @Override
    public void close() throws Exception {
        IoHelper.safeClose(driver);
    }

    @Override
    public void init(IEntityModel entityModel, IEntityIdGenerator idGenerator, IPersistEnv env) {
        this.entityModel = entityModel;
        this.idGenerator = idGenerator;
        this.useGlobalCache = entityModel.isUseGlobalCache() && CFG_ENTITY_GLOBAL_CACHE_ENABLED.get();
        this.env = env;
        if (useGlobalCache)
            globalCache = env.getGlobalCache(entityModel.getName());

        this.driver = env.createEntityPersistDriver(entityModel.getPersistDriver());

        driver.init(entityModel, env);

        this.constructor = env.getEntityConstructor(entityModel);
    }

    int getMaxBatchLoadSize() {
        Integer size = this.entityModel.getMaxBatchLoadSize();
        if (size == null)
            return CFG_ORM_DEFAULT_ENTITY_BATCH_LOAD_SIZE.get();
        return size;
    }

    public IEntityPersistDriver getDriver() {
        return driver;
    }

    @Override
    public IEntityModel getEntityModel() {
        return entityModel;
    }

    @Override
    public void generateId(IOrmEntity entity) {
        idGenerator.generateId(entity);
    }

    @Override
    public IOrmEntity newEntity(IOrmSessionImplementor session) {
        IOrmEntity entity = (IOrmEntity) constructor.newInstance();
        entity.orm_entityModel(entityModel);
        // 需要在attach之前设置值，否则实体会被标记为dirty
        bindFilter(entity);
        entity.orm_attach(session);
        return entity;
    }

    @Override
    public CompletionStage<Void> batchLoadAsync(Collection<IOrmEntity> entities, IntArray propIds,
                                                FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        if (entities.size() == 1) {
            return loadAsync(CollectionHelper.first(entities), propIds, subSelection, session);
        }

        for (IOrmEntity entity : entities) {
            Guard.checkArgument(entity.orm_state().isAllowLoad(), "entity not allow load");
        }

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        Set<IOrmEntity> toLoad = this.getToLoad(entities, session);
        if (toLoad.isEmpty())
            return FutureHelper.voidPromise();

        // 使用全局缓存的情况下，总是装载所有属性
        if (useGlobalCache) {
            propIds = entityModel.getAllPropIds();
        }

        CompletionStage<Void> future = FutureHelper.voidPromise();
        if (entityModel.getShardPropId() <= 0) {
            future = this._batchLoad(null, toLoad, propIds, subSelection, session);
        } else {
            for (Map.Entry<ShardSelection, List<IOrmEntity>> entry : splitForShard(toLoad).entrySet()) {
                CompletionStage<Void> shardFuture = this._batchLoad(entry.getKey(), entry.getValue(), propIds,
                        subSelection, session);
                if (shardFuture != FutureHelper.voidPromise()) {
                    future = FutureHelper.bothSuccess(future, shardFuture);
                }
            }
            if (future == null)
                future = FutureHelper.voidPromise();
        }

        if (useGlobalCache) {
            future.thenRun(() -> {
                // 更新全局缓存
                for (IOrmEntity entity : toLoad) {
                    this.updateGlobalCache(entity, session);
                }
            });
        }

        return FutureHelper.voidPromise();
    }

    Map<ShardSelection, List<IOrmEntity>> splitForShard(Collection<IOrmEntity> list) {
        Map<ShardSelection, List<IOrmEntity>> ret = new HashMap<>();
        for (IOrmEntity entity : list) {
            ShardSelection shard = getShardSelection(entity);
            List<IOrmEntity> shardList = ret.get(shard);
            if (shardList == null) {
                shardList = new ArrayList<>();
                ret.put(shard, shardList);
            }
            shardList.add(entity);
        }
        return ret;
    }

    CompletionStage<Void> _batchLoad(ShardSelection shard, Collection<IOrmEntity> toLoad, IntArray propIds,
                                     FieldSelectionBean subSelection, IOrmSessionImplementor session) {
        int maxSize = this.getMaxBatchLoadSize();
        if (toLoad.size() <= maxSize) {
            return driver.batchLoadAsync(shard, toLoad, propIds, subSelection, session);
        }

        CompletionStage<Void> future = FutureHelper.voidPromise();
        for (final Collection<IOrmEntity> list : CollectionHelper.splitChunk(toLoad, this.getMaxBatchLoadSize())) {
            CompletionStage<Void> chunkFuture = driver.batchLoadAsync(shard, list, propIds, subSelection, session);
            future = FutureHelper.bothSuccess(future, chunkFuture);
        }
        return future;
    }

    @Override
    public CompletionStage<Void> loadAsync(IOrmEntity entity, IntArray propIds, FieldSelectionBean subSelection,
                                           IOrmSessionImplementor session) {
        Guard.checkArgument(entity.orm_state().isAllowLoad(), "entity not allow load");

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        processTenantId(entity);

        if (useGlobalCache) {
            if (loadFromGlobalCache(entity, session))
                return FutureHelper.voidPromise();
        }

        ShardSelection shardSelection = this.getShardSelection(entity);

        CompletionStage<Void> future = driver.loadAsync(shardSelection, entity,
                useGlobalCache ? entityModel.getAllPropIds() : propIds, subSelection, session);

        if (useGlobalCache) {
            future = future.thenApply(r -> {
                updateGlobalCache(entity, session);
                return null;
            });
        }
        return future;
    }

    @Override
    public boolean lock(IOrmEntity entity, IntArray propIds, IOrmSessionImplementor session, Runnable unlockCallback) {
        Guard.checkArgument(entity.orm_state().isAllowLoad(), "entity not allow load");

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        processTenantId(entity);
        ShardSelection shard = this.getShardSelection(entity);
        boolean b = driver.lock(shard, entity, useGlobalCache ? entityModel.getAllPropIds() : propIds, unlockCallback,
                session);
        if (useGlobalCache) {
            updateGlobalCache(entity, session);
        }
        return b;
    }

    protected ShardSelection getShardSelection(IOrmEntity entity) {
        if (entity == null)
            return null;

        int shardPropId = entityModel.getShardPropId();
        if (shardPropId <= 0)
            return null;

        Object value = entity.orm_propValue(shardPropId);
        String shardProp = entityModel.getColumnByPropId(shardPropId, false).getName();
        ShardSelection shard = env.getShardSelector().selectShard(entity.orm_entityName(), shardProp, value);
        return shard;
    }

    protected IOrmEntity findLatest(IOrmEntity entity, IOrmSessionImplementor session) {
        ShardSelection selection = getShardSelection(entity);
        return driver.findLatest(selection, entity, session);
    }

    @Override
    public void save(IOrmEntity entity, final IOrmSessionImplementor session) {
        LogicalDeleteHelper.onSave(entityModel, entity);

        bindFilter(entity);
        processTenantId(entity);
        processOptimisticLockVersion(entity);

        if (entityModel.isUseRevision()) {
            OrmRevisionHelper.onRevSave(entityModel, entity, this, session);
            return;
        }

        queueSave(entity, session);
    }

    @Override
    public void update(IOrmEntity entity, final IOrmSessionImplementor session) {
        bindFilter(entity);
        processTenantId(entity);

        if (entityModel.isUseRevision()) {
            OrmRevisionHelper.onRevUpdate(entityModel, entity, this, session);
            return;
        }

        queueUpdate(entity, session);
    }

    @Override
    public void delete(IOrmEntity entity, final IOrmSessionImplementor session) {
        // 如果是逻辑删除，则转为调用修改操作
        if (entityModel.isUseLogicalDelete() && !entity.orm_disableLogicalDelete()) {
            entity.orm_propValue(entityModel.getDeleteFlagPropId(), DaoConstants.YES_VALUE);
            if (entityModel.getDeleteVersionProp() != null) {
                entity.orm_propValueByName(entityModel.getDeleteFlagProp(), env.newDeleteVersion());
            }
            syncComponentWhenDelete(entity, true);
            update(entity, session);
            return;
        }

        processTenantId(entity);

        if (entityModel.isUseRevision()) {
            OrmRevisionHelper.onRevDelete(entityModel, entity, this, session);
            syncComponentWhenDelete(entity, true);
            return;
        }

        syncComponentWhenDelete(entity, false);
        queueDelete(entity, session);
    }

    void syncComponentWhenDelete(IOrmEntity entity, boolean logicalDelete) {
        for (IEntityComponentModel componentModel : entityModel.getComponents()) {
            if (componentModel.isNeedFlush()) {
                Object component = entity.orm_propValueByName(componentModel.getName());
                if (component instanceof IOrmComponent)
                    ((IOrmComponent) component).onEntityDelete(logicalDelete);
            }
        }
    }

    void processOptimisticLockVersion(IOrmEntity entity) {
        int versionProp = entityModel.getVersionPropId();
        if (versionProp > 0) {
            Object value = entity.orm_propValue(versionProp);
            if (value == null) {
                // 将版本字段初始化为0
                entity.orm_propValue(versionProp, 0);
            }
        }
    }

    void bindFilter(IOrmEntity entity) {
        if (entityModel.hasFilter()) {
            for (OrmEntityFilterModel filter : entityModel.getFilters()) {
                entity.orm_propValueByName(filter.getName(), filter.getValue());
            }
        }
    }

    /**
     * 修改成功之后数据库中的乐观锁版本号会自动加一。这里提升内存中的版本号，从而和数据库保持一致
     */
    void incOptimisticLockVersion(IOrmEntity entity) {
        int versionProp = entityModel.getVersionPropId();
        if (versionProp > 0) {
            Object value = entity.orm_propValue(versionProp);
            value = MathHelper.add(value, 1);
            entity.orm_internalSet(versionProp, value);
        }
    }

    void checkMandatoryWhenSave(IOrmEntity entity) {
        for (IColumnModel propModel : entityModel.getColumns()) {
            if (!propModel.isMandatory())
                continue;

            Object value = entity.orm_propValue(propModel.getPropId());
            if (value == null) {
                if (propModel.containsTag(OrmConstants.TAG_SEQ)) {
                    // 如果指定了seq标签，且字段非空，则自动根据sequence配置生成
                    String propKey = OrmModelHelper.buildEntityPropKey(propModel);
                    Object seqValue = propModel.getStdDataType().isNumericType() ?
                            env.getSequenceGenerator().generateLong(propKey, false)
                            : env.getSequenceGenerator().generateString(propKey, false);
                    entity.orm_propValue(propModel.getPropId(), seqValue);
                } else if (propModel.getDefaultValue() != null) {
                    entity.orm_propValue(propModel.getPropId(), propModel.getDefaultValue());
                } else {
                    throw newError(ERR_ORM_MANDATORY_PROP_IS_NULL, entity).param(ARG_PROP_NAME, propModel.getName());
                }
            }
        }
    }

    void checkMandatoryWhenUpdate(IOrmEntity entity) {
        for (IColumnModel propModel : entityModel.getColumns()) {
            if (!propModel.isMandatory())
                continue;

            // 如果是没有修改的字段则直接跳过。有可能有历史数据为空，这里没有进行检查
            if (!entity.orm_propDirty(propModel.getPropId()))
                continue;

            Object value = entity.orm_propValue(propModel.getPropId());
            if (value == null) {
                throw newError(ERR_ORM_MANDATORY_PROP_IS_NULL, entity).param(ARG_PROP_NAME, propModel.getName());
            }
        }
    }

    protected NopException newError(ErrorCode err, IOrmEntity entity) {
        return OrmException.newError(err, entity);
    }

    void processTenantId(IOrmEntity entity) {
        if (entityModel.isUseTenant()) {
            int tenantPropId = entityModel.getTenantPropId();
            if (tenantPropId > 0) {
                String currentTenant = ContextProvider.currentTenantId();
                String tenantId;
                // 第一次按照主键进行load的时候没有设置tenantId。如果直接读取则导致proxy加载
                if (!entity.orm_propInited(tenantPropId)) {
                    tenantId = currentTenant;
                    entity.orm_internalSet(tenantPropId, tenantId);
                } else {
                    tenantId = StringHelper.toString(entity.orm_propValue(tenantPropId), null);
                }
                if (StringHelper.isEmpty(tenantId)) {
                    tenantId = currentTenant;
                    if (tenantId == null)
                        throw newError(ERR_ORM_MISSING_TENANT_ID, entity).param(ARG_PROP_NAME,
                                entity.orm_propName(tenantPropId));
                    entity.orm_propValue(tenantPropId, tenantId);
                } else {
                    if (currentTenant != null && !currentTenant.equals(tenantId))
                        throw newError(ERR_ORM_NOT_ALLOW_PROCESS_ENTITY_IN_OTHER_TENANT, entity)
                                .param(ARG_TENANT_ID, tenantId).param(ARG_CURRENT_TENANT, currentTenant);
                }
            }
        }
    }

    protected void queueSave(IOrmEntity entity, IOrmSessionImplementor session) {
        OrmTimestampHelper.onCreate(entityModel, entity);

        if (CFG_ORM_CHECK_MANDATORY_WHEN_SAVE.get()) {
            this.checkMandatoryWhenSave(entity);
        }

        ShardSelection shard = getShardSelection(entity);

        IBatchAction.EntitySaveAction action = new IBatchAction.EntitySaveAction(entity, shard, (ret, err) -> {
            if (err == null) {
                evictGlobalCache(shard, entity);
                session.persisterPostSave(entity);
            }
        });
        session.getBatchActionQueue(getQuerySpace(shard)).enqueueSave(action);
    }

    protected void queueUpdate(IOrmEntity entity, final IOrmSessionImplementor session) {
        OrmTimestampHelper.onUpdate(entityModel, entity);

        if (CFG_ORM_CHECK_MANDATORY_WHEN_UPDATE.get()) {
            this.checkMandatoryWhenUpdate(entity);
        }

        ShardSelection shard = getShardSelection(entity);

        IBatchAction.EntityUpdateAction action = new IBatchAction.EntityUpdateAction(entity, shard, (ret, err) -> {
            if (err == null) {
                checkUpdateResult(ret, entity);
                incOptimisticLockVersion(entity);
                evictGlobalCache(shard, entity);
                session.persisterPostUpdate(entity);
            }
        });

        session.getBatchActionQueue(getQuerySpace(shard)).enqueueUpdate(action);
    }

    protected void queueDelete(IOrmEntity entity, IOrmSessionImplementor session) {
        ShardSelection shard = getShardSelection(entity);

        IBatchAction.EntityDeleteAction action = new IBatchAction.EntityDeleteAction(entity, shard, (ret, err) -> {
            evictGlobalCache(shard, entity);

            if (err == null) {
                session.persisterPostDelete(entity);
            }
        });
        session.getBatchActionQueue(getQuerySpace(shard)).enqueueDelete(action);
    }

    protected void checkUpdateResult(int count, IOrmEntity entity) {
        if (count > 1) {
            throw newError(ERR_ORM_UPDATE_ENTITY_MULTIPLE_ROWS, entity);
        } else if (count == 0) {
            throw newError(ERR_ORM_UPDATE_ENTITY_NOT_FOUND, entity);
        }
    }

    protected boolean loadFromGlobalCache(IOrmEntity entity, IOrmSessionImplementor session) {
        Object values = globalCache.get(entity.orm_idString());
        if (values != null) {
            Object[] cacheValues = convertCacheValues(values);
            // 全局缓存中保存实体全部属性，简化动态改变装载策略时的处理逻辑
            session.internalAssemble(entity, cacheValues, entityModel.getAllPropIds());
            return true;
        } else {
            return false;
        }
    }

    protected void updateGlobalCache(IOrmEntity entity, IOrmSessionImplementor session) {
        if (entity.orm_state().isMissing()) {
            globalCache.removeAsync(entity.orm_idString());
        } else {
            Object[] values = OrmAssembly.getPropValues(entity, entityModel.getAllPropIds());
            globalCache.putAsync(entity.orm_idString(), values);
        }
    }

    Set<IOrmEntity> asSet(Collection<IOrmEntity> entities) {
        if (entities instanceof Set)
            return (Set<IOrmEntity>) entities;
        return new HashSet<>(entities);
    }

    void evictGlobalCache(ShardSelection shard, final IOrmEntity entity) {
        if (!useGlobalCache)
            return;

        String querySpace = getQuerySpace(shard);

        if (env.txn().isTransactionOpened(querySpace)) {
            this.env.txn().addTransactionListener(querySpace, new ITransactionListener() {
                @Override
                public void onAfterCommit(ITransaction txn) {
                    globalCache.removeAsync(entity.orm_idString());
                }
            });
        } else {
            globalCache.removeAsync(entity.orm_idString());
        }
    }

    protected String getQuerySpace(ShardSelection shard){
        return shard == null ? entityModel.getQuerySpace() : shard.getQuerySpace();
    }

    /**
     * 尝试从全局缓存获取，如果能够获取到，则不再需要装载
     */
    protected Set<IOrmEntity> getToLoad(Collection<IOrmEntity> entities, IOrmSessionImplementor session) {
        if (!useGlobalCache)
            return asSet(entities);

        HashSet<IOrmEntity> ret = new HashSet<>(entities);

        List<String> keys = new ArrayList<>(entities.size());
        for (IOrmEntity entity : ret) {
            keys.add(entity.orm_idString());
        }

        Map<String, Object> values = globalCache.getAll(keys);
        // 如果全局缓存中未找到，则直接返回
        if (values.isEmpty())
            return ret;

        Iterator<IOrmEntity> it = ret.iterator();
        while (it.hasNext()) {
            IOrmEntity entity = it.next();
            Object value = values.get(entity.orm_idString());
            if (value != null) {
                // 如果从全局缓存中查到，则从待装载集合中删除
                Object[] cacheValues = convertCacheValues(value);
                session.internalAssemble(entity, cacheValues, entityModel.getAllPropIds());
                it.remove();
            }
        }
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
    public CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace,
                                                   List<IBatchAction.EntitySaveAction> saveActions, List<IBatchAction.EntityUpdateAction> updateActions,
                                                   List<IBatchAction.EntityDeleteAction> deleteActions, IOrmSessionImplementor session) {
        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null && topoAsc) {
            if (saveActions != null && !saveActions.isEmpty()) {
                daoListener.onSave(entityModel);
            }
            if (updateActions != null && !updateActions.isEmpty()) {
                daoListener.onUpdate(entityModel);
            }
            if (deleteActions != null && !deleteActions.isEmpty()) {
                daoListener.onDelete(entityModel);
            }
        }
        return driver.batchExecuteAsync(topoAsc, querySpace, saveActions, updateActions, deleteActions, session);
    }

    @Override
    public <T> T getExtension(Class<T> extensionClass) {
        return driver.getExtension(extensionClass);
    }

    @Override
    public <T extends IOrmEntity> List<T> findPageByExample(T example, List<OrderFieldBean> orderBy, long offset,
                                                            int limit, IOrmSessionImplementor session) {
        bindFilter(example);
        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        ShardSelection shard = getShardSelection(example);
        addDeleteFlagToExample(example);
        return driver.findPageByExample(shard, example, orderBy, offset, limit, session);
    }

    @Override
    public <T extends IOrmEntity> List<T> findAllByExample(T example, List<OrderFieldBean> orderBy,
                                                           IOrmSessionImplementor session) {
        bindFilter(example);

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        ShardSelection shard = getShardSelection(example);
        addDeleteFlagToExample(example);
        return driver.findAllByExample(shard, example, orderBy, session);
    }

    @Override
    public long deleteByExample(IOrmEntity example, IOrmSessionImplementor session) {
        bindFilter(example);

        if (session.isEntityMode() && entityModel.isEntityModeEnabled()) {
            List<IOrmEntity> entities = findAllByExample(example, null, session);
            for (IOrmEntity entity : entities) {
                session.delete(entity);
            }
            return entities.size();
        }

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onDelete(entityModel);

        ShardSelection shard = getShardSelection(example);
        addDeleteFlagToExample(example);
        return driver.deleteByExample(shard, example, session);
    }

    @Override
    public IOrmEntity findFirstByExample(IOrmEntity example, IOrmSessionImplementor session) {
        bindFilter(example);

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        ShardSelection shard = getShardSelection(example);
        addDeleteFlagToExample(example);
        return driver.findFirstByExample(shard, example, session);
    }

    @Override
    public long countByExample(IOrmEntity example, IOrmSessionImplementor session) {
        bindFilter(example);

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null)
            daoListener.onRead(entityModel);

        ShardSelection shard = getShardSelection(example);
        addDeleteFlagToExample(example);
        return driver.countByExample(shard, example, session);
    }

    @Override
    public long updateByExample(IOrmEntity example, IOrmEntity updated, IOrmSessionImplementor session) {
        bindFilter(example);

        if (session.isEntityMode() && entityModel.isEntityModeEnabled()) {
            List<IOrmEntity> entities = findAllByExample(example, null, session);
            for (IOrmEntity entity : entities) {
                updated.orm_forEachInitedProp((value, propId) -> {
                    entity.orm_propValue(propId, value);
                });
            }
            return entities.size();
        }

        IOrmDaoListener daoListener = env.getDaoListener();
        if (daoListener != null) {
            daoListener.onUpdate(entityModel);
        }

        ShardSelection shard = getShardSelection(example);
        addDeleteFlagToExample(example);
        return driver.updateByExample(shard, example, updated, session);
    }

    private void addDeleteFlagToExample(IOrmEntity example) {
        if (entityModel.isUseLogicalDelete() && !example.orm_disableLogicalDelete()) {
            example.orm_propValue(entityModel.getDeleteFlagPropId(), DaoConstants.NO_VALUE);
        }
    }
}