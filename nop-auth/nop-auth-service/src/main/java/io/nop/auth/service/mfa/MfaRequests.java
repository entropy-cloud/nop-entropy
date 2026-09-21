/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.config.AppConfig;
import io.nop.auth.api.messages.LoginRequest;

/**
 * MFA 流程合成请求工具（plan 2274 Phase 3 单一落点）：信道引导/mfaVerify 无客户端输入，
 * 用合成 {@link LoginRequest}（loginType=真实信道值，locale/timezone 取 AppConfig 默认）
 * 传入 completeLogin/saveSession。
 */
public final class MfaRequests {

    private MfaRequests() {
    }

    public static LoginRequest synthetic(int loginType) {
        LoginRequest request = new LoginRequest();
        request.setLoginType(loginType);
        request.setLocale(AppConfig.appLocale());
        request.setTimeZone(AppConfig.appTimezone());
        return request;
    }
}
