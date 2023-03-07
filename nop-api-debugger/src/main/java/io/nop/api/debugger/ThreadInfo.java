/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class ThreadInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String threadName;
    private final long threadId;
    private final boolean suspended;

    @JsonCreator
    public ThreadInfo(@JsonProperty("threadName") String threadName, @JsonProperty("threadId") long threadId,
                      @JsonProperty("suspended") boolean suspended) {
        this.threadName = threadName;
        this.threadId = threadId;
        this.suspended = suspended;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getThreadId() {
        return threadId;
    }
}
