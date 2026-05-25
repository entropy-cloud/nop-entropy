/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.io.Serializable;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class JobTerminationContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private final JobTerminationMode mode;
    private final long timeout;
    private final boolean waitForSinkCommit;
    private final String savepointNamespace;
    private final boolean abortTransactions;

    public JobTerminationContext(JobTerminationMode mode, long timeout,
                                 boolean waitForSinkCommit,
                                 String savepointNamespace,
                                 boolean abortTransactions) {
        this.mode = mode;
        this.timeout = timeout;
        this.waitForSinkCommit = waitForSinkCommit;
        this.savepointNamespace = savepointNamespace;
        this.abortTransactions = abortTransactions;
    }

    public JobTerminationContext() {
        this(JobTerminationMode.CANCEL, 0, false, null, false);
    }

    public static JobTerminationContext cancel() {
        return new JobTerminationContext(JobTerminationMode.CANCEL, 30000, false, null, true);
    }

    public static JobTerminationContext drain(long timeout) {
        return new JobTerminationContext(JobTerminationMode.DRAIN, timeout, true, null, false);
    }

    public static JobTerminationContext suspend(String savepointNamespace) {
        return new JobTerminationContext(JobTerminationMode.SUSPEND, 30000, true, savepointNamespace, false);
    }

    public static JobTerminationContext exportSavepoint(String savepointNamespace) {
        return new JobTerminationContext(JobTerminationMode.EXPORT_SAVEPOINT, 30000, true, savepointNamespace, false);
    }

    public JobTerminationMode getMode() { return mode; }
    public long getTimeout() { return timeout; }
    public boolean isWaitForSinkCommit() { return waitForSinkCommit; }
    public String getSavepointNamespace() { return savepointNamespace; }
    public boolean isAbortTransactions() { return abortTransactions; }
}
