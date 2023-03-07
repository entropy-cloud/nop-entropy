/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.List;

@DataBean
public class StackInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String threadName;
    private long threadId;
    private List<StackTraceElement> stackTrace;

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public List<StackTraceElement> getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(List<StackTraceElement> stackTrace) {
        this.stackTrace = stackTrace;
    }

    @JsonIgnore
    public StackTraceElement getTopElement() {
        if (stackTrace == null || stackTrace.isEmpty())
            return null;
        return stackTrace.get(0);
    }

    @JsonIgnore
    public LineLocation getTopLocation() {
        StackTraceElement frame = getTopElement();
        if (frame == null)
            return null;
        return new LineLocation(frame.getSourcePath(), frame.getLine());
    }
}
