/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gpt.core.api;

import io.nop.api.core.beans.ExtensibleBean;

public class ChatOptions extends ExtensibleBean {
    private IChatProgressListener progressListener;

    public ChatOptions progressListener(IChatProgressListener progressListener) {
        this.setProgressListener(progressListener);
        return this;
    }

    public IChatProgressListener getProgressListener() {
        return progressListener;
    }

    public void setProgressListener(IChatProgressListener progressListener) {
        this.progressListener = progressListener;
    }
}
