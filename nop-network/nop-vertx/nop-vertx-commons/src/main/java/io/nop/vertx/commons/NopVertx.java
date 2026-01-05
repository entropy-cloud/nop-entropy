/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.vertx.commons;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import io.vertx.core.Vertx;

import static io.nop.vertx.commons.VertxCommonErrors.ERR_VERTX_NOT_INITIALIZED;

@GlobalInstance
public class NopVertx {
    private static Vertx _INSTANCE;

    public static Vertx instance() {
        Vertx instance = _INSTANCE;
        if (instance == null)
            throw new NopException(ERR_VERTX_NOT_INITIALIZED);

        return instance;
    }

    public static void registerInstance(Vertx vertx) {
        _INSTANCE = vertx;
    }
}
