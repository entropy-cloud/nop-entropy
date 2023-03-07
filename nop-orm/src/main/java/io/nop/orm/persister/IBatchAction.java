/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.persister;

import io.nop.dao.shard.ShardSelection;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;

import java.util.Set;
import java.util.function.BiConsumer;

public interface IBatchAction {
    ShardSelection getShardSelection();

    void onSuccess(Integer changeCount);

    void onFailure(Throwable t);

    interface IEntityBatchAction extends IBatchAction {
        String getEntityName();

        IOrmEntity getEntity();

        Object getEntityId();

        BiConsumer<Integer, Throwable> getCallback();

        ShardSelection getShardSelection();
    }

    class EntityBatchAction implements IEntityBatchAction {
        private final IOrmEntity entity;
        private final BiConsumer<Integer, Throwable> callback;
        private final ShardSelection shardSelection;

        public EntityBatchAction(IOrmEntity entity, ShardSelection shardSelection,
                                 BiConsumer<Integer, Throwable> callback) {
            this.entity = entity;
            this.callback = callback;
            this.shardSelection = shardSelection;
        }

        public String getIdString() {
            return getEntity().orm_idString();
        }

        public ShardSelection getShardSelection() {
            return this.shardSelection;
        }

        public String getQuerySpace() {
            return shardSelection == null ? null : shardSelection.getQuerySpace();
        }

        @Override
        public String getEntityName() {
            return entity.orm_entityName();
        }

        @Override
        public IOrmEntity getEntity() {
            return entity;
        }

        @Override
        public Object getEntityId() {
            return entity.get_id();
        }

        @Override
        public BiConsumer<Integer, Throwable> getCallback() {
            return callback;
        }

        @Override
        public void onSuccess(Integer o) {
            if (callback != null)
                callback.accept(o, null);
        }

        @Override
        public void onFailure(Throwable t) {
            if (callback != null)
                callback.accept(null, t);
        }
    }

    class EntitySaveAction extends EntityBatchAction {
        public EntitySaveAction(IOrmEntity entity, ShardSelection shardSelection,
                                BiConsumer<Integer, Throwable> callback) {
            super(entity, shardSelection, callback);
        }
    }

    class EntityUpdateAction extends EntityBatchAction {
        public EntityUpdateAction(IOrmEntity entity, ShardSelection shardSelection,
                                  BiConsumer<Integer, Throwable> callback) {
            super(entity, shardSelection, callback);
        }
    }

    class EntityDeleteAction extends EntityBatchAction {
        public EntityDeleteAction(IOrmEntity entity, ShardSelection shardSelection,
                                  BiConsumer<Integer, Throwable> callback) {
            super(entity, shardSelection, callback);
        }
    }

    interface ICollectionAction extends IBatchAction {
        IOrmEntitySet getCollection();

        Set<IOrmEntity> getRemoved();

        ShardSelection getShardSelection();

        BiConsumer<Integer, Throwable> getCallback();
    }

    class CollectionBatchAction implements ICollectionAction {
        private final IOrmEntitySet coll;
        private final BiConsumer<Integer, Throwable> callback;
        private final Set<IOrmEntity> removed;
        private final ShardSelection shardSelection;

        public CollectionBatchAction(IOrmEntitySet coll, ShardSelection shardSelection,
                                     BiConsumer<Integer, Throwable> callback) {
            this.coll = coll;
            this.callback = callback;
            // 暂存下来
            this.removed = coll.orm_removed();
            this.shardSelection = shardSelection;
        }

        public String getQuerySpace() {
            return shardSelection == null ? null : shardSelection.getQuerySpace();
        }

        public ShardSelection getShardSelection() {
            return shardSelection;
        }

        @Override
        public IOrmEntitySet getCollection() {
            return coll;
        }

        @Override
        public Set<IOrmEntity> getRemoved() {
            return removed;
        }

        @Override
        public BiConsumer<Integer, Throwable> getCallback() {
            return callback;
        }

        @Override
        public void onSuccess(Integer changeCount) {
            if (callback != null)
                callback.accept(changeCount, null);
        }

        @Override
        public void onFailure(Throwable t) {
            if (callback != null)
                callback.accept(null, t);
        }
    }
}