/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.record;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface RecordErrors {
    String ARG_FIELD_NAME = "fieldName";

    String ARG_ENCODER = "encoder";

    String ARG_CODEC = "codec";

    String ARG_LENGTH = "length";
    String ARG_MAX_VALUE = "maxValue";

    String ARG_MIN_VALUE = "minValue";

    String ARG_MAX_LENGTH = "maxLength";

    ErrorCode ERR_RECORD_NO_ENOUGH_DATA =
            define("nop.err.record.no-enough-data", "缺少数据，无法读取");

    ErrorCode ERR_RECORD_UNKNOWN_FIELD_CODEC =
            define("nop.err.record.unknown-field-codec", ARG_FIELD_NAME, ARG_CODEC);

    ErrorCode ERR_RECORD_FIELD_LENGTH_GREATER_THAN_MAX_VALUE =
            define("nop.err.record.field-length-greater-than-max-value",
                    ARG_FIELD_NAME, ARG_MAX_VALUE);

    ErrorCode ERR_RECORD_FIELD_LENGTH_LESS_THAN_MIN_VALUE =
            define("nop.err.record.field-length-less-than-min-value",
                    ARG_FIELD_NAME, ARG_MIN_VALUE);

    ErrorCode ERR_RECORD_DECODE_LENGTH_IS_TOO_LONG = define("nop.err.record.decode-length-is-too-long",
            ARG_LENGTH, ARG_MAX_LENGTH);
}
