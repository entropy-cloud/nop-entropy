/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.session;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.collections.IntArray;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntityEnhancer;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.IOrmSession;
import io.nop.orm.loader.IOrmBatchLoadQueueImplementor;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.persister.IBatchActionQueue;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * 基本执行流程如下： session.save(entity) --> session.internalSave(entity) 然后实体保存到sessionEntityCache中，等待执行session.flush操作。
 * <p>
 * session.flush(entity) --> session.flushSave(entity) --> persister.save(entity) --> session.persisterPostSave(entity)
 */
public interface IOrmSessionImplementor extends IOrmSession, IOrmEntityEnhancer {

    IOrmBatchLoadQueueImplementor getBatchLoadQueue();

    void internalAssemble(IOrmEntity entity, Object[] values, IntArray propIds);

    void markMissing(IOrmEntity entity);

    void internalSave(IOrmEntity entity);

    void internalDelete(IOrmEntity entity);

    /**
     * CascadeFlusher发现需要被保存的实体之后调用session.flushSave来实际生成BatchAction消息，放入action队列中。
     */
    void flushSave(IOrmEntity entity);

    void flushUpdate(IOrmEntity entity);

    void flushDelete(IOrmEntity entity);

    /**
     * IEntityPersister的save操作执行成功之后调用此函数来修改实体状态
     */
    void persisterPostSave(IOrmEntity entity);

    /**
     * IEntityPersister的update操作执行成功之后调用此函数来修改实体状态
     */
    void persisterPostUpdate(IOrmEntity entity);

    /**
     * IEntityPersister的delete操作执行成功之后调用此函数来修改实体状态
     */
    void persisterPostDelete(IOrmEntity entity);

    void flushCollectionChange(IOrmEntitySet coll);

    CompletionStage<Void> internalBatchLoadCollectionAsync(String collectionName, Collection<IOrmEntitySet> collections,
                                                           IntArray propIds, FieldSelectionBean subSelection);

    CompletionStage<Void> internalBatchLoadAsync(String entityName, Collection<IOrmEntity> entities, IntArray propIds,
                                                 FieldSelectionBean subSelection);

    IBatchActionQueue getBatchActionQueue(String querySpace);

    IEntityModel getEntityModel(String entityName);

    IEntityRelationModel getCollectionModel(String collectionName);

    long getSessionRevVersion();
}
