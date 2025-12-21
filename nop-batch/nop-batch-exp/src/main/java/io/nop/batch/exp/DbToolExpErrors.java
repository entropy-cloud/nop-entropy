/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DbToolExpErrors {
    String ARG_TABLE_NAME = "tableName";
    String ARG_FIELD_NAME = "fieldName";
    String ARG_FIELD_NAMES = "fieldNames";

    ErrorCode ERR_EXP_UNDEFINED_TABLE = define("nop.err.exp.undefined-table",
            "未定义的数据库表:{tableName}", ARG_TABLE_NAME);

    ErrorCode ERR_EXP_UNKNOWN_KEY_FIELD = define("nop.err.exp.unknown-key-field",
            "数据表[{tableName}]没有定义唯一键字段:{fieldName},已定义的字段名为：{fieldNames}",
            ARG_TABLE_NAME, ARG_FIELD_NAME, ARG_FIELD_NAMES);
}
