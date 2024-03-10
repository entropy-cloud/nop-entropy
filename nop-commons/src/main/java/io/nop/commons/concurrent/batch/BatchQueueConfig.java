/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.batch;

import io.nop.api.core.annotations.config.ConfigBean;
import io.nop.commons.concurrent.QueueOverflowPolicy;

@ConfigBean
public class BatchQueueConfig extends BatchConsumeConfig {
    private int queueSize = 500;
    private int threadPoolSize = 1;
    private QueueOverflowPolicy overflowPolicy = QueueOverflowPolicy.BLOCK_WAIT;

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public QueueOverflowPolicy getOverflowPolicy() {
        return overflowPolicy;
    }

    public void setOverflowPolicy(QueueOverflowPolicy overflowPolicy) {
        this.overflowPolicy = overflowPolicy;
    }
}
