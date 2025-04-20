package io.nop.ai.coder;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AiCoderErrors {
    String ARG_SQL_TYPE = "sqlType";

    ErrorCode ERR_AI_CODER_UNKNOWN_SQL_TYPE = define("nop.err.ai.coder.unknown-sql-type",
            "未知的SQL类型: {sqlType}", ARG_SQL_TYPE);
}
