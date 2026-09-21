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
import io.nop.auth.service.ratelimit.ISendCodeRateLimiter;
import io.nop.auth.service.ratelimit.LocalSendCodeRateLimiter;
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


import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_OLD_PASSWORD_NOT_MATCH;
import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_ENABLED;
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
     * 发码限流组件（plan 2274 Phase 1，design §3.1）：bindSms/bindEmail/channel-proof 共用
     * （scope=bind）。可选注入 + 缺省 Local 实例（ormTemplate 先例）；缺省为按实例独立
     * （等价原 per-instance 限流 Map 拓扑，与 LoginServiceImpl 的 login scope 隔离）。
     */
    @Inject
    @Nullable
    protected ISendCodeRateLimiter sendCodeRateLimiter;

    private ISendCodeRateLimiter defaultRateLimiter;

    protected ISendCodeRateLimiter rateLimiter() {
        if (sendCodeRateLimiter != null)
            return sendCodeRateLimiter;
        if (defaultRateLimiter == null)
            defaultRateLimiter = new LocalSendCodeRateLimiter();
        return defaultRateLimiter;
    }

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

    // ===================== MFA 用户自助管理薄入口（plan 2274 Phase 4，design §3.4） =====================
    // GraphQL/RPC 面签名与注解不变（API 兼容裁定）；实现全部迁至 mfa/UserMfaSelfService。

    private io.nop.auth.service.mfa.UserMfaSelfService selfService;

    /** MFA 自助服务组件：以宿主 wired 实例构造（手工 wiring 路径行为不变）。 */
    protected io.nop.auth.service.mfa.UserMfaSelfService selfService() {
        if (selfService == null) {
            selfService = new io.nop.auth.service.mfa.UserMfaSelfService(totpAuthenticator, smsCodeStore,
                    emailCodeStore, smsSender, emailSender, mfaFactorVerifier, mfaChallengeStore,
                    webAuthnAuthenticator, trustedDeviceManager, auditService, ormTemplate,
                    roleMfaPolicyEvaluator, passwordEncoder, daoProvider(), rateLimiter(), txn());
        }
        return selfService;
    }

    @Description("发起MFA绑定")
    @BizMutation
    @BizAudit(logRequestFields = "mfaType")
    public MfaBindResult bindMfa(@Name("mfaType") String mfaType,
                                  @io.nop.api.core.annotations.core.Optional @Name("proof") String proofToken,
                                  @io.nop.api.core.annotations.core.Optional @Name("channel") String channel,
                                  IServiceContext context) {
        return selfService().bindMfa(mfaType, proofToken, channel, context);
    }

    /** 兼容重载（W13/W14 调用点）。无 @BizMutation 注解——GraphQL 面仅暴露四参版本。 */
    public MfaBindResult bindMfa(String mfaType, String proofToken, IServiceContext context) {
        return bindMfa(mfaType, proofToken, null, context);
    }

    /** 兼容重载（一期调用点）。无注解——GraphQL 面仅暴露四参版本。 */
    public MfaBindResult bindMfa(String mfaType, IServiceContext context) {
        return selfService().bindMfa(mfaType, context);
    }

    @Description("确认WebAuthn注册")
    @BizMutation
    @BizAudit(logRequestFields = "challengeToken")
    public List<String> confirmWebauthnRegistration(@Name("challengeToken") String challengeToken,
                                                    @Name("attestation") WebAuthnAttestation attestation,
                                                    IServiceContext context) {
        return selfService().confirmWebauthnRegistration(challengeToken, attestation, context);
    }

    @Description("发起WebAuthn解绑验证")
    @BizMutation
    @BizAudit
    public MfaWebauthnBeginResult webauthnBeginVerify(IServiceContext context) {
        return selfService().webauthnBeginVerify(context);
    }

    @Description("确认MFA绑定")
    @BizMutation
    @BizAudit(logRequestFields = "mfaType")
    public List<String> confirmMfa(@Name("bindToken") String bindToken, @Name("code") String code,
                                   IServiceContext context) {
        return selfService().confirmMfa(bindToken, code, context);
    }

    @Description("解绑MFA")
    @BizMutation
    @BizAudit
    @MfaRequired
    public void unbindMfa(@Name("code") String code,
                          @Optional @Name("challengeToken") String challengeToken,
                          @Optional @Name("assertion") WebAuthnAssertion assertion,
                          IServiceContext context) {
        selfService().unbindMfa(code, challengeToken, assertion, context);
    }

    /** 兼容重载（W14 前调用点）。无注解——GraphQL 面仅暴露四参版本。 */
    public void unbindMfa(String code, IServiceContext context) {
        selfService().unbindMfa(code, context);
    }

    @Description("重置MFA恢复码")
    @BizMutation
    @BizAudit
    @MfaRequired
    public List<String> generateRecoveryCodes(IServiceContext context) {
        return selfService().generateRecoveryCodes(context);
    }

    @Description("查询MFA状态")
    @BizQuery
    public MfaStatusResult getMfaStatus(IServiceContext context) {
        return selfService().getMfaStatus(context);
    }

    @Description("查询WebAuthn凭证列表")
    @BizQuery
    public List<NopAuthMfaCredentialOutputBean> listWebauthnCredentials(IServiceContext context) {
        return selfService().listWebauthnCredentials(context);
    }

    @Description("移除WebAuthn凭证")
    @BizMutation
    @BizAudit(logRequestFields = "sid")
    @MfaRequired
    public void removeWebauthnCredential(@Name("sid") String sid, IServiceContext context) {
        selfService().removeWebauthnCredential(sid, context);
    }

    @Description("重命名WebAuthn凭证")
    @BizMutation
    @BizAudit(logRequestFields = "sid")
    public void renameWebauthnCredential(@Name("sid") String sid, @Name("name") String name,
                                         IServiceContext context) {
        selfService().renameWebauthnCredential(sid, name, context);
    }

    @Description("发起追加WebAuthn钥匙")
    @BizMutation
    @BizAudit
    public MfaWebauthnAddKeyBeginResult webauthnBeginAddKey(IServiceContext context) {
        return selfService().webauthnBeginAddKey(context);
    }

    @Description("确认追加WebAuthn钥匙")
    @BizMutation
    @BizAudit(logRequestFields = "addChallengeToken")
    @MfaRequired
    public void confirmWebauthnAddKey(@Name("addChallengeToken") String addChallengeToken,
                                      @Name("attestation") WebAuthnAttestation attestation,
                                      @Name("verifyChallengeToken") String verifyChallengeToken,
                                      @Name("assertion") WebAuthnAssertion assertion,
                                      IServiceContext context) {
        selfService().confirmWebauthnAddKey(addChallengeToken, attestation, verifyChallengeToken, assertion, context);
    }

    @Description("查询可信设备列表")
    @BizQuery
    public List<TrustedDeviceInfo> listTrustedDevices(IServiceContext context) {
        return selfService().listTrustedDevices(context);
    }

    @Description("移除可信设备")
    @BizMutation
    @BizAudit(logRequestFields = "sid")
    public void removeTrustedDevice(@Name("sid") String sid, IServiceContext context) {
        selfService().removeTrustedDevice(sid, context);
    }

    @Description("管理员重置用户MFA")
    @BizMutation
    @BizAudit(logRequestFields = "userId")
    @MfaRequired
    public void resetUserMfa(@Name("userId") String userId, IServiceContext context) {
        selfService().resetUserMfa(userId, context);
    }

    private String requireCurrentUserId(IServiceContext context) {
        return io.nop.auth.service.mfa.MfaAccessGuard.requireCurrentUserId(context);
    }

    private void requireAdmin(IServiceContext context) {
        io.nop.auth.service.mfa.MfaAccessGuard.requireAdmin(context);
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
