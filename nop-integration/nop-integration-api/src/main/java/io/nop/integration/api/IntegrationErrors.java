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

    // ==================== W16-impl 凭证解析（共享解析支持，设计 §4.3/§六） ====================

    String ARG_CREDENTIAL_ID = "credentialId";
    String ARG_TYPE_NAME = "typeName";
    String ARG_EXPECTED_TYPE_NAMES = "expectedTypeNames";
    String ARG_FIELD_NAME = "fieldName";

    ErrorCode ERR_CREDENTIAL_PROVIDER_NOT_CONFIGURED =
            define("nop.err.integration.credential-provider-not-configured",
                    "credentialId 已配置但 ICredentialProvider 未装配（部署不一致，fail-closed 拒绝回退静态值）:{credentialId}",
                    ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_RESOLVE_FAILED =
            define("nop.err.integration.credential-resolve-failed",
                    "凭证解析失败（fail-closed，不回退静态值）:{credentialId}", ARG_CREDENTIAL_ID);

    ErrorCode ERR_CREDENTIAL_TYPE_MISMATCH =
            define("nop.err.integration.credential-type-mismatch",
                    "凭证类型与当前渠道不匹配（typeName 不在渠道允许集）:{credentialId},实际类型:{typeName},允许集:{expectedTypeNames}",
                    ARG_CREDENTIAL_ID, ARG_TYPE_NAME, ARG_EXPECTED_TYPE_NAMES);

    ErrorCode ERR_CREDENTIAL_FIELD_REQUIRED =
            define("nop.err.integration.credential-field-required",
                    "凭证必填字段缺失或为空:{credentialId},字段:{fieldName}", ARG_CREDENTIAL_ID, ARG_FIELD_NAME);

    ErrorCode ERR_CREDENTIAL_FIELD_CONVERT_FAILED =
            define("nop.err.integration.credential-field-convert-failed",
                    "凭证字段值类型转换失败:{credentialId},字段:{fieldName}", ARG_CREDENTIAL_ID, ARG_FIELD_NAME);
}
