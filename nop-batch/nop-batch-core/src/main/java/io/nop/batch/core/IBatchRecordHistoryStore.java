package io.nop.batch.core;

import java.util.List;

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
    List<S> filterProcessed(List<S> records, IBatchChunkContext context);

    void saveProcessed(List<S> filtered, Throwable exception, IBatchChunkContext context);
}