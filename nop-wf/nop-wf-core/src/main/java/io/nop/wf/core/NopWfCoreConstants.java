/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

public interface NopWfCoreConstants extends _NopWfCoreConstants {
    String XDEF_PATH_WF = "/nop/schema/wf/wf.xdef";

    String POSTFIX_XWF = ".xwf";

    String FILE_TYPE_XWF = "xwf";

    String RESOLVE_WF_NS_PREFIX = "resolve-wf:";

    int WF_STEP_STATUS_HISTORY_BOUND = WF_STEP_STATUS_COMPLETED;

    int WF_STATUS_HISTORY_BOUND = WF_STATUS_COMPLETED;

    String PARAM_BIZ_OBJ_ID = "bizObjId";

    String PARAM_BIZ_OBJ_NAME = "bizObjName";

    String PARAM_BIZ_KEY = "bizKey";

    String PARAM_TITLE = "title";

    String VAR_WF = "wf";
    String VAR_WF_RT = "wfRt";
    String VAR_WF_STEP = "wfStep";
    String VAR_ACTOR_MODEL = "actorModel";
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

    String SPECIAL_STEP_ID_PREFIX = "@";

    String TIMER_DUE_TIME = "due_time";

    String WF_ACTOR_LIB_PATH = "/nop/wf/xlib/wf-actor.xlib";
    String WF_ACTOR_NS_PREFIX = "wf-actor:";

    String SYS_ACTION_START = "_start";
    String SYS_ACTION_SUSPEND = "_suspend";
    String SYS_ACTION_RESUME = "_resume";
    String SYS_ACTION_KILL = "_kill";
}
