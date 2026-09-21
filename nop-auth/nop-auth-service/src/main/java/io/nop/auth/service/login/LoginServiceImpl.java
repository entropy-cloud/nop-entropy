/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.login;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.api.messages.RoleInfo;
import io.nop.auth.core.login.AbstractLoginService;
import io.nop.auth.core.login.AbstractUserContextCache;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.ILoginAttemptStore;
import io.nop.auth.core.login.ISessionBootstrap;
import io.nop.auth.core.login.LoginAttemptStores;
import io.nop.auth.core.login.SessionInfo;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.core.verifycode.IVerifyCodeGenerator;
import io.nop.auth.core.verifycode.VerifyCode;
import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthTenant;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.mfa.LoginMfaFlow;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaCodeSender;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.MfaRequests;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.auth.service.mfa.RoleMfaPolicy;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
import io.nop.auth.service.ratelimit.ISendCodeRateLimiter;
import io.nop.auth.service.ratelimit.LocalSendCodeRateLimiter;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.api.AuthApiConstants.LOGIN_TYPE_EMAIL_PASSWORD;
import static io.nop.auth.api.AuthApiConstants.LOGIN_TYPE_PHONE_PASSWORD;
import static io.nop.auth.api.AuthApiConstants.LOGIN_TYPE_PHONE_SMS;
import static io.nop.auth.api.AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD;
import static io.nop.auth.api.AuthApiErrors.ARG_LOGIN_TYPE;
import static io.nop.auth.api.AuthApiErrors.ARG_PRINCIPAL_ID;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_ACCESS_TOKEN_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_ALLOW_CREATE_DEFAULT_USER;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MAX_LOGIN_FAIL_COUNT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_WINDOW_SKEW;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_REFRESH_TOKEN_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_ALLOW_REGISTER;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_TEMPLATE_ID;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_VERIFY_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConstants.EMAIL_KEY_MFA;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_DISABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_EMAIL;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_TOTP;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_WEBAUTHN;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_LOGIN;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_VERIFY_CODE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_LOGIN_CHECK_FAIL;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_LOGIN_WITH_UNKNOWN_USER;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CODE_UNSUPPORTED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_FAIL;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_REQUIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_INVALID;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_USER_NOT_ALLOW_LOGIN;
import static io.nop.commons.util.StringHelper.isYes;
import static io.nop.dao.DaoConfigs.CFG_ORM_ENABLE_TENANT_BY_DEFAULT;

public class LoginServiceImpl extends AbstractLoginService implements ISessionBootstrap {
    static final Logger LOG = LoggerFactory.getLogger(LoginServiceImpl.class);

    @Inject
    protected IPasswordEncoder passwordEncoder;

    @Inject
    protected IDaoProvider daoProvider;

    /**
     * ORM 模板（A2-followup-1 D3-F2，plan 2257 平移自 jdbcTemplate）：恢复码 used 条件写
     * （原子 EQL UPDATE + affected-row，{@code DbMfaChallengeStore.markVerified} 同型）。
     * 手工 wiring 测试可缺省——缺省时退化为实体写（直调路径无并发竞争，语义等价）。
     */
    @Inject
    @Nullable
    protected IOrmTemplate ormTemplate;

    @Inject
    protected IAuditService auditService;

    @Inject
    @Nullable
    protected IVerifyCodeGenerator verifyCodeGenerator;

    /**
     * MFA challenge store（设计 §3.3）。仅在 {@code nop.auth.mfa.enabled=true} 时被访问；
     * 由 {@code MfaStoreProvider} 在 beans 中装配（默认 Local）。
     */
    @Inject
    @Nullable
    protected MfaChallengeStore mfaChallengeStore;

    /**
     * 短信验证码 store（设计 §3.3）。loginType=5 第一因子校验 + MFA 第二因子校验共用，
     * key 隔离（{@code login:phone} / {@code mfa:userId}）。
     */
    @Inject
    @Nullable
    protected SmsCodeStore smsCodeStore;

    /**
     * TOTP 验证器（W4）。由 beans 装配（auth-service.beans.xml）。
     * 因子校验逻辑已收敛至 {@link MfaFactorVerifier}（W12-impl）。
     */
    @Inject
    @Nullable
    protected TOTPAuthenticator totpAuthenticator;

    /**
     * 共享因子校验组件（W12-impl，设计 §3.1 结论 5）：登录级/绑定级/操作级三处因子
     * 校验收敛；TOTP 窗口统一推进内聚于组件（调用方不可选）。
     */
    @Inject
    @Nullable
    protected MfaFactorVerifier mfaFactorVerifier;

    /**
     * 角色级 MFA 策略评估器（W13-impl，设计 §4.3）：{@code checkMfaRequired} 第三态
     * （受限决策）的评估输入。可选注入：未装配（如测试手工 wiring）= 无策略 = 一期行为。
     */
    @Inject
    @Nullable
    protected RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;

