/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.fsm;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface FsmErrors {
    String ARG_STATE_VALUE = "stateValue";

    String ARG_STATE_ID = "stateId";

    String ARG_EVENT = "event";

    String ARG_OLD_STATE_ID = "oldStateId";

    String ARG_TYPE = "type";
    String ARG_VALUE = "value";

    ErrorCode ERR_FSM_UNKNOWN_STATE_VALUE = define("nop.err.fsm.err-unknown-state-value", "未知的状态:{}", ARG_STATE_VALUE);

    ErrorCode ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT = define("nop.err.fsm.state-no-transition-for-event",
            "状态[{stateId}]不存在针对事件[{event}]的转换");

    ErrorCode ERR_FSM_UNDEFINED_STATE = define("nop.err.fsm.undefined-state", "未定义的状态[{stateId}]", ARG_STATE_ID);

    ErrorCode ERR_FSM_DUPLICATE_STATE_VALUE = define("nop.err.fsm.duplicate-state-value",
            "状态[{stateId}]的值[{stateValue}]与状态[{oldStateId}]的值重复",
            ARG_STATE_ID, ARG_STATE_VALUE, ARG_OLD_STATE_ID);

    ErrorCode ERR_FSM_STATE_VALUE_CAN_NOT_CONVERT_TO_TYPE = define("nop.err.fsm.state-value-can-not-convert-to-type",
            "状态值[{value}]无法转换为类型[{type}]", ARG_STATE_ID, ARG_VALUE, ARG_TYPE);
}
