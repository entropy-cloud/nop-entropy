/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.session;

import io.nop.api.core.util.Guard;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmEntityState;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.IEntityJoinConditionModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityPropModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.support.OrmEntityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static io.nop.orm.OrmErrors.ERR_ORM_FLUSH_LOOP_COUNT_EXCEED_LIMIT;

public class CascadeFlusher {
    private static final Logger LOG = LoggerFactory.getLogger(CascadeFlusher.class);

    static final int MAX_FLUSH_LOOP_COUNT = 10;

    private final IOrmSessionImplementor session;
    private final IOrmSessionEntityCache sessionCache;
    private final List<IOrmEntity> waitDeletes = new ArrayList<>();
    private final List<IOrmEntitySet> waitDeleteCollections = new ArrayList<>();

    // 在flush执行过程中，interceptor或者IPersistLifecycle的回调函数有可能会修改其他实体。
    // 从性能角度考虑，要求主动调用session上的save/update等函数来主动标记所有被修改的实体，这样就不必做递归处理。
    private List<IOrmEntity> changedDuringFlush;

    private boolean flushing = false;

    public CascadeFlusher(IOrmSessionImplementor session, IOrmSessionEntityCache sessionCache) {
        this.session = session;
        this.sessionCache = sessionCache;
    }

    public void addChangeDuringFlush(IOrmEntity entity) {
        if (changedDuringFlush == null) {
            changedDuringFlush = new ArrayList<>();
        }
        if (changedDuringFlush.size() > 1 && changedDuringFlush.get(changedDuringFlush.size() - 1) == entity)
            return;

        changedDuringFlush.add(entity);
    }

    public void execute() {
        // 标记为flushVisiting的实体不再需要被递归处理
        sessionCache.forEachDirty(entity -> cascadeEntity(entity, false));

        // 加载所有待删除的实体，这样下面的processWaitDeletes()才能够针对单实体进行处理
        session.flushBatchLoadQueue();

        this.processWaitDeletes();

        this.flushing = true;
        sessionCache.forEachDirty(entity -> {
            entity.orm_flushVisiting(false);
            internalFlush(entity);
            if (entity.orm_extDirty())
                entity.orm_extDirty(false);
        });

        flushChanged();

        this.flushing = false;
    }

    boolean isFlushing() {
        return flushing;
    }

    // 将internalFlush过程中产生的变化刷新到数据库中
    void flushChanged() {
        int count = 0;
        while (this.changedDuringFlush != null) {
            // 防止错误编程导致实体在flush过程中不断被修改形成死循环
            if (count > MAX_FLUSH_LOOP_COUNT)
                throw new OrmException(ERR_ORM_FLUSH_LOOP_COUNT_EXCEED_LIMIT);

            List<IOrmEntity> changed = this.changedDuringFlush;
            this.changedDuringFlush = null;
            for (IOrmEntity entity : changed) {
                internalFlush(entity);
            }
            count++;
        }
    }

    <T> List<T> getAndClear(List<T> list) {
        if (list.isEmpty())
            return Collections.emptyList();

        List<T> ret = new ArrayList<T>(list);
        list.clear();
        return ret;
    }

    void processWaitDeletes() {
        while (!waitDeletes.isEmpty() || !waitDeleteCollections.isEmpty()) {
            List<IOrmEntity> copyDeletes = getAndClear(waitDeletes);
            List<IOrmEntitySet> copyDeleteCollections = getAndClear(waitDeleteCollections);

            for (IOrmEntity entity : copyDeletes) {
                // 这里的实体都是在flush过程中放入待处理队列的
                Guard.checkState(!entity.orm_proxy(), "orm.err_waitDelete_entity_not_loaded");
                this._cascadeEntity(entity, false);
            }

            for (IOrmEntitySet coll : copyDeleteCollections) {
                // 所有到达这里的collection都是因为父元素级联删除
                Guard.checkState(!coll.orm_proxy(), "orm.err_waitDelete_collection_not_loaded");
                this.cascadeCollection(coll, true);
            }

            session.flushBatchLoadQueue();
        }
    }

    public void execute(IOrmEntity entity) {
        this.cascadeEntity(entity, false);

        session.flushBatchLoadQueue();

        this.processWaitDeletes();

        cascadeInternalFlush(entity);

        this.flushChanged();
    }