    /**
     * 可信设备共享组件（W15-impl，设计 §六）：checkMfaRequired 豁免判定 + mfaVerify
     * rememberDevice 登记共用。可选注入：未装配 = 豁免/登记结构性跳过（一期行为）。
     */
    @Inject
    @Nullable
    protected MfaTrustedDeviceManager trustedDeviceManager;

    /**
     * 短信发送器（nop-integration-api）。sendSmsCode/sendMfaCode 调用。
     * 可选注入：无短信通道的部署（如测试环境）可缺省，sendSmsCode 时 fail-closed。
     */
    @Inject
    @Nullable
    protected ISmsSender smsSender;

    /**
     * 邮件验证码 store（W15-impl，设计 §5.3.3）：sendMfaCode email 分支发码
     * （key={@code mfa-email:{userId}}）。与 smsCodeStore 平行装配。
     */
    @Inject
    @Nullable
    protected EmailCodeStore emailCodeStore;

    /**
     * 邮件发送器（nop-integration-api，W15-impl 复用既有实现零变更）。可选注入：
     * 未装配时 email 因子发码 fail-closed（对齐 sms {@code smsSender == null} 行为）。
     */
    @Inject
    @Nullable
    protected IEmailSender emailSender;

    /**
     * 发码限流组件（plan 2274 Phase 1，design §3.1）：sendSmsCode/sendMfaCode(sms/email)
     * 共用（scope=login）。可选注入 + 缺省 Local 实例（手工 wiring 测试不经容器也有正确行为，
     * ormTemplate 先例）；缺省为按实例独立（等价原 per-instance 限流 Map 拓扑）。
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

    private Set<String> allowedLoginMethods;

    /**
     * 登录失败计数 store（plan 2274 Phase 2，design §3.2）：原子递增内聚到实现
     * （原 loginFailCountLock 的职责迁移，check2 P1 语义保持）。可选注入 + 缺省解析自
     * wired 的 {@code AbstractUserContextCache}（同一实例，手工 wiring 写读一致性），
     * 最后回落 JVM 共享缺省单例。
     */
    @Inject
    @Nullable
    protected ILoginAttemptStore loginAttemptStore;

    protected ILoginAttemptStore attemptStore() {
        if (loginAttemptStore != null)
            return loginAttemptStore;
        if (userContextCache instanceof AbstractUserContextCache)
            return ((AbstractUserContextCache) userContextCache).attemptStore();
        return LoginAttemptStores.sharedLocalDefault();
    }

    @Inject
    protected IAuthTokenProvider authTokenProvider;

    private boolean returnDeptName;
    // 是否允许同一用户名同时存在多个登录会话（不可动态修改），默认 false
    private boolean allowMultipleSessionsSameUser;

    public boolean isReturnDeptName() {
        return returnDeptName;
    }

    @InjectValue("@cfg:nop.login.return-dept-name|false")
    public void setReturnDeptName(boolean returnDeptName) {
        this.returnDeptName = returnDeptName;
    }

    @InjectValue("@cfg:nop.auth.login.allow-multiple-sessions-same-user|false")
    public void setAllowMultipleSessionsSameUser(boolean allowMultipleSessionsSameUser) {
        this.allowMultipleSessionsSameUser = allowMultipleSessionsSameUser;
    }

    public Set<String> getAllowedLoginMethods() {
        return allowedLoginMethods;
    }

    public void setAllowedLoginMethods(Set<String> allowedLoginMethods) {
        this.allowedLoginMethods = allowedLoginMethods;
    }

    public void lazyInit() {
        addDefaultUser();
    }

    void addDefaultUser() {
        if (CFG_AUTH_ALLOW_CREATE_DEFAULT_USER.get()) {
            ContextProvider.runWithTenant("0", ()->{
                IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
                // 用户表为空，插入缺省用户
                if (dao.isEmpty()) {
                    NopAuthUser user = dao.newEntity();
                    user.setUserName("nop");
                    String salt = passwordEncoder.generateSalt();
                    user.setPassword(passwordEncoder.encodePassword(salt, "123"));
                    user.setSalt(salt);
                    user.setOpenId("0");
                    user.setNickName("Nopper");
                    user.setStatus(AuthApiConstants.USER_STATUS_ACTIVE);
                    user.setGender(AuthApiConstants.USER_GENDER_DEFAULT);
                    user.setUserType(AuthApiConstants.USER_TYPE_DEFAULT);
                    user.setDelFlag(DaoConstants.NO_VALUE);
                    user.setCreatedBy("sys");
                    user.setUpdatedBy("sys");
                    user.setUserId(StringHelper.generateUUID());
                    user.setTenantId("0");
                    dao.saveEntity(user);
                }

                if (CFG_ORM_ENABLE_TENANT_BY_DEFAULT.get()) {
                    IEntityDao<NopAuthTenant> tenantDao = daoProvider.daoFor(NopAuthTenant.class);
                    if (tenantDao.isEmpty()) {
                        NopAuthTenant tenant = tenantDao.newEntity();
                        tenant.setTenantId("0");
                        tenant.setName("DefaultTenant");
                        tenant.setStatus(1);
                        tenantDao.saveEntity(tenant);
                    }
                }
                return null;
            });

        }
    }

