/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_ALLOW_REGISTER;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_TEMPLATE_ID;
import static io.nop.auth.service.NopAuthConstants.EMAIL_KEY_MFA;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_EMAIL;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_LOGIN;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CODE_UNSUPPORTED;

/**
 * 验证码发送流程组件（design nop-auth §3.4，plan 2274 Phase 3 自 LoginServiceImpl 平移，
 * 行为等价重构）：sendSmsCode / sendMfaCode 按 mfaType 分派（sms/email/不支持类型显式拒绝）、
 * 发送器 fail-closed、脱敏日志。限流经 {@code ISendCodeRateLimiter}（scope=login，Phase 1）。
 * <p>
 * 依赖为宿主 wired 实例传递（{@code LoginServiceImpl} accessor 以自身字段构造，手工 wiring
 * 测试路径行为不变）。
 */
public class MfaCodeSender {

    static final Logger LOG = LoggerFactory.getLogger(MfaCodeSender.class);

    private final MfaChallengeStore mfaChallengeStore;
    private final SmsCodeStore smsCodeStore;
    private final EmailCodeStore emailCodeStore;
    private final ISmsSender smsSender;
    private final IEmailSender emailSender;
    private final IDaoProvider daoProvider;
    private final io.nop.auth.service.ratelimit.ISendCodeRateLimiter rateLimiter;

    public MfaCodeSender(@Nullable MfaChallengeStore mfaChallengeStore,
                         @Nullable SmsCodeStore smsCodeStore,
                         @Nullable EmailCodeStore emailCodeStore,
                         @Nullable ISmsSender smsSender,
                         @Nullable IEmailSender emailSender,
                         IDaoProvider daoProvider,
                         io.nop.auth.service.ratelimit.ISendCodeRateLimiter rateLimiter) {
        this.mfaChallengeStore = mfaChallengeStore;
        this.smsCodeStore = smsCodeStore;
        this.emailCodeStore = emailCodeStore;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
        this.daoProvider = daoProvider;
        this.rateLimiter = rateLimiter;
    }

