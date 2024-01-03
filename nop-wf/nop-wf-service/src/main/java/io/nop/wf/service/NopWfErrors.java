/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopWfErrors {
    String ARG_ACTOR_TYPE = "actorType";
    String ARG_ACTOR_NAME = "actorName";

    String ARG_ACTOR_ID = "actorId";

    ErrorCode ERR_WF_NULL_ACTOR = define("nop.err.wf.null-actor", "参与者不允许为空");

    ErrorCode ERR_WF_ACTOR_NOT_USER = define("nop.err.wf.actor-not-user",
            "参与者[{actorName}]必须是用户类型，实际是:{actorType}", ARG_ACTOR_TYPE, ARG_ACTOR_NAME, ARG_ACTOR_ID);

    ErrorCode ERR_WF_ACTOR_NO_DEPT_ID = define("nop.err.wf.actor-no-dept-id",
            "参与者[{actorName}]没有关联到部门", ARG_ACTOR_TYPE, ARG_ACTOR_NAME, ARG_ACTOR_ID);

    ErrorCode ERR_WF_UNKNOWN_ACTOR_TYPE =
            define("nop.err.wf.unknown-actor-type", "未知的参与者类型：{actorType}",
                    ARG_ACTOR_TYPE, ARG_ACTOR_ID);
}
