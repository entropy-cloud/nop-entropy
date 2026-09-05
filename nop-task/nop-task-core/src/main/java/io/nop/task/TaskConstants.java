/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

public interface TaskConstants {
    String XDEF_PATH_TASK = "/nop/schema/task/task.xdef";
    String TAG_INPUT = "input";
    String TAG_OUTPUT = "output";

    String TAG_SOURCE = "source";
    String TAG_META = "meta";

    String ATTR_NAME = "name";
    String MODEL_TYPE_TASK = "task";

    String FILE_TYPE_TASK = "task.xml";

    String FILE_TYPE_TASK_LIB = "task-lib.xml";

    String ATTR_TASK_NAME = "task:name";

    String ATTR_TASK_VERSION = "task:version";

    String MAIN_STEP_NAME = "@main";

    String DEFAULT_VALUE = "default";

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
    String VAR_TASK_STEP_RT = "taskStepRt";
    String VAR_RESULT = "RESULT";

    String VAR_STEP_RESULTS = "STEP_RESULTS";

    String PROP_OUTPUTS = "outputs";
    String PROP_ERROR = "error";
    String VAR_REQUEST = "request";

    String VAR_ERROR = "ERROR";

    String VAR_OUTPUTS = "OUTPUTS";

    String VAR_EXCEPTION = "EXCEPTION";

    String VAR_STEP = "step";

    String VAR_DECORATOR_MODEL = "decoratorModel";

    String VAR_STEP_RESULT = "STEP_RESULT";

    String VAR_NODE = "node";

    String STATE_ID_DEFAULT = "default";

    String PARAM_DELAY = "delay";
    String PARAM_TIMEOUT = "timeout";

    String PARAM_COUNT = "count";

    /*
     * 任务/步骤状态码（plan 349 Phase 2 对齐）：数值必须与 ORM 字典（nop-task/model/nop-task.orm.xml
     * 的 task/task-status、task/task-step-status dict）及生成常量 _NopTaskCoreConstants 保持一致，
     * 守卫测试 TestTaskConstantsAlignment 强制约束。历史版本曾使用 30/40/50/60 体系，
     * 与字典错位导致 DB/展示层（ext:dict 绑定）把 COMPLETED 读成"执行中"、KILLED 读成"已完成"等全错位。
     */
    int TASK_STATUS_CREATED = 0;

    /**
     * 对应字典 SUSPENDED（已暂停）
     */
    int TASK_STATUS_SUSPENDED = 10;

    /**
     * 对应字典 WAITING（等待调度）
     */
    int TASK_STATUS_WAITING = 20;

    /**
     * 运行中，对应字典 ACTIVATED（执行中）
     */
    int TASK_STATUS_ACTIVE = 30;

    int TASK_STATUS_COMPLETED = 40;

    /**
     * 超时，对应字典 EXPIRED（已超时）
     */
    int TASK_STATUS_TIMEOUT = 50;

    int TASK_STATUS_FAILED = 60;

    int TASK_STATUS_KILLED = 70;

    /**
     * 运行中，对应字典 ACTIVATED（执行中）。历史值 10 与字典 SUSPENDED（已暂停）错位，
     * 引擎写入的"执行中"步骤在展示层被读成"已暂停"
     */
    int TASK_STEP_STATUS_ACTIVE = 30;

    String STEP_TYPE_CUSTOM = "custom";

    String ATTR_CUSTOM_TYPE = "customType";

    String STEP_TYPE_XPL = "xpl";

    String STEP_TYPE_STEP = "step";

    String STEP_TYPE_SCRIPT = "script";

    String STEP_TYPE_SEQUENTIAL = "sequential";

    String STEP_TYPE_SELECTOR = "selector";

    String STEP_TYPE_GRAPH = "graph";

    String STEP_TYPE_PARALLEL = "parallel";

    String STEP_TYPE_FORK = "fork";

    String STEP_TYPE_FORK_N = "fork-n";

    String STEP_TYPE_LOOP = "loop";

    String STEP_TYPE_LOOP_N = "loopN";

    String STEP_TYPE_INVOKE = "invoke";

    String STEP_TYPE_CHOOSE = "choose";

    String STEP_TYPE_IF = "if";

    String STEP_TYPE_SUSPEND = "suspend";

    String STEP_TYPE_SLEEP = "sleep";

    String STEP_TYPE_DELAY = "delay";

    String STEP_TYPE_EXIT = "exit";

    String STEP_TYPE_END = "end";

    String STEP_TYPE_SIMPLE = "simple";

    String STEP_TYPE_CASE = "case";

    String STEP_TYPE_OTHERWISE = "otherwise";

    String STEP_TYPE_THEN = "then";

    String STEP_TYPE_ELSE = "else";

    String STEP_TYPE_TASK = "task";

    String STEP_TYPE_INVOKE_STATIC = "invoke-static";

    String STEP_TYPE_CALL_STEP = "call-step";

    String STEP_TYPE_CALL_TASK = "call-task";

    String ATTR_GRAPHQL_OPERATION_TYPE = "graphql:operationType";

    String OPERATION_TYPE_QUERY = "query";

    String OPERATION_TYPE_MUTATION = "mutation";

    String METER_TASK = "flow.task";

    String METER_STEP = "flow.step";

    String STATUS_SUCCESS = "SUCCESS";
    String STATUS_FAILURE = "FAILURE";

    String DEFAULT_METER_PREFIX = "nop.";

    String BEAN_PREFIX_TASK_STEP_DECORATOR = "nopTaskStepDecorator_";

    String REASON_TASK_COMPLETE = "task-complete";

    String EXECUTOR_BEAN_PREFIX = "executor_";
}