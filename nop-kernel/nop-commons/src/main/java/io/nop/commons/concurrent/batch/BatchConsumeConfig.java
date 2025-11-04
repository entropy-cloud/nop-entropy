/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.batch;

import io.nop.api.core.annotations.config.ConfigBean;

@ConfigBean
public class BatchConsumeConfig {
    private int batchSize = 20;
    private long minWaitMillis = 100;
    private long maxWaitMillis = 1000;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getMinWaitMillis() {
        return minWaitMillis;
    }

    public void setMinWaitMillis(long minWaitMillis) {
        this.minWaitMillis = minWaitMillis;
    }

    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    public void setMaxWaitMillis(long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }
}
