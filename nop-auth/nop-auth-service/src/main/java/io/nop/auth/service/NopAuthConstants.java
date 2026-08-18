/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.auth.api.AuthApiConstants;

public interface NopAuthConstants {

    String SEQUENCE_LOGIN_SESSION = "login-session";

    String SITE_ID_MAIN = "main";

    String ROLE_ADMIN = "admin";

    String ROLE_NOP_ADMIN = "nop-admin";

    String ROLE_USER = "user";

    int USER_STATUS_ACTIVE = AuthApiConstants.USER_STATUS_ACTIVE;

    String RESOURCE_TYPE_TOP_MENU = AuthApiConstants.RESOURCE_TYPE_TOP_MENU;
    String RESOURCE_TYPE_SUB_MENU = AuthApiConstants.RESOURCE_TYPE_SUB_MENU;
    String RESOURCE_TYPE_FUNCTION_POINT = AuthApiConstants.RESOURCE_TYPE_FUNCTION_POINT;

    String PATH_MAIN_ACTION_AUTH = "/nop/main/auth/app.action-auth.xml";

    String PATH_MAIN_DATA_AUTH = "/nop/main/auth/app.data-auth.xml";

    // ===== MFA 常量（设计 §3.5 / §3.6） =====

    /** NopAuthMfaSetting.status 绑定状态：待确认 / 已启用 / 已禁用。启用判定统一口径 status==enabled。 */
    String MFA_STATUS_PENDING = "pending";
    String MFA_STATUS_ENABLED = "enabled";
    String MFA_STATUS_DISABLED = "disabled";

    /** NopAuthMfaSetting.mfaType 第二因子类型。 */
    String MFA_TYPE_TOTP = "totp";
    String MFA_TYPE_SMS = "sms";

    /**
     * WebAuthn/FIDO2 硬件因子（W14-impl，设计 §5.3.2）：多 credential 模型——用户级仍是单值
     * mfaType，密钥材料在 {@code NopAuthMfaCredential} 行（1:N）。
     */
    String MFA_TYPE_WEBAUTHN = "webauthn";

    /**
     * 邮件验证码因子（W15-impl，设计 §5.3.3）：OTP 拥有通道类（factorLevel=1），码经
     * {@code EmailCodeStore}（key={@code mfa-email:{userId}}）生命周期同一期 sms 模式。
     */
    String MFA_TYPE_EMAIL = "email";

    /** SmsCodeStore key 前缀：登录验证码 / MFA 第二因子验证码 / 登记通道 proof（互不通用，通道隔离）。 */
    String SMS_KEY_LOGIN = "login:";
    String SMS_KEY_MFA = "mfa:";
    String SMS_KEY_PROOF = "proof:";

    /** EmailCodeStore key 前缀（W15-impl，设计 §5.3.3 通道隔离：与 sms 侧 key 互不通用）。 */
    String EMAIL_KEY_MFA = "mfa-email:";
    String EMAIL_KEY_PROOF = "proof-email:";

    /**
     * 登记通道 proof 的通道标识（W15-impl，设计 §4.3 既定扩展——"phone 与 email 均登记时
     * 允许用户选择"）：bindMfa/verifyChannelProof 可选 {@code channel} 参数取值，服务端限定
     * 已登记通道集合，缺省解析 = phone 优先、phone 缺失回退 email。
     */
    String PROOF_CHANNEL_PHONE = "phone";
    String PROOF_CHANNEL_EMAIL = "email";
}