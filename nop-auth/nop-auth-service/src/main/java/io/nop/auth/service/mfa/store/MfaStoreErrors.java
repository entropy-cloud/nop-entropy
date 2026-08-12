/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa.store;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * MFA 存储组件模块内部错误码（基础设施级失败路径，区别于 W5 的用户面 NopAuthErrors）。
 * 所有失败路径显式抛出，不静默跳过（Plan Guide Rule #24）。
 */
public interface MfaStoreErrors {

    String ARG_ACTUAL_TYPE = "actualType";
    String ARG_STORE_TYPE = "storeType";
    String ARG_REASON = "reason";

    ErrorCode ERR_MFA_STORE_INVALID_VALUE_TYPE = define(
            "nop.err.mfa-store.invalid-value-type",
            "MFA 存储中读取到非预期类型的值（序列化/编码不一致）", ARG_ACTUAL_TYPE);

    ErrorCode ERR_MFA_STORE_REDIS_BACKEND_NOT_AVAILABLE = define(
            "nop.err.mfa-store.redis-backend-not-available",
            "请求 redis 存储后端但 INosqlService 未配置（fail-closed，不静默回退 Local）",
            ARG_STORE_TYPE, ARG_REASON);
}
