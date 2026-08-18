/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import io.nop.auth.core.AuthCoreConfigs;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface NopAuthConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopAuthConfigs.class);

    @Description("访问令牌的超时时间，单位为秒")
    IConfigReference<Integer> CFG_AUTH_ACCESS_TOKEN_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.access-token-expire-seconds",
            Integer.class, 30 * 60);

    @Description("更新令牌的超时时间，单位为秒")
    IConfigReference<Integer> CFG_AUTH_REFRESH_TOKEN_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.refresh-token-expire-seconds",
            Integer.class, 300 * 60);

    @Description("系统菜单缓存的大小")
    IConfigReference<Integer> CFG_AUTH_SITE_MAP_CACHE_MAX_SIZE = varRef(s_loc, "nop.auth.site-map.cache-max-size",
            Integer.class, 10);

    @Description("系统菜单缓存的超时时间")
    IConfigReference<Duration> CFG_AUTH_SITE_MAP_CACHE_TIMEOUT = varRef(s_loc, "nop.auth.site-map.cache-timeout",
            Duration.class, Duration.of(10, ChronoUnit.MINUTES));

    @Description("静态配置的菜单文件路径")
    IConfigReference<String> CFG_AUTH_SITE_MAP_STATIC_CONFIG_PATH = varRef(s_loc, "nop.auth.site-map.static-config-path",
            String.class, NopAuthConstants.PATH_MAIN_ACTION_AUTH);

    @Description("是否启用前端调试模式")
    IConfigReference<Boolean> CFG_AUTH_SITE_MAP_SUPPORT_DEBUG = AuthCoreConfigs.CFG_AUTH_SITE_MAP_SUPPORT_DEBUG;

    @Description("是否自动创建缺省用户")
    IConfigReference<Boolean> CFG_AUTH_ALLOW_CREATE_DEFAULT_USER = varRef(s_loc, "nop.auth.login.allow-create-default-user",
            Boolean.class, false);

    @Description("连续登录验证失败之后会临时禁用用户一段时间。管理员可以到后台解锁")
    IConfigReference<Integer> CFG_AUTH_MAX_LOGIN_FAIL_COUNT = varRef(s_loc, "nop.auth.login.max-login-fail-count",
            Integer.class, 10);

    @Description("是否使用验证码机制")
    IConfigReference<Boolean> CFG_AUTH_VERIFY_CODE_ENABLED = varRef(s_loc, "nop.auth.login.verify-code.enabled", Boolean.class,
            false);

    @Description("是否检查数据权限缓存需要被更新")
    IConfigReference<Boolean> CFG_AUTH_DATA_AUTH_CACHE_CHECK_CHANGED =
            varRef(s_loc, "nop.auth.data-auth-cache.check-changed", Boolean.class, true);

    @Description("数据权限配置文件对应的虚拟路径")
    IConfigReference<String> CFG_AUTH_DATA_AUTH_CONFIG_PATH =
            varRef(s_loc, "nop.auth.data-auth-config-path", String.class, NopAuthConstants.PATH_MAIN_DATA_AUTH);

    @Description("启用数据库数据权限配置")
    IConfigReference<Boolean> CFG_AUTH_USE_DATA_AUTH_TABLE =
            varRef(s_loc, "nop.auth.use-data-auth-table", Boolean.class, false);

    @Description("数据权限缓存的超时时间")
    IConfigReference<Duration> CFG_AUTH_DATA_AUTH_CACHE_TIMEOUT = varRef(s_loc, "nop.auth.data-auth.cache-timeout",
            Duration.class, null);

    @Description("是否跳过对管理员的操作权限检查")
    IConfigReference<Boolean> CFG_AUTH_SKIP_CHECK_FOR_ADMIN = varRef(s_loc, "nop.auth.skip-check-for-admin", Boolean.class, false);

    // ===== MFA 配置（设计 §3.7） =====

    @Description("全局 MFA 开关，关闭时即使有用户配置也不强制第二因子")
    IConfigReference<Boolean> CFG_AUTH_MFA_ENABLED = varRef(s_loc, "nop.auth.mfa.enabled",
            Boolean.class, false);

    @Description("MFA challenge / 短信验证码存储实现类型：local、db（默认）或 redis")
    IConfigReference<String> CFG_AUTH_MFA_STORE_TYPE = varRef(s_loc, "nop.auth.mfa.store-type",
            String.class, "db");

    @Description("MFA challenge 有效期，单位秒")
    IConfigReference<Integer> CFG_AUTH_MFA_CHALLENGE_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.mfa.challenge-expire-seconds",
            Integer.class, 300);

    @Description("第二因子最大尝试次数，超限后作废 challenge")
    IConfigReference<Integer> CFG_AUTH_MFA_MAX_ATTEMPTS = varRef(s_loc, "nop.auth.mfa.max-attempts",
            Integer.class, 5);

    @Description("TOTP issuer 名称（otpauth URI 中显示）")
    IConfigReference<String> CFG_AUTH_MFA_TOTP_ISSUER = varRef(s_loc, "nop.auth.mfa.totp-issuer",
            String.class, "nop");

    @Description("TOTP 校验允许的时钟偏差窗口数")
    IConfigReference<Integer> CFG_AUTH_MFA_TOTP_WINDOW_SKEW = varRef(s_loc, "nop.auth.mfa.totp-window-skew",
            Integer.class, 1);

    @Description("MFA 信道类登录成功后签发 accessCode 的有效期，单位秒")
    IConfigReference<Integer> CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.mfa.access-code-expire-seconds",
            Integer.class, 300);

    @Description("MFA 绑定流程 bindToken 有效期，单位秒。confirmMfa 据此判定 pending 记录是否过期（复用 pending 记录 updateTime）")
    IConfigReference<Integer> CFG_AUTH_MFA_BIND_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.mfa.bind-expire-seconds",
            Integer.class, 300);

    // ===== TOTP 绑定/解绑失败上限（A2-followup-1 D1-1） =====

    @Description("confirmMfa/unbindMfa 的 TOTP 分支连续失败次数上限（对齐登录级 max-attempts）：pending 路径达上限作废 bindToken（BIND_EXPIRED），enabled 路径进入冷却窗口")
    IConfigReference<Integer> CFG_AUTH_MFA_TOTP_VERIFY_MAX_FAILS = varRef(s_loc, "nop.auth.mfa.totp-verify-max-fails",
            Integer.class, 5);

    @Description("unbindMfa 的 TOTP 分支失败达上限后的冷却窗口（秒）：窗口内因子验证直接拒绝（ERR_AUTH_MFA_COOLDOWN），过期后可重试；成功验证清零计数")
    IConfigReference<Integer> CFG_AUTH_MFA_TOTP_COOLDOWN_SECONDS = varRef(s_loc, "nop.auth.mfa.totp-cooldown-seconds",
            Integer.class, 300);

    // ===== WebAuthn/FIDO2 配置（W14-impl，设计 §5.3.2 RP 配置） =====

    @Description("WebAuthn Relying Party ID（WebAuthn rpId，一般为域名，如 example.com）。webauthn 因子使用时必配")
    IConfigReference<String> CFG_AUTH_MFA_WEBAUTHN_RP_ID = varRef(s_loc, "nop.auth.mfa.webauthn.rp-id",
            String.class, null);

    @Description("WebAuthn Relying Party 显示名（creationOptions.rp.name）。webauthn 因子使用时必配")
    IConfigReference<String> CFG_AUTH_MFA_WEBAUTHN_RP_NAME = varRef(s_loc, "nop.auth.mfa.webauthn.rp-name",
            String.class, null);

    @Description("WebAuthn 允许的 origin 列表（逗号分隔，如 https://a.com,https://b.com）。验证时精确匹配，不匹配即拒绝（fail-closed 防钓鱼域）。webauthn 因子使用时必配")
    IConfigReference<String> CFG_AUTH_MFA_WEBAUTHN_ORIGINS = varRef(s_loc, "nop.auth.mfa.webauthn.origins",
            String.class, null);

    // ===== 操作级 MFA 配置（设计 §3.1 结论 8 / §3.7） =====

    @Description("操作级 MFA 总开关（会话内敏感操作二次验证）。缺省 false：关闭时拦截器零介入，一期零回归")
    IConfigReference<Boolean> CFG_AUTH_OPERATION_MFA_ENABLED = varRef(s_loc, "nop.auth.operation-mfa.enabled",
            Boolean.class, false);

    @Description("操作级票窗口（秒）：mfaVerifyOperation 验证后允许重试原操作的时间（票绑定 operation+sessionId+单次消费）")
    IConfigReference<Integer> CFG_AUTH_OPERATION_MFA_OP_TICKET_EXPIRE_SECONDS = varRef(s_loc,
            "nop.auth.operation-mfa.op-ticket-expire-seconds", Integer.class, 60);

    // ===== 短信验证码配置（设计 §3.7） =====

    @Description("短信验证码登录开关")
    IConfigReference<Boolean> CFG_AUTH_SMS_CODE_ENABLED = varRef(s_loc, "nop.auth.sms-code.enabled",
            Boolean.class, false);

    @Description("短信验证码有效期，单位秒")
    IConfigReference<Integer> CFG_AUTH_SMS_CODE_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.sms-code.expire-seconds",
            Integer.class, 300);

    @Description("同一手机号短信发送最小间隔，单位秒")
    IConfigReference<Integer> CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS = varRef(s_loc, "nop.auth.sms-code.send-interval-seconds",
            Integer.class, 60);

    @Description("同一手机号每日短信发送上限")
    IConfigReference<Integer> CFG_AUTH_SMS_CODE_DAILY_LIMIT = varRef(s_loc, "nop.auth.sms-code.daily-limit",
            Integer.class, 20);

    @Description("同一 IP 每日短信发送上限")
    IConfigReference<Integer> CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT = varRef(s_loc, "nop.auth.sms-code.ip-daily-limit",
            Integer.class, 50);

    @Description("短信验证码错误上限，超过后作废需重发")
    IConfigReference<Integer> CFG_AUTH_SMS_CODE_MAX_ATTEMPTS = varRef(s_loc, "nop.auth.sms-code.max-attempts",
            Integer.class, 5);

    @Description("短信模板 ID（SmsMessage.templateCode）")
    IConfigReference<String> CFG_AUTH_SMS_CODE_TEMPLATE_ID = varRef(s_loc, "nop.auth.sms-code.template-id",
            String.class, null);

    @Description("未注册手机号是否允许发送验证码（防枚举：关闭时未注册号也统一响应已发送）")
    IConfigReference<Boolean> CFG_AUTH_SMS_CODE_ALLOW_REGISTER = varRef(s_loc, "nop.auth.sms-code.allow-register",
            Boolean.class, false);

    // ===== 邮件验证码配置（W15-impl，设计 §5.3.3——对齐 sms-code 三层限流先例） =====

    @Description("邮件验证码开关（缺省 false：关闭时 bindMfa(email)/sendMfaCode email 分支/登记通道 email 路径显式拒绝，非静默跳过）")
    IConfigReference<Boolean> CFG_AUTH_EMAIL_CODE_ENABLED = varRef(s_loc, "nop.auth.email-code.enabled",
            Boolean.class, false);

    @Description("邮件验证码有效期，单位秒")
    IConfigReference<Integer> CFG_AUTH_EMAIL_CODE_EXPIRE_SECONDS = varRef(s_loc, "nop.auth.email-code.expire-seconds",
            Integer.class, 300);

    @Description("同一邮箱邮件发送最小间隔，单位秒")
    IConfigReference<Integer> CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS = varRef(s_loc, "nop.auth.email-code.send-interval-seconds",
            Integer.class, 60);

    @Description("同一邮箱每日邮件发送上限")
    IConfigReference<Integer> CFG_AUTH_EMAIL_CODE_DAILY_LIMIT = varRef(s_loc, "nop.auth.email-code.daily-limit",
            Integer.class, 20);

    @Description("同一 IP 每日邮件发送上限")
    IConfigReference<Integer> CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT = varRef(s_loc, "nop.auth.email-code.ip-daily-limit",
            Integer.class, 50);

    @Description("邮件验证码错误上限，超过后作废需重发")
    IConfigReference<Integer> CFG_AUTH_EMAIL_CODE_MAX_ATTEMPTS = varRef(s_loc, "nop.auth.email-code.max-attempts",
            Integer.class, 5);

    @Description("邮件验证码主题模板（{code} 占位符服务端替换）")
    IConfigReference<String> CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE = varRef(s_loc, "nop.auth.email-code.subject-template",
            String.class, "Verification Code");

    @Description("邮件验证码正文模板（{code} 占位符服务端替换）")
    IConfigReference<String> CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE = varRef(s_loc, "nop.auth.email-code.text-template",
            String.class, "Your verification code is {code}. It expires in 5 minutes.");

    // ===== 可信设备配置（W15-impl，设计 §六） =====

    @Description("可信设备豁免固定窗口天数（自登记日起算，命中不续期）")
    IConfigReference<Integer> CFG_AUTH_MFA_TRUSTED_DEVICE_TTL_DAYS = varRef(s_loc, "nop.auth.mfa.trusted-device.ttl-days",
            Integer.class, 30);

    @Description("每用户可信设备数量上限（仅计未过期行；同 hash 覆盖刷新不受限；满员新增显式提示不阻断登录）")
    IConfigReference<Integer> CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT = varRef(s_loc, "nop.auth.mfa.trusted-device.max-count",
            Integer.class, 5);
}
