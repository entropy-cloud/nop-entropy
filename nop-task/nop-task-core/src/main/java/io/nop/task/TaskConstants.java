/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task;

public interface TaskConstants {
    char SPECIAL_STEP_PREFIX = '@';

    /**
     * 结束整个task
     */
    String STEP_ID_END = "@end";

    /**
     * 跳过后续同级步骤
     */
    String STEP_ID_EXIT = "@exit";

    /**
     * 暂时挂起。如果是支持状态保持的步骤，后续执行时可以从历史状态恢复执行。
     */
    String STEP_ID_SUSPEND = "@suspend";

    /**
     * 如果返回STEP_ID_ASYNC, 则表示本步骤为异步执行，AsyncTaskPromise.take()必须不为空，通过它可以等待异步执行完毕
     */
    String STEP_ID_ASYNC = "@async";

    String VAR_TASK_CONTEXT = "taskContext";
    String VAR_STATE = "state";
    String VAR_RETURN_VALUE = "returnValue";
    String VAR_REQUEST = "request";

    String STATE_ID_DEFAULT = "default";

    String PARAM_DELAY = "delay";
    String PARAM_TIMEOUT = "timeout";

    String PARAM_COUNT = "count";

    int TASK_STATUS_CREATED = 0;
    int TASK_STATUS_ACTIVE = 10;
    int TASK_STATUS_SUSPENDED = 20;
    int TASK_STATUS_COMPLETED = 30;
    int TASK_STATUS_KILLED = 40;
    int TASK_STATUS_FAILED = 50;
    int TASK_STATUS_TIMEOUT = 60;

    int TASK_STATUS_HISTORY_BOUND = TASK_STATUS_COMPLETED;
}