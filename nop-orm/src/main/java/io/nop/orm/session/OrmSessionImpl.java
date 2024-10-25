/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.MapCache;
import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoEntity;
import io.nop.dataset.IComplexDataSet;
import io.nop.dataset.IDataSet;
import io.nop.orm.ILoadedOrmModel;
import io.nop.orm.IOrmComponent;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntityEnhancer;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.OrmConstants;
import io.nop.orm.OrmEntityState;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.loader.IOrmBatchLoadQueueImplementor;
import io.nop.orm.loader.IQueryExecutor;
import io.nop.orm.loader.OrmBatchLoadQueueImpl;
import io.nop.orm.model.IComputePropModel;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.persister.IBatchActionQueue;
import io.nop.orm.persister.ICollectionPersister;
import io.nop.orm.persister.IEntityPersister;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.persister.OrmAssembly;
import io.nop.orm.support.OrmCompositePk;
import io.nop.orm.support.OrmEntityHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.nop.orm.OrmErrors.ARG_COLLECTION_NAME;
import static io.nop.orm.OrmErrors.ARG_ENTITY_ID;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_OLD_VERSION;
import static io.nop.orm.OrmErrors.ARG_OWNER;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ARG_STATUS;
import static io.nop.orm.OrmErrors.ARG_VERSION;
import static io.nop.orm.OrmErrors.ERR_ORM_COLLECTION_NOT_ALLOW_NULL;
import static io.nop.orm.OrmErrors.ERR_ORM_COLLECTION_NOT_IN_SESSION;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NOT_DETACHED;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_NOT_IN_SESSION;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_PROP_NOT_REF_ENTITY;
import static io.nop.orm.OrmErrors.ERR_ORM_ENTITY_VERSION_CHANGED;
import static io.nop.orm.OrmErrors.ERR_ORM_LOCK_ENTITY_FAIL;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_ALLOW_LOCK_DIRTY_ENTITY;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_SUPPORT_COMPUTE;
import static io.nop.orm.OrmErrors.ERR_ORM_READONLY_NOT_ALLOW_UPDATE;
import static io.nop.orm.OrmErrors.ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT;
import static io.nop.orm.OrmErrors.ERR_ORM_SAVE_ENTITY_REPLACE_EXISTING_ENTITY;
import static io.nop.orm.OrmErrors.ERR_ORM_SESSION_CLOSED;
import static io.nop.orm.OrmErrors.ERR_ORM_SESSION_NOT_RUN_ON_CONTEXT;
import static io.nop.orm.OrmErrors.ERR_ORM_UPDATE_ENTITY_NOT_MANAGED;

/**
 * 所有实体状态变迁均在此类中完成。
 *
 * @author canonical_entropy@163.com
 */
public class OrmSessionImpl implements IOrmSessionImplementor {
    static final Logger LOG = LoggerFactory.getLogger(OrmSessionImpl.class);

    private static final AtomicLong s_seq = new AtomicLong();
    private final long seq = s_seq.incrementAndGet();

    private final IOrmSessionEntityCache cache;
    private final IPersistEnv env;
    private final ILoadedOrmModel loadedOrmModel;

    private boolean readOnly;
    private boolean closed;

    private boolean dirty;
    private boolean entityMode;

    private CascadeFlusher flusher;

    private final List<IOrmInterceptor> interceptors;

    private SessionBatchActionQueue batchActionQueue = new SessionBatchActionQueue();

    private IOrmBatchLoadQueueImplementor batchLoadQueue;

    private ICache<Object, Object> sessionCache;

    private long sessionRevVersion = -1L;

    // 这里总使用当前的context，不需要自己创建
    private final IContext context = ContextProvider.currentContext();

    private List<Runnable> onClose;

    public OrmSessionImpl(boolean stateless, IPersistEnv env, List<IOrmInterceptor> interceptors) {
        this.env = env;
        this.loadedOrmModel = env.getLoadedOrmModel();
        this.loadedOrmModel.incRef();
        this.cache = stateless ? new StatelessOrmSessionEntityCache(this) :
                (loadedOrmModel.isAnyEntityUseTenant() ? new TenantOrmSessionEntityCache(this) : new OrmSessionEntityCache(this));
        this.interceptors = CollectionHelper.toNotNull(interceptors);


        env.getOrmMetrics().onSessionOpen();
    }

    public String toString() {
        return "OrmSession[seq=" + seq + "]";
    }

    @Override
    public boolean isStateless() {
        return cache.isStateless();
    }

    @Override
    public boolean isReadonly() {
        return readOnly;
    }

    @Override
    public void setReadonly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public long getSessionRevVersion() {
        if (sessionRevVersion < 0)
            sessionRevVersion = env.newSessionRevVer();
        return sessionRevVersion;
    }

    @Override
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void addOnClose(Runnable task) {
        if (onClose == null)
            onClose = new ArrayList<>();
        onClose.add(task);
    }

