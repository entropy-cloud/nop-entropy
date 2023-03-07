/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

public interface WfConstants {
    String XDEF_PATH_WF = "/nop/schema/wf/wf.xdef";
    
    /**
     * 工作流刚在内存中创建
     */
    int WF_STATUS_UNKNOWN = -1;

    /**
     * 工作流实例已经被保存到库中，但尚未启动
     */
    int WF_STATUS_CREATED = 0;

    /**
     * 工作流实例已经被启动，处于正常运行状态
     */
    int WF_STATUS_RUNNING = 10;

    /**
     * 工作流实例已经被挂起
     */
    int WF_STATUS_SUSPENDED = 20;

    /**
     * 工作流实例已经正常结束
     */
    int WF_STATUS_COMPLETED = 30;

    /**
     * 工作流实例已经被强制终止
     */
    int WF_STATUS_KILLED = 40;

    /**
     * 工作流实例因为出现致命错误而自动终止
     */
    int WF_STATUS_ERROR = 50;

    /**
     * 大于此常量的状态值都是历史状态
     */
    int WF_STATUS_HISTORY_BOUND = WF_STATUS_COMPLETED;

    int ACTION_STATUS_SUCCESS = 0;
    int ACTION_STATUS_ERROR = 10;

    // =================以下为Activity的状态 =======
    int ACTIVITY_STATUS_CREATED = 0;

    /**
     * 步骤处于挂起状态
     */
    int ACTIVITY_STATUS_SUSPENDED = 5;

    /**
     * join步骤在等待上游步骤完成的过程中或者flow步骤在等待子流程完成的过程中处于此状态
     */
    int ACTIVITY_STATUS_WAITING = 10;

    /**
     * 步骤处于激活状态，一般此时在界面上才显示操作按钮
     */
    int ACTIVITY_STATUS_ACTIVATED = 20;

    /**
     * 步骤正常结束
     */
    int ACTIVITY_STATUS_COMPLETED = 50;

    /**
     * 流程步骤已经超时，由自动触发的action实现步骤转移
     */
    int ACTIVITY_STATUS_EXPIRED = 60;

    /**
     * 退回当前步骤时，当前步骤的状态标记为REJECT，便于和正常的历史状态区分开来
     */
    int ACTIVITY_STATUS_REJECT = 70;

    /**
     * 发送到下一步骤之后，在下一步骤的状态转变为历史状态之前，可以执行撤回操作，被撤回的下一步骤状态变为WITHDRAWN。
     */
    int ACTIVITY_STATUS_WITHDRAWN = 80;

    /**
     * 流程步骤被强制终止
     */
    int ACTIVITY_STATUS_KILLED = 90;

    /**
     * 流程步骤出现致命错误，自动终止
     */
    int ACTIVITY_STATUS_ERROR = 100;

    int ACTIVITY_STATUS_ACTIVE_BOUND = ACTIVITY_STATUS_ACTIVATED;
    int ACTIVITY_STATUS_HISTORY_BOUND = ACTIVITY_STATUS_COMPLETED;

    int ACTIVITY_DEFAULT_PRIORITY = 10;

    String VAR_WF = "wf";
    String VAR_WF_RT = "wfRt";
    String VAR_WF_STEP = "wfStep";
    String VAR_WF_ACTOR_MODEL = "wfActorModel";
    String VAR_SELECTED_ACTORS = "selectedActors";

    String VAR_SELECTED_STEP_ACTORS = "selectedStepActors";

    String VAR_ACTORS = "actors";
    String VAR_REJECT_STEPS = "rejectSteps"; // reject action的参数
    String VAR_TARGET_STEPS = "targetSteps";
    String VAR_TARGET_CASES = "targetCases";

    String VAR_ACTION_RECORD = "actionRecord";
    String VAR_SUB_WF_RESULTS = "subWfResults";
    String VAR_EXCEPTION = "exception";

    String EVENT_AFTER_SAVE = "after-save";

    String EVENT_BEFORE_START = "before-start";
    String EVENT_AFTER_START = "after-start";

    String EVENT_ON_NO_ASSIGN = "on-no-assign";
    String EVENT_ENTER_STEP = "enter-step";
    String EVENT_EXIT_STEP = "exit-step";
    String EVENT_TRANSITION = "transition";
    String EVENT_BEFORE_ACTION = "before-action";
    String EVENT_AFTER_ACTION = "after-action";

    String EVENT_BEFORE_END = "before-end";
    String EVENT_AFTER_END = "before-end";

    String EVENT_BEFORE_KILL = "before-kill";
    String EVENT_AFTER_KILL = "after-kill";

    String EVENT_SUSPEND = "suspend";
    String EVENT_RESUME = "resume";
    String EVENT_REMOVE = "remove";
    String EVENT_KILL_STEP = "kill-step";
    String EVENT_ACTIVATE_STEP = "activate-step";

    String EVENT_MARK_READ = "mark-read";
    String EVENT_CHANGE_ACTOR = "change-actor";
    String EVENT_CHANGE_OWNER = "change-owner";


    String EVENT_SIGNAL_ON = "signal-on";
    String EVENT_SIGNAL_OFF = "signal-off";

    String STEP_ID_END = "@end";
    String STEP_ID_EMPTY = "@empty";
    String STEP_ID_ASSIGNED = "@assigned";

    String TIMER_DUE_TIME = "due_time";

    String WF_ACTOR_LIB_PATH = "/nop/wf/xlib/wf-actor.xlib";
    String WF_ACTOR_NS_PREFIX = "wf-actor:";

    String SYS_ACTION_START = "_start";
    String SYS_ACTION_SUSPEND = "_suspend";
    String SYS_ACTION_RESUME = "_resume";
    String SYS_ACTION_KILL = "_kill";
}