    @Override
    public CompletionStage<IUserContext> loginAsync(LoginRequest request, Map<String, Object> headers) {
        long beginTime = CoreMetrics.currentTimeMillis();

        ErrorCode errorCode = null;
        NopAuthUser user = null;
        int failCount = 0;
        // 短信验证码类失败不进用户锁账号计数（设计 §3.3），单独标记
        boolean smsCodeFail = false;

        if (CFG_AUTH_VERIFY_CODE_ENABLED.get() && !checkVerifyCode(request)) {
            errorCode = ERR_AUTH_INVALID_VERIFY_CODE;
        } else if (!isValid(request)) {
            errorCode = ERR_AUTH_INVALID_LOGIN_REQUEST;
        } else {
            user = getAuthUser(request);
            if (user != null) {
                failCount = userContextCache.getLoginFailCountForUser(user.getUserName());

                int maxFailCount = CFG_AUTH_MAX_LOGIN_FAIL_COUNT.get();
                // 锁号判定与凭证校验解耦：maxFailCount<=0 仅表示关闭锁号（跳过 failCount 判定），
                // isAllowLogin/SMS 验证码/密码比对必须无条件执行，不能随锁号一起被跳过
                if (maxFailCount > 0 && failCount >= maxFailCount) {
                    errorCode = ERR_AUTH_LOGIN_CHECK_FAIL_TOO_MANY_TIMES;
                } else if (!isAllowLogin(user)) {
                    errorCode = ERR_AUTH_USER_NOT_ALLOW_LOGIN;
                } else if (request.getLoginType() == LOGIN_TYPE_PHONE_SMS) {
                    // 短信验证码登录：第一因子为一次性验证码校验（SmsCodeStore 内部失败计数，作废即防爆破），
                    // 失败不进 setLoginFailCountForUser（避免与用户锁账号语义混淆，设计 §3.3）
                    CodeVerifyResult r = smsCodeStore == null ? CodeVerifyResult.EXPIRED
                            : smsCodeStore.verify(SMS_KEY_LOGIN + request.getPrincipalId(), request.getPrincipalSecret());
                    if (r == CodeVerifyResult.EXPIRED) {
                        errorCode = ERR_AUTH_SMS_CODE_EXPIRED;
                        smsCodeFail = true;
                    } else if (r == CodeVerifyResult.MISMATCH) {
                        errorCode = ERR_AUTH_SMS_CODE_INVALID;
                        smsCodeFail = true;
                    }
                } else if (needCheckPassword(request) && !passwordMatches(user, request)) {
                    errorCode = ERR_AUTH_LOGIN_CHECK_FAIL;
                }
            }
        }

        if (errorCode != null || user == null) {
            if (errorCode == null)
                errorCode = ERR_AUTH_LOGIN_WITH_UNKNOWN_USER;

            LOG.info("nop.auth.login-fail:errorCode={},loginType={},principalId={}",
                    errorCode, request.getLoginType(), request.getPrincipalId());

            // 短信验证码失败不递增用户失败计数（验证码一次性，作废即防爆破）
            if (!smsCodeFail) {
                failCount++;
                if (user != null)
                    failCount = incrementLoginFailCount(user.getUserName());
            }

            if (userContextHook != null) {
                userContextHook.onLoginFail(request, errorCode, user == null ? null : user.getUserName(), failCount);
            }

            if (errorCode != ERR_AUTH_INVALID_VERIFY_CODE) {
                auditLogFail(errorCode.getErrorCode(), errorCode.getDescription(), request,
                        user, beginTime, failCount);
            }

            // 用户名错误对外也只显示用户名或者密码错误
            if (errorCode == ERR_AUTH_LOGIN_WITH_UNKNOWN_USER)
                errorCode = ERR_AUTH_LOGIN_CHECK_FAIL;

            NopException err = new NopException(errorCode).param(ARG_LOGIN_TYPE, request.getLoginType())
                    .param(ARG_PRINCIPAL_ID, request.getPrincipalId());

            return FutureHelper.reject(err);
        } else {
            // 第一因子通过 → MFA 门禁（设计 §3.2 / §4.3 三层判定）。启用 MFA 的用户在此被拦截，
            // 不签发 token；角色策略不达标（W13 第三态）走受限签发。
            // W15-impl：headers 透传 checkMfaRequired（可信设备豁免判定输入——密码类路径真实值）。
            LoginMfaFlow.MfaChallengeDecision mfa = checkMfaRequired(user, request.getLoginType(), headers);
            if (mfa != null) {
                if (mfa.isRestricted()) {
                    // 受限签发（W13，§4.1 结论 5/9）：第一因子通过 + 策略不达标 → 受限会话
                    return completeLogin(user, request, headers, true, true, true);
                }
                NopException err = new NopException(ERR_AUTH_MFA_REQUIRED)
                        .param(ARG_CHALLENGE_TOKEN, mfa.getChallengeToken())
                        .param(ARG_MFA_TYPE, mfa.getMfaType())
                        .param(ARG_LOGIN_TYPE, request.getLoginType());
                LOG.info("nop.auth.mfa-required:loginType={},userName={}", request.getLoginType(), user.getUserName());
                return FutureHelper.reject(err);
            }
            // completeLogin：loginAsync 成功路径 = resetLoginFailCountForUser + notifyHook（设计 §3.2 裁决）
            return completeLogin(user, request, headers, true, true);
        }
    }

