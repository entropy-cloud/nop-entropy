/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.vertx.commons;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface VertxCommonErrors {

    ErrorCode ERR_VERTX_NOT_INITIALIZED = define("nop.err.vertx.not-initialized", "Vertx对象尚未初始化或者已经被销毁");

}
