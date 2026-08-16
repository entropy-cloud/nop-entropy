/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.ApiConstants.API_STATUS_BAD_REQUEST;
import static io.nop.api.core.ApiConstants.API_STATUS_UNAUTHORIZED;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface NopAuthErrors {
    String ARG_USER_NAME = "userName";
    String ARG_SITE_ID = "siteId";

    String ARG_ROLE_ID = "roleId";

    String ARG_USER_ID = "userId";

    String ARG_PRINCIPAL_ID = "principalId";

    String ARG_SESSION_ID = "sessionId";
    String ARG_AUTH_TOKEN = "authToken";

    String ARG_VAR_NAME = "varName";
    String ARG_ATTR_NAME = "attrName";

    String ARG_WHEN_CONFIG = "whenConfig";

    /**
     * MFA challenge 参数名（{@link #ERR_AUTH_MFA_REQUIRED} 的 errorParams 携带）。
     */
    String ARG_CHALLENGE_TOKEN = "challengeToken";
    String ARG_MFA_TYPE = "mfaType";
    String ARG_LOGIN_TYPE = "loginType";
    String ARG_PHONE = "phone";

    /** 操作名（{@link #ERR_AUTH_OPERATION_MFA_REQUIRED} 的 errorParams 携带，bizObjName__action 全名）。 */
    String ARG_OPERATION = "operation";

    ErrorCode ERR_AUTH_INVALID_LOGIN_REQUEST = define(API_STATUS_BAD_REQUEST, "nop.err.auth.invalid-login-request",
            "登录请求参数不合法");

    ErrorCode ERR_AUTH_LOGIN_CHECK_FAIL = define("nop.err.auth.login-check-fail", "登陆失败，用户名或者密码不匹配");

    ErrorCode ERR_AUTH_LOGIN_WITH_UNKNOWN_USER = define("nop.err.auth.login-with-unknown-user", "登陆使用的用户名不存在");

    ErrorCode ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES = define("nop.err.auth.login-check-fail-too-many-times",
            "登录失败次数过多，账号已被暂时禁用，请等待一段时间再尝试或者联系管理员");

    ErrorCode ERR_AUTH_USER_NOT_ALLOW_LOGIN = define("nop.err.auth.user-not-allow-login",
            "用户[{principalId}]的账号已经过期或者被禁用，不允许登录", ARG_PRINCIPAL_ID);

    ErrorCode ERR_AUTH_INVALID_VERIFY_CODE = define("nop.err.auth.invalid-verify-code", "验证码不匹配或者已失效");

    ErrorCode ERR_AUTH_UNKNOWN_SITE = define("nop.err.auth.unknown-site", "未知的站点：{siteId}", ARG_SITE_ID);

    ErrorCode ERR_AUTH_SESSION_EXPIRED = define(API_STATUS_UNAUTHORIZED, "nop.err.auth.session-expired", "用户未登录或者会话已过期",
            ARG_SESSION_ID, ARG_AUTH_TOKEN);

    ErrorCode ERR_AUTH_NOT_ALLOW_EDIT_INTERNAL_ROLE = define("nop.err.auth.not-allow-edit-internal-role",
            "不允许新建或者修改系统内部角色:{roleId}", ARG_ROLE_ID);

    ErrorCode ERR_AUTH_ONLY_ADMIN_CAN_ASSIGN_INTERNAL_ROLE = define("nop.err.auth.only-admin-can-assign-internal-role",
            "只有系统管理员可以为用户指定内部角色: {roleId}", ARG_ROLE_ID, ARG_USER_ID);

    ErrorCode ERR_AUTH_INVALID_AUTH_WHEN_CONFIG = define("nop.err.auth.invalid-auth-when-config",
            "权限条件配置只支持auth-when名字空间中的标签，不支持其他动态脚本", ARG_WHEN_CONFIG);

    // ===== MFA / SMS 系列（设计 §3.8） =====

    /**
     * 第一因子已通过但用户启用了 MFA，需提交第二因子验证码。
     * errorParams 携带 challengeToken/mfaType/loginType。
     */
    ErrorCode ERR_AUTH_MFA_REQUIRED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.mfa-required",
            "需要多因子验证", ARG_CHALLENGE_TOKEN, ARG_MFA_TYPE, ARG_LOGIN_TYPE);

    ErrorCode ERR_AUTH_MFA_FAIL = define("nop.err.auth.mfa-fail", "多因子验证失败");

    /**
     * 操作级 MFA 拦截（会话内敏感操作二次验证，设计 §3.3）：errorParams 携带
     * challengeToken/mfaType/operation——客户端凭 challengeToken 调 mfaVerifyOperation
     * 验证后携票重试。与一期 {@link #ERR_AUTH_MFA_REQUIRED}（登录期）编码区分，前端可
     * 区分"登录期"与"会话期"弹窗。
     */
    ErrorCode ERR_AUTH_OPERATION_MFA_REQUIRED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.operation-mfa-required",
            "敏感操作需要二次验证", ARG_CHALLENGE_TOKEN, ARG_MFA_TYPE, ARG_OPERATION);

    ErrorCode ERR_AUTH_MFA_CHALLENGE_EXPIRED = define("nop.err.auth.mfa-challenge-expired",
            "多因子验证已过期或已失效，请重新登录");

    ErrorCode ERR_AUTH_MFA_NOT_ENABLED = define("nop.err.auth.mfa-not-enabled",
            "未启用多因子验证", ARG_USER_ID);

    ErrorCode ERR_AUTH_MFA_ALREADY_ENABLED = define("nop.err.auth.mfa-already-enabled",
            "已启用多因子验证", ARG_USER_ID);

    ErrorCode ERR_AUTH_MFA_BIND_EXPIRED = define("nop.err.auth.mfa-bind-expired",
            "多因子绑定流程已过期，请重新发起绑定");

    ErrorCode ERR_AUTH_SMS_CODE_INVALID = define("nop.err.auth.sms-code-invalid",
            "短信验证码不匹配");

    ErrorCode ERR_AUTH_SMS_CODE_EXPIRED = define("nop.err.auth.sms-code-expired",
            "短信验证码已失效，请重新获取");

    ErrorCode ERR_AUTH_SMS_RATE_LIMITED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.sms-rate-limited",
            "短信发送过于频繁，请稍后再试", ARG_PHONE);

    ErrorCode ERR_AUTH_SMS_DAILY_LIMIT = define(API_STATUS_BAD_REQUEST, "nop.err.auth.sms-daily-limit",
            "当日短信发送次数已达上限", ARG_PHONE);

    ErrorCode ERR_AUTH_MFA_RECOVERY_CODE_USED = define("nop.err.auth.mfa-recovery-code-used",
            "该恢复码已被使用过");
}