    /**
     * 登录失败计数原子递增（check2 P1 修复语义保持，plan 2274 Phase 2 实现 migration）：
     * 原子性由 {@link ILoginAttemptStore#incrementLoginFailCount} 承接（Local 临界区 /
     * Redis INCRBY），返回递增后的计数。锁号阈值判定（loginAsync 入口处读取）允许读到
     * 略旧值——在途请求至多多滑过一次尝试，计数本身不再丢失更新。集群部署注入 redis
     * store 后全节点共享计数（锁号阈值不再被节点数稀释）。
     */
    protected int incrementLoginFailCount(String userName) {
        return attemptStore().incrementLoginFailCount(ILoginAttemptStore.userKey(userName));
    }

    /**
     * {@link ISessionBootstrap} 实现：为已通过外部信道认证（扫码/SSO）的用户
     * 引导一个完整 session。loginType 参数化（设计 §3.2），使审计与 MFA 出口不失真。
     * 启用 MFA 的信道用户在此被拦截（抛 {@code ERR_AUTH_MFA_REQUIRED}）。
     */
    @Override
    public CompletionStage<IUserContext> createSessionForUserAsync(String userId, int loginType) {
        Guard.notEmpty(userId, "userId");
        NopAuthUser user = getUserByUserId(userId);
        if (user == null) {
            LOG.info("nop.auth.session-bootstrap-unknown-user:userId={}", userId);
            throw new NopException(ERR_AUTH_LOGIN_WITH_UNKNOWN_USER).param(ARG_PRINCIPAL_ID, userId);
        }
        if (!isAllowLogin(user)) {
            throw new NopException(ERR_AUTH_USER_NOT_ALLOW_LOGIN).param(ARG_PRINCIPAL_ID, user.getUserName());
        }
        // MFA 门禁（设计 §3.2 / §4.3）：信道用户启用 MFA 同样拦截，loginType 为真实信道值（审计不失真）；
        // 角色策略不达标（W13 第三态）→ 受限签发（信道路径同样受策略约束）。
        // W15-impl：信道路径 headers 传 null——无 HTTP headers 豁免判定结构性跳过（设计 §6.1 结论 3）。
        LoginMfaFlow.MfaChallengeDecision mfa = checkMfaRequired(user, loginType, null);
        if (mfa != null) {
            if (mfa.isRestricted()) {
                LoginRequest restrictedRequest = syntheticRequest(loginType);
                return completeLogin(user, restrictedRequest, new HashMap<>(), false, false, true);
            }
            NopException err = new NopException(ERR_AUTH_MFA_REQUIRED)
                    .param(ARG_CHALLENGE_TOKEN, mfa.getChallengeToken())
                    .param(ARG_MFA_TYPE, mfa.getMfaType())
                    .param(ARG_LOGIN_TYPE, loginType);
            LOG.info("nop.auth.mfa-required:loginType={},userName={}", loginType, user.getUserName());
            throw err;
        }
        LoginRequest request = syntheticRequest(loginType);
        return completeLogin(user, request, new HashMap<>(), false, false);
    }

    /**
     * 单参数版本向后兼容，委托到 {@code LOGIN_TYPE_SSO=4}（设计 §3.2）。
     *
     * @deprecated 使用 {@link #createSessionForUserAsync(String, int)} 传入真实信道 loginType。
     */
    @Deprecated
    @Override
    public CompletionStage<IUserContext> createSessionForUserAsync(String userId) {
        return createSessionForUserAsync(userId, AuthApiConstants.LOGIN_TYPE_SSO);
    }

