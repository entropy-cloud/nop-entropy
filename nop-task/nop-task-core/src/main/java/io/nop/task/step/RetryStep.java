/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.core.context.IEvalContext;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import static io.nop.task.TaskErrors.ARG_STEP_ID;
import static io.nop.task.TaskErrors.ERR_TASK_RETRY_TIMES_EXCEED_LIMIT;

public class RetryStep extends AbstractStep {
    private IRetryPolicy<IEvalContext> retryPolicy;
    private ITaskStep body;

    public IRetryPolicy<IEvalContext> getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(IRetryPolicy<IEvalContext> retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    public ITaskStep getBody() {
        return body;
    }

    public void setBody(ITaskStep body) {
        this.body = body;
    }

    public boolean isShareState() {
        return false;
    }

    @DataBean
    public static class RetryStateBean {
        int retryTimes;
        long lastRetryTime;

        public int getRetryTimes() {
            return retryTimes;
        }

        public void setRetryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
        }

        public long getLastRetryTime() {
            return lastRetryTime;
        }

        public void setLastRetryTime(long lastRetryTime) {
            this.lastRetryTime = lastRetryTime;
        }

        public void incRetryTimes() {
            retryTimes++;
        }
    }

    @Override
    protected void initStepState(ITaskStepState state) {
        super.initStepState(state);
        RetryStateBean stateBean = new RetryStateBean();
        state.setStateBean(stateBean);
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        RetryStateBean stateBean = state.getStateBean(RetryStateBean.class);

        do {
            long delay = retryPolicy.getRetryDelay(null, stateBean.getRetryTimes(), state.evalScope());
            if (delay < 0) {
                Throwable e = state.exception();
                if (e == null)
                    e = new NopException(ERR_TASK_RETRY_TIMES_EXCEED_LIMIT)
                            .source(this)
                            .param(ARG_STEP_ID, getStepId());
                throw NopException.adapt(e);
            }

            TaskStepResult result = body.execute(state.getRunId(), state, context);
            if (result.isAsync()) {
                return result.thenCompose((v, err) -> doRetry(v, err, state, context));
            } else {
                stateBean.incRetryTimes();
                saveState(state, context);
            }
        } while (true);
    }

    Object doRetry(Object value, Throwable err, ITaskStepState state, ITaskContext context) {
        if (err != null) {
            RetryStateBean stateBean = state.getStateBean(RetryStateBean.class);
            stateBean.incRetryTimes();
            saveState(state, context);
            return doExecute(state, context);
        } else {
            return TaskStepResult.of(getNextStepId(), value);
        }
    }
}