    void cascadeInternalFlush(IOrmEntity entity) {
        // 如果没有标记说明已经执行过internalFlush了
        if (!entity.orm_flushVisiting())
            return;

        entity.orm_flushVisiting(false);

        // 如果是proxy, 则不必递归
        if (entity.orm_state().isProxy()) {
            return;
        }

        IEntityModel entityModel = session.getEntityModel(entity.orm_entityName());
        flushComponent(entity, entityModel);

        internalFlush(entity);

        for (IEntityRelationModel propModel : entityModel.getRelations()) {
            if (propModel.isToOneRelation()) {
                // 如果是尚未装载的lazy属性，则没有必要进行处理
                if (OrmEntityHelper.isRefPropLoaded(propModel, entity)) {
                    IOrmEntity refEntity = entity.orm_refEntity(propModel.getName());
                    if (refEntity != null) {
                        cascadeInternalFlush(refEntity);
                    }
                }
            } else if (propModel.isToManyRelation()) {
                IOrmEntitySet<IOrmEntity> coll = entity.orm_refEntitySet(propModel.getName());
                if (coll != null && !coll.orm_proxy()) {
                    //coll.orm_onFlush();
                    for (IOrmEntity element : coll) {
                        cascadeInternalFlush(element);
                    }
                }
            }
        }
    }

    void flushComponent(IOrmEntity entity, IEntityModel entityModel) {
        entity.orm_flushComponent();
    }

    // 针对单个实体的修改生成sql语句，送入执行队列
    void internalFlush(IOrmEntity entity) {
        // 只读实体不需要更新
        if (entity.orm_readonly())
            return;

        OrmEntityState state = entity.orm_state();

        if (state.isSaving()) {
            session.flushSave(entity);
        } else if (state.isDeleting()) {
            session.flushDelete(entity);
        } else if (state.isManaged() && entity.orm_dirty()) {
            session.flushUpdate(entity);
        }
    }

    void cascadeEntity(IOrmEntity entity, boolean autoCascadeDelete) {
        if (entity.orm_flushVisiting())
            return;

        LOG.trace("orm.flush_check_entity:{}", entity);
        entity.orm_flushVisiting(true);

        _cascadeEntity(entity, autoCascadeDelete);
    }

    void _cascadeEntity(IOrmEntity entity, boolean autoCascadeDelete) {
        OrmEntityState state = entity.orm_state();

        // 如果是只读实体, 则没有必要递归查找。注意，只读实体的to-many和to-one关联即使被修改，这里也不会察觉。
        if (entity.orm_readonly())
            return;

        // 如果实体已被删除或者在数据库中不存在，则不需要递归处理。
        if (state.isMissing() || state.isDeleted())
            return;


        // 对于proxy的情况，不需要处理
        if (state.isProxy()) {
            if (entity.orm_extDirty()) {
                IEntityModel entityModel = entity.orm_entityModel();
                // 如果实体被标记为extDirty，则需要检查to-many关联集合。关联集合发生变动时会标记实体为extDirty
                for (IEntityRelationModel propModel : entityModel.getRelations()) {
                    if (propModel.isToManyRelation()) {
                        IOrmEntitySet coll = entity.orm_refEntitySet(propModel.getName());
                        if (coll != null) {
                            cascadeCollection(coll, false);
                        }
                    }
                }
            }
            return;
        }

        // 如果是新建实体，执行internalSave将标记它为saving状态
        if (state.isTransient()) {
            session.internalSave(entity);
        }

        IEntityModel entityModel = entity.orm_entityModel();
        flushComponent(entity, entityModel);

        boolean deleting = state.isDeleting();

        for (IEntityRelationModel propModel : entityModel.getRelations()) {
            boolean deleteProp = deleting && propModel.isCascadeDelete();

            if (propModel.isToManyRelation()) {
                if (deleteProp || entity.orm_extDirty()) {
                    IOrmEntitySet coll = entity.orm_refEntitySet(propModel.getName());
                    if (coll != null) {
                        cascadeCollection(coll, deleteProp);
                    }
                }
            } else if (entity.orm_refLoaded(propModel.getName())) {
                IOrmEntity refEntity = entity.orm_refEntity(propModel.getName());
                if (deleteProp) {
                    cascadeDeleteEntity(entity, propModel.isAutoCascadeDelete());
                } else {
                    if (refEntity.orm_state().isTransient()) {
                        cascadeEntity(refEntity, false);
                    }
                }
            }
        }
    }