    public void sendSmsCode(String phone, String clientIp) {
        io.nop.api.core.util.Guard.notEmpty(phone, "phone");
        if (!CFG_AUTH_SMS_CODE_ENABLED.get()) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "sms code login is disabled (nop.auth.sms-code.enabled=false)");
        }

        // 防枚举：未注册手机号且 !allow-register → 统一响应"已发送"（不暴露"未注册"信号）
        NopAuthUser user = getUserByPhone(phone);
        if (user == null && !CFG_AUTH_SMS_CODE_ALLOW_REGISTER.get()) {
            LOG.info("nop.auth.sms-code-unregistered-noop:phone={}", MfaContacts.maskPhone(phone));
            return;
        }

        // 限流（scope=login：sendSmsCode 与 sendMfaCode(sms) 共用计数，等价原拓扑）
        rateLimiter.checkSmsAllowed(io.nop.auth.service.ratelimit.ISendCodeRateLimiter.SCOPE_LOGIN, phone, clientIp);

        // 生成 + 存储 + 发送
        String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_LOGIN + phone);
        sendSms(phone, code);
    }

    /**
     * MFA 第二因子验证码重发（设计 §3.6；W14 #8 按 challenge.mfaType 分派）：
     * sms → peek → 手机号解析 → 限流 → 发码 key=mfa:userId；
     * email（W15，设计 §5.3.3）→ 解析 user.email（服务端解析）→ 限流 → EmailCodeStore.send
     * → 邮件发送；其余（totp/webauthn）→ 显式抛 ERR_AUTH_MFA_CODE_UNSUPPORTED。
     */
    public void sendMfaCode(String challengeToken, String clientIp) {
        io.nop.api.core.util.Guard.notEmpty(challengeToken, "challengeToken");
        // peek 校验 challenge 未消费/未作废
        MfaChallenge challenge = mfaChallengeStore == null ? null : mfaChallengeStore.peek(challengeToken);
        if (challenge == null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        // #8 分派（W14，设计 §5.3.0 + W15 email 分支接入）
        if (MFA_TYPE_EMAIL.equals(challenge.getMfaType())) {
            sendMfaEmailCode(challenge, challengeToken, clientIp);
            return;
        }
        if (!MFA_TYPE_SMS.equals(challenge.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_CODE_UNSUPPORTED)
                    .param(ARG_MFA_TYPE, challenge.getMfaType())
                    .param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        // 解析手机号：优先 setting.phone，回退 user.phone
        String phone = challenge.getPhone();
        if (StringHelper.isEmpty(phone)) {
            NopAuthUser user = ContextProvider.runWithTenant(challenge.getTenantId(),
                    () -> getUserByUserId(challenge.getUserId()));
            if (user != null) {
                phone = user.getPhone();
            }
        }
        if (StringHelper.isEmpty(phone)) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken)
                    .param("msg", "no phone number associated with this MFA challenge");
        }

        // 限流（MFA 短信路径使用手机号维度，scope=login）
        rateLimiter.checkSmsAllowed(io.nop.auth.service.ratelimit.ISendCodeRateLimiter.SCOPE_LOGIN, phone, clientIp);

        // 生成 + 存储 + 发送（key=mfa:userId）
        String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_MFA + challenge.getUserId());
        sendSms(phone, code);
    }

    /**
     * email 分支发码（W15-impl，设计 §5.3.3）：enabled 门控（显式拒绝非静默）→ 服务端解析
     * user.email → email+IP 双维度限流 → EmailCodeStore.send（key=mfa-email:{userId}）→
     * IEmailSender 发送。
     */
    protected void sendMfaEmailCode(MfaChallenge challenge, String challengeToken, String clientIp) {
        if (!CFG_AUTH_EMAIL_CODE_ENABLED.get()) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken)
                    .param("msg", "email code is disabled (nop.auth.email-code.enabled=false)");
        }
        if (emailCodeStore == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken)
                    .param("msg", "EmailCodeStore is not configured; email MFA code cannot be sent");
        }
        // 发送目标服务端解析（NopAuthUser.email，不接受客户端指定——防枚举/骚扰，sms 先例）
        NopAuthUser user = ContextProvider.runWithTenant(challenge.getTenantId(),
                () -> getUserByUserId(challenge.getUserId()));
        String email = user == null ? null : user.getEmail();
        if (StringHelper.isEmpty(email)) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken)
                    .param("msg", "no email address associated with this MFA challenge");
        }

        // 限流（email+IP 双维度，scope=login）
        rateLimiter.checkEmailAllowed(io.nop.auth.service.ratelimit.ISendCodeRateLimiter.SCOPE_LOGIN, email, clientIp);

        // 生成 + 存储 + 发送（key=mfa-email:userId，与验证侧 MfaFactorVerifier 消费口径一致）
        String code = emailCodeStore.send(EMAIL_KEY_MFA + challenge.getUserId());
        sendMfaEmail(email, code);
    }

    /**
     * 邮件发送：按 {@code nop.auth.email-code.subject-template}/{@code text-template} 组装
     * {@link EmailMessage} 并发送。无 emailSender 时 fail-closed（对齐 sendSms）。
     */
    protected void sendMfaEmail(String email, String code) {
        if (emailSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "IEmailSender is not configured; email cannot be sent");
        }
        EmailMessage msg = new EmailMessage();
        msg.setTo(Collections.singletonList(email));
        msg.setSubject(CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE.get().replace("{code}", code));
        msg.setText(CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE.get().replace("{code}", code));
        emailSender.sendEmail(msg);
        LOG.info("nop.auth.email-code-sent:email={}", MfaContacts.maskEmail(email));
    }

    /**
     * 短信发送：组装 SmsMessage 并发送。无 smsSender 或无验证码（smsCodeStore 未装配，
     * check2 P3 修复——对齐 email 侧判空）时 fail-closed。
     */
    public void sendSms(String phone, String code) {
        if (smsSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "ISmsSender is not configured; SMS cannot be sent");
        }
        if (code == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "SmsCodeStore is not configured; SMS code cannot be generated");
        }
        SmsMessage msg = new SmsMessage();
        msg.setMobile(phone);
        msg.setTemplateCode(CFG_AUTH_SMS_CODE_TEMPLATE_ID.get());
        msg.setParams(Collections.singletonList(code));
        smsSender.sendMessage(msg);
        LOG.info("nop.auth.sms-code-sent:phone={}", MfaContacts.maskPhone(phone));
    }

    protected NopAuthUser getUserByPhone(String phone) {
        NopAuthUser example = new NopAuthUser();
        example.setPhone(phone);
        return daoProvider.daoFor(NopAuthUser.class).findFirstByExample(example);
    }

    protected NopAuthUser getUserByUserId(String userId) {
        return daoProvider.daoFor(NopAuthUser.class).getEntityById(userId);
    }
}
