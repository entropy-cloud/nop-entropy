/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
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

    void flushCollectionChange(ShardSelection shard, IOrmEntitySet collection, IOrmSessionImplementor session);
}