    /**
     * 公共会话签发方法（设计 §3.2 completeLogin 行为裁决），三处复用：
     * loginAsync / createSessionForUserAsync / mfaVerify。
     * <p>
     * 行为差异裁决（写入 design §3.2）：
     * <ul>
     *   <li>{@code resetFailCount}：loginAsync=true（凭证成功后清零）；
     *       createSessionForUserAsync/mfaVerify=false（第二因子失败不触发用户锁账号）。</li>
     *   <li>{@code notifyHook}（onLoginSuccess）：loginAsync=true；
     *       createSessionForUserAsync/mfaVerify=false（信道引导/mfaVerify 是凭证登录的延续，
     *       不重复触发登录成功钩子；信道侧的登录通知由信道层负责）。</li>
     *   <li>{@code request}/{@code headers}：loginAsync 有完整客户端输入；
     *       createSessionForUserAsync/mfaVerify 用合成 {@link LoginRequest}（loginType=真实信道值，
     *       locale/timezone 取 AppConfig 默认）+ 空 headers 传入 buildUserContext/saveSession。</li>
     * </ul>
     */
    protected CompletionStage<IUserContext> completeLogin(NopAuthUser user, LoginRequest request,
                                                           Map<String, Object> headers,
                                                           boolean resetFailCount, boolean notifyHook) {
        return completeLogin(user, request, headers, resetFailCount, notifyHook, false);
    }

    /**
     * completeLogin 受限变体（W13-impl，设计 §4.3/§4.5）：{@code restricted=true} 时在
     * {@code saveSession} 与 {@code saveUserContextAsync} 之前写入 {@code mfaRestricted}
     * 标志（"先设后存"——覆盖 Dao-cache 与 session 行两持久化路径），并落受限签发审计
     * 事件（经 {@link IAuditService#saveAudit} 落 NopAuthOpLog）。
     * <p>
     * resetFailCount/notifyHook 差异裁决照旧（第一因子成功仍属登录成功——受限会话是
     * 成功登录 + 受限标志，非异常路径）。一期三处调用点（loginAsync/
     * createSessionForUserAsync/mfaVerify 经双参重载）行为不变。
     */
    protected CompletionStage<IUserContext> completeLogin(NopAuthUser user, LoginRequest request,
                                                           Map<String, Object> headers,
                                                           boolean resetFailCount, boolean notifyHook,
                                                           boolean restricted) {
        NopAuthUser fixedUser = user;
        int loginType = request.getLoginType();
        return ContextProvider.runWithTenant(user.getTenantId(), () -> {
            if (resetFailCount)
                userContextCache.resetLoginFailCountForUser(fixedUser.getUserName());
            UserContextImpl userContext = buildUserContext(fixedUser, request);
            // 受限标志"先设后存"：在 saveSession（session 行路径）与 saveUserContextAsync
            // （cache 路径）之前写入 userContext
            if (restricted) {
                userContext.setMfaRestricted(true);
                auditRestrictedLogin(userContext, loginType);
            }
            autoLogout(userContext);
            saveSession(userContext, request, headers == null ? new HashMap<>() : headers);

            if (notifyHook && userContextHook != null)
                userContextHook.onLoginSuccess(userContext, request);

            if (restricted) {
                LOG.info("nop.auth.login-restricted:loginType={},userName={}", loginType, userContext.getUserName());
            } else {
                LOG.info("nop.auth.login-ok:loginType={},userName={}", loginType, userContext.getUserName());
            }
            return userContextCache.saveUserContextAsync(userContext).thenApply(v -> userContext);
        });
    }

