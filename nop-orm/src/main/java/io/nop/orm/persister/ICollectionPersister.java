/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.persister;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.collections.IntArray;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.orm.session.IOrmSessionImplementor;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

public interface ICollectionPersister extends AutoCloseable {

    void init(IEntityRelationModel relation, IPersistEnv env);

    IEntityRelationModel getCollectionModel();

    void loadCollection(IOrmEntitySet collection, IntArray propIds, FieldSelectionBean selection,
                        IOrmSessionImplementor session);

    CompletionStage<Void> batchLoadCollectionAsync(Collection<IOrmEntitySet> collections, IntArray propIds,
                                                   FieldSelectionBean subSelection, IOrmSessionImplementor session);

    void flushCollectionChange(IOrmEntitySet collection, IOrmSessionImplementor session);
}