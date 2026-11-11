/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.spring.delta;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * 4.4-C4-004（plan 2562 裁定 G）：模块本地错误码。nop-spring-delta 不改动平台
 * 公共错误码注册面（nop-api-core 为 protected area），故在此以
 * {@link ErrorCode#define} 定义模块级 typed 拒绝错误。
 */
public class SpringDeltaErrors {

    public static final ErrorCode ERR_SPRING_DELTA_CORE_NOT_INITIALIZED = ErrorCode.define(
            "nop.err.spring-delta.core-not-initialized",
            "Nop platform CoreInitialization is not initialized. The Nop Spring integration " +
                    "requires the platform to be initialized (e.g. NopApplication.run or " +
                    "CoreInitialization.initialize()) BEFORE the Spring context refreshes. " +
                    "Behavior change (plan 2562 R4-28): variant-A applications that previously " +
                    "started with a silently empty bean discovery now fail fast at startup.");
}
