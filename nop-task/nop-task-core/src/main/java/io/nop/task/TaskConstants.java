/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

public interface TaskConstants {
    char SPECIAL_STEP_PREFIX = '@';

    /**
     * 结束整个task
     */
    String STEP_NAME_END = "@end";

    /**
     * 跳过后续同级步骤
     */
    String STEP_NAME_EXIT = "@exit";

    /**
     * 暂时挂起。如果是支持状态保持的步骤，后续执行时可以从历史状态恢复执行。
     */
    String STEP_NAME_SUSPEND = "@suspend";

    String VAR_TASK_RT = "taskRt";
    String VAR_STEP_STATE = "stepState";
    String VAR_RETURN_VALUE = "returnValue";
    String VAR_REQUEST = "request";

    String VAR_RESULTS = "results";

    String VAR_EXCEPTION = "exception";

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


    String STEP_TYPE_AWAIT = "await";
    String STEP_TYPE_XPL = "xpl";

    String STEP_TYPE_SCRIPT = "script";

    String STEP_TYPE_SEQUENTIAL = "sequential";

    String STEP_TYPE_FLOW = "flow";

    String STEP_TYPE_PARALLEL = "parallel";

    String STEP_TYPE_FORK = "fork";

    String STEP_TYPE_FORK_N = "fork-n";

    String STEP_TYPE_LOOP = "loop";

    String STEP_TYPE_LOOP_N = "loopN";

    String STEP_TYPE_SUB_TASK = "sub-task";

    String STEP_TYPE_INVOKE = "invoke";

    String STEP_TYPE_CHOOSE = "choose";

    String STEP_TYPE_SUSPEND = "suspend";

    String STEP_TYPE_SLEEP = "sleep";

    String STEP_TYPE_DELAY = "delay";

    String STEP_TYPE_EXIT = "exit";

    String STEP_TYPE_END = "end";

    String STEP_TYPE_SIMPLE = "simple";

    String STEP_TYPE_CASE = "case";

    String STEP_TYPE_OTHERWISE = "otherwise";

    String STEP_TYPE_TASK = "task";

    String STEP_TYPE_TRY = "try";
    String STEP_TYPE_RETRY = "retry";

    String STEP_TYPE_THROTTLE = "throttle";

    String STEP_TYPE_RATE_LIMIT = "rate-limit";

    String STEP_TYPE_TIMEOUT = "timeout";

    String STEP_TYPE_CALL_STEP = "call-step";

    String STEP_TYPE_CALL_TASK = "call-task";
}