    @Override
    public void flush() {
        checkContext();
        checkValid();

        LOG.debug("orm.begin-flush");

        // 如果正在执行flush, 则直接返回
        if (this.flusher != null) {
            LOG.debug("orm.exit-flush-when-another-flush-is-running");
            return;
        }

        // 如果没有实体被修改，直接返回
        if (!dirty) {
            LOG.debug("orm.exit-flush-when-session-is-not-dirty");
            return;
        }

        // 只读session不允许修改
        if (readOnly) {
            LOG.debug("orm.exit-flush-when-session-is-readonly");
            return;
        }

        env.getOrmMetrics().onFlush();

        interceptPreFlush();
        Throwable exp = null;
        try {
            this.flusher = new CascadeFlusher(this, cache);
            this.flusher.execute();

            this.dirty = false;
            this.cache.clearDirty();

            this.batchActionQueue.flush();
        } catch (Throwable e) {
            exp = e;
        } finally {
            sessionRevVersion = -1L;
            this.flusher = null;
        }

        interceptPostFlush(exp);
        if (exp != null)
            throw NopException.adapt(exp);

        LOG.debug("orm.end-flush");
    }

    void checkContext() {
        if (context != null && !context.isRunningOnContext())
            throw new OrmException(ERR_ORM_SESSION_NOT_RUN_ON_CONTEXT);
    }

    void checkValid() {
        if (closed)
            throw new OrmException(ERR_ORM_SESSION_CLOSED);
    }

    void checkValid(IOrmEntity entity) {
        checkValid();
        if (entity.orm_enhancer() != this)
            throw new OrmException(ERR_ORM_SESSION_CLOSED);
    }

    void checkValid(IOrmEntitySet coll) {
        checkValid();
        if (coll.orm_enhancer() != this)
            throw new OrmException(ERR_ORM_SESSION_CLOSED);
    }

    void checkNotReadOnly() {
        if (this.readOnly)
            throw new OrmException(ERR_ORM_READONLY_NOT_ALLOW_UPDATE);
    }

    @Override
    public ILoadedOrmModel getLoadedOrmModel() {
        return loadedOrmModel;
    }

    @Override
    public Class<?> getEntityClass(IEntityModel entityModel) {
        return env.getEntityClassModel(entityModel).getRawClass();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public IOrmBatchLoadQueueImplementor getBatchLoadQueue() {
        checkContext();
        if (batchLoadQueue == null)
            this.batchLoadQueue = new OrmBatchLoadQueueImpl(this);
        return this.batchLoadQueue;
    }

    @Override
    public void flushBatchLoadQueue() {
        checkContext();
        if (this.batchLoadQueue != null)
            this.batchLoadQueue.flush();
    }

    IEntityPersister requireEntityPersister(String entityName) {
        return loadedOrmModel.requireEntityPersister(entityName);
    }

    ICollectionPersister requireCollectionPersister(String collectionName) {
        return loadedOrmModel.requireCollectionPersister(collectionName);
    }

    @Override
    public IOrmComponent newComponent(String componentName) {
        return env.newComponent(componentName);
    }

    @Override
    public boolean contains(IOrmEntity object) {
        checkContext();
        checkValid();
        return cache.contains(object);
    }

    @Override
    public void evict(IOrmEntity entity) {
        checkContext();
        checkValid();
        cache.remove(entity);
    }

    @Override
    public void evictAll(String entityName) {
        checkContext();
        checkValid();
        cache.removeAll(entityName);
    }

    @Override
    public void clear() {
        checkContext();
        checkValid();
        LOG.debug("orm.session_clear");
        cache.clear();
        if (sessionCache != null)
            sessionCache.clear();
    }

    @Override
    public IOrmEntity newEntity(String entityName) {
        // session关闭之后也可以新建实体
        //this.checkValid();
        LOG.trace("orm.newEntity:entityName={}", entityName);
        return this.requireEntityPersister(entityName).newEntity(this);
    }

    @Override
    public IOrmEntity get(String entityName, Object id) {
        IOrmEntity persistEntity = load(entityName, id);

        OrmEntityState state = persistEntity.orm_state();
        if (!state.isProxy()) {
            // 实体已经被删除, 则返回null
            if (state.isGone())
                return null;

            return persistEntity;
        }

        if (!this.internalLoad(persistEntity))
            return null;

        return persistEntity;
    }

    @Override
    public boolean internalLoad(IOrmEntity entity) {
        checkContext();
        checkValid();

        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());

        return _internalLoad(persister, entity, persister.getEntityModel().getEagerLoadProps());
    }

    @Override
    public void internalAssemble(IOrmEntity entity, Object[] values, IntArray propIds) {
        IEntityPersister persister = requireEntityPersister(entity.orm_entityName());
        IEntityModel entityModel = persister.getEntityModel();

        env.getOrmMetrics().onLoadEntity(entity.orm_entityName());

        if (!entity.orm_state().isManaged()) {
            for (int i = 0, n = values.length; i < n; i++) {
                int propId = propIds.get(i);
                entity.orm_internalSet(propId, values[i]);
            }
        } else {
            boolean lazyCheck = entityModel.isCheckVersionWhenLazyLoad();
            int versionPropId = entityModel.getVersionPropId();
            for (int i = 0, n = values.length; i < n; i++) {
                int propId = propIds.get(i);
                // 如果已经设置过，在不再更新，确保session范围内看到的数据具有一致性
                if (!entity.orm_propInited(propId)) {
                    entity.orm_internalSet(propId, values[i]);
                } else if (lazyCheck) {
                    // 检查乐观锁版本号是否已经发生变化
                    if (propId == versionPropId) {
                        Object value = values[i];
                        Object oldValue = entity.orm_propValue(propId);
                        if (!Objects.equals(value, oldValue))
                            throw newError(ERR_ORM_ENTITY_VERSION_CHANGED, entity).param(ARG_OLD_VERSION, oldValue)
                                    .param(ARG_VERSION, value);

                    }
                }
            }
        }

        OrmEntityState state = OrmEntityState.MANAGED;
        if (entityModel.isUseRevision()) {
            byte revision = (Byte) entity.orm_propValue(entityModel.getNopRevTypePropId());
            if (revision == OrmConstants.REV_TYPE_DELETE) {
                state = OrmEntityState.DELETED;
            }
        }
        entity.orm_state(state);

        if (entityModel.isReadonly())
            entity.orm_readonly(true);

        if (values.length == entityModel.getColumns().size()) {
            entity.orm_markFullyLoaded();
        }

        // 成功装载后执行回调函数
        interceptPostLoad(entity);
    }

