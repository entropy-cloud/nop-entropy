/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.Collection;

/**
 * 用于记录已处理过的记录，避免重复处理。一般和处理函数在一个事务中，确保成功处理时一定会保存处理记录
 */
public interface IBatchRecordHistoryStore<S> {
    /**
     * 过滤掉已经处理过的记录
     *
     * @param records 待处理的记录列表
     * @param context 任务上下文
     */
    Collection<S> filterProcessed(Collection<S> records, IBatchChunkContext context);

    void saveProcessed(Collection<S> filtered, Throwable exception, IBatchChunkContext context);
}