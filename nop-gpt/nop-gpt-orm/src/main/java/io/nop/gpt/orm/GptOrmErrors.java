package io.nop.gpt.orm;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GptOrmErrors {
    String ARG_SQL_TYPE = "sqlType";
    ErrorCode ERR_GPT_ORM_UNKNOWN_SQL_TYPE =
            define("nop.err.gpt.orm.unknown-sql-type", "未识别的SQL类型:{sqlType}", ARG_SQL_TYPE);
}
