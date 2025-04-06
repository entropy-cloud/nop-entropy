/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api.execution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;

/**
 * IJobInvoker的执行结果，一般应返回null表示按缺省调度逻辑继续执行。
 * 如果返回CONTINUE(nextScheduleTime)且nextScheduleTime大于0，则以指定的nextScheduleTime为准，忽略trigger计算得到的结果，进行下一次调度。
 * 如果返回ERROR，则表示job进入ERROR状态，不再执行后续的调度。 如果返回COMPLETED，则表示job进入COMPLETED状态，不再执行后续的调度。
 */
@DataBean
public class JobFireResult {
    private final boolean completed;
    private final ErrorBean error;
    private final long nextScheduleTime;

    public JobFireResult(@JsonProperty("completed") boolean completed,
                         @JsonProperty("error") ErrorBean error,
                         @JsonProperty("nextScheduleTime") long nextScheduleTime) {
        this.completed = completed;
        this.error = error;
        this.nextScheduleTime = nextScheduleTime;
    }

    public boolean isCompleted() {
        return completed;
    }

    public ErrorBean getError() {
        return error;
    }

    public long getNextScheduleTime() {
        return nextScheduleTime;
    }

    @JsonIgnore
    public boolean isErrorResult() {
        return error != null;
    }

    /**
     * 继续执行下一次调度
     */
    public static final JobFireResult CONTINUE = CONTINUE(-1);

    public static final JobFireResult CONTINUE(long nextScheduleTime) {
        return new JobFireResult(false, null, nextScheduleTime);
    }

    public static final JobFireResult COMPLETED = new JobFireResult(true, null, -1);

    /**
     * 执行失败，任务需要终止，不再继续调度
     */
    public static final JobFireResult ERROR(ErrorBean error) {
        return new JobFireResult(true, error, -1);
    }
}