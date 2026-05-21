/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.pulsar;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class PulsarProducerConfig {
    private boolean batchingEnabled = true;
    private int batchMaxMessages = 1000;
    private int sendTimeout = 30000;

    public boolean isBatchingEnabled() {
        return batchingEnabled;
    }

    public void setBatchingEnabled(boolean batchingEnabled) {
        this.batchingEnabled = batchingEnabled;
    }

    public int getBatchMaxMessages() {
        return batchMaxMessages;
    }

    public void setBatchMaxMessages(int batchMaxMessages) {
        this.batchMaxMessages = batchMaxMessages;
    }

    public int getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(int sendTimeout) {
        this.sendTimeout = sendTimeout;
    }
}
