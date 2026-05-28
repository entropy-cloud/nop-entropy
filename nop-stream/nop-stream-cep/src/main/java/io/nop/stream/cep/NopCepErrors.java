/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import io.nop.api.core.exceptions.ErrorCode;
import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopCepErrors {
    String ARG_PART_NAME = "partName";
    String ARG_NEXT = "next";

    String ARG_FOLLOW_KIND = "followKind";
    String ARG_STATE_NAME = "stateName";

    ErrorCode ERR_CEP_UNKNOWN_PATTERN_PART =
            define("nop.err.cep.unknown-pattern-part", "Undefined pattern part: {partName}", ARG_PART_NAME);

    ErrorCode ERR_CEP_PATTERN_PART_NOT_ALLOW_LOOP =
            define("nop.err.cep.pattern-part-not-allow-loop",
                    "Pattern next part cannot reference a preceding step: partName={partName}, next={next}", ARG_PART_NAME, ARG_NEXT);

    ErrorCode ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP =
            define("nop.err.cep.follow-not-does-support-group", "Not condition does not support complex patterns",
                    ARG_PART_NAME, ARG_FOLLOW_KIND);

    ErrorCode ERR_CEP_NFA_START_STATE_CHECK_FAILED =
            define("nop.err.cep.nfa-start-state-check-failed", "NFA state check failed: state {stateName} does not exist in the NFA", ARG_STATE_NAME);

    ErrorCode ERR_CEP_NFA_STOP_STATE_CHECK_FAILED =
            define("nop.err.cep.nfa-stop-state-check-failed", "NFA state check failed: state {stateName} does not exist in the NFA", ARG_STATE_NAME);

    ErrorCode ERR_CEP_NFA_FINAL_STATE_CHECK_FAILED =
            define("nop.err.cep.nfa-final-state-check-failed", "NFA state check failed: state {stateName} does not exist in the NFA", ARG_STATE_NAME);

    ErrorCode ERR_CEP_NFA_FILTER_EXECUTION_FAILED =
            define("nop.err.cep.nfa-filter-execution-failed", "Failure happened in filter function");

    ErrorCode ERR_CEP_NFA_SHARED_BUFFER_ACCESS_FAILED =
            define("nop.err.cep.nfa-shared-buffer-access-failed", "Shared buffer access failed");
}
