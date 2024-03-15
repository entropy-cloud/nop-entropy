/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.collections.IntArray;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmEntityState;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.session.IOrmSessionImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 对实体和集合对象进行批量加载。类似于GraphQL的DataLoader机制，但是实现层面上专门针对OrmEntity进行了优化处理。
 *
 * @author canonical_entropy@163.com
 */
public class OrmBatchLoadQueueImpl implements IOrmBatchLoadQueueImplementor {
    static final Logger LOG = LoggerFactory.getLogger(OrmBatchLoadQueueImpl.class);

    static class LoadQueue {
        /**
         * 初次加载eagerProps
         */
        final Map<String/* entityName */, EntityLoad> entityLoadMap = new HashMap<>();

        /**
         * 已经加载了eagerProps的实体只需要处理延迟加载的属性
         */
        final Map<String, EntityLoad> entityPropLoadMap = new HashMap<>();

        final Map<String /* collectionName */, CollectionLoad> collectionLoadMap = new HashMap<>();

        void clear() {
            entityLoadMap.clear();
            entityPropLoadMap.clear();
            collectionLoadMap.clear();
        }
    }

    private LoadQueue loadQueue = null;

    static abstract class AbstractLoad {
        final IEntityModel entityModel;

        IntArray propIds;

        /**
         * 如果非空，则将实体装载完毕之后还需要继续装载如下指定字段
         */
        FieldSelectionBean subSelection;

        public AbstractLoad(IEntityModel entityModel, boolean eager) {
            this.entityModel = entityModel;
            this.propIds = eager ? entityModel.getEagerLoadProps() : entityModel.getMinimumLazyLoadProps();
        }

        FieldSelectionBean addProp(String propName) {
            return addProp(propName, false);
        }

        /**
         * 增加需要加载的属性。如果是复合属性，则把子属性记录到subSelection上。
         *
         * @param propName 可能是通过符号.分隔的复合属性名称。
         * @param hasNext  如果hasNext为true，则返回一个可以修改的subField，用于追加子属性
         */
        FieldSelectionBean addProp(String propName, boolean hasNext) {
            IEntityPropModel propModel = entityModel.getProp(propName, true);
            if (propModel != null) {
                if (propModel.isAliasModel()) {
                    return addProp(propModel.getAliasPropPath(), hasNext);
                } else {
                    // 如果指向关联对象，则装载本对象上的字段值之后，需要再加载关联对象
                    if (propModel.isRelationModel())
                        hasNext = true;

                    addProp(propModel);
                    if (hasNext)
                        return makeSubSelection(propName);
                    return null;
                }
            } else {
                int pos = propName.indexOf('.');
                if (pos > 0) {
                    // 如果是复合属性
                    String name = propName.substring(0, pos);
                    // 属性可能对应于多个字段组成的外键关联
                    propModel = entityModel.getProp(name, true);
                    if (propModel != null) {
                        if (propModel.isAliasModel()) {
                            String aliasPath = propModel.getAliasPropPath();
                            return addProp(aliasPath + propName.substring(pos), hasNext);
                        } else {
                            addProp(propModel);
                            return makeSubSelection(name).addCompositeField(propName.substring(pos + 1), hasNext);
                        }
                    }
                }
            }
            // 执行到这里时表示是未知属性
            return null;
        }

        void addProp(IEntityPropModel prop) {
            // 不是延迟加载属性，则必然已经在首次加载的propIds集合中，不需要向propIds集合中追加
            if (!prop.hasLazyLoadColumn())
                return;
            if (prop.isSingleColumn()) {
                this.propIds = propIds.merge(prop.getColumnPropId());
            } else {
                this.propIds = propIds.merge(prop.getColumnPropIds());
            }
        }

        void addProp(int propId) {
            this.propIds = propIds.merge(propId);
        }

        void addProps(IntArray propIds) {
            this.propIds = this.propIds.merge(propIds);
        }

        FieldSelectionBean makeSubSelection(String propName) {
            if (subSelection == null) {
                subSelection = new FieldSelectionBean();
            } else if (subSelection.frozen()) {
                subSelection = subSelection.deepClone();
            }
            return subSelection.makeSubField(propName, true);
        }

