/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizAudit;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.beans.NopAuthMfaCredentialOutputBean;
import io.nop.auth.api.mfa.MfaRequired;
import io.nop.auth.api.messages.WebAuthnAssertion;
import io.nop.auth.api.messages.WebAuthnAttestation;
import io.nop.auth.api.messages.WebAuthnCreationOptions;
import io.nop.auth.biz.INopAuthUserBiz;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.IPasswordPolicy;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.generator.IUserIdGenerator;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.biz.dto.MfaStatusResult;
import io.nop.auth.service.biz.dto.MfaWebauthnAddKeyBeginResult;
import io.nop.auth.service.biz.dto.MfaWebauthnBeginResult;
import io.nop.auth.service.biz.dto.TrustedDeviceInfo;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.auth.service.mfa.WebAuthnAuthenticator;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_OLD_PASSWORD_NOT_MATCH;
import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_RATE_TRACKER_EXPIRE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_RATE_TRACKER_MAX_SIZE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_BIND_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_COOLDOWN_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_ISSUER;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_VERIFY_MAX_FAILS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_TEMPLATE_ID;
import static io.nop.auth.service.NopAuthConstants.EMAIL_KEY_MFA;
import static io.nop.auth.service.NopAuthConstants.EMAIL_KEY_PROOF;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_DISABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_PENDING;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_EMAIL;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_TOTP;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_WEBAUTHN;
import static io.nop.auth.service.NopAuthConstants.PROOF_CHANNEL_EMAIL;
import static io.nop.auth.service.NopAuthConstants.PROOF_CHANNEL_PHONE;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_PROOF;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ARG_USER_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_RATE_LIMITED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_ALREADY_ENABLED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_FAIL;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_LAST_CREDENTIAL;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED;