    void cascadeDeleteEntity(IOrmEntity entity, boolean autoCascadeDelete) {
        if (entity.orm_proxy()) {
            LOG.debug("nop.orm.cascade-delete-entity:entity={}", entity);
            // 如果要删除的对象尚未加载，则将对象放入加载对象，并标记为待删除
            waitDeletes.add(entity);
            session.getBatchLoadQueue().enqueue(entity);
        } else {
            // gone包含deleting状态
            if (!entity.orm_state().isGone()) {
                LOG.debug("nop.orm.cascade-delete-entity:entity={}", entity);
                session.internalDelete(entity);
                // 此前可能已经遍历过，但是现在因为集合级联删除要把实体删除，则需要重新标记。
                // 下面的cascadeEntity会处理针对此entity的级联删除的情况
                entity.orm_flushVisiting(false);
            }
            cascadeEntity(entity, autoCascadeDelete);
        }
    }

    void cascadeCollection(IOrmEntitySet coll, boolean parentCascadeDelete) {
        if(coll.orm_readonly())
            return;
        IEntityRelationModel rel = session.getCollectionModel(coll.orm_collectionName());

        if (!coll.orm_proxy()) {
            this.addOwnerJoinProps(coll, rel);

            if (parentCascadeDelete) {
                coll.clear();
            } else {
                Iterator<IOrmEntity> it = coll.iterator();
                while (it.hasNext()) {
                    IOrmEntity entity = it.next();
                    if (entity.orm_state().isTransient()) {
                        // 新增实体
                        cascadeEntity(entity, false);
                    } else if (entity.orm_state().isGone()) {
                        // 实体已经被删除，则它也应该从集合中被删除
                        it.remove();
                    }
                }
            }

            Collection<IOrmEntity> removed = coll.orm_removed();
            if (removed != null) {
                for (IOrmEntity entity : removed) {
                    // 如果尚未保存就被删除，则不需要处理
                    if (entity.orm_state().isTransient())
                        continue;

                    // 如果已经和父元素解除了绑定则不会级联删除子元素。只有owner==parent的时候才需要被处理
                    if (isOrphan(entity, coll)) {
                        cascadeDeleteEntity(entity, rel.isAutoCascadeDelete());
                    }
                }
            }

            // 新增或者删除元素已经在前面被处理。这里的flush调用仅仅是增加一个清空globalCache的回调动作到actionQueue中。
            if (coll.orm_dirty())
                session.flushCollectionChange(coll);

            coll.orm_clearDirty();
        } else {
            if (parentCascadeDelete && !rel.isAutoCascadeDelete()) {
                LOG.trace("orm.enqueue_collection_proxy_wait_delete:{}", coll);
                session.getBatchLoadQueue().enqueue(coll);
                waitDeleteCollections.add(coll);
            }
        }
    }

    private boolean isOrphan(IOrmEntity entity, IOrmEntitySet coll) {
        if (coll.orm_refPropName() == null)
            return true;

        IOrmEntity parent = entity.orm_refEntity(coll.orm_refPropName());
        // 如果已经和父元素解除了绑定则不会级联删除子元素。只有owner==parent的时候才需要被处理
        if (coll.orm_owner() == parent) {
            return true;
        }
        return false;
    }

    /**
     * 加入集合的对象如果是transient的，则可能需要根据关联条件初始化属性
     *
     * @param coll     集合对象
     * @param relModel 关联模型
     */
    private void addOwnerJoinProps(IOrmEntitySet<IOrmEntity> coll, IEntityRelationModel relModel) {
        IOrmEntity owner = coll.orm_owner();
        if (owner.orm_state().isUnsaved()) {
            // 如果是新建实体，则它的主键可能是临时分配的，需要同步到子表记录上
            for (IOrmEntity entity : coll) {
                for (IEntityJoinConditionModel join : relModel.getJoin()) {
                    IEntityPropModel rightProp = join.getRightPropModel();
                    if (rightProp != null) {
                        Object leftValue = OrmEntityHelper.getLeftValue(join, owner);
                        OrmEntityHelper.setPropValue(rightProp, entity, leftValue);
                    }
                }
            }
        } else {
            for (IOrmEntity entity : coll) {
                if (entity.orm_state().isTransient()) {
                    for (IEntityJoinConditionModel join : relModel.getJoin()) {
                        IEntityPropModel rightProp = join.getRightPropModel();
                        if (rightProp != null) {
                            Object leftValue = OrmEntityHelper.getLeftValue(join, owner);
                            OrmEntityHelper.setPropValue(rightProp, entity, leftValue);
                        }
                    }
                }
            }
        }
    }
}