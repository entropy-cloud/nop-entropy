/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.dsl.orm;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface GptOrmErrors {
    String ARG_SQL_TYPE = "sqlType";
    ErrorCode ERR_GPT_ORM_UNKNOWN_SQL_TYPE =
            define("nop.err.gpt.orm.unknown-sql-type", "未识别的SQL类型:{sqlType}", ARG_SQL_TYPE);
}
