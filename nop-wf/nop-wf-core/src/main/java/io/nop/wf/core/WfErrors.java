/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface WfErrors {
    String ARG_WF_NAME = "wfName";
    String ARG_STEP_NAME = "stepName";
    String ARG_ACTION_NAME = "actionName";

    String ARG_WF_VERSION = "wfVersion";

    String ARG_WF_ID = "wfId";
    String ARG_STEP_ID = "stepId";

    String ARG_STEP_STATUS = "stepStatus";

    String ARG_WF_STATUS = "wfStatus";

    String ARG_WF_ACTOR_TYPE = "wfActorType";
    String ARG_WF_ACTOR_ID = "wfActorId";
    String ARG_ARG_NAME = "argName";

    String ARG_VALUE = "value";

    String ARG_REJECT_STEP = "rejectStep";

    String ARG_ACTOR_CANDIDATES = "actorCandidates";

    String ARG_TARGET_STEPS = "targetSteps";
    String ARG_TARGET_CASES = "targetCases";

    ErrorCode ERR_WF_STEP_INSTANCE_NOT_EXISTS =
            define("nop.err.wf.step-instance-not-exists",
                    "工作流[{wfName}]的步骤实例[{stepId}]不存在", ARG_WF_NAME, ARG_STEP_ID);

    ErrorCode ERR_WF_ALREADY_STARTED =
            define("nop.err.wf.already-started", "工作流已经启动，不能重复启动", ARG_WF_NAME, ARG_WF_ID);

    ErrorCode ERR_WF_NOT_ALLOW_START =
            define("nop.err.wf.not-allow-start",
                    "工作流不满足启动条件，不允许启动", ARG_WF_NAME, ARG_WF_ID);

    ErrorCode ERR_WF_ASSIGNMENT_DYNAMIC_RETURN_NOT_WF_ACTOR =
            define("nop.err.wf.assignment-dynamic-return-not-wf-actor",
                    "动态分配[{wfActorType}]返回的结果不是IWfActor或者IWfActor列表", ARG_WF_ACTOR_TYPE);

    ErrorCode ERR_WF_UNKNOWN_ACTION_ARG =
            define("nop.err.wf.unknown-action-arg",
                    "执行工作流[{wfName}]的[{actionName}]操作时，参数[{argName}]没有定义",
                    ARG_WF_NAME, ARG_ACTION_NAME, ARG_ARG_NAME);

    ErrorCode ERR_WF_EMPTY_ACTION_ARG =
            define("nop.err.wf.empty-action-arg",
                    "执行工作流[{wfName}]的[{actionName}]操作时，参数[{argName}]为空",
                    ARG_WF_NAME, ARG_ACTION_NAME, ARG_ARG_NAME);

    ErrorCode ERR_WF_SELECTED_ACTOR_NOT_IN_ASSIGNMENT =
            define("nop.err.wf.selected-actor-not-in-assignment",
                    "选择的参与者[{wfActorType}:{wfActorId}]不在配置范围之内", ARG_WF_ACTOR_TYPE, ARG_WF_ACTOR_ID);

    ErrorCode ERR_WF_SELECTED_ACTOR_COUNT_NOT_ONE =
            define("nop.err.wf.selected-actor-count-not-one",
                    "选择的参与者个数不是1");

    ErrorCode ERR_WF_START_WF_FAIL =
            define("nop.err.wf.start-wf-fail", "启动工作流[{wfName}]失败", ARG_WF_NAME);

    ErrorCode ERR_WF_ASSIGNMENT_OWNER_EXPR_RESULT_NOT_WF_ACTOR =
            define("nop.err.wf.assignment-owner-expr-result-not-wf-actor",
                    "拥有者表达式的返回值类型不是IWfActor类型", ARG_VALUE);

    ErrorCode ERR_WF_STEP_NO_ASSIGNMENT =
            define("nop.err.wf.step-no-assignment", "工作流[{wfName}]的步骤[{stepName}]没有指定参与者",
                    ARG_WF_NAME, ARG_STEP_NAME);

    ErrorCode ERR_WF_UNKNOWN_ACTION =
            define("nop.err.wf.unknown-action", "工作流[{wfName}]中没有定义操作[{actionName}]",
                    ARG_WF_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS =
            define("nop.err.wf.not-allow-action-in-current-step-status",
                    "当前工作流[{wfName}]的步骤[{stepName}]的状态为[{stepStatus}]，不允许执行操作[{actionName}]",
                    ARG_WF_NAME, ARG_STEP_NAME, ARG_ACTION_NAME, ARG_STEP_STATUS);

    ErrorCode ERR_WF_REJECT_ACTION_IS_NOT_ALLOWED =
            define("nop.err.wf.reject-action-is-not-allowed",
                    "工作流[{wfName}]的步骤[{stepName}]不允许执行退回操作[{actionName}]",
                    ARG_WF_NAME, ARG_STEP_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_WF_WITHDRAW_ACTION_IS_NOT_ALLOWED =
            define("nop.err.wf.withdraw-action-is-not-allowed",
                    "工作流[{wfName}]的步骤[{stepName}]不允许执行撤回操作[{actionName}]",
                    ARG_WF_NAME, ARG_STEP_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_WF_ACTION_CONDITIONS_NOT_PASSED =
            define("nop.err.wf.action-conditions-not-passed",
                    "工作流[{wfName}]的步骤[{stepName}]的操作[{actionName}]不满足执行条件",
                    ARG_WF_NAME, ARG_STEP_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_WF_ACTION_NOT_ALLOWED_WHEN_SIGNAL_NOT_READY =
            define("nop.err.wf.action-not-allowed-when-signal-not-ready",
                    "工作流[{wfName}]的步骤[{stepName}]的操作[{actionName}]等待的信号没有被设置",
                    ARG_WF_NAME, ARG_STEP_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_WF_UNKNOWN_STEP =
            define("nop.err.wf.unknown-step",
                    "工作流[{wfName}]中没有定义步骤[{stepName}]", ARG_WF_NAME, ARG_STEP_NAME);

    ErrorCode ERR_WF_REJECT_STEP_IS_NOT_ANCESTOR_OF_CURRENT_STEP =
            define("nop.err.wf.reject-step-is-not-ancestor-of-current-step",
                    "退回步骤[{rejectStep}]不是当前步骤[{stepName}]的前置步骤", ARG_WF_NAME, ARG_REJECT_STEP, ARG_STEP_NAME);

    ErrorCode ERR_WF_ACTION_TRANSITION_NO_NEXT_STEP =
            define("nop.err.wf.action-transition-no-next-step",
                    "工作流[{wfName}]执行操作[{actionName}]后没有有效的后续步骤", ARG_WF_NAME, ARG_ACTION_NAME);

    ErrorCode ERR_WF_TRANSITION_TARGET_STEPS_NOT_MATCH =
            define("nop.err.wf.transition-target-steps-not-match",
                    "没有匹配的目标步骤：{targetSteps}", ARG_TARGET_STEPS);

    ErrorCode ERR_WF_TRANSITION_TARGET_CASES_NOT_MATCH =
            define("nop.err.wf.transition-target-cases-not-match",
                    "没有匹配的目标步骤：{targetCases}", ARG_TARGET_CASES);

    ErrorCode ERR_WF_NOT_ALLOW_SUSPEND =
            define("nop.err.wf.not-allow-suspend","工作流实例不允许暂停操作");

    ErrorCode ERR_WF_NOT_ALLOW_REMOVE =
            define("nop.err.wf.not-allow-remove","正在运行的工作流实例不允许删除。需要先停止才能删除");

    ErrorCode ERR_WF_MISSING_WF_INSTANCE =
            define("nop.err.wf.missing-wf-instance","工作流实例[{wfId}]不存在");
}