        void addProps(FieldSelectionBean selection) {
            for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
                String name = entry.getKey();
                addProp(name, entry.getValue());
            }
        }

        void addProp(String name, FieldSelectionBean field) {
            if (field.getName() != null)
                name = field.getName();

            boolean hasNext = field.hasField();
            FieldSelectionBean sub = addProp(name, hasNext);
            if (sub == null) {
                // 不是实体属性
                return;
            }

            if (hasNext) {
                sub.mergeFields(field.getFields());
            }
        }
    }

    static class EntityLoad extends AbstractLoad {
        Set<IOrmEntity> entities = new HashSet<>();

        public EntityLoad(IEntityModel entityModel, boolean eager) {
            super(entityModel, eager);
        }
    }

    static class CollectionLoad extends AbstractLoad {
        Set<IOrmEntitySet> collections = new HashSet<>();

        public CollectionLoad(IEntityRelationModel relModel) {
            super(relModel.getRefEntityModel(), true);
        }
    }

    private final IOrmSessionImplementor session;

    private volatile boolean flushing = false;

    private List<Runnable> afterFlushCallbacks = null;

    public OrmBatchLoadQueueImpl(IOrmSessionImplementor session) {
        this.session = session;
    }

    private LoadQueue makeQueue() {
        if (loadQueue == null)
            loadQueue = new LoadQueue();
        return loadQueue;
    }

    @Override
    public IOrmBatchLoadQueue enqueueMany(Collection<?> ormObjects) {
        if (ormObjects == null)
            return this;

        if (ormObjects instanceof IOrmEntitySet) {
            IOrmEntitySet coll = (IOrmEntitySet) ormObjects;
            this._enqueueCollection(coll);
            return this;
        }

        for (Object object : ormObjects) {
            enqueue(object);
        }
        return this;
    }

    @Override
    public IOrmBatchLoadQueue enqueueManyProps(Collection<?> ormObjects, Collection<String> propNames) {
        if (ormObjects == null)
            return this;

        if (propNames == null || propNames.isEmpty())
            return this;

        if (ormObjects instanceof IOrmEntitySet) {
            IOrmEntitySet entitySet = (IOrmEntitySet) ormObjects;
            if (entitySet.orm_proxy()) {
                this._enqueueCollection(entitySet, FieldSelectionBean.fromProps(propNames));
                return this;
            }
        }

        for (Object entity : ormObjects) {
            for (String propName : propNames) {
                enqueueProp(entity, propName);
            }
        }
        return this;
    }

    @Override
    public IOrmBatchLoadQueue enqueueProp(Object ormObject, String propName) {
        if (ormObject == null || propName == null || propName.length() <= 0)
            return this;

        _enqueueProp(ormObject, propName);
        return this;
    }

    void _enqueueProp(Object obj, String propName) {
        if (obj instanceof IOrmEntitySet) {
            _enqueueCollectionProp((IOrmEntitySet) obj, propName);
        } else if (obj instanceof IOrmEntity) {
            _enqueueEntityProp((IOrmEntity) obj, propName,null);
        } else if (obj instanceof Map) {
            int pos = propName.indexOf('.');
            if (pos < 0) {
                Object value = ((Map<?, ?>) obj).get(propName);
                if (value != null) {
                    enqueue(value);
                }
            } else {
                String key = propName.substring(0, pos);
                Object value = ((Map<?, ?>) obj).get(key);
                if (value != null) {
                    _enqueueProp(value, propName.substring(pos + 1));
                }
            }
        }
    }

    @Override
    public IOrmBatchLoadQueue enqueue(Object ormObject) {
        if (ormObject instanceof IOrmEntity) {
            _enqueueEntity((IOrmEntity) ormObject);
        } else if (ormObject instanceof IOrmEntitySet) {
            _enqueueCollection((IOrmEntitySet) ormObject);
        }
        return this;
    }

    @Override
    public void internalEnqueueCollection(Collection<IOrmEntity> coll, IntArray propIds,
                                          FieldSelectionBean subSelection) {
        for (IOrmEntity entity : coll) {
            boolean eager = entity.orm_proxy();
            EntityLoad load = makeEntityLoad(entity.orm_entityName(), eager);
            load.addProps(propIds);
            if (subSelection != null)
                load.addProps(subSelection);
        }
    }

    EntityLoad _enqueueEntity(IOrmEntity entity) {
        // 实体已经加载，则直接跳过
        if (!entity.orm_proxy())
            return null;

        String entityName = entity.orm_entityName();
        EntityLoad load = makeEntityLoad(entityName, true);
        load.entities.add(entity);
        LOG.debug("nop.orm.enqueue-entity-to-load:entity={}", entity);
        return load;
    }

    CollectionLoad _enqueueCollection(IOrmEntitySet coll) {
        // 集合已经加载，则直接跳过
        if (!coll.orm_proxy()) {
            return null;
        }

        CollectionLoad load = makeCollectionLoad(coll.orm_collectionName());
        load.collections.add(coll);
        LOG.debug("nop.orm.enqueue-collection-to-load:coll={}", coll);
        return load;
    }

    EntityLoad makeEntityLoad(String entityName, boolean eager) {
        Map<String, EntityLoad> loadMap = eager ? makeQueue().entityPropLoadMap : makeQueue().entityLoadMap;
        EntityLoad load = loadMap.get(entityName);
        if (load == null) {
            IEntityModel entityModel = session.getEntityModel(entityName);
            load = new EntityLoad(entityModel, eager);
            loadMap.put(entityName, load);
        }
        return load;
    }

    CollectionLoad makeCollectionLoad(String collectionName) {
        Map<String, CollectionLoad> collMap = makeQueue().collectionLoadMap;
        CollectionLoad load = collMap.get(collectionName);
        if (load == null) {
            IEntityRelationModel relModel = session.getCollectionModel(collectionName);
            load = new CollectionLoad(relModel);
            collMap.put(collectionName, load);
        }
        return load;
    }

    void _enqueueCollectionProp(IOrmEntitySet<IOrmEntity> coll, String propName) {
        CollectionLoad load = _enqueueCollection(coll);
        if (load != null) {
            // collection尚未加载。如果没有延迟加载的属性，则没有必要再逐个检查每个属性是否需要加载
            if (load.entityModel.hasLazyColumn())
                load.addProp(propName);
        } else {
            for (IOrmEntity entity : coll) {
                _enqueueEntityProp(entity, propName, null);
            }
        }
    }

    void _enqueueEntityProp(IOrmEntity entity, String propName, FieldSelectionBean subSelection) {
        OrmEntityState state = entity.orm_state();
        if (!state.isAllowLoad())
            return;

        EntityLoad load = _enqueueEntity(entity);
        if (load != null) {
            // load不为null表示entity尚未加载
            if (subSelection != null) {
                load.addProp(propName, subSelection);
            } else {
                load.addProp(propName);
            }
        } else {
            // eagerProps已经加载
            IEntityModel entityModel = entity.orm_entityModel();
            IEntityPropModel propModel = entityModel.getProp(propName, true);
            if (propModel != null) {
                if (propModel.isAliasModel()) {
                    _enqueueEntityProp(entity, propModel.getAliasPropPath(), subSelection);
                    return;
                }
                EntityLoad propLoad = addPropLoad(entityModel, propModel, entity);
                if (propModel.isRelationModel()) {
                    if (propLoad != null) {
                        if (subSelection != null) {
                            propLoad.addProp(propName, subSelection);
                        } else {
                            propLoad.addProp(propName, true);
                        }
                    } else {
                        Object value = entity.orm_propValueByName(propName);
                        if (value != null) {
                            _enqueueWithSelection(value, subSelection);
                        }
                    }
                }
                return;
            }

            // 如果propName不是实体属性则直接忽略
            int pos = propName.indexOf('.');
            if (pos < 0)
                return;

            String name = propName.substring(0, pos);
            propModel = entityModel.getProp(name, true);
            // 如果不是实体属性，则直接忽略
            if (propModel == null)
                return;

            if (propModel.isAliasModel()) {
                _enqueueEntityProp(entity, propModel.getAliasPropPath() + propName.substring(pos), subSelection);
                return;
            }

            EntityLoad propLoad = addPropLoad(entityModel, propModel, entity);
            if (propLoad != null) {
                // 复合属性的第一部分需要延迟加载，则后续部分需要注册到subSelection上
                boolean hasNext = subSelection != null && subSelection.hasField();
                FieldSelectionBean field = propLoad.makeSubSelection(propModel.getName()).addCompositeField(propName.substring(pos + 1), hasNext);
                if (hasNext) {
                    field.merge(subSelection);
                }
            } else {
                // prop已经被加载，则获取到关联对象，递归加载关联对象上的属性
                Object value = getPropValue(entity, propModel);
                if (value != null)
                    enqueueProp(value, propName.substring(pos + 1));
            }
        }
    }

    private Object getPropValue(IOrmEntity entity, IEntityPropModel propModel) {
        if (propModel.isColumnModel())
            return entity.orm_propValue(propModel.getColumnPropId());
        return entity.orm_propValueByName(propModel.getName());
    }

    /**
     * 检查实体属性是否已经被加载。如果尚未加载，则放入延迟加载队列
     *
     * @return 返回对应实体类型的延迟加载队列
     */
    EntityLoad addPropLoad(IEntityModel entityModel, IEntityPropModel propModel, IOrmEntity entity) {
        EntityLoad load = null;
        if (propModel.isSingleColumn()) {
            int propId = propModel.getColumnPropId();
            if (!entity.orm_propInited(propId)) {
                load = addPropLoad(entityModel, propId);
                load.entities.add(entity);
            }
        } else {
            int[] propIds = propModel.getColumnPropIds();
            for (int i = 0, n = propIds.length; i < n; i++) {
                int propId = propIds[i];
                if (!entity.orm_propInited(propId)) {
                    if (load == null) {
                        load = addPropLoad(entityModel, propId);
                    }
                    load.entities.add(entity);
                }
            }
        }
        return load;
    }

    EntityLoad addPropLoad(IEntityModel entityModel, int propId) {
        EntityLoad load = makeEntityLoad(entityModel.getName(), false);
        load.addProp(propId);
        return load;
    }

    @Override
    public IOrmBatchLoadQueue enqueueEntity(IOrmEntity entity, FieldSelectionBean selection) {
        if (selection == null || !selection.hasField()) {
            this._enqueueEntity(entity);
        } else {
            this._enqueueEntity(entity, selection);
        }
        return this;
    }

    @Override
    public IOrmBatchLoadQueue enqueueSelection(Collection<?> ormObjects, FieldSelectionBean selection) {
        if (ormObjects == null)
            return this;

        if (selection == null || !selection.hasField()) {
            enqueueMany(ormObjects);
            return this;
        }

        if (ormObjects instanceof IOrmEntitySet) {
            _enqueueCollection((IOrmEntitySet) ormObjects, selection);
            return this;
        }

        for (Object obj : ormObjects) {
            _enqueueWithSelection(obj, selection);
        }
        return this;
    }

    private void _enqueueWithSelection(Object obj, FieldSelectionBean selection) {
        if (selection == null || !selection.hasField()) {
            enqueue(obj);
            return;
        }

        if (obj instanceof IOrmEntity) {
            _enqueueEntity((IOrmEntity) obj, selection);
        } else if (obj instanceof IOrmEntitySet) {
            _enqueueCollection((IOrmEntitySet) obj, selection);
        } else if (obj instanceof Map) {
            _enqueueMap((Map<String, Object>) obj, selection);
        }
    }

    private void _enqueueMap(Map<String, Object> map, FieldSelectionBean selection) {
        for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
            _enqueueProp(map, entry.getKey());
        }
    }

    private void _enqueueEntity(IOrmEntity entity, FieldSelectionBean selection) {
        EntityLoad entityLoad = _enqueueEntity(entity);
        for (Map.Entry<String, FieldSelectionBean> entry : selection.getFields().entrySet()) {
            String name = entry.getKey();
            FieldSelectionBean field = entry.getValue();
            if (field.getName() != null) {
                name = field.getName();
            }

            if (entityLoad != null) {
                entityLoad.addProp(name, field);
            } else {
                _enqueueEntityProp(entity, name, field);
            }
        }
    }

    private void _enqueueCollection(IOrmEntitySet<IOrmEntity> coll, FieldSelectionBean selection) {
        CollectionLoad load = _enqueueCollection(coll);
        if (load != null) {
            if (load.entityModel.hasLazyColumn())
                load.addProps(selection);
        } else {
            for (IOrmEntity entity : coll) {
                _enqueueEntity(entity, selection);
            }
        }
    }

    @Override
    public void flush() {
        // 避免递归执行导致死循环
        if (this.flushing)
            return;

        this.flushing = true;
        try {
            LoadQueue queue = this.loadQueue;
            this.loadQueue = null;
            if (queue != null) {
                do {
                    List<CompletionStage<?>> futures = new ArrayList<>();
                    // 先加载集合，其中有可能已经包含了后续要加载的实体
                    _flushCollection(queue, futures);
                    _flushEntity(queue, futures);

                    queue = this.loadQueue;
                    this.loadQueue = null;

                    FutureHelper.syncGet(FutureHelper.waitAll(futures));
                } while (queue != null);
            }
            invokeCallback();
        } finally {
            this.afterFlushCallbacks = null;
            this.flushing = false;
        }
    }

    private void invokeCallback() {
        List<Runnable> tasks = this.afterFlushCallbacks;
        if (tasks != null) {
            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return loadQueue != null;
    }

    @Override
    public IOrmBatchLoadQueue afterFlush(Runnable task) {
        if (loadQueue == null) {
            task.run();
        } else {
            if (afterFlushCallbacks == null)
                afterFlushCallbacks = new ArrayList<>();
            afterFlushCallbacks.add(task);
        }
        return this;
    }

    void _flushCollection(LoadQueue queue, List<CompletionStage<?>> futures) {
        for (Map.Entry<String, CollectionLoad> entry : queue.collectionLoadMap.entrySet()) {
            CollectionLoad load = entry.getValue();
            CompletionStage<Void> future = session.internalBatchLoadCollectionAsync(entry.getKey(), load.collections,
                    load.propIds, load.subSelection);

            future = future.thenRun(() -> {
                if (!load.entityModel.hasLazyColumn()) {
                    EntityLoad entityLoad = queue.entityLoadMap.get(load.entityModel.getName());
                    if (entityLoad != null) {
                        entityLoad.entities.removeIf(entity -> !entity.orm_proxy());
                    }
                }

                if (load.subSelection != null) {
                    enqueueSelection(load.collections, load.subSelection);
                }
            });

            FutureHelper.collectWaiting(future, futures);
        }
    }

    void _flushEntity(LoadQueue queue, List<CompletionStage<?>> futures) {
        for (Map.Entry<String, EntityLoad> entry : queue.entityLoadMap.entrySet()) {
            EntityLoad load = entry.getValue();
            CompletionStage<Void> future = session.internalBatchLoadAsync(entry.getKey(), load.entities, load.propIds,
                    load.subSelection);

            if (load.subSelection != null) {
                future = future.thenRun(() -> {
                    enqueueSelection(load.entities, load.subSelection);
                });
            }
            FutureHelper.collectWaiting(future, futures);
        }

        for (Map.Entry<String, EntityLoad> entry : queue.entityPropLoadMap.entrySet()) {
            EntityLoad load = entry.getValue();
            CompletionStage<Void> future = session.internalBatchLoadAsync(entry.getKey(), load.entities, load.propIds,
                    load.subSelection);

            if (load.subSelection != null) {
                future = future.thenRun(() -> {
                    enqueueSelection(load.entities, load.subSelection);
                });
            }

            FutureHelper.collectWaiting(future, futures);
        }
    }
}