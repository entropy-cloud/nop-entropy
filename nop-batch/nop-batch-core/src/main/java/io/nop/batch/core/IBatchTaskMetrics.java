/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

public interface IBatchTaskMetrics {
    /**
     * 记录任务开始时刻
     *
     * @return meter对象。用于endTask的第一个参数
     */
    Object beginTask();

    /**
     * 记录任务结束时刻
     *
     * @param meter   beginTask调用的返回对象
     * @param success 任务是否成功执行
     */
    void endTask(Object meter, boolean success);

    Object beginChunk();

    void endChunk(Object meter, boolean success);

    Object beginLoad();

    void endLoad(Object meter, int count, boolean success);

    Object beginConsume(int count);

    void endConsume(Object meter, int count, boolean success);

    Object beginProcess();

    void endProcess(Object meter, boolean success);

    void retry(int count);

    void skipError(int count);

    long getLoadItemCount();

    long getProcessItemCount();

    long getSkipItemCount();

    long getRetryItemCount();

    long getConsumeItemCount();
}