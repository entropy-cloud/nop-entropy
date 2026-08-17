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
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.mfa.MfaRequired;
import io.nop.auth.biz.INopAuthUserBiz;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.password.IPasswordPolicy;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.generator.IUserIdGenerator;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.biz.dto.MfaStatusResult;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_OLD_PASSWORD_NOT_MATCH;
import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_BIND_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_ISSUER;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_TEMPLATE_ID;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_DISABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_PENDING;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_TOTP;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_PROOF;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ARG_USER_ID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_ALREADY_ENABLED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_FAIL;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED;

@BizModel("NopAuthUser")
@Locale("zh-CN")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> implements INopAuthUserBiz {

    /** 恢复码数量（设计 §3.4：10 个一次性）。 */
    private static final int RECOVERY_CODE_COUNT = 10;

    /** 恢复码位数（设计 §3.4：10 位数字）。 */
    private static final int RECOVERY_CODE_DIGITS = 10;

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
     * 角色级 MFA 策略评估器（W13-impl，设计 §4.3）：confirmMfa 策略校验（防因子降级）。
     * 可选注入：未装配 = 无策略 = 不校验（一期行为）。
     */
    @Inject
    @Nullable
    protected io.nop.auth.service.mfa.RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;

    /** 审计服务（W13：登记通道 proof 发码事件）。{@code @BizAudit} 为装饰性注解（W12 裁定）。 */
    @Inject
    @Nullable
    protected io.nop.api.core.audit.IAuditService auditService;

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
                                  IServiceContext context) {
        String userId = requireCurrentUserId(context);
        if (!MFA_TYPE_TOTP.equals(mfaType) && !MFA_TYPE_SMS.equals(mfaType)) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST).param(ARG_MFA_TYPE, mfaType)
                    .param("msg", "unsupported mfaType: " + mfaType + " (only totp/sms supported)");
        }

        // W13-impl：受限会话内 bindMfa 前置登记通道验证（防 enrollment attack，设计 §4.3）。
        // 正常会话 bindMfa 不受影响（零改动）
        IUserContext uc = context.getUserContext();
        if (uc != null && uc.isMfaRestricted()) {
            requireChannelProof(userId, proofToken);
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
        return bindSms(settingDao, setting, user, bindToken);
    }

    /**
     * 兼容重载（W13 前调用点）：无 proof 参数 = 正常会话路径（受限会话内将触发 proof 发码
     * 引导）。无 @BizMutation 注解——GraphQL 面仅暴露三参版本。
     */
    public MfaBindResult bindMfa(String mfaType, IServiceContext context) {
        return bindMfa(mfaType, null, context);
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

        // 校验第二因子（用 pending 记录的 secret；W12-impl 收敛至 MfaFactorVerifier——
        // TOTP 成功即内聚推进 lastVerifiedWindow/lastVerifiedAt，此处不再重复验证窗口）
        boolean ok = mfaFactorVerifier.verify(setting, setting.getMfaType(), code);
        if (!ok) {
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId);
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

        // 生成恢复码（作废旧码）
        return regenerateRecoveryCodes(userId);
    }

    /**
     * 解绑 MFA（设计 §3.6）。需验证当前第二因子通过才可解绑；成功 → status=disabled + 删除全部恢复码。
     * <p>
     * 敏感操作（W12-impl 首批标注：修改认证因子类）。
     */
    @Description("解绑MFA")
    @BizMutation
    @BizAudit
    @MfaRequired
    public void unbindMfa(@Name("code") String code, IServiceContext context) {
        String userId = requireCurrentUserId(context);
        IEntityDao<NopAuthMfaSetting> settingDao = daoFor(NopAuthMfaSetting.class);
        NopAuthMfaSetting setting = settingDao.getEntityById(userId);

        // 未 enabled → NOT_ENABLED（无静默跳过）
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())) {
            throw new NopException(ERR_AUTH_MFA_NOT_ENABLED).param(ARG_USER_ID, userId);
        }

        // 验证当前因子（totp/sms；W12-impl 收敛至 MfaFactorVerifier）
        boolean ok = mfaFactorVerifier.verify(setting, setting.getMfaType(), code);
        if (!ok) {
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_USER_ID, userId);
        }

        setting.setStatus(MFA_STATUS_DISABLED);
        setting.setBindToken(null);
        deleteRecoveryCodes(userId);
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
    }

    // ===================== MFA 内部辅助 =====================

    // 绑定/解绑的因子校验已收敛至 MfaFactorVerifier（W12-impl；原 verifyFactorForBind
    // 的 totp/sms 分支语义逐条迁入组件：totp 解密校验+窗口推进 / sms 原子消费+EXPIRED 抛错 /
    // 未知 mfaType 返回 false fail-closed）。

    /** 登记通道 proof 发码限流追踪：phone → [lastSendMs, dailyCount, dailyDate]（对齐 LoginServiceImpl sms 限流模式）。 */
    private final Map<String, long[]> proofRateTracker = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 受限会话内 bindMfa 的登记通道 proof 门槛（设计 §4.3 防 enrollment attack）：
     * <ol>
     *   <li>有效票核验：scene=channel-proof + 已验证（peek 不变式：verifiedAt 非空 ⇒ 票在
     *       op-ticket 窗口内）+ userId 绑定 + 原子消费（一次性）——通过即返回。</li>
     *   <li>无有效票：服务端解析登记通道（W13 仅 phone，不接受客户端指定；为空抛
     *       {@code NO_RECOVERY_CHANNEL}）→ 复用 sms-code 限流模式（60s 间隔/日上限——防受限
     *       会话内 proof 码轰炸受害者登记手机）→ {@code SmsCodeStore.send(proof:{userId})}
     *       发码 → 抛 {@code CHANNEL_PROOF_REQUIRED}（脱敏提示）。</li>
     * </ol>
     */
    private void requireChannelProof(String userId, String proofToken) {
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

        // 2. 通道解析（服务端；W13 仅 phone）
        NopAuthUser user = daoFor(NopAuthUser.class).getEntityById(userId);
        String phone = user == null ? null : user.getPhone();
        if (StringHelper.isEmpty(phone)) {
            throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_NO_RECOVERY_CHANNEL)
                    .param(ARG_USER_ID, userId);
        }

        // 3. 限流（60s 间隔 + 日上限；sendMfaCode 调用点限流先例）
        checkProofRateLimit(phone);

        // 4. 发码（key=proof:{userId}，通道隔离）→ 脱敏提示抛错（客户端持码调 verifyChannelProof）
        if (smsCodeStore == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "SmsCodeStore is not configured; channel proof is unavailable");
        }
        String code = smsCodeStore.send(SMS_KEY_PROOF + userId);
        sendSmsForBinding(phone, code);
        auditChannelProofSent(userId, user.getUserName(), phone);
        throw new NopException(io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED)
                .param(io.nop.auth.service.NopAuthErrors.ARG_CHANNEL, maskPhone(phone));
    }

    /** proof 发码限流：同手机号 send-interval-seconds 间隔 + 每日 daily-limit 上限（复用 sms-code 配置）。 */
    private void checkProofRateLimit(String phone) {
        long now = CoreMetrics.currentTimeMillis();
        long today = io.nop.api.core.time.CoreMetrics.today().toEpochDay();
        int interval = io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS.get();
        int dailyLimit = io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_DAILY_LIMIT.get();
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

    /**
     * 登记通道 proof 发码审计事件（防滥用审计），落 NopAuthOpLog（phone 脱敏）。
     * userName 必填（NopAuthOpLog 非空列——缺失会使批处理整批回滚，W13 E2E 钉定）。
     */
    private void auditChannelProofSent(String userId, String userName, String phone) {
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
        data.put("phone", maskPhone(phone));
        audit.setRequestData(io.nop.core.lang.json.JsonTool.stringify(data));
        auditService.saveAudit(audit);
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

    /** 生成 10 位数字恢复码（设计 §3.4）。 */
    private static String generateRecoveryCode() {
        long n = Math.abs(MathHelper.secureRandom().nextLong()) % 10_000_000_000L;
        return StringHelper.leftPad(String.valueOf(n), RECOVERY_CODE_DIGITS, '0');
    }

    private void sendSmsForBinding(String phone, String code) {
        if (smsSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "ISmsSender is not configured; sms MFA binding is disabled");
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
                    .param("msg", "only admin can reset user MFA");
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
    }

    @Description("@i18n:common.resetUserPassword")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    @MfaRequired
    public void resetUserPassword(@Name("userId") String userId,
                                  @Name("password") String password,
                                  IServiceContext context) {
        NopAuthUser user = this.get(userId, false, context);
        passwordPolicy.checkAllowedPassword(user.getUserName(), password);

        String salt = passwordEncoder.generateSalt();
        password = passwordEncoder.encodePassword(salt, password);
        user.setSalt(salt);
        user.setPassword(password);
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
    }

    @Description("@i18n:common.enableUser")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    public void enableUser(@Name("userId") String userId,
                           IServiceContext context) {
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
        NopAuthUser user = this.get(userId, false, context);
        if (user.getStatus() == AuthApiConstants.USER_STATUS_ACTIVE) {
            user.setStatus(AuthApiConstants.USER_STATUS_DISABLED);
        }
    }
}
