package io.nop.dbtool.exp;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DbToolExpErrors {
    String ARG_TABLE_NAME = "tableName";
    ErrorCode ERR_EXP_UNDEFINED_TABLE = define("nop.err.exp.undefined-table",
            "未定义的数据库表:{}", ARG_TABLE_NAME);
}