    @Override
    public void markMissing(IOrmEntity entity) {
        entity.orm_state(OrmEntityState.MISSING);
    }

    @Override
    public IOrmEntity load(String entityName, Object id) {
        return makeProxy(entityName, id);
    }

    @Override
    public IOrmEntity internalLoad(String entityName, Object id) {
        return makeProxy(entityName, id);
    }

    @Override
    public IBeanProvider getBeanProvider() {
        return this.env.getBeanProvider();
    }

    @Override
    public void unload(IOrmEntity entity) {
        if (!contains(entity))
            throw newError(ERR_ORM_ENTITY_NOT_IN_SESSION, entity);

        if (entity.orm_state().isProxy())
            return;

        entity.orm_unload();
    }

    private NopException newError(ErrorCode errorCode, IOrmEntity entity) {
        return new OrmException(errorCode).param(ARG_ENTITY_NAME, entity.orm_entityName()).param(ARG_ENTITY_ID,
                entity.get_id());
    }

    private NopException newError(ErrorCode errorCode, IOrmEntitySet coll) {
        return new OrmException(errorCode).param(ARG_COLLECTION_NAME, coll.orm_collectionName()).param(ARG_OWNER,
                coll.orm_owner());
    }

    @Override
    public void unloadCollection(Collection<? extends IOrmEntity> collection) {
        checkValid();

        if (!(collection instanceof IOrmEntitySet))
            return;

        IOrmEntitySet coll = (IOrmEntitySet) collection;
        if (coll.orm_enhancer() != this)
            throw newError(ERR_ORM_COLLECTION_NOT_IN_SESSION, coll);

        coll.orm_unload();
    }

    @Override
    public Object save(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        internalSave(entity);

        if (this.isStateless())
            this.flushImmediately(entity);

        return entity.get_id();
    }

    @Override
    public Object saveDirectly(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        OrmEntityState state = entity.orm_state();
        LOG.debug("session.internalSave:{}", entity);

        if (!state.isTransient())
            throw newError(ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT, entity).param(ARG_STATUS, state);

        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        entity.orm_state(OrmEntityState.SAVING);
        entity.orm_entityModel(persister.getEntityModel());

        initEntityId(persister, entity);

        // 新建实体不会有延迟加载属性
        entity.orm_markFullyLoaded();

        flushSave(entity);

        return entity.get_id();
    }

    @Override
    public void updateDirectly(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        OrmEntityState state = entity.orm_state();
        if (!state.isManaged())
            throw newError(ERR_ORM_UPDATE_ENTITY_NOT_MANAGED, entity);

        if (!entity.orm_dirty())
            return;

        flushUpdate(entity);
    }

    @Override
    public void deleteDirectly(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        OrmEntityState state = entity.orm_state();
        if (!state.isManaged())
            throw newError(ERR_ORM_UPDATE_ENTITY_NOT_MANAGED, entity);

        flushDelete(entity);
    }

    private void update(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        internalUpdate(entity);

        if (this.isStateless())
            this.flushImmediately(entity);
    }

    public void internalUpdate(IOrmEntity entity) {
        OrmEntityState state = entity.orm_state();
        if (!state.isManaged() && !state.isSaving())
            throw newError(ERR_ORM_UPDATE_ENTITY_NOT_MANAGED, entity);
        //
        // if (flusher != null) {
        // flusher.addChangeDuringFlush(entity);
        // }
    }

    @Override
    public void internalMarkDirty(IOrmEntity entity) {
        if (entity.orm_enhancer() != this)
            return;

        markDirty();
        cache.markDirty(entity.orm_entityName());
        // if (flusher != null)
        // flusher.addChangeDuringFlush(entity);
    }

    @Override
    public void lock(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        if (entity.orm_locked()) {
            LOG.debug("nop.orm.entity-already-locked:entity={}", entity);
            return;
        }

        if (entity.orm_dirty())
            throw newError(ERR_ORM_NOT_ALLOW_LOCK_DIRTY_ENTITY, entity);

        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());

