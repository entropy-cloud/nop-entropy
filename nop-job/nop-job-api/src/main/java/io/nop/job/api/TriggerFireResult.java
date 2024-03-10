/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.job.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;

/**
 * ITriggerExecutor的执行结果，一般应返回null表示按缺省调度逻辑继续执行。
 * 如果返回CONTINUE(nextScheduleTime)且nextScheduleTime大于0，则以指定的nextScheduleTime为准，忽略trigger计算得到的结果，进行下一次调度。
 * 如果返回ERROR，则表示job进入ERROR状态，不再执行后续的调度。 如果返回COMPLETED，则表示job进入COMPLETED状态，不再执行后续的调度。
 */
@DataBean
public abstract class TriggerFireResult {

    public abstract String getName();

    public long getNextScheduleTime() {
        return -1;
    }

    public ErrorBean getError() {
        return null;
    }

    @JsonIgnore
    public boolean isContinueResult() {
        return false;
    }

    @JsonIgnore
    public boolean isCompletedResult() {
        return false;
    }

    @JsonIgnore
    public boolean isErrorResult() {
        return false;
    }

    /**
     * 继续执行下一次调度
     */
    public static final TriggerFireResult CONTINUE = new ContinueResult(-1);

    public static final TriggerFireResult CONTINUE(long nextScheduleTime) {
        return new ContinueResult(nextScheduleTime);
    }

    public static final TriggerFireResult COMPLETED = new CompletedResult();

    /**
     * 执行失败，任务需要终止，不再继续调度
     */
    public static final TriggerFireResult ERROR(ErrorBean error) {
        return new ErrorResult(error);
    }

    @DataBean
    public static class ContinueResult extends TriggerFireResult {
        private final long nextScheduleTime;

        public ContinueResult(@JsonProperty("nextScheduleTime") long nextScheduleTime) {
            this.nextScheduleTime = nextScheduleTime;
        }

        public String getName() {
            return "CONTINUE";
        }

        @Override
        public long getNextScheduleTime() {
            return nextScheduleTime;
        }

        @Override
        public boolean isContinueResult() {
            return true;
        }
    }

    @DataBean
    public static class CompletedResult extends TriggerFireResult {
        public String getName() {
            return "COMPLETED";
        }

        @Override
        public boolean isCompletedResult() {
            return true;
        }
    }

    @DataBean
    public static class ErrorResult extends TriggerFireResult {
        private final ErrorBean error;

        public ErrorResult(@JsonProperty("error") ErrorBean error) {
            this.error = error;
        }

        public String getName() {
            return "ERROR";
        }

        @Override
        public ErrorBean getError() {
            return error;
        }

        @Override
        public boolean isErrorResult() {
            return true;
        }
    }
}