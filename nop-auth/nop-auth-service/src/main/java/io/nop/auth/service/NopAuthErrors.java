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
    ErrorCode ERR_AUTH_MALFORMED_USER_HANDLE = define("nop.err.auth.malformed-user-handle",
            "Malformed WebAuthn userHandle");

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

    /** 登记通道（{@link #ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED} 的脱敏提示，如手机号后 4 位）。 */
    String ARG_CHANNEL = "channel";

    /** 操作名（{@link #ERR_AUTH_OPERATION_MFA_REQUIRED} 的 errorParams 携带，bizObjName__action 全名）。 */
    String ARG_OPERATION = "operation";

    /** 因子强度（角色策略 minMfaLevel 相关错误的 errorParams 携带）。 */
    String ARG_MFA_LEVEL = "mfaLevel";

    /** bizObj 名（{@link #ERR_AUTH_MFA_CRUD_DISABLED} 的 errorParams 携带）。 */
    String ARG_BIZ_OBJ_NAME = "bizObjName";

    /** 动作名（{@link #ERR_AUTH_MFA_CRUD_DISABLED} 的 errorParams 携带）。 */
    String ARG_ACTION = "action";

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

    // ===== 角色级强制策略（W13-impl，设计 §4.3） =====

    /**
     * 受限会话拦截（mfaRestricted 会话的非白名单 mutation）。errorParams 携带 operation
     * （bizObjName__action 全名）——前端据此渲染受限引导页（绑定强因子后重新登录）。
     */
    ErrorCode ERR_AUTH_MFA_RESTRICTED_SESSION = define(API_STATUS_BAD_REQUEST,
            "nop.err.auth.mfa-restricted-session", "当前会话受多因子策略限制，仅允许安全设置相关操作",
            ARG_OPERATION);

    /**
     * 受限会话内 bindMfa 需先通过登记通道验证（防 enrollment attack）。errorParams 携带
     * channel（脱敏提示：登记手机号后 4 位）。
     */
    ErrorCode ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED = define(API_STATUS_BAD_REQUEST,
            "nop.err.auth.mfa-channel-proof-required",
            "需要先通过登记手机/邮箱的验证码确认身份，验证码已发送至 {channel}", ARG_CHANNEL);

    /** 登记通道为空（无已登记 phone/email，无法自助脱困——管理员介入）。 */
    ErrorCode ERR_AUTH_MFA_NO_RECOVERY_CHANNEL = define("nop.err.auth.mfa-no-recovery-channel",
            "账户未登记可用于身份确认的手机号或邮箱，请联系管理员");

    /**
     * confirmMfa 策略校验（防因子降级，设计 §4.1 结论 6）：确认因子强度 < 角色策略
     * minMfaLevel。errorParams 携带 mfaType/mfaLevel（要求的强度下限）。
     */
    ErrorCode ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK = define(API_STATUS_BAD_REQUEST,
            "nop.err.auth.mfa-policy-factor-too-weak",
            "该因子强度不满足角色策略要求（需要强度级别 {mfaLevel}）", ARG_MFA_TYPE, ARG_MFA_LEVEL);

    // ===== WebAuthn/FIDO2（W14-impl，设计 §5.3.2） =====

    /**
     * sendMfaCode 对无验证码可发的因子类型（totp/webauthn）显式拒绝（设计 §5.3.0 #8——
     * 按 challenge.mfaType 分派；email 分支 W15 接入）。errorParams 携带 mfaType。
     */
    ErrorCode ERR_AUTH_MFA_CODE_UNSUPPORTED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.mfa-code-unsupported",
            "该多因子类型不支持发送验证码", ARG_MFA_TYPE);

    /**
     * 移除最后一把 enabled WebAuthn credential 被拒绝（设计 §5.4——enabled 但零 credential
     * = 用户自锁死；整体解绑走 unbindMfa 的 webauthn ceremony，有恢复码兜底）。
     */
    ErrorCode ERR_AUTH_MFA_LAST_CREDENTIAL = define(API_STATUS_BAD_REQUEST, "nop.err.auth.mfa-last-credential",
            "不能移除最后一把启用的WebAuthn凭证，如需解绑请使用解绑MFA功能");

    // ===== 邮件验证码（W15-impl，设计 §5.3.3——错误码定稿：email 专属三码，不复用 sms 编码） =====

    /** 邮件验证码已失效/不存在（EXPIRED 三态；对齐 sms 侧 ERR_AUTH_SMS_CODE_EXPIRED 的等价通道）。 */
    ErrorCode ERR_AUTH_EMAIL_CODE_EXPIRED = define("nop.err.auth.email-code-expired",
            "邮件验证码已失效，请重新获取");

    ErrorCode ERR_AUTH_EMAIL_RATE_LIMITED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.email-rate-limited",
            "邮件发送过于频繁，请稍后再试");

    ErrorCode ERR_AUTH_EMAIL_DAILY_LIMIT = define(API_STATUS_BAD_REQUEST, "nop.err.auth.email-daily-limit",
            "当日邮件发送次数已达上限");

    // ===== MFA 敏感数据治理（A2-followup-1，D5-F1/D6-1/D3-F3 写路径收口） =====

    /**
     * MFA 敏感表的通用 CRUD 写动作被显式禁用（写路径收口到专项入口：bind/confirm/unbind 族、
     * saveMfaPolicy/removeMfaPolicy、regenerateRecoveryCodes、可信设备 manager 等）。
     * errorParams 携带 action（被禁动作名）与 bizObjName。
     */
    ErrorCode ERR_AUTH_MFA_CRUD_DISABLED = define(API_STATUS_BAD_REQUEST, "nop.err.auth.mfa-crud-disabled",
            "Generic CRUD mutation '{action}' is disabled on MFA data '{bizObjName}'; "
                    + "use the dedicated MFA management API instead", ARG_ACTION, ARG_BIZ_OBJ_NAME);

    /**
     * 非管理员经通用 CRUD 修改用户联系方式（phone/email）被拒（W12 路由项 3——受限会话
     * enrollment attack 链闭合：改 phone 后 bindSms）。联系方式变更需管理员或专用流程。
     */
    ErrorCode ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED = define(API_STATUS_BAD_REQUEST,
            "nop.err.auth.contact-change-not-allowed",
            "Changing phone/email via generic CRUD is not allowed; contact info can only be updated "
                    + "by an administrator or through a dedicated flow", ARG_USER_ID);

    /**
     * TOTP 验证失败次数达上限后的冷却窗口内拒绝（A2-followup-1 D1-1：confirmMfa/unbindMfa
     * TOTP 分支失败计数——pending 路径作废 bindToken 报 BIND_EXPIRED，enabled 路径本码）。
     */
    ErrorCode ERR_AUTH_MFA_COOLDOWN = define(API_STATUS_BAD_REQUEST, "nop.err.auth.mfa-cooldown",
            "Too many failed MFA verification attempts; please retry after the cooldown window",
            ARG_USER_ID);
}