        if (!persister.lock(entity, persister.getEntityModel().getEagerLoadProps(), this, () -> {
            entity.orm_locked(false);
        })) {
            entity.orm_state(OrmEntityState.MISSING);

            // lock是重要的业务操作，如果未成功则应该抛出异常，避免后续在实体已被锁定的错误假设下工作
            throw newError(ERR_ORM_LOCK_ENTITY_FAIL, entity);
        } else {
            entity.orm_locked(true);

            // interceptPostLoad(entity);
        }
    }

    @Override
    public void saveOrUpdate(IOrmEntity entity) {
        if (this.contains(entity)) {
            this.update(entity);
        } else {
            this.save(entity);
        }
    }

    @Override
    public void delete(IOrmEntity entity) {
        checkValid();
        checkNotReadOnly();

        if (!this.contains(entity))
            throw newError(ERR_ORM_ENTITY_NOT_IN_SESSION, entity);

        internalDelete(entity);

        if (this.isStateless())
            this.flushImmediately(entity);
    }

    @Override
    public <T extends IOrmEntity> T findFirstByExample(T example) {
        checkValid();
        IEntityPersister persister = requireEntityPersister(example.orm_entityName());
        return (T) persister.findFirstByExample(example, this);
    }

    @Override
    public <T extends IOrmEntity> List<T> findAllByExample(T example, List<OrderFieldBean> orderBy) {
        checkValid();
        IEntityPersister persister = requireEntityPersister(example.orm_entityName());
        return persister.findAllByExample(example, orderBy, this);
    }

    @Override
    public <T extends IOrmEntity> List<T> findPageByExample(T example, List<OrderFieldBean> orderBy, long offset,
                                                            int limit) {
        checkValid();
        IEntityPersister persister = requireEntityPersister(example.orm_entityName());
        return persister.findPageByExample(example, orderBy, offset, limit, this);
    }

    @Override
    public long countByExample(IOrmEntity example) {
        checkValid();
        IEntityPersister persister = requireEntityPersister(example.orm_entityName());
        return persister.countByExample(example, this);
    }

    @Override
    public long deleteByExample(IOrmEntity example) {
        checkValid();
        IEntityPersister persister = requireEntityPersister(example.orm_entityName());
        return persister.deleteByExample(example, this);
    }

    @Override
    public long updateByExample(IOrmEntity example, IOrmEntity updated) {
        checkValid();
        IEntityPersister persister = requireEntityPersister(example.orm_entityName());

        // 没有任何属性修改，则直接返回
        if (!updated.orm_inited())
            return 0;

        return persister.updateByExample(example, updated, this);
    }

    @Override
    public void assembleAllCollectionInMemory(String collectionName) {
        checkValid();
        IEntityRelationModel rel = requireCollectionPersister(collectionName).getCollectionModel();

        Map<IOrmEntity, IOrmEntitySet> map = new HashMap<>();

        cache.forEachCurrent(rel.getRefEntityName(), entity -> {

            IOrmEntity owner = entity.orm_refEntity(rel.getRefPropName());
            if (owner != null) {
                IOrmEntitySet pc = map.get(owner);
                if (pc == null) {
                    pc = owner.orm_refEntitySet(rel.getName());
                    // 如果不是proxy，则忽略该集合
                    if (!pc.orm_proxy()) {
                        return;
                    }

                    map.put(owner, pc);

                    pc.orm_beginLoad();
                    pc.add(entity);
                } else {
                    pc.add(entity);
                }
            }
        });

        for (IOrmEntitySet<?> pc : map.values()) {
            pc.orm_endLoad();
        }
    }

    @Override
    public void assembleCollectionInMemory(Collection<? extends IOrmEntity> coll) {
        checkValid();
        if (!(coll instanceof IOrmEntitySet))
            return;

        IOrmEntitySet pc = (IOrmEntitySet) coll;
        if (!pc.orm_proxy())
            return;

        IOrmEntity owner = pc.orm_owner();
        pc.orm_beginLoad();

        ICollectionPersister persister = requireCollectionPersister(pc.orm_collectionName());
        IEntityRelationModel relModel = persister.getCollectionModel();
        String refEntityName = relModel.getRefEntityName();

        this.cache.forEachCurrent(refEntityName, entity -> {
            if (entity.orm_refEntity(relModel.getRefPropName()) == owner) {
                pc.orm_internalAdd(entity);
            }
        });
        pc.orm_endLoad();
    }

    @Override
    public void assembleSelectionInMemory(Object ormObject, FieldSelectionBean selection) {

    }

    @Override
    public void close() {
        if (closed)
            return;

        env.getOrmMetrics().onSessionClosed();

        LOG.debug("orm.session_close:{}", this);

        this.loadedOrmModel.decRef();

        cache.clear();
        if (sessionCache != null)
            sessionCache.clear();
        closed = true;

        if (onClose != null)
            for (Runnable task : onClose) {
                try {
                    task.run();
                } catch (Exception e) {
                    LOG.error("nop.orm.session-on-close-fail", e);
                }
            }
    }

    IOrmEntity makeProxy(String entityName, Object id) {
        checkContext();
        checkValid();

        IEntityPersister persister = requireEntityPersister(entityName);

        return _makeProxy(persister, id);
    }

    IOrmEntity _makeProxy(IEntityPersister persister, Object id) {
        IEntityModel entityModel = persister.getEntityModel();
        if (entityModel.isNoPrimaryKey()) {
            IOrmEntity entity = persister.newEntity(this);
            entity.orm_state(OrmEntityState.PROXY);
            return entity;
        }

        id = OrmEntityHelper.castId(entityModel, id);
        IOrmEntity entity = cache.get(entityModel.getName(), id);
        if (entity != null)
            return entity;

        entity = persister.newEntity(this);
        OrmEntityHelper.setId(entityModel, entity, id);

        entity.orm_state(OrmEntityState.PROXY);

        // 初始化集合属性
        for (IEntityRelationModel rel : entityModel.getRelations()) {
            if (rel.isToManyRelation()) {
                IOrmEntitySet refSet = entity.orm_refEntitySet(rel.getName());
                if (refSet == null)
                    throw new OrmException(ERR_ORM_COLLECTION_NOT_ALLOW_NULL)
                            .param(ARG_ENTITY_NAME, entityModel.getName()).param(ARG_PROP_NAME, rel.getName());
                refSet.orm_proxy(true);
            }
        }

        cache.add(entity);

        return entity;
    }

    @Override
    public IEntityModel getEntityModel(String entityName) {
        return requireEntityPersister(entityName).getEntityModel();
    }

    @Override
    public IEntityRelationModel getCollectionModel(String collectionName) {
        return requireCollectionPersister(collectionName).getCollectionModel();
    }

    @Override
    public IBatchActionQueue getBatchActionQueue(String querySpace) {
        return this.batchActionQueue.getBatchActionQueue(querySpace, this);
    }

    @Override
    public boolean internalLoadProperty(IOrmEntity entity, int propId) {
        checkContext();
        checkValid(entity);
        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        IEntityModel entityModel = persister.getEntityModel();

        IntArray propIds;
        if (!entity.orm_inited()) {
            // 第一次加载，则加载所有eager属性，以及指定的propId
            propIds = entityModel.getEagerLoadProps();
            if (propIds.indexOf(propId) < 0) {
                propIds = propIds.toMutable().add(propId);
            }
        } else {
            propIds = new MutableIntArray().add(propId);
        }

        return _internalLoad(persister, entity, propIds);
    }

    private boolean _internalLoad(IEntityPersister persister, IOrmEntity entity, IntArray propIds) {
        CompletionStage<Void> ret = persister.loadAsync(entity, propIds, null, this);
        FutureHelper.syncGet(ret);
        return entity.orm_state() != OrmEntityState.MISSING;
        // if (!persister.loadAsync(entity, propIds, this)) {
        // LOG.info("orm.err_load_property_fail:entity={}", entity);
        // entity.orm_state(OrmEntityState.MISSING);
        // return false;
        // } else {
        // // 由assemble函数调用postLoad和修改状态
        // //interceptPostLoad(entity);
        // return true;
        // }
    }

    private void markDirty() {
        this.dirty = true;
    }

    @Override
    public CompletionStage<Void> internalBatchLoadAsync(String entityName, Collection<IOrmEntity> entities,
                                                        IntArray propIds, FieldSelectionBean subSelection) {
        checkContext();
        checkValid();
        IEntityPersister persister = this.requireEntityPersister(entityName);

        return persister.batchLoadAsync(entities, propIds, subSelection, this);
    }

    @Override
    public CompletionStage<Void> internalBatchLoadCollectionAsync(String collectionName,
                                                                  Collection<IOrmEntitySet> collections, IntArray propIds, FieldSelectionBean subSelection) {
        checkContext();
        checkValid();
        ICollectionPersister persister = this.requireCollectionPersister(collectionName);

        return persister.batchLoadCollectionAsync(collections, propIds, subSelection, this);
    }

    @Override
    public void flushCollectionChange(IOrmEntitySet coll) {
        ICollectionPersister persister = requireCollectionPersister(coll.orm_collectionName());
        persister.flushCollectionChange(coll, this);
    }

    @Override
    public void internalSave(IOrmEntity entity) {
        OrmEntityState state = entity.orm_state();
        // 如果已经执行过save操作
        if (state.isSaving())
            return;

        LOG.debug("session.internalSave:{}", entity);

        if (!state.isTransient())
            throw newError(ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT, entity).param(ARG_STATUS, state);

        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        entity.orm_state(OrmEntityState.SAVING);
        entity.orm_entityModel(persister.getEntityModel());

        initEntityId(persister, entity);

        // 新建实体不会有延迟加载属性
        entity.orm_markFullyLoaded();

        IOrmEntity oldEntity = cache.add(entity);
        if (oldEntity != null && entity != oldEntity) {
            // need to load??
            // if (!oldEntity.orm_isProxyLoaded())
            // this.internalLoad(oldEntity);

            if (!oldEntity.orm_state().isGone()) {
                throw newError(ERR_ORM_SAVE_ENTITY_REPLACE_EXISTING_ENTITY, entity);
            }

            // 删除后又新建的情况
            if (oldEntity.orm_state().isDeleting()) {
                entity.orm_state(OrmEntityState.MANAGED);
                entity.orm_useOldValues(oldEntity);
            }
        }

        this.markDirty();

        if (flusher != null && flusher.isFlushing()) {
            flusher.addChangeDuringFlush(entity);
        }
    }

    void initEntityId(IEntityPersister persister, IOrmEntity entity) {
        if (!entity.orm_hasId()) {
            IEntityModel entityModel = persister.getEntityModel();
            for (IEntityRelationModel rel : entityModel.getRelations()) {
                if (rel.isOneToOne() && !rel.isReverseDepends()) {
                    // 发现一对一表的主表
                    IOrmEntity relatedEntity = entity.orm_refEntity(rel.getName());
                    if (relatedEntity != null) {
                        IEntityPersister relatedPersister = this
                                .requireEntityPersister(rel.getRefEntityModel().getName());
                        initEntityId(relatedPersister, relatedEntity);
                        OrmEntityHelper.copyRefProps(entity, rel, relatedEntity);
                        return;
                    }
                }
            }
            persister.generateId(entity);
        }
    }

    @Override
    public void internalDelete(IOrmEntity entity) {
        LOG.debug("orm.internalDelete:{}", entity);
        OrmEntityState state = entity.orm_state();

        if (state.isDeleting())
            return;

        if (state.isProxy()) {
            internalLoad(entity);
        }

        if (state.isTransient()) {
            entity.orm_state(OrmEntityState.DELETED);
        } else if (state.isSaving()) {
            entity.orm_state(OrmEntityState.DELETED);
        } else if (state.isMissing()) {  //NOPMD - suppressed EmptyControlStatement - do nothing
            // do nothing
        } else {
            entity.orm_state(OrmEntityState.DELETING);
            this.markDirty();
            cache.markDirty(entity.orm_entityName());
        }

        if (this.flusher != null && flusher.isFlushing()) {
            flusher.addChangeDuringFlush(entity);
        }
    }

    void flushImmediately(IOrmEntity entity) {
        boolean createExecutor = false;
        if (this.flusher == null) {
            this.flusher = new CascadeFlusher(this, cache);
            createExecutor = true;
        }
        boolean oldDirty = this.dirty;
        try {
            flusher.execute(entity);
            this.batchActionQueue.flush();
        } finally {
            if (createExecutor)
                this.flusher = null;
            this.dirty = oldDirty;
        }
    }

    @Override
    public void flushSave(IOrmEntity entity) {
        if (interceptPreSave(entity) == ProcessResult.STOP) {
            LOG.debug("session.preSave_VETO:entity={}", entity);
            return;
        }

        env.getOrmMetrics().onFlushSaveEntity(entity.orm_entityName());

        LOG.trace("session.flushSave:entity={}", entity);
        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        persister.save(entity, this);
    }

    @Override
    public void flushUpdate(IOrmEntity entity) {
        if (interceptPreUpdate(entity) == ProcessResult.STOP) {
            LOG.debug("session.preUpdate_VETO:entity={}", entity);
            return;
        }

        env.getOrmMetrics().onFlushUpdateEntity(entity.orm_entityName());

        LOG.trace("session.flushUpdate:entity={}", entity);
        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        persister.update(entity, this);
    }

    @Override
    public void flushDelete(IOrmEntity entity) {
        if (interceptPreDelete(entity) == ProcessResult.STOP) {
            LOG.debug("session.preDelete_VETO:entity={}", entity);
            return;
        }

        env.getOrmMetrics().onFlushDeleteEntity(entity.orm_entityName());

        LOG.trace("session.flushDelete:entity={}", entity);
        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        persister.delete(entity, this);
    }

    void interceptPostLoad(IOrmEntity entity) {
        entity.orm_postLoad();
        if (loadedOrmModel.getOrmInterceptor() != null)
            loadedOrmModel.getOrmInterceptor().postLoad(entity);

        for (IOrmInterceptor interceptor : interceptors)
            interceptor.postLoad(entity);
    }

    void interceptPostSave(IOrmEntity entity) {
        entity.orm_postSave();
        if (loadedOrmModel.getOrmInterceptor() != null)
            loadedOrmModel.getOrmInterceptor().postSave(entity);
        for (IOrmInterceptor interceptor : interceptors)
            interceptor.postSave(entity);
    }

    void interceptPostUpdate(IOrmEntity entity) {
        entity.orm_postUpdate();
        if (loadedOrmModel.getOrmInterceptor() != null)
            loadedOrmModel.getOrmInterceptor().postUpdate(entity);
        for (IOrmInterceptor interceptor : interceptors)
            interceptor.postUpdate(entity);
    }

    void interceptPostDelete(IOrmEntity entity) {
        entity.orm_postDelete();

        if (loadedOrmModel.getOrmInterceptor() != null)
            loadedOrmModel.getOrmInterceptor().postDelete(entity);
        for (IOrmInterceptor interceptor : interceptors)
            interceptor.postDelete(entity);
    }

    void interceptPreFlush() {
        if (loadedOrmModel.getOrmInterceptor() != null)
            loadedOrmModel.getOrmInterceptor().preFlush();

        for (IOrmInterceptor interceptor : interceptors)
            interceptor.preFlush();
    }

    void interceptPostFlush(Throwable e) {
        if (loadedOrmModel.getOrmInterceptor() != null)
            loadedOrmModel.getOrmInterceptor().postFlush(e);

        for (IOrmInterceptor interceptor : interceptors)
            interceptor.postFlush(e);
    }

    ProcessResult interceptPreSave(IOrmEntity entity) {
        if (entity.orm_preSave() == ProcessResult.STOP)
            return ProcessResult.STOP;
        if (loadedOrmModel.getOrmInterceptor() != null) {
            if (loadedOrmModel.getOrmInterceptor().preSave(entity) == ProcessResult.STOP)
                return ProcessResult.STOP;
        }

        for (IOrmInterceptor interceptor : interceptors)
            if (interceptor.preSave(entity) == ProcessResult.STOP)
                return ProcessResult.STOP;

        return ProcessResult.CONTINUE;
    }

    ProcessResult interceptPreUpdate(IOrmEntity entity) {
        if (entity.orm_preUpdate() == ProcessResult.STOP)
            return ProcessResult.STOP;

        if (loadedOrmModel.getOrmInterceptor() != null) {
            if (loadedOrmModel.getOrmInterceptor().preUpdate(entity) == ProcessResult.STOP)
                return ProcessResult.STOP;
        }

        for (IOrmInterceptor interceptor : interceptors)
            if (interceptor.preUpdate(entity) == ProcessResult.STOP)
                return ProcessResult.STOP;

        return ProcessResult.CONTINUE;
    }

    ProcessResult interceptPreDelete(IOrmEntity entity) {
        if (entity.orm_preDelete() == ProcessResult.STOP)
            return ProcessResult.STOP;

        if (loadedOrmModel.getOrmInterceptor() != null) {
            if (loadedOrmModel.getOrmInterceptor().preDelete(entity) == ProcessResult.STOP)
                return ProcessResult.STOP;
        }

        for (IOrmInterceptor interceptor : interceptors)
            if (interceptor.preDelete(entity) == ProcessResult.STOP)
                return ProcessResult.STOP;

        return ProcessResult.CONTINUE;
    }

    @Override
    public void persisterPostDelete(IOrmEntity entity) {
        this.interceptPostDelete(entity);
        entity.orm_state(OrmEntityState.DELETED);
    }

    @Override
    public void persisterPostUpdate(IOrmEntity entity) {
        this.interceptPostUpdate(entity);
        entity.orm_clearDirty();
    }

    @Override
    public void persisterPostSave(IOrmEntity entity) {
        this.interceptPostSave(entity);
        entity.orm_clearDirty();
        entity.orm_state(OrmEntityState.MANAGED);
    }

    @Override
    public Object initEntityId(IOrmEntity entity) {
        checkValid();

        IEntityPersister persister = this.requireEntityPersister(entity.orm_entityName());
        this.initEntityId(persister, entity);
        return entity.get_id();
    }

    @Override
    public Object internalCompute(IOrmEntity entity, String propName, Map<String, Object> args) {
        IEntityModel entityModel = entity.orm_entityModel();
        IEntityPropModel propModel = entityModel.getProp(propName, false);
        if (!(propModel instanceof IComputePropModel))
            throw newError(ERR_ORM_NOT_SUPPORT_COMPUTE, entity).param(ARG_PROP_NAME, propName);

        IComputePropModel computeModel = (IComputePropModel) propModel;
        return computeModel.computeValue(entity, args);
    }

    @Override
    public void internalClearDirty(IOrmEntity entity) {

    }

    @Override
    public void internalMarkExtDirty(IOrmEntity entity) {
        markDirty();
        this.cache.markExtDirty(entity.get_entityName());
    }

    @Override
    public void internalClearExtDirty(IOrmEntity entity) {

    }

    @Override
    public IOrmEntity internalLoadRefEntity(IOrmEntity entity, String propName) {
        checkContext();
        checkValid(entity);
        IEntityPersister persister = requireEntityPersister(entity.orm_entityName());
        IEntityModel entityModel = persister.getEntityModel();
        IEntityRelationModel relModel = entityModel.getRelation(propName, false);
        if (!relModel.isToOneRelation())
            throw newError(ERR_ORM_ENTITY_PROP_NOT_REF_ENTITY, entity).param(ARG_PROP_NAME, propName);

        IEntityModel refEntityModel = relModel.getRefEntityModel();
        List<? extends IEntityJoinConditionModel> join = relModel.getJoin();
        if (relModel.isJoinOnNonPkColumn()) {
            return loadRefEntityByProp(entity, relModel);
        }

        Object id;
        if (join.size() == 1) {
            IEntityJoinConditionModel cond = join.get(0);
            Object value = OrmEntityHelper.getLeftValue(cond, entity);
            if (value == null)
                return null;
            id = OrmEntityHelper.castId(refEntityModel, value);
        } else {
            Object[] values = new Object[refEntityModel.getPkColumns().size()];
            int i = 0;
            for (IEntityJoinConditionModel cond : join) {
                if (cond.getRightPropModel() == null) {
                    Object value = OrmEntityHelper.getLeftValue(cond, entity);
                    if (!Objects.equals(value, cond.getRightValue()))
                        return null;
                    continue;
                }

                Object value = OrmEntityHelper.getLeftValue(cond, entity);
                // 如果复合主键中存在字段的值为null，则直接返回null。这里假设了主键字段都不为null
                if (value == null)
                    return null;

                values[i] = value;
                i++;
            }
            id = OrmCompositePk.build(refEntityModel, values);
        }
        if (id == null)
            return null;
        return load(refEntityModel.getName(), id);
    }

    // 通过非主键字段加载关联对象
    private IOrmEntity loadRefEntityByProp(IOrmEntity entity, IEntityRelationModel relModel) {
        IOrmEntity example = this.newEntity(relModel.getRefEntityName());
        for (IEntityJoinConditionModel cond : relModel.getJoin()) {
            Object leftValue = OrmEntityHelper.getLeftValue(cond, entity);
            if (cond.getRightPropModel() != null) {
                OrmEntityHelper.setPropValue(cond.getRightPropModel(), example, leftValue);
            }
        }
        return findFirstByExample(example);
    }


    @Override
    public void internalLoadCollection(IOrmEntitySet coll) {
        checkContext();
        checkValid(coll);
        ICollectionPersister persister = requireCollectionPersister(coll.orm_collectionName());
        persister.loadCollection(coll, persister.getCollectionModel().getRefEntityModel().getEagerLoadProps(), null,
                this);
    }

    @Override
    public ICache<Object, Object> getSessionCache() {
        checkContext();
        if (sessionCache == null)
            sessionCache = new MapCache<>("orm-session-cache", false);
        return sessionCache;
    }

    @Override
    public void reset() {
        checkContext();
        checkValid();
        cache.forEachCurrent(entity -> {
            resetEntity(entity);
        });
    }

    private void resetEntity(IOrmEntity entity) {
        entity.orm_reset();
        IEntityPersister persister = requireEntityPersister(entity.orm_entityName());
        IEntityModel entityModel = persister.getEntityModel();
        for (IEntityRelationModel rel : entityModel.getRelations()) {
            if (rel.isToManyRelation()) {
                IOrmEntitySet pc = entity.orm_refEntitySet(rel.getName());
                pc.orm_reset();
            }
        }
    }

    @Override
    public void refresh(IOrmEntity entity) {
        unload(entity);
    }

    @Override
    public void attach(IOrmEntity entity) {
        checkContext();
        if (entity.orm_enhancer() == this)
            return;

        if (isBindToOtherSession(entity))
            throw newError(ERR_ORM_ENTITY_NOT_DETACHED, entity);
        cache.add(entity);

        IEntityModel entityModel = entity.orm_entityModel();
        for (IEntityRelationModel relModel : entityModel.getRelations()) {
            String propName = relModel.getName();
            if (!entity.orm_refLoaded(propName))
                continue;

            if (relModel.isToOneRelation()) {
                IOrmEntity refEntity = entity.orm_refEntity(propName);
                if (refEntity.orm_enhancer() == this)
                    continue;

                if (refEntity.orm_enhancer() == null) {
                    attach(refEntity);
                } else {
                    entity.orm_unsetRef(propName);
                }
            } else if (relModel.isToManyRelation()) {
                IOrmEntitySet<? extends IOrmEntity> refSet = entity.orm_refEntitySet(propName);
                for (IOrmEntity refEntity : refSet) {
                    if (isBindToOtherSession(refEntity)) {
                        refSet.orm_unload();
                        break;
                    }
                    attach(refEntity);
                }
            }
        }
    }

    @Override
    public void detach(IOrmEntity entity, FieldSelectionBean selection) {
        if (entity.orm_enhancer() == null)
            return;

        entity.orm_detach();

        IEntityModel entityModel = entity.orm_entityModel();

        if (selection != null) {
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String name = entry.getKey();
                FieldSelectionBean field = entry.getValue();
                if (field.getName() != null)
                    name = field.getName();

                IEntityRelationModel relModel = entityModel.getRelation(name, true);
                if (relModel != null) {
                    detachProp(entity, name, field);
                }
            }
        } else {
            for (IEntityRelationModel relModel : entityModel.getRelations()) {
                detachProp(entity, relModel.getName(), null);
            }
        }
    }

    private void detachProp(IOrmEntity entity, String name, FieldSelectionBean field) {
        Object value = entity.orm_propValueByName(name);
        if (value instanceof IOrmEntity) {
            IOrmEntity refEntity = (IOrmEntity) value;
            if (!refEntity.orm_proxy()) {
                detach(refEntity, field);
            }
        } else if (value instanceof IOrmEntitySet) {
            IOrmEntitySet<IOrmEntity> refSet = ((IOrmEntitySet<IOrmEntity>) value);
            if (!refSet.orm_proxy()) {
                for (IOrmEntity refEntity : refSet) {
                    detach(refEntity, field);
                }
            }
        }
    }

    private boolean isBindToOtherSession(IOrmEntity entity) {
        IOrmEntityEnhancer session = entity.orm_enhancer();
        return session != null && session != this && !session.isClosed();
    }

    @Override
    public long executeUpdate(SQL sql) {
        checkContext();
        IQueryExecutor executor = env.getQueryExecutor(sql.getQuerySpace());
        return executor.executeUpdate(this, sql);
    }

    @Override
    public <T> T executeQuery(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<? super IDataSet, T> callback) {
        checkContext();
        IQueryExecutor executor = env.getQueryExecutor(sql.getQuerySpace());
        return executor.executeQuery(this, sql, range, callback);
    }

    @Override
    public <T> T executeStatement(@Nonnull SQL sql, LongRangeBean range, @Nonnull Function<IComplexDataSet, T> callback,
                                  ICancelToken cancelToken) {
        checkContext();
        IQueryExecutor executor = env.getQueryExecutor(sql.getQuerySpace());
        return executor.executeStatement(this, sql, range, callback, cancelToken);
    }

    @Override
    public boolean isEntityMode() {
        return entityMode;
    }

    @Override
    public void setEntityMode(boolean entityMode) {
        this.entityMode = entityMode;
    }

    @Override
    public Object internalReadId(Object[] values, int fromIndex, IEntityModel entityModel) {
        return OrmAssembly.readId(values, fromIndex, entityModel);
    }

    @Override
    public Object castId(IEntityModel entityModel, Object id) {
        return OrmEntityHelper.castId(entityModel, id);
    }

    @Override
    public IDaoEntity internalMakeEntity(String entityName, Object id, Object[] propValues, IntArray propIds) {
        IOrmEntity entity = internalLoad(entityName, id);
        internalAssemble(entity, propValues, propIds);
        return entity;
    }
}