@BizModel("NopAuthUser")
@Locale("zh-CN")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> implements INopAuthUserBiz {

    /** 恢复码数量（设计 §3.4：10 个一次性）。 */
    private static final int RECOVERY_CODE_COUNT = 10;

    /** 恢复码位数（设计 §3.4：10 位数字）。 */
    private static final int RECOVERY_CODE_DIGITS = 10;

    /** bindMfa 入参白名单（§5.3.0 #2，W14 含 webauthn、W15 含 email；错误消息动态拼接自此单点）。 */
    private static final List<String> SUPPORTED_MFA_TYPES = List.of(MFA_TYPE_TOTP, MFA_TYPE_SMS, MFA_TYPE_WEBAUTHN, MFA_TYPE_EMAIL);

    @Inject
    IPasswordEncoder passwordEncoder;

    @Inject
    IUserIdGenerator userIdGenerator;

    @Inject
    IPasswordPolicy passwordPolicy;

    /**
     * TOTP 验证器（W4）。bindMfa 生成 secret + provisioning URI，confirmMfa/unbindMfa 校验。
     * 装配与 W5 {@code LoginServiceImpl} 一致（auth-service.beans.xml 的 {@code totpAuthenticator} bean）。
     */
    @Inject
    @Nullable
    protected TOTPAuthenticator totpAuthenticator;

    /**
     * 短信验证码 store（W4）。bindMfa(sms) 发码、confirmMfa/unbindMfa(sms) 校验，key={@code mfa:userId}。
     * 装配与 W5 {@code LoginServiceImpl} 一致。
     */
    @Inject
    @Nullable
    protected SmsCodeStore smsCodeStore;

    /**
     * 短信发送器（nop-integration-api）。bindMfa(sms) 发送验证码到用户手机。无 smsSender 时 fail-closed。
     */
    @Inject
    @Nullable
    protected ISmsSender smsSender;

    /**
     * 邮件验证码 store（W15-impl，设计 §5.3.3）：bindMfa(email)/登记通道 email proof 发码
     * （key={@code mfa-email:{userId}} / {@code proof-email:{userId}}）。
     */
    @Inject
    @Nullable
    protected EmailCodeStore emailCodeStore;

    /**
     * 邮件发送器（nop-integration-api，W15-impl 复用既有实现零变更）。未装配时 email 因子
     * 绑定/发码 fail-closed（对齐 sms {@code smsSender == null} 行为）。
     */
    @Inject
    @Nullable
    protected IEmailSender emailSender;

    /**
     * 共享因子校验组件（W12-impl，设计 §3.1 结论 5）：confirmMfa/unbindMfa 的因子校验
     * 收敛于此（TOTP 窗口推进内聚组件，confirmMfa 不再重复验证）。
     */
    @Inject
    @Nullable
    protected MfaFactorVerifier mfaFactorVerifier;

    /**
     * MFA challenge store（W13-impl，设计 §4.3）：受限会话内 bindMfa 的登记通道 proof 票
     * （scene=channel-proof）核验与一次性消费。与 LoginServiceImpl 共享同一装配。
     */
    @Inject
    @Nullable
    protected io.nop.auth.core.mfa.store.MfaChallengeStore mfaChallengeStore;

    /**
     * WebAuthn 验证器组件（W14-impl，设计 §5.3.2）：注册/解绑 ceremony 的 attestation/options
     * 构造；断言验证经 {@link MfaFactorVerifier} 统一收敛。未装配（无 webauthn 部署）时
     * webauthn 绑定显式拒绝（fail-closed）。
     */
    @Inject
    @Nullable
    protected WebAuthnAuthenticator webAuthnAuthenticator;

    /**
     * 登录服务（密码变更/重置后吊销目标用户既有会话）。可选注入：未装配时跳过会话吊销
     * （测试/手工装配路径），密码本身仍正常变更。
     */
    @Inject
    @Nullable
    protected ILoginService loginService;

    /**
     * 角色级 MFA 策略评估器（W13-impl，设计 §4.3）：confirmMfa 策略校验（防因子降级）。
     * 可选注入：未装配 = 无策略 = 不校验（一期行为）。
     */
    @Inject
    @Nullable
    protected io.nop.auth.service.mfa.RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;

    /**
     * 审计服务（W13：登记通道 proof 发码事件）。{@code @BizAudit} 为装饰性注解（W12 裁定）。 */
    @Inject
    @Nullable
    protected io.nop.api.core.audit.IAuditService auditService;

    /**
     * 可信设备共享组件（W15-impl，设计 §六）：撤销矩阵钩子（unbindMfa/confirmMfa 换绑/
     * resetUserMfa 全量删除）+ 管理 API（listTrustedDevices/removeTrustedDevice）。
     * 可选注入：未装配 = 钩子/管理显式拒绝（fail-closed，不静默）。
     */
    @Inject
    @Nullable
    protected MfaTrustedDeviceManager trustedDeviceManager;

    /**
     * JDBC 模板（A2-followup-1 D1-1）：TOTP 绑定/解绑失败计数的原子递增/条件判定
     * （{@code DbMfaChallengeStore.incrFailCount} 同型 raw SQL）。手工 wiring 测试可缺省——
     * 缺省时计数退化为实体写（无装饰器直调路径同样持久）。
     */
    @Inject
    @Nullable
    protected io.nop.orm.IOrmTemplate ormTemplate;

    public NopAuthUserBizModel() {
        setEntityName(NopAuthUser.class.getName());
    }

    // ===================== MFA 用户自助管理（设计 §3.6） =====================

    /**
     * 发起 MFA 绑定（设计 §3.6 绑定状态机）。status 检查（enabled → ALREADY_ENABLED）；
     * totp 生成 secret + provisioning URI（明文 base32 secret 一次性返回）+ bindToken；
     * sms 向用户手机号发码 + bindToken。重复 bindMfa 覆盖旧 pending。
     * <p>
     * bindToken TTL 裁决（Phase 1 Decision）：option (a)——复用 pending 记录 {@code updateTime}
     * + 配置 {@code nop.auth.mfa.bind-expire-seconds}（默认 300）。confirmMfa 据此区分"未找到/不匹配"
     * 与"已过期"两种失效（均映射 BIND_EXPIRED，但实现显式判定）。
     */
    @Description("发起MFA绑定")
    @BizMutation
    @BizAudit(logRequestFields = "mfaType")
    public MfaBindResult bindMfa(@Name("mfaType") String mfaType,
                                  @io.nop.api.core.annotations.core.Optional @Name("proof") String proofToken,
                                  @io.nop.api.core.annotations.core.Optional @Name("channel") String channel,
                                  IServiceContext context) {
        String userId = requireCurrentUserId(context);
        // #2 白名单扩容（W14/W15，设计 §5.3.0）：合法值集合含 webauthn/email；错误消息动态拼接
        // 合法值（新增因子只改 SUPPORTED_MFA_TYPES 单点）
        if (!SUPPORTED_MFA_TYPES.contains(mfaType)) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_MFA_TYPE, mfaType)
                    .param("msg", "unsupported mfaType: " + mfaType + " (supported: " + String.join("/", SUPPORTED_MFA_TYPES) + ")");
        }

        // W13-impl：受限会话内 bindMfa 前置登记通道验证（防 enrollment attack，设计 §4.3）。
        // 正常会话 bindMfa 不受影响（零改动）；webauthn 路径在分派之前自动继承（W14 专项断言）。
        // W15-impl：通道解析扩展——phone 缺失回退 email，双通道登记时可选 channel 参数选择
        //（服务端限定已登记通道集合，设计 §4.3 既定扩展）。
        IUserContext uc = context.getUserContext();
        if (uc != null && uc.isMfaRestricted()) {
            requireChannelProof(userId, proofToken, channel, context);
        }

        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        if (user == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "current user not found");
        }

        IEntityDao<NopAuthMfaSetting> settingDao = daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting setting = settingDao.getEntityById(userId);

        // 已 enabled → ALREADY_ENABLED（无静默跳过）
        if (setting != null && MFA_STATUS_ENABLED.equals(setting.getStatus())) {
            throw new NopException(ERR_AUTH_MFA_ALREADY_ENABLED).param(ARG_USER_ID, userId);
        }

        String bindToken = StringHelper.generateUUID();

        if (MFA_TYPE_TOTP.equals(mfaType)) {
            return bindTotp(settingDao, setting, user, bindToken);
        }
        // #3 分派扩展（W14，设计 §5.3.0）：webauthn → 注册 ceremony 发起
        if (MFA_TYPE_WEBAUTHN.equals(mfaType)) {
            return bindWebauthn(settingDao, setting, user, context);
        }
        // #3 分派扩展（W15-impl，设计 §5.3.3）：email → sms 同形路径（发码到服务端解析的登记 email）
        if (MFA_TYPE_EMAIL.equals(mfaType)) {
            return bindEmail(settingDao, setting, user, bindToken, context);
        }
        return bindSms(settingDao, setting, user, bindToken);
    }

    /**
     * 兼容重载（W13/W14 调用点）：无 channel 参数 = 缺省通道解析（phone 优先、phone 缺失回退
     * email）。无 @BizMutation 注解——GraphQL 面仅暴露四参版本。
     */
    public MfaBindResult bindMfa(String mfaType, String proofToken, IServiceContext context) {
        return bindMfa(mfaType, proofToken, null, context);
    }

    /** 兼容重载（一期调用点）：无 proof/channel 参数 = 正常会话路径。无注解——GraphQL 面仅暴露四参版本。 */
    public MfaBindResult bindMfa(String mfaType, IServiceContext context) {
        return bindMfa(mfaType, null, null, context);
    }

    private MfaBindResult bindTotp(IEntityDao<NopAuthMfaSetting> settingDao, NopAuthMfaSetting setting,
                                   NopAuthUser user, String bindToken) {
        if (totpAuthenticator == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "TOTPAuthenticator bean is not available; totp MFA binding is disabled");
        }
        // 生成明文 base32 secret → 加密落库（pending）；provisioning URI 一次性返回明文
        String base32Secret = totpAuthenticator.generateSecret();
        String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret);
        String issuer = CFG_AUTH_MFA_TOTP_ISSUER.get();
        String account = StringHelper.isEmpty(user.getUserName()) ? user.getUserId() : user.getUserName();
        String provisioningUri = totpAuthenticator.generateProvisioningUri(issuer, account, base32Secret);

        setting = upsertPending(settingDao, setting, user, MFA_TYPE_TOTP, encrypted, null, bindToken);

        MfaBindResult result = new MfaBindResult();
        result.setMfaType(MFA_TYPE_TOTP);
        result.setProvisioningUri(provisioningUri);
        result.setBindToken(bindToken);
        return result;
    }

    private MfaBindResult bindSms(IEntityDao<NopAuthMfaSetting> settingDao, NopAuthMfaSetting setting,
                                  NopAuthUser user, String bindToken) {
        String phone = user.getPhone();
        if (StringHelper.isEmpty(phone)) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, user.getUserId())
                    .param("msg", "user has no phone number; cannot bind sms MFA");
        }
        // phone维度限流前置（对齐bindEmail的checkEmailRateLimit/sendMfaCode的checkSmsRateLimit先例）：
        // 已登录用户可对本人手机号无限触发短信，构成运营商费用滥用
        checkProofRateLimit(phone);
        // 发送验证码到用户手机（key=mfa:userId，与 mfaVerify 消费口径一致）
        String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_MFA + user.getUserId());
        sendSmsForBinding(phone, code);

        upsertPending(settingDao, setting, user, MFA_TYPE_SMS, null, phone, bindToken);

        MfaBindResult result = new MfaBindResult();
        result.setMfaType(MFA_TYPE_SMS);
        result.setSmsSent(true);
        result.setBindToken(bindToken);
        return result;
    }

    /**
     * email 因子绑定发起（W15-impl，设计 §5.3.3——bindSms 同形）：发码到<b>服务端解析</b>的
     * {@code NopAuthUser.email}（不接受客户端指定邮箱，防枚举/骚扰——sms bindMfa 先例）；
     * key={@code mfa-email:{userId}}（与 mfaVerify 消费口径一致，通道隔离）。
     * <p>
     * 门控与限流（email-code 配置组，缺省 enabled=false）：enabled=false / EmailCodeStore 未装配
     * / IEmailSender 未装配 均 fail-closed 显式报错；发码前 email 维度限流（60s 间隔 + 日上限）。
     */
    private MfaBindResult bindEmail(IEntityDao<NopAuthMfaSetting> settingDao, NopAuthMfaSetting setting,
                                    NopAuthUser user, String bindToken, IServiceContext context) {
        String email = user.getEmail();
        if (StringHelper.isEmpty(email)) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, user.getUserId())
                    .param("msg", "user has no email address; cannot bind email MFA");
        }
        if (!CFG_AUTH_EMAIL_CODE_ENABLED.get()) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_MFA_TYPE, MFA_TYPE_EMAIL)
                    .param("msg", "email code is disabled (nop.auth.email-code.enabled=false)");
        }
        if (emailCodeStore == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_MFA_TYPE, MFA_TYPE_EMAIL)
                    .param("msg", "EmailCodeStore is not configured; email MFA binding is disabled");
        }
        // email 维度限流（发码入口统一前置，设计 §5.3.3——email 间隔/日上限 + IP 日上限三层）
        checkEmailRateLimit(email, extractClientIp(context));
        // 发送验证码到登记邮箱（key=mfa-email:userId，与 mfaVerify 消费口径一致）
        String code = emailCodeStore.send(EMAIL_KEY_MFA + user.getUserId());
        sendEmailForBinding(email, code);

        upsertPending(settingDao, setting, user, MFA_TYPE_EMAIL, null, null, bindToken);

        MfaBindResult result = new MfaBindResult();
        result.setMfaType(MFA_TYPE_EMAIL);
        result.setEmailSent(true);
        result.setBindToken(bindToken);
        return result;
    }

    // ===================== WebAuthn/FIDO2 三 ceremony（W14-impl，设计 §5.3.2） =====================

    /**
     * 注册 ceremony 发起（bindMfa(webauthn) 分派目标，设计 §5.3.2 伪代码）：
     * <ol>
     *   <li>创建 pending setting（mfaType=webauthn，secret=null——WebAuthn 无共享秘密；
     *       覆盖写语义同一期；bindToken 不参与 webauthn ceremony，confirm 凭 challengeToken）。</li>
     *   <li>创建 scene=webauthn-register challenge，payload={sessionId, cryptoChallenge}
     *       <b>一次写入</b>（§3.1 结论 4——无后置更新原语）。</li>
     *   <li>返回 challengeToken + creationOptions（challenge=payload.cryptoChallenge；
     *       excludeCredentials=该用户既有 credential 防同一钥匙重复注册）。</li>
     * </ol>
     * 受限会话 proof 前置由 bindMfa 主流程在分派之前统一执行（W13——webauthn 路径自动继承）。
     */
    private MfaBindResult bindWebauthn(IEntityDao<NopAuthMfaSetting> settingDao, NopAuthMfaSetting setting,
                                       NopAuthUser user, IServiceContext context) {
        requireWebauthnConfigured();
        if (mfaChallengeStore == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, user.getUserId())
                    .param("msg", "MfaChallengeStore is not configured; webauthn MFA binding is unavailable");
        }
        IUserContext uc = context.getUserContext();
        if (uc == null || StringHelper.isEmpty(uc.getSessionId())) {
            // ceremony 的会话绑定是 confirm 校验的前置（无会话即不可确认，fail-closed 显式报错）
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, user.getUserId())
                    .param("msg", "webauthn registration requires an active session");
        }

        // pending setting（secret=null；bindToken=null）
        upsertPending(settingDao, setting, user, MFA_TYPE_WEBAUTHN, null, null, null);

        // 注册 challenge：payload={sessionId, cryptoChallenge} 一次写入
        String payload = MfaChallengeHelper.webauthnScenePayload(uc.getSessionId());
        String challengeToken = mfaChallengeStore.create(MfaChallenge.SCENE_WEBAUTHN_REGISTER,
                user.getUserId(), MFA_TYPE_WEBAUTHN, 0, user.getTenantId(), null, payload);
        String cryptoChallenge = MfaChallengeHelper.cryptoChallengeOf(mfaChallengeStore.peek(challengeToken));

        List<String> existingCredentialIds = new ArrayList<>();
        for (NopAuthMfaCredential c : listCredentials(user.getUserId())) {
            existingCredentialIds.add(c.getCredentialId());
        }
        WebAuthnCreationOptions options = webAuthnAuthenticator.buildCreationOptions(
                cryptoChallenge, user.getUserId(), webauthnUserName(user), existingCredentialIds);

        MfaBindResult result = new MfaBindResult();
        result.setMfaType(MFA_TYPE_WEBAUTHN);
        result.setChallengeToken(challengeToken);
        result.setCreationOptions(options);
        return result;
    }

    /**
     * 确认 WebAuthn 注册（设计 §5.3.2 注册 ceremony 确认端点，需登录态）。
     * <p>
     * 校验链：challengeToken → peek（scene=webauthn-register + userId 绑定 + payload.sessionId
     * ==当前会话）→ setting 复核（pending + webauthn）→ attestation 验证（clientData.challenge
     * 匹配 payload.cryptoChallenge / origin / rpId / fmt=none 直接信任）。
     * <ul>
     *   <li>验证失败：incrFailCount（超限作废）+ MFA_FAIL，<b>不消费 challenge</b>（一期失败模式）。</li>
     *   <li>credentialId 唯一冲突（全局唯一约束）：重复注册拒绝（MFA_FAIL）。</li>
     *   <li>成功：credential 落库 + setting.status=enabled（换绑原子性：仅此刻生效）+ 恢复码生成
     *       （对齐一期 confirmMfa）+ consume。</li>
     * </ul>
     * <b>受限会话白名单</b>：本端点在 {@code OperationMfaCheckerImpl.RESTRICTED_SESSION_WHITELIST}
     * （minMfaLevel=3 用户的升级路径 = 受限会话内 bindMfa(webauthn) → confirm，缺白名单即断链）。
     */
    @Description("确认WebAuthn注册")
    @BizMutation
    @BizAudit(logRequestFields = "challengeToken")
    public List<String> confirmWebauthnRegistration(@Name("challengeToken") String challengeToken,
                                                    @Name("attestation") WebAuthnAttestation attestation,
                                                    IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IUserContext uc = context.getUserContext();
        if (StringHelper.isEmpty(challengeToken) || mfaChallengeStore == null) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        // 1. peek + scene + userId 绑定 + 会话绑定（防跨会话搬运 challenge）
        MfaChallenge c = mfaChallengeStore.peek(challengeToken);
        String payloadSessionId = c == null ? null : MfaChallengeHelper.sessionIdOf(c);
        if (c == null || !MfaChallenge.SCENE_WEBAUTHN_REGISTER.equals(c.getScene())
                || !userId.equals(c.getUserId())
                || StringHelper.isEmpty(payloadSessionId) || uc == null
                || !payloadSessionId.equals(uc.getSessionId())) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        // 2. setting 复核（pending + webauthn；换绑必经 disabled 态——状态复核即作废换绑前发起的注册）
        NopAuthMfaSetting setting = daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        if (setting == null || !MFA_STATUS_PENDING.equals(setting.getStatus())
                || !MFA_TYPE_WEBAUTHN.equals(setting.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        String userName = user == null ? userId : webauthnUserName(user);

        // 3. attestation 验证（失败 fail-closed：计数不消费）
        String cryptoChallenge = MfaChallengeHelper.cryptoChallengeOf(c);
        WebAuthnAuthenticator.RegistrationCheck check = webAuthnAuthenticator == null
                || StringHelper.isEmpty(cryptoChallenge)
                ? null : webAuthnAuthenticator.verifyRegistration(cryptoChallenge, userId, userName, attestation);
        if (check == null) {
            incrWebauthnFailCountOrDiscard(challengeToken);
            auditWebauthnEvent(userId, uc, null, false, "webauthn-register-fail", "attestation-invalid");
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken);
        }

        // 4. credentialId 全局唯一冲突 = 重复注册拒绝（同一钥匙已注册过——含其他用户）
        if (findCredentialByCredentialId(check.getCredentialId()) != null) {
            incrWebauthnFailCountOrDiscard(challengeToken);
            auditWebauthnEvent(userId, uc, check.getCredentialId(), false, "webauthn-register-fail",
                    "duplicate-credential");
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken)
                    .param("msg", "webauthn credential already registered");
        }

        // 5. credential 落库 + setting enabled + 恢复码 + consume（一次性）
        IEntityDao<NopAuthMfaCredential> credDao = daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential credential = credDao.newEntity();
        credential.setUserId(userId);
        credential.setCredentialId(check.getCredentialId());
        credential.setPublicKey(check.getPublicKeyCose());
        credential.setSignCount(check.getSignCount());
        credential.setTransports(attestation != null && attestation.getTransports() != null
                ? String.join(",", attestation.getTransports()) : null);
        credential.setName(defaultCredentialName(userId));
        credential.setStatus(MFA_STATUS_ENABLED);
        credential.setTenantId(setting.getTenantId());
        credDao.saveEntity(credential);

        setting.setStatus(MFA_STATUS_ENABLED);
        setting.setBindToken(null);
        List<String> recoveryCodes = regenerateRecoveryCodes(userId);

        mfaChallengeStore.consume(challengeToken);
        auditWebauthnEvent(userId, uc, check.getCredentialId(), true, "webauthn-register-ok", "ok");
        return recoveryCodes;
    }

    /**
     * 解绑 ceremony 发起（设计 §5.3.2）：创建 scene=webauthn-unbind challenge（fresh
     * cryptoChallenge，payload={sessionId, cryptoChallenge} 一次写入）+ requestOptions。
     * webauthn 无"验证码字符串"可输——断言验证需 fresh challenge（与 totp 用户"输码"同位）。
     * <p>
     * <b>受限会话白名单裁定（W14）</b>：本端点<b>不入</b>白名单——受限会话用户的 setting.mfaType
     * 不可能为 webauthn（webauthn=3 已达 factorLevel 表上限，策略 minMfaLevel≤3 时启用 webauthn
     * 的用户永不进入受限态），入白名单为不可达死代码；同理 credential 管理三 API 不入。
     */
    @Description("发起WebAuthn解绑验证")
    @BizMutation
    @BizAudit
    public MfaWebauthnBeginResult webauthnBeginVerify(IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IUserContext uc = context.getUserContext();
        requireWebauthnConfigured();
        if (mfaChallengeStore == null) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }

        NopAuthMfaSetting setting = daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())
                || !MFA_TYPE_WEBAUTHN.equals(setting.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }
        if (uc == null || StringHelper.isEmpty(uc.getSessionId())) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "webauthn verify requires an active session");
        }

        String payload = MfaChallengeHelper.webauthnScenePayload(uc.getSessionId());
        String challengeToken = mfaChallengeStore.create(MfaChallenge.SCENE_WEBAUTHN_UNBIND,
                userId, MFA_TYPE_WEBAUTHN, 0, setting.getTenantId(), null, payload);
        String cryptoChallenge = MfaChallengeHelper.cryptoChallengeOf(mfaChallengeStore.peek(challengeToken));

        MfaWebauthnBeginResult result = new MfaWebauthnBeginResult();
        result.setChallengeToken(challengeToken);
        result.setRequestOptions(webAuthnAuthenticator.buildRequestOptions(cryptoChallenge,
                listEnabledCredentialIds(userId)));
        return result;
    }

    /**
     * 覆盖写 pending 记录（旧 pending 的 secret/bindToken 作废）。换绑原子性：pending 期间旧 enabled
     * secret 不被触碰（本方法仅在 status != enabled 时调用）。
     */
    private NopAuthMfaSetting upsertPending(IEntityDao<NopAuthMfaSetting> settingDao, NopAuthMfaSetting setting,
                                            NopAuthUser user, String mfaType, String secret, String phone,
                                            String bindToken) {
        if (setting == null) {
            setting = settingDao.newEntity();
            setting.setUserId(user.getUserId());
            setting.setTenantId(user.getTenantId());
            setting.setMfaType(mfaType);
            setting.setSecret(secret);
            setting.setPhone(phone);
            setting.setStatus(MFA_STATUS_PENDING);
            setting.setBindToken(bindToken);
            settingDao.saveEntity(setting);
        } else {
            // 旧 pending 覆盖写（tracked entity，事务提交时 flush）
            setting.setMfaType(mfaType);
            setting.setSecret(secret);
            setting.setPhone(phone);
            setting.setStatus(MFA_STATUS_PENDING);
            setting.setBindToken(bindToken);
            setting.setLastVerifiedWindow(null);
        }
        return setting;
    }

    /**
     * 确认绑定（设计 §3.6）。按当前用户 userId 定位 pending 记录，校验 bindToken 匹配 + 未过期，
     * 用 pending secret 校验因子；成功 → status=enabled + 清 bindToken + 生成恢复码（换绑原子性）。
     * <p>
     * 失效区分（Phase 1 裁决约束）：未找到/不匹配 → BIND_EXPIRED；已过期 → BIND_EXPIRED。两种均映射同一错误码，
     * 但实现显式分支判定（不把过期当未找到静默处理）。
     */
    @Description("确认MFA绑定")
    @BizMutation
    @BizAudit(logRequestFields = "mfaType")
    public List<String> confirmMfa(@Name("bindToken") String bindToken, @Name("code") String code,
                                   IServiceContext context) {
        String userId = requireCurrentUserId(context);
        if (StringHelper.isEmpty(bindToken) || StringHelper.isEmpty(code)) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        IEntityDao<NopAuthMfaSetting> settingDao = daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting setting = settingDao.getEntityById(userId);

        // 失效分支 1：未找到 / bindToken 不匹配 → BIND_EXPIRED
        if (setting == null || !bindToken.equals(setting.getBindToken())
                || !MFA_STATUS_PENDING.equals(setting.getStatus())) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        // 失效分支 2：已过期（复用 pending 记录 updateTime，Phase 1 裁决 option a）
        if (isBindExpired(setting)) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        // A2-followup-1 D1-1（pending 路径锁门）：TOTP 失败计数达上限（bindToken 已被作废）→
        // BIND_EXPIRED（用户重新 bindMfa）。SMS/EMAIL 分支不引入本计数（store 内部
        // max-attempts 已覆盖——边界钉定，防误扩）。
        if (MFA_TYPE_TOTP.equals(setting.getMfaType()) && isTotpLocked(userId)) {
            throw new NopException(ERR_AUTH_MFA_BIND_EXPIRED).param(ARG_USER_ID, userId);
        }

        // 校验第二因子（用 pending 记录的 secret；W12-impl 收敛至 MfaFactorVerifier——
        // TOTP 成功即内聚推进 lastVerifiedWindow/lastVerifiedAt，此处不再重复验证窗口）
        boolean ok = mfaFactorVerifier.verify(setting, setting.getMfaType(), code);
        if (!ok) {
            // D1-1：TOTP 分支失败递增计数；达上限作废 bindToken（后续 confirm 报 BIND_EXPIRED）
            if (MFA_TYPE_TOTP.equals(setting.getMfaType())) {
                incrTotpVerifyFail(setting, true);
            }
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId);
        }
        // D1-1：成功清零计数（TOTP 分支；SMS/EMAIL/webauthn 无本计数——webauthn 经
        // challenge store incrFailCountOrDiscard 既有覆盖）
        if (MFA_TYPE_TOTP.equals(setting.getMfaType())) {
            resetTotpVerifyFail(setting);
        }

        // W13-impl 策略校验（防因子降级，设计 §4.1 结论 6）：确认因子强度 < 角色策略
        // minMfaLevel → 拒绝。解绑不受限（用户自主权 + 下次登录受限兜底）；受限会话内
        // confirmMfa 成功后不原位升级会话（无会话变更代码路径——引导重新登录，§4.1 结论 7）
        if (roleMfaPolicyEvaluator != null) {
            io.nop.auth.service.mfa.RoleMfaPolicy policy = roleMfaPolicyEvaluator.evaluateForUser(userId);
            if (policy.getMaxLevel() > 0
                    && io.nop.auth.service.mfa.RoleMfaPolicyEvaluator.factorLevel(setting.getMfaType())
                    < policy.getMaxLevel()) {
                throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK)
                        .param(ARG_MFA_TYPE, setting.getMfaType())
                        .param(io.nop.auth.service.NopAuthErrors.ARG_MFA_LEVEL, policy.getMaxLevel());
            }
        }

        // 成功 → enabled + 清 bindToken（换绑原子性：仅成功才置生效）
        setting.setStatus(MFA_STATUS_ENABLED);
        setting.setBindToken(null);

        // W15-impl 撤销矩阵（设计 §6.3）：换绑判定点 = confirmMfa 成功——因子变更即全量撤销
        //（首次绑定无可信行，删除为无害幂等；pending 覆盖写不算因子变更，仅此刻生效才算）
        revokeTrustedDevices(userId, "factor-change");

        // 生成恢复码（作废旧码）
        return regenerateRecoveryCodes(userId);
    }

    /**
     * 解绑 MFA（设计 §3.6 + §5.3.2 解绑 ceremony）。需验证当前第二因子通过才可解绑；
     * 成功 → status=disabled + 删除全部恢复码（+ W15 可信设备撤销挂点位——本 plan 保持既有行为）。
     * <p>
     * W14-impl 扩展（向后兼容）：webauthn 用户凭可选 {@code challengeToken}+{@code assertion}
     * 完成"验证当前因子"（webauthnBeginVerify 发起 → 断言验证成功 consume；失败 incrFailCount +
     * MFA_FAIL 不消费）；其余 mfaType 一期因子验证路径原样（code 经 MfaFactorVerifier）。
     * <p>
     * 敏感操作（W12-impl 首批标注：修改认证因子类）。
     * <p>
     * 双 ceremony 组合提示：本端点自身标注 {@code @MfaRequired}——{@code operation-mfa.enabled=true}
     * 时 webauthn 用户解绑需两次断言（操作级票一次 + unbind challenge 断言一次），与 totp 用户
     * "输两次码"同构，非缺陷。
     */
    @Description("解绑MFA")
    @BizMutation
    @BizAudit
    @MfaRequired
    public void unbindMfa(@Name("code") String code,
                          @Optional @Name("challengeToken") String challengeToken,
                          @Optional @Name("assertion") WebAuthnAssertion assertion,
                          IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IEntityDao<NopAuthMfaSetting> settingDao = daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting setting = settingDao.getEntityById(userId);

        // 未 enabled → NOT_ENABLED（无静默跳过）
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }

        if (MFA_TYPE_WEBAUTHN.equals(setting.getMfaType())) {
            // 解绑 ceremony（W14，设计 §5.3.2）：断言验证等价保持"验证当前因子"语义
            verifyWebauthnUnbindAssertion(userId, setting, challengeToken, assertion, context);
        } else {
            // A2-followup-1 D1-1（enabled 路径锁门）：TOTP 失败计数达上限 → 冷却窗口内
            // 直接拒绝（窗口 = 最近失败时间 + totp-cooldown-seconds，过期后可重试）。
            // SMS/EMAIL 分支不引入本计数（store 内部 max-attempts 已覆盖——边界钉定，防误扩）
            if (MFA_TYPE_TOTP.equals(setting.getMfaType()) && isTotpLocked(userId)) {
                throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_COOLDOWN)
                        .param(ARG_USER_ID, userId);
            }
            // 一期因子验证路径原样（totp/sms；W12-impl 收敛至 MfaFactorVerifier）
            boolean ok = mfaFactorVerifier.verify(setting, setting.getMfaType(), code);
            if (!ok) {
                // D1-1：TOTP 分支失败递增计数（达上限进入冷却窗口）
                if (MFA_TYPE_TOTP.equals(setting.getMfaType())) {
                    incrTotpVerifyFail(setting, false);
                }
                throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId);
            }
            // D1-1：成功清零计数（TOTP 分支）
            if (MFA_TYPE_TOTP.equals(setting.getMfaType())) {
                resetTotpVerifyFail(setting);
            }
        }

        setting.setStatus(MFA_STATUS_DISABLED);
        setting.setBindToken(null);
        deleteRecoveryCodes(userId);
        // A2-audit D3-F1（P1 修复）：解绑 = 因子作废 → 物理删除该用户全部 webauthn credential 行。
        // 残留 enabled 行会在重绑 webauthn 后复活旧（被窃）钥匙；逻辑删除则占用 credentialId
        // 唯一键阻断同钥匙复注册——故用 bulk 物理 DELETE（deleteWebauthnCredentials，
        // 可信设备撤销矩阵同"信任失效即物理删除"语义）。
        deleteWebauthnCredentials(userId);
        // W15-impl 撤销矩阵（设计 §6.3）：解绑成功 = 因子变更 → 全量删除该用户可信设备
        //（信任前提 = 特定因子持有，因子变更即失效）
        revokeTrustedDevices(userId, "unbind");
    }

    /** 兼容重载（W14 前调用点）：一期 totp/sms 解绑路径。无注解——GraphQL 面仅暴露四参版本。 */
    public void unbindMfa(String code, IServiceContext context) {
        unbindMfa(code, null, null, context);
    }

    /**
     * 解绑 ceremony 断言验证（W14，设计 §5.3.2）：scene=webauthn-unbind + userId 绑定 +
     * payload.sessionId==当前会话（防跨会话重放）→ {@link MfaFactorVerifier} 统一 webauthn
     * 分支（cryptoChallenge 取自服务端 payload + signCount 单调写内聚组件）。失败 incrFailCount
     * （超限作废）+ MFA_FAIL 不消费；成功 consume + 解绑审计事件。
     */
    private void verifyWebauthnUnbindAssertion(String userId, NopAuthMfaSetting setting, String challengeToken,
                                               WebAuthnAssertion assertion, IServiceContext context) {
        IUserContext uc = context.getUserContext();
        MfaChallenge c = StringHelper.isEmpty(challengeToken) || mfaChallengeStore == null
                ? null : mfaChallengeStore.peek(challengeToken);
        String payloadSessionId = c == null ? null : MfaChallengeHelper.sessionIdOf(c);
        if (c == null || !MfaChallenge.SCENE_WEBAUTHN_UNBIND.equals(c.getScene())
                || !userId.equals(c.getUserId())
                || StringHelper.isEmpty(payloadSessionId) || uc == null
                || !payloadSessionId.equals(uc.getSessionId())) {
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId);
        }

        boolean ok = mfaFactorVerifier.verify(setting, MFA_TYPE_WEBAUTHN, null, assertion, c);
        if (!ok) {
            incrWebauthnFailCountOrDiscard(challengeToken);
            auditWebauthnEvent(userId, uc, assertion == null ? null : assertion.getCredentialId(),
                    false, "webauthn-unbind-fail", "assertion-invalid");
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        mfaChallengeStore.consume(challengeToken);
        auditWebauthnEvent(userId, uc, assertion.getCredentialId(), true, "webauthn-unbind-ok", "ok");
    }

    /**
     * 生成/重置恢复码（设计 §3.4 / §3.6）。仅 enabled 状态可调；重置 = 作废旧码 + 生成新码（10 个一次性，
     * BCrypt 加盐哈希存 codeHash，明文一次性返回）。
     * <p>
     * 敏感操作（W12-impl 首批标注：恢复通道重置类——作废旧码）。
     */
    @Description("重置MFA恢复码")
    @BizMutation
    @BizAudit
    @MfaRequired
    public List<String> generateRecoveryCodes(IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IEntityDao<NopAuthMfaSetting> settingDao = daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting setting = settingDao.getEntityById(userId);

        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }
        return regenerateRecoveryCodes(userId);
    }

    /**
     * 查询自己的 MFA 状态（设计 §3.6）。返回 mfaType/status/phone（脱敏）——<b>永不返回 secret</b>。
     */
    @Description("查询MFA状态")
    @BizQuery
    public MfaStatusResult getMfaStatus(IServiceContext context) {
        String userId = requireCurrentUserId(context);
        NopAuthMfaSetting setting = daoFor(NopAuthMfaSetting.class).getEntityById(userId);

        MfaStatusResult result = new MfaStatusResult();
        if (setting == null) {
            result.setStatus(MFA_STATUS_DISABLED);
            return result;
        }
        result.setMfaType(setting.getMfaType());
        result.setStatus(setting.getStatus());
        result.setPhone(maskPhone(setting.getPhone()));
        return result;
    }

    // ===================== WebAuthn credential 管理（W14-impl，设计 §5.3.2；W6 并入先例） =====================

    /**
     * 列出本人的 WebAuthn credentials（本人数据限定）。返回展示字段
     * （sid/name/transports/status/signCount/lastUsedAt/createTime）——<b>不含
     * credentialId/publicKey</b>（公钥材料对齐 secret 列 masked 策略不展示）。
     * <p>
     * 受限会话白名单裁定：不入白名单（受限用户 setting.mfaType 不可能为 webauthn，不可达死代码）。
     */
    @Description("查询WebAuthn凭证列表")
    @BizQuery
    public List<NopAuthMfaCredentialOutputBean> listWebauthnCredentials(IServiceContext context) {
        String userId = requireCurrentUserId(context);
        List<NopAuthMfaCredentialOutputBean> result = new ArrayList<>();
        for (NopAuthMfaCredential c : listCredentials(userId)) {
            NopAuthMfaCredentialOutputBean bean = new NopAuthMfaCredentialOutputBean();
            bean.setSid(c.getSid());
            bean.setUserId(c.getUserId());
            bean.setName(c.getName());
            bean.setTransports(c.getTransports());
            bean.setStatus(c.getStatus());
            bean.setSignCount(c.getSignCount());
            bean.setLastUsedAt(c.getLastUsedAt());
            bean.setCreateTime(c.getCreateTime());
            result.add(bean);
        }
        return result;
    }

    /**
     * 移除一把 WebAuthn credential（本人数据限定——越权归一"不存在"）。
     * 移除<b>最后一把 enabled</b> credential 拒绝（{@code ERR_AUTH_MFA_LAST_CREDENTIAL}——
     * enabled 但零 credential = 用户自锁死；整体解绑走 unbindMfa 的 webauthn ceremony，
     * 有恢复码兜底）。禁用单把（status=disabled）不在此路径——禁用钥匙仍可移除。
     * <p>
     * <b>敏感操作标注（A2-audit 路由项 1 终局裁定，2026-08-19 落地）</b>：删除一把钥匙 =
     * 修改认证因子集合，与 {@code unbindMfa}（删全部钥匙，已标注）同族同强度——
     * {@code @MfaRequired} 标注（A1 §二#4 缩窄先例的区分论证）。last-credential 守卫
     * 防"自锁死"属可用性保护，不构成劫持面（攻击者删钥匙仍需先过操作级票）。
     */
    @Description("移除WebAuthn凭证")
    @BizMutation
    @BizAudit(logRequestFields = "sid")
    @MfaRequired
    public void removeWebauthnCredential(@Name("sid") String sid, IServiceContext context) {
        String userId = requireCurrentUserId(context);
        NopAuthMfaCredential credential = requireOwnCredential(sid, userId);

        if (MFA_STATUS_ENABLED.equals(credential.getStatus()) && countEnabledCredentials(userId) <= 1) {
            throw new NopException(ERR_AUTH_MFA_LAST_CREDENTIAL).param(ARG_USER_ID, userId);
        }
        String credentialId = credential.getCredentialId();
        String name = credential.getName();
        // A2-audit fix-verification V-F1（对齐 D3-F1 修复语义）：物理删除而非逻辑删除——
        // 软删行的 credentialId 仍占用全局唯一键，同钥匙复注册会撞 DB 约束抛未归一异常；
        // 且软删行对重复注册守卫不可见（findAllByExample 过滤 delFlag），守卫与约束判定分裂。
        QueryBean deleteQuery = new QueryBean();
        deleteQuery.addFilter(FilterBeans.eq("sid", sid));
        daoFor(NopAuthMfaCredential.class).deleteByQuery(deleteQuery);
        auditWebauthnEvent(userId, context.getUserContext(), credentialId, true,
                "webauthn-credential-removed", name);
    }

    /**
     * 重命名一把 WebAuthn credential（本人数据限定——越权归一"不存在"）。
     * <p>
     * <b>不标注裁定（A2-audit 路由项 1 终局裁定）</b>：纯展示元数据变更，不触碰认证因子
     * 集合——C1b 缩窄先例（saveCredential/beginOAuthFlow 类展示性动作不标注）直接适用。
     */
    @Description("重命名WebAuthn凭证")
    @BizMutation
    @BizAudit(logRequestFields = "sid")
    public void renameWebauthnCredential(@Name("sid") String sid, @Name("name") String name,
                                         IServiceContext context) {
        String userId = requireCurrentUserId(context);
        if (StringHelper.isEmpty(name)) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "credential name must not be empty");
        }
        NopAuthMfaCredential credential = requireOwnCredential(sid, userId);
        credential.setName(name);
    }

    /** 按 sid 定位本人 credential；不存在与非本人归一"不存在"（不泄露他人数据存在性）。 */
    private NopAuthMfaCredential requireOwnCredential(String sid, String userId) {
        NopAuthMfaCredential credential = StringHelper.isEmpty(sid)
                ? null : daoFor(NopAuthMfaCredential.class).getEntityById(sid);
        if (credential == null || !userId.equals(credential.getUserId())) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "webauthn credential not found");
        }
        return credential;
    }

    // ===================== add-key-while-enabled（A2-followup-2，设计 §10.2） =====================

    /**
     * 发起追加 WebAuthn 钥匙（add-key-while-enabled 正门，A2-followup-2，设计 §10.2）：
     * enabled webauthn 用户在<b>不解绑</b>（保留既有钥匙/恢复码/可信设备）的前提下添加第二把
     * 硬件钥匙——替代 A2 D3-F1 修复后关闭的"解绑保留行累积"旁路（多设备 UX 正门缺失）。
     * <p>
     * 前置守卫（不符显式拒绝，无静默跳过）：登录态会话 + setting enabled + mfaType=webauthn +
     * ≥1 把 enabled credential（持有证明的对象——无钥匙会话不可发起）。非 webauthn/未 enabled
     * 用户仍走 bindMfa 主入口（guard：enabled → ALREADY_ENABLED）。
     * <p>
     * 创建<b>两行</b> scene=webauthn-add challenge（持有证明行 + 注册行，各自独立
     * cryptoChallenge、payload={sessionId, cryptoChallenge} 一次写入——get/create 两 ceremony
     * 各需匹配的 clientData.challenge，单行无法同时服务两 ceremony）；返回
     * verifyChallengeToken + assertionOptions（既有钥匙，allowCredentials=enabled）+
     * addChallengeToken + creationOptions（excludeCredentials=全部既有，防同钥匙重复注册）。
     * <p>
     * <b>不标注裁定</b>：只读准备动作（创建 challenge 不变更任何持久状态），C1b 缩窄先例
     * （beginOAuthFlow 类）直接适用。**受限会话白名单：不入**——受限用户 setting 不可能
     * enabled+webauthn（webauthn=当前 factorLevel 上限，W14 同构裁定先例），入白名单为不可达
     * 死代码。
     */
    @Description("发起追加WebAuthn钥匙")
    @BizMutation
    @BizAudit
    public MfaWebauthnAddKeyBeginResult webauthnBeginAddKey(IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IUserContext uc = context.getUserContext();
        requireWebauthnConfigured();
        if (mfaChallengeStore == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "MfaChallengeStore is not configured; webauthn add-key is unavailable");
        }
        if (uc == null || StringHelper.isEmpty(uc.getSessionId())) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "webauthn add-key requires an active session");
        }

        NopAuthMfaSetting setting = daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())
                || !MFA_TYPE_WEBAUTHN.equals(setting.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }
        List<String> enabledIds = listEnabledCredentialIds(userId);
        if (enabledIds.isEmpty()) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "no enabled webauthn credential to prove possession; re-bind via bindMfa");
        }

        // 双 challenge 行（同一 scene；两行独立 cryptoChallenge；payload 一次写入）
        String verifyToken = mfaChallengeStore.create(MfaChallenge.SCENE_WEBAUTHN_ADD,
                userId, MFA_TYPE_WEBAUTHN, 0, setting.getTenantId(), null,
                MfaChallengeHelper.webauthnScenePayload(uc.getSessionId()));
        String addToken = mfaChallengeStore.create(MfaChallenge.SCENE_WEBAUTHN_ADD,
                userId, MFA_TYPE_WEBAUTHN, 0, setting.getTenantId(), null,
                MfaChallengeHelper.webauthnScenePayload(uc.getSessionId()));

        List<String> existingIds = new ArrayList<>();
        for (NopAuthMfaCredential c : listCredentials(userId)) {
            existingIds.add(c.getCredentialId());
        }
        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        String userName = user == null ? userId : webauthnUserName(user);

        MfaWebauthnAddKeyBeginResult result = new MfaWebauthnAddKeyBeginResult();
        result.setVerifyChallengeToken(verifyToken);
        result.setAssertionOptions(webAuthnAuthenticator.buildRequestOptions(
                MfaChallengeHelper.cryptoChallengeOf(mfaChallengeStore.peek(verifyToken)), enabledIds));
        result.setAddChallengeToken(addToken);
        result.setCreationOptions(webAuthnAuthenticator.buildCreationOptions(
                MfaChallengeHelper.cryptoChallengeOf(mfaChallengeStore.peek(addToken)),
                userId, userName, existingIds));
        return result;
    }

    /**
     * 确认追加 WebAuthn 钥匙（四参 ceremony，设计 §10.2）：add-challenge 绑定复核 + setting
     * 复核（仍 enabled + webauthn）+ 新钥匙 attestation 验证（含 credentialId 重复注册检查）+
     * 持有证明（既有 enabled 钥匙 webauthn.get 断言，校验链对齐
     * {@code verifyWebauthnUnbindAssertion} 形态——scene/userId/sessionId 绑定 +
     * {@link MfaFactorVerifier} 统一 webauthn 分支，无钥匙会话不可伪造）→ credential 落库
     * （status=enabled）→ 双 challenge 一并消费 + 审计（持有证明与钥匙新增分别落）。
     * <p>
     * <b>步骤序裁定（执行期定稿）</b>：读路径（attestation 验证/重复检查/缺省命名）前置于持有
     * 证明——证明的 signCount 条件 UPDATE（组件内聚 raw SQL）会推进既有 credential 行的乐观锁
     * 版本，同 ORM 会话内后续再装载该行会触发 entity-version-changed。安全语义不变：两证明
     * 均须通过才有任何持久化；部分失败语义不变（attestation 失败计数 add 行 / 证明失败计数
     * verify 行）。
     * <p>
     * <b>部分失败语义</b>：持有证明失败仅计数 verify 行、attestation 失败仅计数 add 行
     * （{@code incrWebauthnFailCountOrDiscard} per-token 先例）；verify 行消费时机 = 整体成功时
     * 与 add 行一并收口（"票在动作成功时才消费"纪律）。
     * <p>
     * <b>副作用边界（裁定）</b>：setting 状态/恢复码/可信设备<b>零副作用</b>——不经 pending
     * 状态机（禁止触碰 enabled setting——防锁死）、不重生成恢复码（仅 confirmMfa/恢复码重置
     * 动作管理恢复码）、不撤销可信设备（新钥匙增加不降低既有信任前提）。credentialId 全局唯一
     * 冲突 → MFA_FAIL（bindWebauthn 同语义）。
     * <p>
     * <b>标注裁定</b>：修改认证因子集合族（unbindMfa/removeWebauthnCredential 同族）——
     * {@code @MfaRequired} 标注（操作级票为第二重验证）。
     */
    @Description("确认追加WebAuthn钥匙")
    @BizMutation
    @BizAudit(logRequestFields = "addChallengeToken")
    @MfaRequired
    public void confirmWebauthnAddKey(@Name("addChallengeToken") String addChallengeToken,
                                      @Name("attestation") WebAuthnAttestation attestation,
                                      @Name("verifyChallengeToken") String verifyChallengeToken,
                                      @Name("assertion") WebAuthnAssertion assertion,
                                      IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IUserContext uc = context.getUserContext();

        // 1. 双 challenge 定位与绑定复核（scene=webauthn-add + userId + payload.sessionId==当前会话；
        //    不符归一 MFA_FAIL——越权/跨会话/不存在不泄露区分）
        MfaChallenge verifyRow = requireAddKeyChallenge(verifyChallengeToken, userId, uc);
        MfaChallenge addRow = requireAddKeyChallenge(addChallengeToken, userId, uc);

        // 2. setting 复核（begin 后被解绑/重置 → 拒绝，fail-closed）
        NopAuthMfaSetting setting = daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())
                || !MFA_TYPE_WEBAUTHN.equals(setting.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }

        // 3. attestation 验证（新钥匙注册；失败仅计数 add 行、不消费）——读路径前置于持有证明
        //    （signCount 条件 UPDATE 推进既有行乐观锁版本，同会话后置装载会版本冲突）
        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        String userName = user == null ? userId : webauthnUserName(user);
        String addCryptoChallenge = MfaChallengeHelper.cryptoChallengeOf(addRow);
        WebAuthnAuthenticator.RegistrationCheck check = webAuthnAuthenticator == null
                || StringHelper.isEmpty(addCryptoChallenge)
                ? null : webAuthnAuthenticator.verifyRegistration(addCryptoChallenge, userId, userName, attestation);
        if (check == null) {
            incrWebauthnFailCountOrDiscard(addChallengeToken);
            auditWebauthnEvent(userId, uc, null, false, "webauthn-add-fail", "attestation-invalid");
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, addChallengeToken);
        }

        // 4. credentialId 全局唯一冲突 = 重复注册拒绝（bindWebauthn 同语义；计数 add 行）
        if (findCredentialByCredentialId(check.getCredentialId()) != null) {
            incrWebauthnFailCountOrDiscard(addChallengeToken);
            auditWebauthnEvent(userId, uc, check.getCredentialId(), false, "webauthn-add-fail",
                    "duplicate-credential");
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, addChallengeToken)
                    .param("msg", "webauthn credential already registered");
        }

        // 5. 持有证明（既有 enabled 钥匙断言；失败仅计数 verify 行、不消费）——最后一步读后执行。
        //    缺省命名在此之前的读窗口内完成（证明的 signCount 条件 UPDATE 推进既有行版本后，
        //    同会话 findAllByExample 再装载会 entity-version-changed）
        String newName = defaultCredentialName(userId);
        boolean proofOk = mfaFactorVerifier.verify(setting, MFA_TYPE_WEBAUTHN, null, assertion, verifyRow);
        if (!proofOk) {
            incrWebauthnFailCountOrDiscard(verifyChallengeToken);
            auditWebauthnEvent(userId, uc, assertion == null ? null : assertion.getCredentialId(),
                    false, "webauthn-add-proof-fail", "assertion-invalid");
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, verifyChallengeToken);
        }

        // 6. 新 credential 落库（status=enabled；不经 pending 状态机——setting/恢复码/可信设备零副作用）
        IEntityDao<NopAuthMfaCredential> credDao = daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential credential = credDao.newEntity();
        credential.setUserId(userId);
        credential.setCredentialId(check.getCredentialId());
        credential.setPublicKey(check.getPublicKeyCose());
        credential.setSignCount(check.getSignCount());
        credential.setTransports(attestation != null && attestation.getTransports() != null
                ? String.join(",", attestation.getTransports()) : null);
        credential.setName(newName);
        credential.setStatus(MFA_STATUS_ENABLED);
        credential.setTenantId(setting.getTenantId());
        credDao.saveEntity(credential);

        // 7. 双 challenge 一并消费（整体成功才收口）+ 审计（持有证明与钥匙新增分别落）
        mfaChallengeStore.consume(verifyChallengeToken);
        mfaChallengeStore.consume(addChallengeToken);
        auditWebauthnEvent(userId, uc, assertion.getCredentialId(), true, "webauthn-add-proof-ok", "ok");
        auditWebauthnEvent(userId, uc, check.getCredentialId(), true, "webauthn-key-added",
                credential.getName());
    }

    /** add-key ceremony 的 challenge 绑定复核（scene=webauthn-add + userId + 会话绑定；不符归一 MFA_FAIL）。 */
    private MfaChallenge requireAddKeyChallenge(String challengeToken, String userId, IUserContext uc) {
        MfaChallenge c = StringHelper.isEmpty(challengeToken) || mfaChallengeStore == null
                ? null : mfaChallengeStore.peek(challengeToken);
        String payloadSessionId = c == null ? null : MfaChallengeHelper.sessionIdOf(c);
        if (c == null || !MfaChallenge.SCENE_WEBAUTHN_ADD.equals(c.getScene())
                || !userId.equals(c.getUserId())
                || StringHelper.isEmpty(payloadSessionId) || uc == null
                || !payloadSessionId.equals(uc.getSessionId())) {
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId)
                    .param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        return c;
    }

    // ===================== MFA 可信设备自助管理（W15-impl，设计 §6.3；W6 并入先例） =====================

    /**
     * 列出本人的可信设备（本人数据限定；全部行含过期标记——支持自助清理，设计 §六）。
     * 不返回 deviceHash（不可逆哈希非秘密，展示无益——最小暴露面）。
     */
    @Description("查询可信设备列表")
    @BizQuery
    public List<TrustedDeviceInfo> listTrustedDevices(IServiceContext context) {
        String userId = requireCurrentUserId(context);
        requireTrustedDeviceManager();
        List<TrustedDeviceInfo> result = new ArrayList<>();
        long now = CoreMetrics.currentTimeMillis();
        for (io.nop.auth.dao.entity.NopAuthMfaTrustedDevice row : trustedDeviceManager.listForUser(userId)) {
            TrustedDeviceInfo info = new TrustedDeviceInfo();
            info.setSid(row.getSid());
            info.setDeviceName(row.getDeviceName());
            info.setExpireAt(row.getExpireAt());
            info.setLastUsedAt(row.getLastUsedAt());
            info.setCreateTime(row.getCreateTime());
            info.setExpired(row.getExpireAt() == null
                    || row.getExpireAt().getTime() <= now);
            result.add(info);
        }
        return result;
    }

    /**
     * 移除一把可信设备（本人数据限定——越权/不存在归一"不存在"；物理删除，设计 §六撤销矩阵）。
     */
    @Description("移除可信设备")
    @BizMutation
    @BizAudit(logRequestFields = "sid")
    public void removeTrustedDevice(@Name("sid") String sid, IServiceContext context) {
        String userId = requireCurrentUserId(context);
        requireTrustedDeviceManager();
        boolean removed = trustedDeviceManager.removeBySid(sid, userId);
        if (!removed) {
            // 越权归一"不存在"（不泄露他人数据存在性——webauthn credential 同先例）
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "trusted device not found");
        }
    }

    /** 可信设备组件可用性前置（未装配显式拒绝——fail-closed，不静默空实现）。 */
    private void requireTrustedDeviceManager() {
        if (trustedDeviceManager == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "MfaTrustedDeviceManager is not configured; trusted device management is unavailable");
        }
    }

    /** 撤销矩阵钩子：全量删除该用户可信设备（组件未装配 = 无可删行，跳过）。 */
    private void revokeTrustedDevices(String userId, String reason) {
        if (trustedDeviceManager != null) {
            trustedDeviceManager.removeAllForUser(userId, reason);
        }
    }

    // ===================== MFA 管理员重置（设计 §3.6，Phase 2） =====================

    /**
     * 管理员重置用户 MFA（设计 §3.6）。清除 setting（status=disabled）+ 删除全部恢复码。
     * 重置后该用户登录不再被 MFA 拦截（status != enabled）。
     * <p>
     * 归属裁决（Phase 2 Decision）：option (b)——并入 {@code NopAuthUserBizModel} 加 admin-only auth，
     * 不新建 BizModel（避免 NopIoC 显式 bean 注册的空壳风险）。admin 权限经 {@code @Auth} 权限门禁 +
     * 运行时角色校验（defense-in-depth，可测试）双重保障。
     * <p>
     * 对不存在用户：显式抛错（不静默成功）。
     * <p>
     * 敏感操作（W12-impl 首批标注：管理员重置类）。
     */
    @Description("管理员重置用户MFA")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    @MfaRequired
    public void resetUserMfa(@Name("userId") String userId, IServiceContext context) {
        requireAdmin(context);

        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        if (user == null) {
            // 对不存在用户显式抛错（不静默成功）
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                    .param("msg", "cannot reset MFA: user not found");
        }

        IEntityDao<NopAuthMfaSetting> settingDao = daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting setting = settingDao.getEntityById(userId);
        if (setting != null) {
            setting.setStatus(MFA_STATUS_DISABLED);
            setting.setSecret(null);
            setting.setBindToken(null);
            setting.setMfaType(null);
            setting.setPhone(null);
            setting.setLastVerifiedWindow(null);
        }
        deleteRecoveryCodes(userId);
        // A2-audit D3-F1（P1 修复）：管理员重置 = 全因子作废 → 物理删除 webauthn credential 行
        //（unbindMfa 同步修复；理由见 deleteWebauthnCredentials javadoc）
        deleteWebauthnCredentials(userId);
        // W15-impl 撤销矩阵（设计 §6.3）：管理员重置 → 全量删除该用户可信设备
        revokeTrustedDevices(userId, "admin-reset");
    }

    // ===================== MFA 内部辅助 =====================

    // 绑定/解绑的因子校验已收敛至 MfaFactorVerifier（W12-impl；原 verifyFactorForBind
    // 的 totp/sms 分支语义逐条迁入组件：totp 解密校验+窗口推进 / sms 原子消费+EXPIRED 抛错 /
    // 未知 mfaType 返回 false fail-closed）。

    // ---- WebAuthn 内部辅助（W14-impl，设计 §5.3.2） ----

    /** webauthn ceremony 可用性前置：组件未装配或 RP 配置缺失即显式拒绝（fail-closed）。 */
    private void requireWebauthnConfigured() {
        if (webAuthnAuthenticator == null || !webAuthnAuthenticator.isConfigured()) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_MFA_TYPE, MFA_TYPE_WEBAUTHN)
                    .param("msg", "WebAuthn RP config is incomplete; configure "
                            + "nop.auth.mfa.webauthn.rp-id/rp-name/origins to enable webauthn MFA");
        }
    }

    /** 失败计数（对齐 LoginServiceImpl.incrFailCountOrDiscard 语义）：超限作废 challenge。 */
    private void incrWebauthnFailCountOrDiscard(String challengeToken) {
        if (mfaChallengeStore == null)
            return;
        int failCount = mfaChallengeStore.incrFailCount(challengeToken);
        if (failCount >= CFG_AUTH_MFA_MAX_ATTEMPTS.get()) {
            mfaChallengeStore.consume(challengeToken);
        }
    }

    /** 本人 credentials（全状态，按 createTime 排序稳定展示）。 */
    private List<NopAuthMfaCredential> listCredentials(String userId) {
        IEntityDao<NopAuthMfaCredential> dao = daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential example = dao.newEntity();
        example.setUserId(userId);
        return dao.findAllByExample(example);
    }

    private List<String> listEnabledCredentialIds(String userId) {
        List<String> ids = new ArrayList<>();
        for (NopAuthMfaCredential c : listCredentials(userId)) {
            if (MFA_STATUS_ENABLED.equals(c.getStatus()))
                ids.add(c.getCredentialId());
        }
        return ids;
    }

    private long countEnabledCredentials(String userId) {
        return listCredentials(userId).stream().filter(c -> MFA_STATUS_ENABLED.equals(c.getStatus())).count();
    }

    /** 按 credentialId 全局定位（唯一约束保证至多一行；重复注册检测）。 */
    private NopAuthMfaCredential findCredentialByCredentialId(String credentialId) {
        if (StringHelper.isEmpty(credentialId))
            return null;
        IEntityDao<NopAuthMfaCredential> dao = daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential example = dao.newEntity();
        example.setCredentialId(credentialId);
        List<NopAuthMfaCredential> found = dao.findAllByExample(example);
        return found.isEmpty() ? null : found.get(0);
    }

    /** 缺省设备命名（"WebAuthn Key #N"，N=注册序号）。 */
    private String defaultCredentialName(String userId) {
        return "WebAuthn Key #" + (listCredentials(userId).size() + 1);
    }

    /** creationOptions 用户名（userName 优先，回退 userId）。 */
    private static String webauthnUserName(NopAuthUser user) {
        return StringHelper.isEmpty(user.getUserName()) ? user.getUserId() : user.getUserName();
    }

    /**
     * WebAuthn ceremony 审计事件（注册成功/失败、解绑、credential 移除），落 NopAuthOpLog。
     * userName 非空列必须设置（W13 执行期缺陷教训——缺失会使批处理整批回滚）。
     */
    private void auditWebauthnEvent(String userId, IUserContext uc, String credentialId, boolean success,
                                    String event, String detail) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation("NopAuthUser__webauthn");
        audit.setDescription(event);
        audit.setResultStatus(success ? 200 : 400);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userId);
        audit.setUserName(uc != null && !StringHelper.isEmpty(uc.getUserName()) ? uc.getUserName() : userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("credentialId", credentialId);
        data.put("detail", detail);
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    /**
     * 登记通道 proof 发码限流追踪（W13 phone 维度 + W15 email 维度独立计数）。
     * Caffeine asMap视图：硬上限+2天过期防止键空间无界增长（与LoginServiceImpl限流追踪同款）。
     */
    private final Map<String, long[]> proofRateTracker = newBoundedRateMap();
    private final Map<String, long[]> emailRateTracker = newBoundedRateMap();
    private final Map<String, long[]> emailIpRateTracker = newBoundedRateMap();

    /** 限流追踪Map：Caffeine asMap视图，上限/过期可配置（与LoginServiceImpl共用同一配置组）。 */
    private static Map<String, long[]> newBoundedRateMap() {
        Cache<String, long[]> cache = Caffeine.newBuilder()
                .maximumSize(CFG_AUTH_RATE_TRACKER_MAX_SIZE.get())
                .expireAfterWrite(CFG_AUTH_RATE_TRACKER_EXPIRE.get()).build();
        return cache.asMap();
    }

    /**
     * 受限会话内 bindMfa 的登记通道 proof 门槛（设计 §4.3 防 enrollment attack）：
     * <ol>
     *   <li>有效票核验：scene=channel-proof + 已验证（peek 不变式：verifiedAt 非空 ⇒ 票在
     *       op-ticket 窗口内）+ userId 绑定 + 原子消费（一次性）——通过即返回。</li>
     *   <li>无有效票：服务端解析登记通道（W15-impl 扩展：phone 优先、phone 缺失回退 email；
     *       双通道均登记时可选 {@code channel} 参数选择——服务端限定已登记通道集合，不接受
     *       任意指定）→ 限流（防受限会话内 proof 码轰炸受害者登记手机/邮箱）→
     *       {@code SmsCodeStore.send(proof:{userId})} 或 {@code EmailCodeStore.send(proof-email:{userId})}
     *       发码（通道隔离）→ 抛 {@code CHANNEL_PROOF_REQUIRED}（脱敏提示）。</li>
     * </ol>
     */
    private void requireChannelProof(String userId, String proofToken, String requestedChannel,
                                     IServiceContext context) {
        // 1. 票核验（一次性消费）
        if (!StringHelper.isEmpty(proofToken) && mfaChallengeStore != null) {
            io.nop.auth.core.mfa.store.MfaChallenge c = mfaChallengeStore.peek(proofToken);
            if (c != null && io.nop.auth.core.mfa.store.MfaChallenge.SCENE_CHANNEL_PROOF.equals(c.getScene())
                    && c.getVerifiedAt() != null && userId.equals(c.getUserId())
                    && mfaChallengeStore.consume(proofToken) != null) {
                return; // proof 票通过（一次性消费成功）
            }
            // 无效/过期票按"无有效票"处理（重发码引导，不静默放行也不暴露票状态）
        }

        // 2. 通道解析（服务端；W13 仅 phone → W15 扩展 phone 缺失回退 email + 双通道可选）
        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        String phone = user == null ? null : user.getPhone();
        String email = user == null ? null : user.getEmail();
        boolean hasPhone = !StringHelper.isEmpty(phone);
        boolean hasEmail = !StringHelper.isEmpty(email);
        if (!hasPhone && !hasEmail) {
            throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_NO_RECOVERY_CHANNEL)
                    .param(ARG_USER_ID, userId);
        }
        String channel = resolveProofChannel(requestedChannel, hasPhone, hasEmail, userId);

        if (PROOF_CHANNEL_EMAIL.equals(channel)) {
            // email 通道（W15-impl）：门控（显式拒绝非静默）→ email 维度限流 →
            // EmailCodeStore 发码（key=proof-email:{userId}，通道隔离）→ 脱敏提示抛错
            if (!CFG_AUTH_EMAIL_CODE_ENABLED.get()) {
                // A2-followup-1 D1-3 Proof：发送侧拒绝分支补 fail 审计（防滥用审计面补全）
                auditChannelProofSendFail(userId, user.getUserName(), PROOF_CHANNEL_EMAIL,
                        maskEmail(email), "channel-disabled");
                throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                        .param("msg", "email code is disabled (nop.auth.email-code.enabled=false)");
            }
            if (emailCodeStore == null) {
                auditChannelProofSendFail(userId, user.getUserName(), PROOF_CHANNEL_EMAIL,
                        maskEmail(email), "store-missing");
                throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                        .param("msg", "EmailCodeStore is not configured; email channel proof is unavailable");
            }
            try {
                checkEmailRateLimit(email, extractClientIp(context));
            } catch (NopException e) {
                auditChannelProofSendFail(userId, user.getUserName(), PROOF_CHANNEL_EMAIL,
                        maskEmail(email), "rate-limited");
                throw e;
            }
            String code = emailCodeStore.send(EMAIL_KEY_PROOF + userId);
            sendEmailForBinding(email, code);
            auditChannelProofSent(userId, user.getUserName(), PROOF_CHANNEL_EMAIL, maskEmail(email));
            throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED)
                    .param(io.nop.auth.service.NopAuthErrors.ARG_CHANNEL, maskEmail(email));
        }

        // 3. phone 通道（W13 原路径）：限流（60s 间隔 + 日上限；sendMfaCode 调用点限流先例）
        try {
            checkProofRateLimit(phone);
        } catch (NopException e) {
            // A2-followup-1 D1-3 Proof：发送侧限流拒绝分支补 fail 审计
            auditChannelProofSendFail(userId, user.getUserName(), PROOF_CHANNEL_PHONE,
                    maskPhone(phone), "rate-limited");
            throw e;
        }

        // 4. 发码（key=proof:{userId}，通道隔离）→ 脱敏提示抛错（客户端持码调 verifyChannelProof）
        if (smsCodeStore == null) {
            auditChannelProofSendFail(userId, user.getUserName(), PROOF_CHANNEL_PHONE,
                    maskPhone(phone), "store-missing");
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "SmsCodeStore is not configured; channel proof is unavailable");
        }
        String code = smsCodeStore.send(SMS_KEY_PROOF + userId);
        sendSmsForBinding(phone, code);
        auditChannelProofSent(userId, user.getUserName(), PROOF_CHANNEL_PHONE, maskPhone(phone));
        throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED)
                .param(io.nop.auth.service.NopAuthErrors.ARG_CHANNEL, maskPhone(phone));
    }

    /**
     * 通道选择解析（W15-impl，设计 §4.3 既定扩展的执行期定稿）：显式请求值必须属于
     * {@code phone|email} 且已登记（不接受任意指定——非法/未登记值显式拒绝非静默回退）；
     * 缺省 = phone 优先、phone 缺失回退 email。
     */
    private static String resolveProofChannel(String requestedChannel, boolean hasPhone, boolean hasEmail,
                                              String userId) {
        if (StringHelper.isEmpty(requestedChannel)) {
            return hasPhone ? PROOF_CHANNEL_PHONE : PROOF_CHANNEL_EMAIL;
        }
        if (PROOF_CHANNEL_PHONE.equals(requestedChannel) && hasPhone)
            return PROOF_CHANNEL_PHONE;
        if (PROOF_CHANNEL_EMAIL.equals(requestedChannel) && hasEmail)
            return PROOF_CHANNEL_EMAIL;
        throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userId)
                .param("msg", "requested proof channel is not registered: " + requestedChannel);
    }

    /** proof 发码限流：同手机号 send-interval-seconds 间隔 + 每日 daily-limit 上限（复用 sms-code 配置）。 */
    private void checkProofRateLimit(String phone) {
        long now = CoreMetrics.currentTimeMillis();
        long today = io.nop.api.core.time.CoreMetrics.today().toEpochDay();
        int interval = io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS.get();
        int dailyLimit = io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_DAILY_LIMIT.get();
        // 间隔检查与lastSendMs占用同原子区，防止并发突发同时通过间隔检查
        synchronized (proofRateTracker) {
            long[] entry = proofRateTracker.compute(phone, (k, v) -> {
                if (v == null || v[2] != today) {
                    return new long[]{now, 1, today};
                }
                return new long[]{v[0], v[1] + 1, today};
            });
            if (entry[1] > 1 && (now - entry[0]) < interval * 1000L) {
                throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_RATE_LIMITED)
                        .param(io.nop.auth.service.NopAuthErrors.ARG_PHONE, maskPhone(phone));
            }
            if (entry[1] > dailyLimit) {
                throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_DAILY_LIMIT)
                        .param(io.nop.auth.service.NopAuthErrors.ARG_PHONE, maskPhone(phone));
            }
            entry[0] = now;
        }
    }

    /**
     * 登记通道 proof 发码审计事件（防滥用审计），落 NopAuthOpLog（通道目标脱敏）。
     * userName 必填（NopAuthOpLog 非空列——缺失会使批处理整批回滚，W13 E2E 钉定）。
     */
    private void auditChannelProofSent(String userId, String userName, String channel, String maskedTarget) {
        if (auditService == null)
            return;
        io.nop.api.core.audit.AuditRequest audit = new io.nop.api.core.audit.AuditRequest();
        audit.setOperation("NopAuthUser__bindMfa");
        audit.setDescription("mfa:channel-proof-sent");
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userId);
        audit.setUserName(userName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "channel-proof-sent");
        data.put("channel", channel);
        data.put("target", maskedTarget);
        audit.setRequestData(io.nop.core.lang.json.JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    /**
     * 登记通道 proof <b>发送侧拒绝</b>审计事件（A2-followup-1 D1-3 Proof：限流/channel-disabled/
     * store-missing 分支"失败无审计"补齐）。事件名区分发送侧（send-fail）与验证侧
     * （{@code mfa:channel-proof-fail}，LoginApiBizModel）；审计字段脱敏（maskedTarget），
     * 不含明文联系方式。userName 兜底 userId（非空列教训）。
     */
    private void auditChannelProofSendFail(String userId, String userName, String channel, String maskedTarget,
                                           String reason) {
        if (auditService == null)
            return;
        io.nop.api.core.audit.AuditRequest audit = new io.nop.api.core.audit.AuditRequest();
        audit.setOperation("NopAuthUser__bindMfa");
        audit.setDescription("mfa:channel-proof-send-fail");
        audit.setResultStatus(400);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userId);
        audit.setUserName(StringHelper.isEmpty(userName) ? userId : userName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "channel-proof-send-fail");
        data.put("channel", channel);
        data.put("target", maskedTarget);
        data.put("reason", reason);
        audit.setRequestData(io.nop.core.lang.json.JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    /**
     * email 发码限流（W15-impl，bindMfa(email) 与登记通道 email proof 共用；镜像
     * {@code LoginServiceImpl.checkEmailRateLimit} 三层：同邮箱 send-interval-seconds 间隔 +
     * email 维度 daily-limit + IP 维度 ip-daily-limit（clientIp 可空时 IP 层跳过））。
     */
    private void checkEmailRateLimit(String email, String clientIp) {
        long now = CoreMetrics.currentTimeMillis();
        long today = io.nop.api.core.time.CoreMetrics.today().toEpochDay();
        int interval = CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS.get();
        int dailyLimit = CFG_AUTH_EMAIL_CODE_DAILY_LIMIT.get();
        // 间隔检查与lastSendMs占用同原子区，防止并发突发同时通过间隔检查
        synchronized (emailRateTracker) {
            long[] entry = emailRateTracker.compute(email, (k, v) -> {
                if (v == null || v[2] != today) {
                    return new long[]{now, 1, today};
                }
                return new long[]{v[0], v[1] + 1, today};
            });
            if (entry[1] > 1 && (now - entry[0]) < interval * 1000L) {
                throw new NopException(ERR_AUTH_EMAIL_RATE_LIMITED)
                        .param(io.nop.auth.service.NopAuthErrors.ARG_CHANNEL, maskEmail(email));
            }
            if (entry[1] > dailyLimit) {
                throw new NopException(ERR_AUTH_EMAIL_DAILY_LIMIT)
                        .param(io.nop.auth.service.NopAuthErrors.ARG_CHANNEL, maskEmail(email));
            }
            entry[0] = now;
        }

        if (!StringHelper.isEmpty(clientIp)) {
            int ipLimit = io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT.get();
            long[] ipEntry = emailIpRateTracker.compute(clientIp, (k, v) -> {
                if (v == null || v[1] != today) {
                    return new long[]{1, today};
                }
                return new long[]{v[0] + 1, today};
            });
            if (ipEntry[0] > ipLimit) {
                throw new NopException(ERR_AUTH_EMAIL_DAILY_LIMIT)
                        .param(io.nop.auth.service.NopAuthErrors.ARG_CHANNEL, maskEmail(email));
            }
        }
    }

    /** 邮箱脱敏（W15-impl，对齐手机号后 4 位先例的 email 侧形态）：保留本地部分前 2 位 + 域名。 */
    static String maskEmail(String email) {
        if (StringHelper.isEmpty(email))
            return email;
        int at = email.indexOf('@');
        if (at <= 0)
            return email;
        String local = email.substring(0, at);
        String prefix = local.substring(0, Math.min(2, local.length()));
        return prefix + "***" + email.substring(at);
    }

    /** 从请求头提取客户端 IP（email 限流 IP 维度；LoginApiBizModel.extractClientIp 同型）。 */
    private static String extractClientIp(IServiceContext context) {
        if (context == null || context.getRequestHeaders() == null) {
            return null;
        }
        Map<String, Object> headers = context.getRequestHeaders();
        Object xff = headers.get("X-Forwarded-For");
        if (xff == null) {
            xff = headers.get("x-forwarded-for");
        }
        if (xff != null && !xff.toString().isEmpty()) {
            String ip = xff.toString().split(",")[0].trim();
            return ip.isEmpty() ? null : ip;
        }
        Object xri = headers.get("X-Real-IP");
        if (xri == null) {
            xri = headers.get("x-real-ip");
        }
        return xri == null ? null : xri.toString();
    }

    /**
     * 邮件发送（绑定/proof 用，W15-impl）：按 {@code nop.auth.email-code.subject-template}/
     * {@code text-template}（{@code {code}} 占位服务端替换）组装 {@link EmailMessage} 并经
     * {@link IEmailSender} 发送。无 emailSender 时 fail-closed（对齐 sendSmsForBinding）。
     */
    private void sendEmailForBinding(String email, String code) {
        if (emailSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "IEmailSender is not configured; email MFA binding is disabled");
        }
        EmailMessage msg = new EmailMessage();
        msg.setTo(java.util.Collections.singletonList(email));
        msg.setSubject(CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE.get().replace("{code}", code));
        msg.setText(CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE.get().replace("{code}", code));
        emailSender.sendEmail(msg);
    }

    /**
     * bindToken 过期判定（Phase 1 裁决 option a）：复用 pending 记录 updateTime + 配置 bind-expire-seconds。
     */
    private boolean isBindExpired(NopAuthMfaSetting setting) {
        if (setting.getUpdateTime() == null) {
            return false;
        }
        long expireMs = CFG_AUTH_MFA_BIND_EXPIRE_SECONDS.get() * 1000L;
        long age = CoreMetrics.currentTimeMillis() - setting.getUpdateTime().getTime();
        return age > expireMs;
    }

    // ===================== TOTP 绑定/解绑失败计数（A2-followup-1 D1-1） =====================

    /**
     * TOTP 锁定判定：失败计数 ≥ 上限 且 处于冷却窗口内（最近失败时间 + totp-cooldown-seconds）。
     * 窗口过期后放行重试（重试失败继续累计，成功清零）。EQL 标量读（直查 DB 绕过一级缓存，
     * {@code DbMfaChallengeStore.readVerifiedAt} 先例——并发递增后判定读到新值）。
     */
    private boolean isTotpLocked(String userId) {
        int maxFails = CFG_AUTH_MFA_TOTP_VERIFY_MAX_FAILS.get();
        long cutoff = CoreMetrics.currentTimeMillis() - CFG_AUTH_MFA_TOTP_COOLDOWN_SECONDS.get() * 1000L;
        if (ormTemplate != null) {
            io.nop.core.lang.sql.SQL sel = io.nop.core.lang.sql.SQL.begin()
                    .name("mfaTotpLockedProbe")
                    .sql("select count(*) from NopAuthMfaSetting o "
                            + "where o.userId = ? and coalesce(o.totpFailCount, 0) >= ?"
                            + " and o.totpFailAt is not null and o.totpFailAt > ?",
                            userId, maxFails, new Timestamp(cutoff))
                    .end();
            return ormTemplate.findInt(sel, 0) > 0;
        }
        // 手工 wiring 退化路径：经 setting 实体值判定（直调路径无会话缓存竞争）
        NopAuthMfaSetting setting = daoFor(NopAuthMfaSetting.class).getEntityById(userId);
        if (setting == null || setting.getTotpFailAt() == null) {
            return false;
        }
        int count = setting.getTotpFailCount() == null ? 0 : setting.getTotpFailCount();
        return count >= maxFails && setting.getTotpFailAt().getTime() > cutoff;
    }

    /**
     * TOTP 验证失败递增计数（原子 SQL；{@code COALESCE} 兼容存量 NULL 行）。
     * pending 路径达上限同步作废 bindToken（后续 confirm 报 BIND_EXPIRED）。
     * <p>
     * <b>事务语义（防生产空壳）</b>：confirmMfa/unbindMfa 是 @BizMutation——外层事务在随后的
     * {@code ERR_AUTH_MFA_FAIL} 抛出时会回滚，计数若随波逐流将永不持久。故计数写必须在
     * {@code REQUIRES_NEW} 独立事务中先行落库（失败路径此前无同行写，无自锁风险）。
     * 手工 wiring（无 ormTemplate，直调无装饰器）退化为实体写。
     */
    private void incrTotpVerifyFail(NopAuthMfaSetting setting, boolean pendingPath) {
        int maxFails = CFG_AUTH_MFA_TOTP_VERIFY_MAX_FAILS.get();
        Timestamp now = new Timestamp(CoreMetrics.currentTimeMillis());
        if (ormTemplate != null && txn() != null) {
            txn().runInTransaction(null, io.nop.api.core.annotations.txn.TransactionPropagation.REQUIRES_NEW, txn -> {
                io.nop.core.lang.sql.SQL incr = io.nop.core.lang.sql.SQL.begin()
                        .name("mfaTotpIncrFail")
                        .sql("update NopAuthMfaSetting o "
                                + "set o.totpFailCount = coalesce(o.totpFailCount, 0) + 1, o.totpFailAt = ?"
                                + " where o.userId = ?", now, setting.getUserId())
                        .end();
                ormTemplate.executeUpdate(incr);
                Integer count = ormTemplate.findInt(io.nop.core.lang.sql.SQL.begin()
                        .name("mfaTotpFailCount")
                        .sql("select o.totpFailCount from NopAuthMfaSetting o where o.userId = ?",
                                setting.getUserId())
                        .end(), null);
                if (pendingPath && count != null && count >= maxFails) {
                    // pending 路径超限语义：作废 bindToken（条件写：仅非空时）
                    ormTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                            .name("mfaTotpInvalidateBindToken")
                            .sql("update NopAuthMfaSetting o set o.bindToken = null"
                                    + " where o.userId = ? and o.bindToken is not null", setting.getUserId())
                            .end());
                }
                return null;
            });
            return;
        }
        // 退化路径（手工 wiring 直调）：实体写（version 冲突语义下并发丢失更新表现为请求失败，
        // 不产生静默计数旁路）
        int count = (setting.getTotpFailCount() == null ? 0 : setting.getTotpFailCount()) + 1;
        setting.setTotpFailCount(count);
        setting.setTotpFailAt(now);
        if (pendingPath && count >= maxFails) {
            setting.setBindToken(null);
        }
        daoFor(NopAuthMfaSetting.class).updateEntityDirectly(setting);
    }

    /**
     * TOTP 验证成功清零计数（条件写：仅非零时触发；不触碰 VERSION——成功路径的 ORM 实体写
     * （lastVerifiedWindow 推进/status 变更）随后正常提交，互不干扰）。
     */
    private void resetTotpVerifyFail(NopAuthMfaSetting setting) {
        if (ormTemplate != null) {
            ormTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .name("mfaTotpResetFail")
                    .sql("update NopAuthMfaSetting o set o.totpFailCount = 0, o.totpFailAt = null"
                            + " where o.userId = ? and (o.totpFailCount is not null and o.totpFailCount <> 0"
                            + " or o.totpFailAt is not null)", setting.getUserId())
                    .end());
            return;
        }
        if ((setting.getTotpFailCount() != null && setting.getTotpFailCount() != 0)
                || setting.getTotpFailAt() != null) {
            setting.setTotpFailCount(0);
            setting.setTotpFailAt(null);
            daoFor(NopAuthMfaSetting.class).updateEntityDirectly(setting);
        }
    }

    /**
     * 重置恢复码：作废旧码 + 生成新码（codeHash = salt:hash，与 W5 verifyRecoveryCode 消费口径逐字兼容）。
     * 返回明文恢复码（一次性）。
     */
    private List<String> regenerateRecoveryCodes(String userId) {
        deleteRecoveryCodes(userId);
        IEntityDao<NopAuthMfaRecoveryCode> rcDao = daoFor(NopAuthMfaRecoveryCode.class);
        List<String> plainCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String plain = generateRecoveryCode();
            plainCodes.add(plain);
            String salt = passwordEncoder.generateSalt();
            String hash = passwordEncoder.encodePassword(salt, plain);
            NopAuthMfaRecoveryCode rc = rcDao.newEntity();
            rc.setUserId(userId);
            rc.setCodeHash(salt + ":" + hash);
            rc.setUsed((byte) 0);
            rcDao.saveEntity(rc);
        }
        return plainCodes;
    }

    private void deleteRecoveryCodes(String userId) {
        IEntityDao<NopAuthMfaRecoveryCode> rcDao = daoFor(NopAuthMfaRecoveryCode.class);
        NopAuthMfaRecoveryCode example = new NopAuthMfaRecoveryCode();
        example.setUserId(userId);
        List<NopAuthMfaRecoveryCode> old = rcDao.findAllByExample(example);
        for (NopAuthMfaRecoveryCode rc : old) {
            rcDao.deleteEntity(rc);
        }
    }

    /**
     * 因子作废（unbindMfa/resetUserMfa）时物理删除该用户全部 webauthn credential
     * （A2-audit D3-F1，P1 修复）。物理删除而非逻辑删除：实体 {@code useLogicalDelete=true}，
     * 软删行的 credentialId 仍占用全局唯一键（同钥匙复注册会撞 DB 约束抛未归一异常），
     * 且残留 enabled 行会在重绑 webauthn 后复活旧（被窃）硬件钥匙。
     * <p>
     * 用 {@code deleteByQuery}（bulk 物理 DELETE）而非逐行 {@code deleteEntityDirectly}：
     * unbind ceremony 的断言验证刚以条件 UPDATE 推进 signCount/version，会话内实体版本
     * 落后于 DB，逐行删除触发乐观锁冲突；bulk DELETE 按条件直接执行，无版本比对。
     */
    private void deleteWebauthnCredentials(String userId) {
        IEntityDao<NopAuthMfaCredential> dao = daoFor(NopAuthMfaCredential.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        dao.deleteByQuery(query);
    }

    /** 生成 10 位数字恢复码（设计 §3.4）。 */
    private static String generateRecoveryCode() {
        return toRecoveryCode(MathHelper.secureRandom().nextLong());
    }

    /**
     * 随机 long → 10 位数字恢复码。check2 P3 修复：{@code Math.abs(Long.MIN_VALUE)} 返回自身
     * （负数），旧实现该边界产生带 {@code -} 前缀的串（概率 2^-64，理论性边界）；
     * {@code & Long.MAX_VALUE} 位掩码消除符号位后取模，恒为非负。
     */
    static String toRecoveryCode(long randomValue) {
        long n = (randomValue & Long.MAX_VALUE) % 10_000_000_000L;
        return StringHelper.leftPad(String.valueOf(n), RECOVERY_CODE_DIGITS, '0');
    }

    private void sendSmsForBinding(String phone, String code) {
        if (smsSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "ISmsSender is not configured; sms MFA binding is disabled");
        }
        if (code == null) {
            // check2 P3 修复：smsCodeStore 未装配时不得以 params=[null] 发出无效短信
            // （对齐 email 侧 sendMfaEmailCode/sendEmailForBinding 判空 fail-closed）
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "SmsCodeStore is not configured; sms MFA binding code cannot be generated");
        }
        SmsMessage msg = new SmsMessage();
        msg.setMobile(phone);
        msg.setTemplateCode(CFG_AUTH_SMS_CODE_TEMPLATE_ID.get());
        msg.setParams(java.util.Collections.singletonList(code));
        smsSender.sendMessage(msg);
    }

    /** 手机号脱敏：仅显示后 4 位。 */
    private static String maskPhone(String phone) {
        if (StringHelper.isEmpty(phone) || phone.length() <= 4) {
            return phone;
        }
        return StringHelper.repeat("*", phone.length() - 4) + phone.substring(phone.length() - 4);
    }

    private String requireCurrentUserId(IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null || StringHelper.isEmpty(userContext.getUserId())) {
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);
        }
        return userContext.getUserId();
    }

    /**
     * 运行时 admin 角色校验（defense-in-depth，配合 {@code @Auth} 权限门禁，使"非 admin 被拒"可测试）。
     * 适用于破坏力高于常规 CRUD 的账号管理动作（resetUserMfa/resetUserPassword/enableUser/disableUser/
     * saveMfaPolicy 等）：仅授予操作权限不足以放行，还要求调用方具有 admin/nop-admin 角色。
     */
    private void requireAdmin(IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null) {
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);
        }
        Set<String> roles = userContext.getRoles();
        if (roles == null || (!roles.contains(NopAuthConstants.ROLE_ADMIN)
                && !roles.contains(NopAuthConstants.ROLE_NOP_ADMIN))) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_USER_ID, userContext.getUserId())
                    .param("msg", "only admin can perform this account management action");
        }
    }

    // ===================== 既有 action（密码/启停） =====================

    @BizQuery
    public NopAuthUser getUserByOpenId(@Name("openId") String openId, IServiceContext context) {
        NopAuthUser example = new NopAuthUser();
        example.setOpenId(openId);
        NopAuthUser user = dao().findFirstByExample(example);
        checkDataAuth("get", user, context);
        return user;
    }

    @BizAction
    @Override
    protected void defaultPrepareSave(@Name("entityData") EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);

        NopAuthUser user = entityData.getEntity();
        passwordPolicy.checkAllowedPassword(user.getUserName(), user.getPassword());

        String salt = passwordEncoder.generateSalt();
        String password = passwordEncoder.encodePassword(salt, user.getPassword());
        user.setSalt(salt);
        user.setPassword(password);

        user.setUserId(userIdGenerator.generateUserId(user));
        user.setOpenId(userIdGenerator.generateUserOpenId(user));

        if (StringHelper.isEmpty(user.getTenantId())) {
            String tenantId = ContextProvider.currentTenantId();
            if (StringHelper.isEmpty(tenantId))
                tenantId = DaoConstants.DEFAULT_TENANT_ID;
            user.setTenantId(tenantId);
        }

        user.setStatus(NopAuthConstants.USER_STATUS_ACTIVE);
        user.setDelFlag(DaoConstants.NO_VALUE);

        // A2-followup-1 W12 路由项 3：创建面联系方式拦截（新增实体上 phone/email 已置值即脏）。
        // 非 admin 调用方（含本人）→ 显式拒绝；admin → 放行 + 联系方式变更审计；无登录态的
        // 内部调用（批量导入/autotest 数据准备先例，W11 归属两层防御同款裁定）→ 放行。
        guardContactChange(user, context, true);
    }

    /**
     * A2-followup-1 W12 路由项 3：update preparer 路径的联系方式拦截钩子。ORM 实体 copier 已
     * 把提交字段拷贝到实体（脏属性可判），prepareUpdate 时机可精确区分"本次提交改了
     * phone/email"与"仅改其他字段"。非联系字段的通用修改（如 nickname）不受影响。
     */
    @BizAction
    @Override
    protected void defaultPrepareUpdate(@Name("entityData") EntityData<NopAuthUser> entityData, IServiceContext context) {
        super.defaultPrepareUpdate(entityData, context);
        // 修改面联系方式拦截（含本人行修改——受限会话 enrollment attack 链闭合：
        // 改 phone 后 bindSms 的组合路径断链，拦截点在 bindMfa 之前）
        guardContactChange(entityData.getEntity(), context, false);
    }

    /**
     * 联系方式（phone/email）经通用 CRUD 的写拦截（W12 路由项 3，A2 裁定路由项 3 落地）：
     * <ul>
     *   <li>非 admin 登录调用方（含修改本人行）→ {@code ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED}
     *       （英文文案指引"联系方式变更需管理员或专用流程"）。</li>
     *   <li>admin 调用方 → 放行 + {@code user:contact-changed} 审计事件（改人留痕，含
     *       target userId/变更字段清单/创建或修改形态；不落明文新旧值——审计面最小化）。</li>
     *   <li>无登录态内部调用 → 放行（W11 凭证归属两层防御先例：无登录态内部调用按一期
     *       行为放行；本类无内部 save/update 调用方，实际命中面为批量导入/autotest）。</li>
     * </ul>
     * 与 D1-7 正交：D1-7 是读面结构性排除（MfaSetting.phone 出参不可见），本拦截是写面
     * （NopAuthUser.phone/email 写路径分级）。
     */
    private void guardContactChange(NopAuthUser user, IServiceContext context, boolean create) {
        if (user == null) {
            return;
        }
        boolean phoneChanged = user.orm_propDirtyByName("phone");
        boolean emailChanged = user.orm_propDirtyByName("email");
        if (!phoneChanged && !emailChanged) {
            return; // 联系字段不涉及：通用修改（nickname 类）行为不变
        }
        IUserContext uc = context == null ? null : context.getUserContext();
        if (uc == null || StringHelper.isEmpty(uc.getUserId())) {
            return; // 无登录态内部调用：放行（W11 先例）
        }
        Set<String> roles = uc.getRoles();
        boolean admin = roles != null && (roles.contains(NopAuthConstants.ROLE_ADMIN)
                || roles.contains(NopAuthConstants.ROLE_NOP_ADMIN));
        if (!admin) {
            throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED)
                    .param(ARG_USER_ID, user.getUserId())
                    .param("msg", "contact info (phone/email) can only be changed by an administrator "
                            + "or through a dedicated flow");
        }
        auditContactChange(uc, user.getUserId(), phoneChanged, emailChanged, create);
    }

    /** admin 联系方式变更审计事件（改人留痕），落 NopAuthOpLog；不记录明文新旧值。 */
    private void auditContactChange(IUserContext operator, String targetUserId, boolean phoneChanged,
                                    boolean emailChanged, boolean create) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation(create ? "NopAuthUser__save" : "NopAuthUser__update");
        audit.setDescription("user:contact-changed");
        audit.setResultStatus(200);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(operator.getUserId());
        audit.setUserName(StringHelper.isEmpty(operator.getUserName()) ? operator.getUserId() : operator.getUserName());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "contact-changed");
        data.put("targetUserId", targetUserId);
        java.util.List<String> fields = new ArrayList<>(2);
        if (phoneChanged)
            fields.add("phone");
        if (emailChanged)
            fields.add("email");
        data.put("fields", fields);
        data.put("mode", create ? "create" : "update");
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    @Description("@i18n:common.resetUserPassword")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    @MfaRequired
    public void resetUserPassword(@Name("userId") String userId,
                                  @Name("password") String password,
                                  IServiceContext context) {
        // 运行时admin校验（defense-in-depth）：操作权限下放给非admin角色时，防止其重置
        // 同租户任意用户（含admin）密码造成账号接管；对照 resetUserMfa 先例
        requireAdmin(context);
        NopAuthUser user = this.get(userId, false, context);
        passwordPolicy.checkAllowedPassword(user.getUserName(), password);

        String salt = passwordEncoder.generateSalt();
        password = passwordEncoder.encodePassword(salt, password);
        user.setSalt(salt);
        user.setPassword(password);

        // 密码被管理员重置：吊销目标用户全部既有会话（旧token依赖session存续，随之失效）
        revokeUserSessions(user, null);
    }

    @Description("修改自己的密码")
    @BizMutation
    @BizAudit
    @MfaRequired
    public void changeSelfPassword(@Name("oldPassword") String oldPassword,
                                   @Name("newPassword") String newPassword, IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);

        passwordPolicy.checkAllowedPassword(userContext.getUserName(), newPassword);

        NopAuthUser user = dao().requireEntityById(userContext.getUserId());
        if (!passwordEncoder.passwordMatches(user.getSalt(), oldPassword, user.getPassword())) {
            throw new NopException(ERR_AUTH_OLD_PASSWORD_NOT_MATCH);
        }

        String salt = passwordEncoder.generateSalt();
        String password = passwordEncoder.encodePassword(salt, newPassword);
        user.setSalt(salt);
        user.setPassword(password);

        // 凭证疑泄露后改密应立即踢出其他设备已持有的token：吊销除当前会话外的全部会话
        revokeUserSessions(user, userContext.getSessionId());
    }

    @Description("@i18n:common.enableUser")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void enableUser(@Name("userId") String userId,
                           IServiceContext context) {
        requireAdmin(context);
        NopAuthUser user = this.get(userId, false, context);
        if (user.getStatus() != AuthApiConstants.USER_STATUS_ACTIVE) {
            user.setStatus(AuthApiConstants.USER_STATUS_ACTIVE);
        }
    }

    @Description("@i18n:common.disableUser")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void disableUser(@Name("userId") String userId,
                            IServiceContext context) {
        requireAdmin(context);
        NopAuthUser user = this.get(userId, false, context);
        if (user.getStatus() == AuthApiConstants.USER_STATUS_ACTIVE) {
            user.setStatus(AuthApiConstants.USER_STATUS_DISABLED);
        }
    }

    /**
     * 密码变更/重置后吊销目标用户会话（复用ILoginService的session+缓存上下文失效链路，
     * 携带onLogout钩子通知）。exceptSessionId非空时保留该会话（本人改密保留当前会话）。
     */
    private void revokeUserSessions(NopAuthUser user, String exceptSessionId) {
        if (loginService == null)
            return;
        FutureHelper.syncGet(loginService.revokeUserSessionsAsync(user.getUserName(), exceptSessionId));
    }
}
