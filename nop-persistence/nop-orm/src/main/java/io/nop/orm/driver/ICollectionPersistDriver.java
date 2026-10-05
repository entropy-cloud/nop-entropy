/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.driver;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.collections.IntArray;
import io.nop.dao.shard.ShardSelection;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

/**
 * @author canonical_entropy@163.com
 */
public interface ICollectionPersistDriver {
    void init(IEntityRelationModel relation, IPersistEnv env);

    void loadCollection(ShardSelection shard, IOrmEntitySet coll, IntArray propIds, FieldSelectionBean selection,
                        IOrmSessionImplementor session);

    CompletionStage<Void> batchLoadCollectionAsync(ShardSelection shard, Collection<IOrmEntitySet> collections,
                                                   IntArray propIds, FieldSelectionBean selection, IOrmSessionImplementor session);

    // Plan 2307 V7: 原先此处有 flushCollectionChange(ShardSelection, IOrmEntitySet,
    // IOrmSessionImplementor)，全仓零调用点；其 JDBC 实现是纯空方法体（什么都不做）。
    // 已删除。真实的集合变更 flush 走另一条同名的、活的路：
    //   OrmSessionImpl#flushCollectionChange(IOrmEntitySet)
    //     -> ICollectionPersister#flushCollectionChange(IOrmEntitySet, IOrmSessionImplementor)
    //        -> CollectionPersisterImpl: 入队 CollectionBatchAction，
    //           在变更成功后回调 evictGlobalCache（即全局缓存失效的真实职责所在）。
    // 见 ai-dev/design/nop-orm-scope-and-non-goals.md §3.3。
}