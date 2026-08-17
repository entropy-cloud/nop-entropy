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
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.login.ISessionBootstrap;
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
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthTenant;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.MfaTrustedDeviceManager;
import io.nop.auth.service.mfa.RoleMfaPolicy;
import io.nop.auth.service.mfa.RoleMfaPolicyEvaluator;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.dao.DaoConstants;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MAX_LOGIN_FAIL_COUNT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_WINDOW_SKEW;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_REFRESH_TOKEN_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_ALLOW_REGISTER;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS;
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
import static io.nop.auth.service.NopAuthErrors.ARG_PHONE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_RATE_LIMITED;
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
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_DAILY_LIMIT;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_RATE_LIMITED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_USER_NOT_ALLOW_LOGIN;
import static io.nop.commons.util.StringHelper.isYes;
import static io.nop.dao.DaoConfigs.CFG_ORM_ENABLE_TENANT_BY_DEFAULT;

public class LoginServiceImpl extends AbstractLoginService implements ISessionBootstrap {
    static final Logger LOG = LoggerFactory.getLogger(LoginServiceImpl.class);

    @Inject
    protected IPasswordEncoder passwordEncoder;

    @Inject
    protected IDaoProvider daoProvider;

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

    private Set<String> allowedLoginMethods;

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
                if (maxFailCount > 0) {
                    if (failCount >= maxFailCount) {
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
                    userContextCache.setLoginFailCountForUser(user.getUserName(), failCount);
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
            MfaChallengeDecision mfa = checkMfaRequired(user, request.getLoginType(), headers);
            if (mfa != null) {
                if (mfa.isRestricted()) {
                    // 受限签发（W13，§4.1 结论 5/9）：第一因子通过 + 策略不达标 → 受限会话
                    return completeLogin(user, request, headers, true, true, true);
                }
                NopException err = new NopException(ERR_AUTH_MFA_REQUIRED)
                        .param(ARG_CHALLENGE_TOKEN, mfa.challengeToken)
                        .param(ARG_MFA_TYPE, mfa.mfaType)
                        .param(ARG_LOGIN_TYPE, request.getLoginType());
                LOG.info("nop.auth.mfa-required:loginType={},userName={}", request.getLoginType(), user.getUserName());
                return FutureHelper.reject(err);
            }
            // completeLogin：loginAsync 成功路径 = resetLoginFailCountForUser + notifyHook（设计 §3.2 裁决）
            return completeLogin(user, request, headers, true, true);
        }
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
        MfaChallengeDecision mfa = checkMfaRequired(user, loginType, null);
        if (mfa != null) {
            if (mfa.isRestricted()) {
                LoginRequest restrictedRequest = syntheticRequest(loginType);
                return completeLogin(user, restrictedRequest, new HashMap<>(), false, false, true);
            }
            NopException err = new NopException(ERR_AUTH_MFA_REQUIRED)
                    .param(ARG_CHALLENGE_TOKEN, mfa.challengeToken)
                    .param(ARG_MFA_TYPE, mfa.mfaType)
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
        LoginRequest request = new LoginRequest();
        request.setLoginType(loginType);
        request.setLocale(AppConfig.appLocale());
        request.setTimeZone(AppConfig.appTimezone());
        return request;
    }

    // ===================== MFA 第二因子验证（设计 §3.2 / §3.6） =====================

    /** IUserContext attr key：信道类 mfaVerify 成功出口的 accessCode（密码类不设此 attr）。 */
    public static final String ATTR_MFA_ACCESS_CODE = "mfaAccessCode";

    /**
     * IUserContext attr key：mfaVerify(rememberDevice) 的可信设备登记结果（W15-impl，仅密码类
     * 路径携带 Boolean true/false；LoginApiBizModel 回填 {@code LoginResult.trustedDeviceRegistered}）。
     */
    public static final String ATTR_TRUSTED_DEVICE_REGISTERED = "trustedDeviceRegistered";

    @Override
    public CompletionStage<IUserContext> mfaVerifyAsync(MfaVerifyRequest request, Map<String, Object> headers) {
        Guard.notEmpty(request.getChallengeToken(), "challengeToken");

        // 1. peek challenge（不消费、不刷新 TTL）
        MfaChallenge challenge = mfaChallengeStore == null ? null : mfaChallengeStore.peek(request.getChallengeToken());
        if (challenge == null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
        }

        // 2. loadUser + setting 复核（runWithTenant 保证租户上下文）
        String tenantId = challenge.getTenantId();
        return ContextProvider.runWithTenant(tenantId, () -> {
            NopAuthUser user = getUserByUserId(challenge.getUserId());
            if (user == null) {
                mfaChallengeStore.consume(request.getChallengeToken());
                throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                        .param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
            }
            NopAuthMfaSetting setting = loadMfaSetting(challenge.getUserId());
            if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus())) {
                // 复核失败：管理员重置/用户解绑后 challenge 作废（安全默认）
                mfaChallengeStore.consume(request.getChallengeToken());
                throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                        .param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
            }

            // 3. 分支判定
            boolean recovery = !StringHelper.isEmpty(request.getRecoveryCode());
            if (recovery) {
                // 恢复码分支不登记可信设备（设计 §6.4——应急通道不应产生 30 天长期豁免）
                return verifyRecoveryCodeAndComplete(request, challenge, user, setting);
            }
            return verifySecondFactorAndComplete(request, challenge, user, setting, headers);
        });
    }

    /**
     * TOTP / SMS / EMAIL / WebAuthn 第二因子验证 + completeLogin（设计 §3.2 mfaVerify 流程）。
     * 因子校验收敛至 {@link MfaFactorVerifier}（W12-impl 等价重构：SMS EXPIRED 抛错/
     * TOTP 窗口推进内聚组件；失败计数与错误码留在本调用方）。
     * <p>
     * W14-impl webauthn 分支（设计 §5.3.2）：{@code code} 载体换 {@code assertion}（统一五参
     * 重载——cryptoChallenge 取自 challenge payload，signCount 单调写内聚组件）；成功 consume →
     * completeLogin（一期出口不变）。
     * <p>
     * W15-impl 可信设备登记（设计 §6.1 结论 4 / §6.3）：{@code headers} 穿线增参——
     * {@code rememberDevice=true} 且 challenge.loginType ∈ 密码类（1/2/3/5）时在
     * completeLogin <b>之前</b>纯 DB 写登记（同 hash 含过期行 upsert 覆盖刷新；满员/无
     * device-id/失败 = false 提示不阻断登录）；结果经 {@code ATTR_TRUSTED_DEVICE_REGISTERED}
     * attr 携带（ATTR_MFA_ACCESS_CODE 先例）由 LoginApiBizModel 回填 LoginResult。登记逻辑
     * 在本方法（非 completeMfaLogin）——后者被恢复码分支共用，放入即恢复码也登记（§6.4 拒绝项）。
     */
    protected CompletionStage<IUserContext> verifySecondFactorAndComplete(MfaVerifyRequest request,
                                                                           MfaChallenge challenge,
                                                                           NopAuthUser user,
                                                                           NopAuthMfaSetting setting,
                                                                           Map<String, Object> headers) {
        String mfaType = challenge.getMfaType();
        if (!MFA_TYPE_TOTP.equals(mfaType) && !MFA_TYPE_SMS.equals(mfaType)
                && !MFA_TYPE_WEBAUTHN.equals(mfaType) && !MFA_TYPE_EMAIL.equals(mfaType)) {
            // 未知 mfaType：fail-closed，作废 challenge（一期兜底保留——未来新值未接入时的安全侧失效）
            mfaChallengeStore.consume(request.getChallengeToken());
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                    .param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
        }

        boolean ok = MFA_TYPE_WEBAUTHN.equals(mfaType)
                ? mfaFactorVerifier.verify(setting, mfaType, request.getCode(), request.getAssertion(), challenge)
                : mfaFactorVerifier.verify(setting, mfaType, request.getCode());
        if (!ok) {
            // 失败计数（peek 阶段，未消费 challenge）
            incrFailCountOrDiscard(request.getChallengeToken());
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
        }

        mfaChallengeStore.consume(request.getChallengeToken());

        // W15-impl 可信设备登记（completeLogin 之前纯 DB 写；仅密码类 loginType；登记失败不阻断登录）
        final boolean rememberRequested = Boolean.TRUE.equals(request.getRememberDevice())
                && isPasswordLoginType(challenge.getLoginType()) && trustedDeviceManager != null;
        final Boolean registered = rememberRequested
                ? trustedDeviceManager.register(user.getUserId(), headers) != null
                : null;

        return completeMfaLogin(user, challenge.getLoginType()).thenApply(ctx -> {
            if (registered != null) {
                // 结果回填（显式 true/false——满员/无 device-id 也是 false 提示非静默；仅密码类路径
                // 携带）：IUserContext attr 载体（ATTR_MFA_ACCESS_CODE 先例——LoginApiBizModel 消费）
                ctx.setAttr(ATTR_TRUSTED_DEVICE_REGISTERED, registered);
            }
            return ctx;
        });
    }

    /**
     * 恢复码验证 + completeLogin（设计 §3.2 / §3.4 恢复码分支）。
     * <ul>
     *   <li>已用恢复码 → 统一 {@code MFA_FAIL}（不计数、不消费、不暴露"曾有效"）。</li>
     *   <li>无效码 → {@code incrFailCount} + {@code MFA_FAIL}。</li>
     *   <li>有效恢复码 → consume challenge + setting.status=disabled（强制重绑）+ 审计 + completeLogin。</li>
     * </ul>
     */
    protected CompletionStage<IUserContext> verifyRecoveryCodeAndComplete(MfaVerifyRequest request,
                                                                           MfaChallenge challenge,
                                                                           NopAuthUser user,
                                                                           NopAuthMfaSetting setting) {
        RecoveryVerifyResult result = verifyRecoveryCode(challenge.getUserId(), request.getRecoveryCode());
        if (result == RecoveryVerifyResult.USED) {
            // 已用过的恢复码：统一 MFA_FAIL，不计数、不消费 challenge（设计 §3.2）
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
        }
        if (result == RecoveryVerifyResult.INVALID) {
            incrFailCountOrDiscard(request.getChallengeToken());
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
        }
        // VALID：消费 challenge + 强制重绑
        mfaChallengeStore.consume(request.getChallengeToken());
        setting.setStatus(MFA_STATUS_DISABLED);
        daoProvider.daoFor(NopAuthMfaSetting.class).updateEntityDirectly(setting);
        LOG.info("nop.auth.mfa-recovery-used:userId={},userName={}", user.getUserId(), user.getUserName());
        return completeMfaLogin(user, challenge.getLoginType());
    }

    /** 恢复码验证三态。 */
    protected enum RecoveryVerifyResult {
        VALID, USED, INVALID
    }

    /**
     * 验证恢复码：遍历用户的所有恢复码，BCrypt 比对。
     * codeHash 格式为 {@code salt:hash}（无独立 salt 列）。
     */
    protected RecoveryVerifyResult verifyRecoveryCode(String userId, String inputCode) {
        IEntityDao<NopAuthMfaRecoveryCode> dao = daoProvider.daoFor(NopAuthMfaRecoveryCode.class);
        NopAuthMfaRecoveryCode example = new NopAuthMfaRecoveryCode();
        example.setUserId(userId);
        List<NopAuthMfaRecoveryCode> codes = dao.findAllByExample(example);

        for (NopAuthMfaRecoveryCode code : codes) {
            String stored = code.getCodeHash();
            if (StringHelper.isEmpty(stored)) {
                continue;
            }
            // codeHash 格式: salt:hash
            String[] parts = stored.split(":", 2);
            String salt = parts.length == 2 ? parts[0] : null;
            String hash = parts.length == 2 ? parts[1] : stored;
            if (passwordEncoder.passwordMatches(salt, inputCode, hash)) {
                if (code.getUsed() != null && code.getUsed() != 0) {
                    return RecoveryVerifyResult.USED;
                }
                // 标记已使用
                code.setUsed((byte) 1);
                code.setUsedAt(new Timestamp(CoreMetrics.currentTimeMillis()));
                dao.updateEntityDirectly(code);
                return RecoveryVerifyResult.VALID;
            }
        }
        return RecoveryVerifyResult.INVALID;
    }

    /**
     * 失败计数：{@code incrFailCount} ≥ {@code max-attempts} 时作废 challenge（设计 §3.2）。
     */
    protected void incrFailCountOrDiscard(String challengeToken) {
        if (mfaChallengeStore == null) {
            return;
        }
        int failCount = mfaChallengeStore.incrFailCount(challengeToken);
        if (failCount >= CFG_AUTH_MFA_MAX_ATTEMPTS.get()) {
            mfaChallengeStore.consume(challengeToken);
        }
    }

    /**
     * MFA 验证成功后的 completeLogin 包装（设计 §3.2 completeLogin 裁决）：
     * <ul>
     *   <li>resetFailCount=false, notifyHook=false（mfaVerify 路径，第二因子失败不锁账号）。</li>
     *   <li>信道类 loginType（20-23）：生成 accessCode 并通过 {@code ATTR_MFA_ACCESS_CODE} attr 携带。</li>
     *   <li>密码类 loginType：completeLogin 后 IUserContext 已含 accessToken。</li>
     * </ul>
     */
    protected CompletionStage<IUserContext> completeMfaLogin(NopAuthUser user, int loginType) {
        LoginRequest request = syntheticRequest(loginType);
        return completeLogin(user, request, new HashMap<>(), false, false).thenApply(ctx -> {
            if (isChannelLoginType(loginType)) {
                String accessCode = authTokenProvider.generateAccessCode(ctx, CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS.get());
                ctx.setAttr(ATTR_MFA_ACCESS_CODE, accessCode);
            }
            return ctx;
        });
    }

    /** 信道类 loginType 判定（20-23=飞书/钉钉/企微/Webhook，4=SSO 也按信道处理以签发 accessCode）。 */
    protected static boolean isChannelLoginType(int loginType) {
        return loginType >= 20 || loginType == AuthApiConstants.LOGIN_TYPE_SSO;
    }

    // ===================== 短信验证码发送（设计 §3.3 / §3.6） =====================

    /** Local 限流追踪：phone → [lastSendMs, dailyCount, dailyDate]；IP → [dailyCount, dailyDate]。 */
    private final Map<String, long[]> smsPhoneTracker = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, long[]> smsIpTracker = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void sendSmsCode(String phone, String clientIp) {
        Guard.notEmpty(phone, "phone");
        if (!CFG_AUTH_SMS_CODE_ENABLED.get()) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "sms code login is disabled (nop.auth.sms-code.enabled=false)");
        }

        // 防枚举：未注册手机号且 !allow-register → 统一响应"已发送"（不暴露"未注册"信号）
        NopAuthUser user = getUserByPhone(phone);
        if (user == null && !CFG_AUTH_SMS_CODE_ALLOW_REGISTER.get()) {
            LOG.info("nop.auth.sms-code-unregistered-noop:phone={}", phone);
            return;
        }

        // 限流
        checkSmsRateLimit(phone, clientIp);

        // 生成 + 存储 + 发送
        String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_LOGIN + phone);
        sendSms(phone, code);
    }

    /**
     * MFA 第二因子验证码重发（设计 §3.6；W14-impl #8 按 challenge.mfaType 分派重构）：
     * <ul>
     *   <li><b>sms</b> → 现行为原样（peek → 手机号解析 → 限流 → 发码 key=mfa:userId）。</li>
     *   <li><b>email</b>（W15-impl，设计 §5.3.3）→ 解析 user.email（服务端解析，不接受客户端
     *       指定）→ 限流（email+IP 双维度）→ {@code EmailCodeStore.send(mfa-email:{userId})}
     *       → 邮件发送；无 email 抛 CHALLENGE_EXPIRED 等价先例错误（对齐 sms "no phone number"）。</li>
     *   <li><b>其余（totp/webauthn）</b> → 显式抛 {@code ERR_AUTH_MFA_CODE_UNSUPPORTED}
     *       （无静默 no-op——这些因子没有"可发的验证码"）。</li>
     * </ul>
     */
    @Override
    public void sendMfaCode(String challengeToken, String clientIp) {
        Guard.notEmpty(challengeToken, "challengeToken");
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

        // 限流（MFA 短信路径使用手机号维度）
        checkSmsRateLimit(phone, clientIp);

        // 生成 + 存储 + 发送（key=mfa:userId）
        String code = smsCodeStore == null ? null : smsCodeStore.send(SMS_KEY_MFA + challenge.getUserId());
        sendSms(phone, code);
    }

    /**
     * email 分支发码（W15-impl，设计 §5.3.3）：enabled 门控（显式拒绝非静默）→ 服务端解析
     * user.email → email+IP 双维度限流 → {@code EmailCodeStore.send}（key=mfa-email:{userId}，
     * 通道隔离）→ {@link IEmailSender} 发送。
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

        // 限流（email+IP 双维度）
        checkEmailRateLimit(email, clientIp);

        // 生成 + 存储 + 发送（key=mfa-email:userId，与验证侧 MfaFactorVerifier 消费口径一致）
        String code = emailCodeStore.send(EMAIL_KEY_MFA + challenge.getUserId());
        sendMfaEmail(email, code);
    }

    /**
     * 邮件发送：按 {@code nop.auth.email-code.subject-template}/{@code text-template}
     * （{@code {code}} 占位服务端替换）组装 {@link EmailMessage} 并经 {@link IEmailSender}
     * 发送。无 emailSender 时 fail-closed（对齐 sendSms）。
     */
    protected void sendMfaEmail(String email, String code) {
        if (emailSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "IEmailSender is not configured; email cannot be sent");
        }
        EmailMessage msg = new EmailMessage();
        msg.setTo(java.util.Collections.singletonList(email));
        msg.setSubject(CFG_AUTH_EMAIL_CODE_SUBJECT_TEMPLATE.get().replace("{code}", code));
        msg.setText(CFG_AUTH_EMAIL_CODE_TEXT_TEMPLATE.get().replace("{code}", code));
        emailSender.sendEmail(msg);
        LOG.info("nop.auth.email-code-sent:email={}", email);
    }

    /**
     * 短信发送：组装 SmsMessage 并经 {@link ISmsSender} 发送。无 smsSender 时 fail-closed。
     */
    protected void sendSms(String phone, String code) {
        if (smsSender == null) {
            throw new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                    .param("msg", "ISmsSender is not configured; SMS cannot be sent");
        }
        SmsMessage msg = new SmsMessage();
        msg.setMobile(phone);
        msg.setTemplateCode(CFG_AUTH_SMS_CODE_TEMPLATE_ID.get());
        msg.setParams(java.util.Collections.singletonList(code));
        smsSender.sendMessage(msg);
        LOG.info("nop.auth.sms-code-sent:phone={}", phone);
    }

    /**
     * Local 限流：同手机号 {@code send-interval-seconds} 间隔 + 每日 {@code daily-limit} 上限 + IP 每日 {@code ip-daily-limit}。
     * 超限显式抛 {@code RATE_LIMITED} / {@code DAILY_LIMIT}（不静默跳过）。
     */
    protected void checkSmsRateLimit(String phone, String clientIp) {
        long now = CoreMetrics.currentTimeMillis();
        long today = CoreMetrics.today().toEpochDay();

        // 手机号间隔 + 日上限
        int interval = CFG_AUTH_SMS_CODE_SEND_INTERVAL_SECONDS.get();
        int dailyLimit = CFG_AUTH_SMS_CODE_DAILY_LIMIT.get();
        long[] phoneEntry = smsPhoneTracker.compute(phone, (k, v) -> {
            if (v == null || v[2] != today) {
                return new long[]{now, 1, today};
            }
            return new long[]{v[0], v[1] + 1, today};
        });
        if (phoneEntry[1] > 1 && (now - phoneEntry[0]) < interval * 1000L) {
            throw new NopException(ERR_AUTH_SMS_RATE_LIMITED).param(ARG_PHONE, phone);
        }
        if (phoneEntry[1] > dailyLimit) {
            throw new NopException(ERR_AUTH_SMS_DAILY_LIMIT).param(ARG_PHONE, phone);
        }
        // 更新 lastSendMs（compute 中已递增 count，此处只更新时间戳）
        phoneEntry[0] = now;

        // IP 日上限
        if (!StringHelper.isEmpty(clientIp)) {
            int ipLimit = CFG_AUTH_SMS_CODE_IP_DAILY_LIMIT.get();
            long[] ipEntry = smsIpTracker.compute(clientIp, (k, v) -> {
                if (v == null || v[1] != today) {
                    return new long[]{1, today};
                }
                return new long[]{v[0] + 1, today};
            });
            if (ipEntry[0] > ipLimit) {
                throw new NopException(ERR_AUTH_SMS_DAILY_LIMIT).param(ARG_PHONE, phone);
            }
        }
    }

    /** Local 限流追踪（email 维度，W15-impl）：email → [lastSendMs, dailyCount, dailyDate]；IP → [dailyCount, dailyDate]。 */
    private final Map<String, long[]> emailTracker = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, long[]> emailIpTracker = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Local 限流（W15-impl，设计 §5.3.3——复用 {@link #checkSmsRateLimit} 模式）：
     * 同邮箱 {@code send-interval-seconds} 间隔 + 每日 {@code daily-limit} 上限 + IP 每日
     * {@code ip-daily-limit}（email-code 配置组）。超限显式抛
     * {@code EMAIL_RATE_LIMITED} / {@code EMAIL_DAILY_LIMIT}（不静默跳过）。
     */
    protected void checkEmailRateLimit(String email, String clientIp) {
        long now = CoreMetrics.currentTimeMillis();
        long today = CoreMetrics.today().toEpochDay();

        // 邮箱间隔 + 日上限
        int interval = CFG_AUTH_EMAIL_CODE_SEND_INTERVAL_SECONDS.get();
        int dailyLimit = CFG_AUTH_EMAIL_CODE_DAILY_LIMIT.get();
        long[] emailEntry = emailTracker.compute(email, (k, v) -> {
            if (v == null || v[2] != today) {
                return new long[]{now, 1, today};
            }
            return new long[]{v[0], v[1] + 1, today};
        });
        if (emailEntry[1] > 1 && (now - emailEntry[0]) < interval * 1000L) {
            throw new NopException(ERR_AUTH_EMAIL_RATE_LIMITED).param("email", email);
        }
        if (emailEntry[1] > dailyLimit) {
            throw new NopException(ERR_AUTH_EMAIL_DAILY_LIMIT).param("email", email);
        }
        // 更新 lastSendMs（compute 中已递增 count，此处只更新时间戳）
        emailEntry[0] = now;

        // IP 日上限
        if (!StringHelper.isEmpty(clientIp)) {
            int ipLimit = CFG_AUTH_EMAIL_CODE_IP_DAILY_LIMIT.get();
            long[] ipEntry = emailIpTracker.compute(clientIp, (k, v) -> {
                if (v == null || v[1] != today) {
                    return new long[]{1, today};
                }
                return new long[]{v[0] + 1, today};
            });
            if (ipEntry[0] > ipLimit) {
                throw new NopException(ERR_AUTH_EMAIL_DAILY_LIMIT).param("email", email);
            }
        }
    }

    /**
     * MFA 门禁判定（设计 §3.2 / §4.3 三层判定矩阵）。返回非 null 表示需要处理：
     * <ul>
     *   <li>{@code restricted} 决策（W13 第三态）：角色策略不达标——返回受限决策对象
     *       （不建 challenge，设计 §4.1 结论 9），调用侧走 completeLogin 受限变体。</li>
     *   <li>challenge 决策（一期语义不变）：调用方据此抛 {@code ERR_AUTH_MFA_REQUIRED}。</li>
     *   <li>返回 null 表示放行走 completeLogin。</li>
     * </ul>
     * 一期分支原位原序保留（全局开关 → store 装配 → setting 检查 → 因子等同 → challenge 创建）；
     * 策略评估插入在 store null 检查之后、setting 装载之前（§4.3 伪代码）——无策略部署
     * （maxLevel=0）不可达第三态，一期路径逐字节等价。
     * <ul>
     *   <li>全局开关关闭（{@code nop.auth.mfa.enabled=false}）→ 放行（显式配置门禁，非静默跳过）。</li>
     *   <li>用户未启用 MFA（无 setting 或 status!=enabled 或 mfaType 空）→ 放行（零回归）。</li>
     *   <li>因子等同：短信登录（loginType=5）且 mfaType=sms → 放行（验证码即第二因子，不重复验证）。</li>
     * </ul>
     * <p>
     * W15-impl（设计 §六）：{@code requestHeaders} 增量参数（protected 单模块内签名变更——
     * {@code loginAsync} 传真实 headers、{@code createSessionForUserAsync} 信道路径传 null）。
     * 可信设备豁免分支插入位 = 因子等同之后、challenge 创建之前；进入条件 = headers 非空 且
     * 密码类 loginType（1/2/3/5）且 {@code policy.allowTrustedDevice}（W13 evaluator 复合结果
     * AND 合并——任一策略行 false 即跳过豁免，本方法为首个消费点）且指纹非空 → 查未过期行 →
     * 命中更新 lastUsedAt（<b>不续 expireAt</b>，固定窗口）→ 放行。无记录用户该分支短路
     * （指纹 null 不查库），行为与一期逐字节一致。
     * <p>
     * <b>同构副本裁定（W15）</b>：{@code MfaLoginPolicyServiceImpl.checkMfaForUserName}（OAuth 入口）
     * <b>不加豁免分支</b>——信道路径无 headers 结构性不可达，同步义务以"本裁定 + OAuth 路径
     * 回归断言（有未过期可信设备行仍创建 challenge）"形式履行（W13 登记的双方同步不变式）。
     */
    protected MfaChallengeDecision checkMfaRequired(NopAuthUser user, int loginType,
                                                    Map<String, Object> requestHeaders) {
        if (!CFG_AUTH_MFA_ENABLED.get())
            return null;
        if (mfaChallengeStore == null)
            return null;
        // W13 增量：角色策略评估（§4.3——store null 检查之后、setting 装载之前；evaluator
        // 未装配 = 无策略 = NONE，一期行为）
        RoleMfaPolicy policy = roleMfaPolicyEvaluator == null ? RoleMfaPolicy.NONE
                : roleMfaPolicyEvaluator.evaluateForUser(user.getUserId());
        NopAuthMfaSetting setting = loadMfaSetting(user.getUserId());
        // 第三态（§4.3 矩阵第 4/5 行）：policy>0 且 (!enabled 或 factorLevel(mfaType) < maxLevel)
        // → 受限决策（不建 challenge——结论 9：多验一次弱因子不改变受限结果）
        if (policy.getMaxLevel() > 0) {
            boolean mfaEnabled = setting != null && MFA_STATUS_ENABLED.equals(setting.getStatus())
                    && !StringHelper.isEmpty(setting.getMfaType());
            if (!mfaEnabled || RoleMfaPolicyEvaluator.factorLevel(setting.getMfaType()) < policy.getMaxLevel()) {
                return MfaChallengeDecision.restricted();
            }
        }
        // ===== 一期分支（原位原序） =====
        if (setting == null || !MFA_STATUS_ENABLED.equals(setting.getStatus()))
            return null;
        String mfaType = setting.getMfaType();
        if (StringHelper.isEmpty(mfaType))
            return null;
        // 因子等同（设计 §3.2 / Vision Non-Goals #9）
        if (loginType == LOGIN_TYPE_PHONE_SMS && MFA_TYPE_SMS.equals(mfaType))
            return null;
        // ===== 可信设备豁免（W15-impl，设计 §6.3——因子等同之后、challenge 创建之前） =====
        if (requestHeaders != null && isPasswordLoginType(loginType)
                && policy.isAllowTrustedDevice() && trustedDeviceManager != null) {
            String deviceHash = MfaTrustedDeviceManager.fingerprint(requestHeaders);
            // 短路：无 device-id 不查库（降级正常 MFA，非错误）
            if (deviceHash != null && trustedDeviceManager.isExempted(user.getUserId(), deviceHash)) {
                return null; // 登录级豁免放行（与一期"放行"同路径）
            }
        }
        // challenge 创建（W14-impl 触点①：webauthn 类型经 helper 增量 payload.cryptoChallenge
        // 一次写入；其余类型一期五参语义逐字节等价）
        String challengeToken = MfaChallengeHelper.createLoginChallenge(mfaChallengeStore,
                user.getUserId(), mfaType, loginType, user.getTenantId(), setting.getPhone());
        return new MfaChallengeDecision(challengeToken, mfaType);
    }

    /**
     * 可信设备豁免/登记适用的登录类型判定（设计 §6.1 结论 3）：密码类 loginType 1/2/3/5
     * （用户名/邮箱/手机密码 + 手机验证码）；信道类（SSO 4 / 20-23）与 OAuth 不适用不登记。
     */
    protected static boolean isPasswordLoginType(int loginType) {
        return loginType == AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD
                || loginType == AuthApiConstants.LOGIN_TYPE_EMAIL_PASSWORD
                || loginType == AuthApiConstants.LOGIN_TYPE_PHONE_PASSWORD
                || loginType == AuthApiConstants.LOGIN_TYPE_PHONE_SMS;
    }

    protected NopAuthMfaSetting loadMfaSetting(String userId) {
        if (StringHelper.isEmpty(userId))
            return null;
        return daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
    }

    /**
     * MFA 门禁结果：challenge 决策（challengeToken + mfaType，仅当需要第二因子时非 null）
     * 或受限决策（{@link #restricted()}，W13 第三态——角色策略不达标，不建 challenge）。
     */
    protected static final class MfaChallengeDecision {
        final String challengeToken;
        final String mfaType;
        final boolean restricted;

        MfaChallengeDecision(String challengeToken, String mfaType) {
            this.challengeToken = challengeToken;
            this.mfaType = mfaType;
            this.restricted = false;
        }

        private MfaChallengeDecision(boolean restricted) {
            this.challengeToken = null;
            this.mfaType = null;
            this.restricted = restricted;
        }

        /** 受限决策（W13 第三态，设计 §4.3 结论 9：不建 challenge）。 */
        static MfaChallengeDecision restricted() {
            return new MfaChallengeDecision(true);
        }

        boolean isRestricted() {
            return restricted;
        }
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
            LOG.debug("nop.auth.verify-code-mismatch:code={},cached={}", request.getVerifyCode(), cachedCode);
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

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("loginType", request.getLoginType());
        map.put("principalId", request.getPrincipalId());
        audit.setRequestData(JSON.stringify(map));

        if (failCount > 1) {
            Map<String, Object> result = new HashMap<>();
            result.put("failCount", failCount);
            audit.setRequestData(JSON.stringify(result));
        }

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
        LOG.debug("nop.login.generate-verify-code:{}", verifyCode.getCode());
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