    /** 受限签发审计事件（W13）：登录成功但角色策略不达标，会话受限签发。 */
    private void auditRestrictedLogin(UserContextImpl userContext, int loginType) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation("LoginApi__login");
        audit.setDescription("mfa:restricted-login");
        audit.setResultStatus(200);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userContext.getUserId());
        audit.setUserName(userContext.getUserName());
        audit.setSessionId(userContext.getSessionId());
        audit.setTenantId(userContext.getTenantId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "mfa-restricted-login");
        data.put("loginType", loginType);
        audit.setRequestData(JSON.stringify(data));
        auditService.saveAudit(audit);
    }

    protected LoginRequest syntheticRequest(int loginType) {
        return MfaRequests.synthetic(loginType);
    }

    // ===================== MFA 流程委托（plan 2274 Phase 3，design §3.4 组件边界） =====================

    public static final String ATTR_MFA_ACCESS_CODE = LoginMfaFlow.ATTR_MFA_ACCESS_CODE;

    public static final String ATTR_TRUSTED_DEVICE_REGISTERED = LoginMfaFlow.ATTR_TRUSTED_DEVICE_REGISTERED;

    private LoginMfaFlow mfaFlow;
    private MfaCodeSender mfaCodeSender;

    /**
     * MFA 流程组件（design §3.4）：以宿主 wired 实例构造（可选注入字段直传）——手工 wiring
     * 测试路径行为不变；容器路径字段已注入，等价 bean 装配。
     */
    protected LoginMfaFlow mfaFlow() {
        if (mfaFlow == null) {
            mfaFlow = new LoginMfaFlow(mfaChallengeStore, mfaFactorVerifier, roleMfaPolicyEvaluator,
                    trustedDeviceManager, ormTemplate, passwordEncoder, authTokenProvider, daoProvider);
        }
        return mfaFlow;
    }

    /** 发码流程组件（design §3.4）：构造方式同 {@link #mfaFlow()}。 */
    protected MfaCodeSender mfaCodeSender() {
        if (mfaCodeSender == null) {
            mfaCodeSender = new MfaCodeSender(mfaChallengeStore, smsCodeStore, emailCodeStore,
                    smsSender, emailSender, daoProvider, rateLimiter());
        }
        return mfaCodeSender;
    }

    /**
     * MFA 第二因子验证（设计 §3.2 mfaVerify 流程）：委托 {@link LoginMfaFlow}，会话出口经
     * 完成回调端口回到宿主 completeLogin（组件不持有 LoginService 引用——接缝裁定）。
     */
    @Override
    public CompletionStage<IUserContext> mfaVerifyAsync(MfaVerifyRequest request, Map<String, Object> headers) {
        return mfaFlow().mfaVerifyAsync(request, headers, this::completeLoginPort);
    }

    /** 完成回调端口实现（6 参受限变体，resetFailCount/notifyHook/restricted 语义由宿主管控）。 */
    protected CompletionStage<IUserContext> completeLoginPort(NopAuthUser user, LoginRequest request,
                                                              Map<String, Object> headers,
                                                              boolean resetFailCount, boolean notifyHook,
                                                              boolean restricted) {
        return completeLogin(user, request, headers, resetFailCount, notifyHook, restricted);
    }

    @Override
    public void sendSmsCode(String phone, String clientIp) {
        mfaCodeSender().sendSmsCode(phone, clientIp);
    }

    @Override
    public void sendMfaCode(String challengeToken, String clientIp) {
        mfaCodeSender().sendMfaCode(challengeToken, clientIp);
    }

    /**
     * MFA 门禁判定（设计 §3.2 / §4.3 三层判定矩阵）：委托 {@link LoginMfaFlow#checkMfaRequired}。
     * 返回非 null 表示需要处理（restricted 决策 / challenge 决策）。
     */
    protected LoginMfaFlow.MfaChallengeDecision checkMfaRequired(NopAuthUser user, int loginType,
                                                                 Map<String, Object> requestHeaders) {
        return mfaFlow().checkMfaRequired(user, loginType, requestHeaders);
    }

    private boolean isAllowLogin(NopAuthUser user) {
        if (user.getStatus() != AuthApiConstants.USER_STATUS_ACTIVE)
            return false;

        if (user.getExpireAt() != null) {
            long time = DateHelper.dateTimeToTimestamp(user.getExpireAt()).getTime();
            return time >= CoreMetrics.currentTimeMillis();
        }
        return true;
    }

    protected UserContextImpl buildUserContext(NopAuthUser user, LoginRequest request) {
        UserContextImpl context = new UserContextImpl();
        context.setLoginType(request.getLoginType());
        context.setUserId(user.getUserId());
        context.setOpenId(user.getOpenId());
        context.setUserName(user.getUserName());
        context.setNickName(user.getNickName());
        context.setLocale(request.getLocale() == null ? AppConfig.appLocale() : request.getLocale());
        context.setTimeZone(request.getTimeZone() == null ? AppConfig.appTimezone() : request.getTimeZone());
        context.setDeptId(user.getDeptId());
        context.setTenantId(user.getTenantId());
        context.setLastAccessTime(CoreMetrics.currentTimeMillis());

        NopAuthDept dept = getDept(user.getDeptId());
        if (dept != null) {
            context.setDeptName(dept.getDeptName());
        }

        Set<NopAuthRole> roles = user.getRoles();
        Set<String> roleIds = new TreeSet<>();
        for (NopAuthRole role : roles) {
            roleIds.add(role.getRoleId());
            // 自动加入关联的子角色。
            if (!StringHelper.isEmpty(role.getChildRoleIds()))
                roleIds.addAll(ConvertHelper.toCsvSet(role.getChildRoleIds()));
        }

        // 用户总是具有user角色。这里检查一下是否为所有user都自动分配了关联角色
        NopAuthRole userRole = daoProvider.daoFor(NopAuthRole.class).getEntityById(NopAuthConstants.ROLE_USER);
        if (userRole != null) {
            if (!StringHelper.isEmpty(userRole.getChildRoleIds()))
                roleIds.addAll(ConvertHelper.toCsvSet(userRole.getChildRoleIds()));
        }

        context.setRoles(roleIds);
        if (request.getPrimaryRoleId() != null && roleIds.contains(request.getPrimaryRoleId())) {
            context.setPrimaryRole(request.getPrimaryRoleId());
        } else {
            String roleId = roles.stream().filter(r -> isYes(r.getIsPrimary())).findFirst().map(NopAuthRole::getRoleId)
                    .orElse(null);
            context.setPrimaryRole(roleId);
        }

        return context;
    }

    @Override
    protected List<RoleInfo> getRoleInfos(IUserContext userContext) {
        List<RoleInfo> roleInfos = new ArrayList<>();
        if (userContext.getRoles() != null) {
            IEntityDao<NopAuthRole> roleDao = daoProvider.daoFor(NopAuthRole.class);
            List<NopAuthRole> roles = roleDao.batchGetEntitiesByIds(userContext.getRoles());
            for (NopAuthRole role : roles) {
                RoleInfo roleInfo = new RoleInfo();
                roleInfo.setRoleId(role.getRoleId());
                roleInfo.setRoleName(role.getRoleName());
                roleInfos.add(roleInfo);
            }
        }
        return roleInfos;
    }

    protected NopAuthDept getDept(String deptId) {
        if (StringHelper.isEmpty(deptId))
            return null;
        if (!returnDeptName)
            return null;

        return daoProvider.daoFor(NopAuthDept.class).getEntityById(deptId);
    }

    protected void saveSession(UserContextImpl userContext, LoginRequest request,
                               Map<String, Object> headers) {
        String sessionId = loginSessionStore.saveSession(userContext, request, headers);
        userContext.setSessionId(sessionId);

        userContext.setAccessToken(authTokenProvider.generateAccessToken(userContext, CFG_AUTH_ACCESS_TOKEN_EXPIRE_SECONDS.get()));
        userContext.setRefreshToken(authTokenProvider.generateRefreshToken(userContext, CFG_AUTH_REFRESH_TOKEN_EXPIRE_SECONDS.get()));
    }

    /**
     * 自动退出当前用户的其他活动session
     */
    protected void autoLogout(IUserContext userContext) {
        if (loginSessionStore == null)
            return;

        // 如果允许同一用户名存在多个登录会话，则不自动登出其他会话
        if (allowMultipleSessionsSameUser)
            return;

        List<String> sessionIds = loginSessionStore.getActionSessions(userContext.getUserName());

        for (String sessionId : sessionIds) {
            doLogout(AuthApiConstants.LOGOUT_TYPE_RELOGIN, new SessionInfo(userContext.getUserName(), sessionId));
        }
    }

    boolean isValid(LoginRequest request) {
        if (!isValidLoginMethod(request)) {
            LOG.info("nop.auth.invalid-login-method:{}", request.getLoginType());
            return false;
        }

        return !StringHelper.isEmpty(request.getPrincipalId());
    }

    boolean checkVerifyCode(LoginRequest request) {
        if (verifyCodeGenerator == null)
            return true;

        String cachedCode = userContextCache.getVerifyCode(request.getVerifySecret());
        boolean b;
        if (cachedCode != null) {
            b = verifyCodeGenerator.checkValid(cachedCode, request.getVerifyCode(), request.getVerifySecret());
        } else {
            b = false;
        }
        if (!b) {
            // check2 P3：验证码明文不落日志（生产误开 debug 级/日志聚合降级时防泄露），仅记长度
            LOG.debug("nop.auth.verify-code-mismatch:codeLength={},cachedLength={}",
                    request.getVerifyCode() == null ? 0 : request.getVerifyCode().length(),
                    cachedCode == null ? 0 : cachedCode.length());
        }
        return b;
    }

    NopAuthUser getAuthUser(LoginRequest request) {
        int loginType = request.getLoginType();
        switch (loginType) {
            case LOGIN_TYPE_USERNAME_PASSWORD: {
                return getUserByUserName(request.getPrincipalId());
            }
            case LOGIN_TYPE_EMAIL_PASSWORD: {
                return getUserByEmail(request.getPrincipalId());
            }
            case LOGIN_TYPE_PHONE_PASSWORD:
            case LOGIN_TYPE_PHONE_SMS: {
                // 短信验证码登录（5）与手机密码登录（3）都按手机号定位用户（设计 §3.2）
                return getUserByPhone(request.getPrincipalId());
            }
            default:
                return null;
        }
    }

    boolean needCheckPassword(LoginRequest request) {
        switch (request.getLoginType()) {
            case LOGIN_TYPE_USERNAME_PASSWORD:
            case LOGIN_TYPE_EMAIL_PASSWORD:
            case LOGIN_TYPE_PHONE_PASSWORD:
                return true;
            // 短信验证码登录（5）不走密码路径，走 SmsCodeStore.verify 分支（设计 §3.2）
        }
        return false;
    }

    boolean isValidLoginMethod(LoginRequest request) {
        int loginType = request.getLoginType();
        if (loginType == LOGIN_TYPE_PHONE_SMS) {
            // 短信验证码登录受独立功能开关门禁（设计 §3.7 nop.auth.sms-code.enabled）
            if (!CFG_AUTH_SMS_CODE_ENABLED.get()) {
                LOG.info("nop.auth.invalid-login-method:sms-code-disabled:{}", loginType);
                return false;
            }
            // 同时受 allowedLoginMethods 白名单约束（与密码类一致）
            if (allowedLoginMethods != null && !allowedLoginMethods.isEmpty())
                return allowedLoginMethods.contains(String.valueOf(loginType));
            return true;
        }

        if (allowedLoginMethods == null || allowedLoginMethods.isEmpty()) {
            return LOGIN_TYPE_USERNAME_PASSWORD == loginType;
        }

        return allowedLoginMethods.contains(String.valueOf(request.getLoginType()));
    }

    protected NopAuthUser getUserByUserName(String userName) {
        NopAuthUser example = new NopAuthUser();
        example.setUserName(userName);
        return daoProvider.daoFor(NopAuthUser.class).findFirstByExample(example);
    }

    private boolean passwordMatches(NopAuthUser user, LoginRequest request) {
        return passwordEncoder.passwordMatches(user.getSalt(), request.getPrincipalSecret(), user.getPassword());
    }

    protected NopAuthUser getUserByPhone(String phone) {
        NopAuthUser example = new NopAuthUser();
        example.setPhone(phone);
        return daoProvider.daoFor(NopAuthUser.class).findFirstByExample(example);
    }

    protected NopAuthUser getUserByEmail(String email) {
        NopAuthUser example = new NopAuthUser();
        example.setEmail(email);
        return daoProvider.daoFor(NopAuthUser.class).findFirstByExample(example);
    }

    protected NopAuthUser getUserByUserId(String userId) {
        return daoProvider.daoFor(NopAuthUser.class).getEntityById(userId);
    }

    protected NopAuthUser getUserByOpenId(String openId, String clientId) {
        NopAuthUser example = new NopAuthUser();
        example.setOpenId(openId);
        return daoProvider.daoFor(NopAuthUser.class).findFirstByExample(example);
    }

    protected void auditLogFail(String errorCode, String defaultMessage, LoginRequest request, NopAuthUser user,
                                long beginTime, int failCount) {
        String locale = AppConfig.defaultLocale();
        AuditRequest audit = new AuditRequest();
        audit.setErrorCode(errorCode);
        String message = I18nMessageManager.instance().getMessage(locale, errorCode, defaultMessage);
        audit.setRetMessage(message);
        audit.setResultStatus(400);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setOperation("LoginApi__login");
        audit.setDescription(I18nMessageManager.instance().getMessage(locale, "api.label.LoginApi__login", null));
        audit.setTenantId(ContextProvider.currentTenantId());

        // failCount合并进同一份requestData：暴力破解排查依赖loginType/principalId定位攻击目标，
        // 第2次及以后的失败记录不得覆盖丢失这两个字段
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("loginType", request.getLoginType());
        map.put("principalId", request.getPrincipalId());
        if (failCount > 1) {
            map.put("failCount", failCount);
        }
        audit.setRequestData(JSON.stringify(map));

        if (user != null) {
            audit.setUserName(user.getUserName());
            audit.setUserId(user.getUserId());
        } else {
            audit.setUserName("-");
        }
        audit.setUsedTime(CoreMetrics.currentTimeMillis() - beginTime);
        auditService.saveAudit(audit);
    }

    @Override
    public CompletionStage<Void> logoutAsync(int logoutType, LogoutRequest request) {
        AuthToken authToken = parseAuthToken(request.getAccessToken());
        if (authToken == null)
            return FutureHelper.success(null);

        LOG.info("nop.auth.logout:userName={},sessionId={},logoutType={}", authToken.getUserName(), authToken.getSessionId(), logoutType);
        return doLogout(logoutType, new SessionInfo(authToken.getUserName(), authToken.getSessionId()));
    }

    @Override
    public String generateVerifyCode(String verifySecret) {
        Guard.notEmpty(verifySecret, "verifySecret");

        if (verifyCodeGenerator == null)
            return "fake-code";

        VerifyCode verifyCode = verifyCodeGenerator.generateCode(verifySecret);
        // check2 P3：验证码答案不落日志，仅记长度（对齐 sso refreshToken 只记长度+前缀先例）
        LOG.debug("nop.login.generate-verify-code:codeLength={}",
                verifyCode.getCode() == null ? 0 : verifyCode.getCode().length());
        userContextCache.setVerifyCode(verifySecret, verifyCode.getCode());
        return verifyCode.getCaptcha();
    }

    @Override
    public AuthToken parseAuthToken(String accessToken) {
        return authTokenProvider.parseAuthToken(accessToken);
    }

    @Override
    public AuthToken parseRefreshToken(String refreshToken) {
        return authTokenProvider.parseRefreshToken(refreshToken);
    }

    @Override
    public AuthToken parseAccessCode(String accessCode) {
        return authTokenProvider.parseAccessCode(accessCode);
    }

    @Override
    public String refreshToken(IUserContext userContext, AuthToken authToken) {
        String accessToken = authTokenProvider.generateAccessToken(userContext, authToken.getExpireSeconds());
        userContext.setRefreshToken(authTokenProvider.generateRefreshToken(userContext, CFG_AUTH_REFRESH_TOKEN_EXPIRE_SECONDS.get()));
        userContext.setAccessToken(accessToken);
        return accessToken;
    }
}