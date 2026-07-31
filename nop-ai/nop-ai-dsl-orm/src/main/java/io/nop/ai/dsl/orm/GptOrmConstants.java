/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.dsl.orm;

/**
 * Constants for the nop-ai-dsl-orm module.
 * <p>
 * The "GptOrm" class-name prefix and the {@code /nop/schema/gpt/orm.xdef} resource path are
 * historical naming retained for backward compatibility (P3-MA1-038 adjudication): renaming
 * them would be a breaking public API change for an independently released module, with the
 * error code prefix already unified under {@code nop.err.ai.dsl-orm.*} (P2-MA1-034). New code
 * should use the unified error code prefix and reference these constants as-is.
 */
public interface GptOrmConstants {
    String XDEF_GPT_ORM = "/nop/schema/gpt/orm.xdef";

    /**
     * Base package for generated entity class names (P2-MA1-020: no more hardcoded
     * "app.demo." prefix). Default keeps the historical "app.demo" value.
     */
    String CFG_BASE_PACKAGE = "nop.ai.dsl-orm.base-package";
}
