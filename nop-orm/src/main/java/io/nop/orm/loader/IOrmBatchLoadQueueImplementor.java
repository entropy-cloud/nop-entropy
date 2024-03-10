/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.loader;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.commons.collections.IntArray;
import io.nop.orm.IOrmBatchLoadQueue;
import io.nop.orm.IOrmEntity;

import java.util.Collection;

public interface IOrmBatchLoadQueueImplementor extends IOrmBatchLoadQueue {
    void internalEnqueueCollection(Collection<IOrmEntity> coll, IntArray propIds, FieldSelectionBean subSelection);
}
