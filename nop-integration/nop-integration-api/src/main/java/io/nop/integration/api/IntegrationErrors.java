/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.api;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface IntegrationErrors {
    String ARG_MOBILE = "mobile";
    String ARG_ERROR_CODE = "errorCode";
    String ARG_MSG = "msg";

    ErrorCode ERR_SEND_SMS_FAIL =
            define("nop.err.integration.send-sms-fail", "发送短信失败，号码:{mobile},错误码:{errorCode},消息:{msg}",
                    ARG_MOBILE, ARG_ERROR_CODE, ARG_MSG);
}
