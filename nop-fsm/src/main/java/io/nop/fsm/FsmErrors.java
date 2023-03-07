/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.fsm;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface FsmErrors {
    String ARG_STATE_VALUE = "stateValue";

    String ARG_STATE_ID = "stateId";

    String ARG_EVENT = "event";

    ErrorCode ERR_FSM_UNKNOWN_STATE_VALUE = define("nop.err.fsm.err-unknown-state-value", "未知的状态:{}", ARG_STATE_VALUE);

    ErrorCode ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT = define("nop.err.fsm.state-no-transition-for-event",
            "状态[{stateId}]不存在针对事件[{event}]的转换");

    ErrorCode ERR_FSM_UNDEFINED_STATE = define("nop.err.fsm.undefined-state", "未定义的状态[{stateId}]", ARG_STATE_ID);
}
