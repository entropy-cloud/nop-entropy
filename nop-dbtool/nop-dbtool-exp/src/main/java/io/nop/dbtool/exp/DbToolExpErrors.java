/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dbtool.exp;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DbToolExpErrors {
    String ARG_TABLE_NAME = "tableName";
    ErrorCode ERR_EXP_UNDEFINED_TABLE = define("nop.err.exp.undefined-table",
            "未定义的数据库表:{}", ARG_TABLE_NAME);
}
