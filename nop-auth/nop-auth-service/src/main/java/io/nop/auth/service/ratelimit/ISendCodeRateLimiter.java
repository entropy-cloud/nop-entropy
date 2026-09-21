/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.ratelimit;

/**
 * 发送类验证码限流器（design nop-auth §3.1，plan 2274 Phase 1）。
 * <p>
 * 三层语义：同目标（手机号/邮箱）发送间隔 + 同目标日配额 + 同 IP 日配额（clientIp 为空时跳过
 * IP 层）。超限抛既有错误码（sms：{@code ERR_AUTH_SMS_RATE_LIMITED}/{@code ERR_AUTH_SMS_DAILY_LIMIT}，
 * email：{@code ERR_AUTH_EMAIL_RATE_LIMITED}/{@code ERR_AUTH_EMAIL_DAILY_LIMIT}），param 统一脱敏
 * （{@code ARG_PHONE}/{@code ARG_CHANNEL} 携带 mask 后的值——两侧现状不一致，统一取脱敏侧）。
 * <p>
 * <b>scope 维度</b>（等价基线 = 重构前的共享拓扑）：scope 内共享计数、scope 间隔离——
 * {@code login} 组对应原 LoginServiceImpl 的 4 个限流 Map（sendSmsCode/sendMfaCode 共用），
 * {@code bind} 组对应原 NopAuthUserBizModel 的 3 个 Map（bindSms/bindEmail/channel-proof 共用）。
 * <p>
 * <b>操作顺序</b>：先递增后检查（被拒绝的尝试同样消耗日配额）——与原 compute 语义一致。
 * <p>
 * 装配：{@code SendCodeRateLimiterProvider} 按 {@code nop.auth.rate-limit.store-type}
 * （{@code local|redis}，默认 {@code local}）选择实现，fail-closed。
 */
public interface ISendCodeRateLimiter {

    /** 登录侧发码（sendSmsCode / sendMfaCode sms+email 分支）。 */
    String SCOPE_LOGIN = "login";

    /** 绑定/通道 proof 侧发码（bindSms / bindEmail / requireChannelProof）。 */
    String SCOPE_BIND = "bind";

    /**
     * 短信发送限流检查。clientIp 为空时跳过 IP 层（bind-sms proof 路径现状无 IP 维度）。
     *
     * @param scope    {@link #SCOPE_LOGIN} 或 {@link #SCOPE_BIND}
     * @param phone    目标手机号
     * @param clientIp 客户端 IP（可空）
     */
    void checkSmsAllowed(String scope, String phone, String clientIp);

    /**
     * 邮件发送限流检查。clientIp 为空时跳过 IP 层。
     *
     * @param scope  {@link #SCOPE_LOGIN} 或 {@link #SCOPE_BIND}
     * @param email  目标邮箱
     * @param clientIp 客户端 IP（可空）
     */
    void checkEmailAllowed(String scope, String email, String clientIp);
}
