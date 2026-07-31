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

/**
 * Error codes for the nop-ai-dsl-orm module.
 * <p>
 * Class names in this module keep the historical "GptOrm" prefix (P3-MA1-038 adjudication):
 * the module evolved from an early "gpt orm" prototype namespace, and renaming the classes
 * would be a breaking public API change for an independently released module. The error code
 * prefixes, however, are unified under {@code nop.err.ai.dsl-orm.*} (P2-MA1-034), and the XDef
 * schema path {@code /nop/schema/gpt/orm.xdef} is retained for the same reason (see
 * {@link GptOrmConstants}).
 */
public interface GptOrmErrors {
    String ARG_SQL_TYPE = "sqlType";
    // P2-MA1-034: unified sub-namespace nop.err.ai.{module}.{specific-error} (was nop.err.gpt.orm.*)
    ErrorCode ERR_DSL_ORM_UNKNOWN_SQL_TYPE =
            define("nop.err.ai.dsl-orm.unknown-sql-type", "未识别的SQL类型:{sqlType}", ARG_SQL_TYPE);
}
