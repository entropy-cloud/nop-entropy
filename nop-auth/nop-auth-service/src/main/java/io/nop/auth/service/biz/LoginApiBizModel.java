/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.AccessCodeRequest;
import io.nop.auth.api.messages.AccessTokenRequest;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.LoginUserInfo;
import io.nop.auth.api.messages.LogoutRequest;
import io.nop.auth.api.messages.MfaVerifyOperationRequest;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.api.messages.RefreshTokenRequest;
import io.nop.auth.api.messages.WebAuthnRequestOptions;
import io.nop.auth.core.login.AuthToken;
import io.nop.auth.core.login.ILoginService;
import io.nop.auth.core.spi.ILoginSpi;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.NopAuthConstants;
import io.nop.auth.service.NopAuthErrors;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.auth.service.mfa.MfaChallengeHelper;
import io.nop.auth.service.mfa.MfaFactorVerifier;
import io.nop.auth.service.mfa.OperationMfaCheckerImpl;
import io.nop.auth.service.mfa.WebAuthnAuthenticator;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.unittest.VarCollector;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.core.AuthCoreErrors.ERR_AUTH_USER_NOT_LOGIN;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_WEBAUTHN;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_FAIL;

/**
 * 两种访问方式: 1. GraphQL : LoginApi__login 2. REST: /r/LoginApi__login
 */
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @Inject
    ILoginService loginService;

    /**
     * MFA challenge store（操作级 mfaVerifyOperation 用）。仅 {@code nop.auth.operation-mfa.enabled=true}
     * 时被访问；与 LoginServiceImpl 共享同一装配（nopActiveMfaChallengeStore）。
     */
    @Inject
    @Nullable
    MfaChallengeStore mfaChallengeStore;

    /** 短信验证码 store（W13：登记通道 proof 码校验，key=proof:{userId}）。 */
    @Inject
    @Nullable
    io.nop.auth.core.mfa.store.SmsCodeStore smsCodeStore;

    /** 共享因子校验组件（W12-impl：操作级验证经组件，TOTP 窗口统一推进）。 */
    @Inject
    @Nullable
    MfaFactorVerifier mfaFactorVerifier;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    @Nullable
    IAuditService auditService;

    /**
     * WebAuthn 验证器组件（W14-impl，设计 §5.3.2）：webauthnAuthOptions 的 requestOptions
     * 构造。断言验证经 {@link MfaFactorVerifier}（统一载体五参重载）。
     */
    @Inject
    @Nullable
    WebAuthnAuthenticator webAuthnAuthenticator;

    @BizMutation("login")
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> loginAsync(@RequestBean LoginRequest request, IServiceContext context) {
        return loginService.loginAsync(request, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizMutation
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<Void> logoutAsync(@RequestBean LogoutRequest request, IServiceContext context) {
        return loginService.logoutAsync(AuthApiConstants.LOGOUT_TYPE_MANUAL, request);
    }

    @BizQuery
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<LoginResult> getLoginResultAsync(@RequestBean AccessCodeRequest request,
                                                            IServiceContext context) {
        AuthToken authToken = loginService.parseAccessCode(request.getAccessCode());
        return loginService.getUserContextAsync(authToken, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizQuery
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<LoginUserInfo> getLoginUserInfoAsync(@RequestBean AccessTokenRequest request,
                                                                IServiceContext context) {
        AuthToken authToken = loginService.parseAuthToken(request.getAccessToken());
        return loginService.getUserContextAsync(authToken, context.getRequestHeaders()).thenApply(loginService::getUserInfo);
    }

    @BizMutation
    @Auth(publicAccess = true)
    @Override
    public CompletionStage<LoginResult> refreshTokenAsync(@RequestBean RefreshTokenRequest request,
                                                          IServiceContext context) {
        AuthToken token = loginService.parseRefreshToken(request.getRefreshToken());
        return loginService.getUserContextAsync(token, context.getRequestHeaders()).thenApply(this::buildLoginResult);
    }

    @BizQuery
    @Auth(publicAccess = true)
    public String generateVerifyCode(@Name("verifySecret") String verifySecret) {
        return loginService.generateVerifyCode(verifySecret);
    }

    // ===================== MFA / SMS 端点（设计 §3.6，W5） =====================

    /**
     * 发送登录短信验证码（公开访问，设计 §3.3 / §3.6）。
     * 含手机号/IP 双维度限流 + 防枚举统一响应。
     */
    @BizMutation
    @Auth(publicAccess = true)
    public void sendSmsCode(@Name("phone") String phone, IServiceContext context) {
        loginService.sendSmsCode(phone, extractClientIp(context));
    }

    /**
     * MFA 第二因子短信验证码重发（公开访问，设计 §3.6）。
     * 凭 challengeToken 服务端取号，challenge 已消费/作废返回 CHALLENGE_EXPIRED。
     */
    @BizMutation
    @Auth(publicAccess = true)
    public void sendMfaCode(@Name("challengeToken") String challengeToken, IServiceContext context) {
        loginService.sendMfaCode(challengeToken, extractClientIp(context));
    }

    /**
     * 第二因子验证（公开访问，设计 §3.2 / §3.6）。成功返回 {@link LoginResult}：
     * 密码类 loginType 签发 accessToken；信道类 loginType 签发 accessCode。
     */
    @BizMutation
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> mfaVerifyAsync(@RequestBean MfaVerifyRequest request, IServiceContext context) {
        return loginService.mfaVerifyAsync(request, context.getRequestHeaders()).thenApply(ctx -> {
            // 信道类 loginType：accessCode 由 completeMfaLogin stashed 到 attr，只返回 accessCode（不签发 accessToken）
            Object accessCode = ctx.getAttr(LoginServiceImpl.ATTR_MFA_ACCESS_CODE);
            if (accessCode instanceof String) {
                LoginResult result = new LoginResult();
                result.setAccessCode((String) accessCode);
                result.setUserInfo(loginService.getUserInfo(ctx));
                return result;
            }
            // 密码类 loginType：normal path（签发 accessToken）
            return buildLoginResult(ctx);
        });
    }

    /**
     * WebAuthn 断言 options 读取端点（W14-impl，设计 §5.3.2 认证 ceremony；公开访问——
     * 对齐一期 mfaVerify 的 publicAccess 先例，登录期无会话）。
     * <p>
     * 任意 scene 的 webauthn challenge 通用：
     * <ul>
     *   <li><b>scene=login</b>：公开可访问（登录期无会话）。</li>
     *   <li><b>scene 非 login</b>（operation/webauthn-unbind 等）：需登录态且
     *       payload.sessionId==当前会话（防跨会话读取他人 options）。</li>
     *   <li>requestOptions.challenge = payload.cryptoChallenge <b>只读复用</b>（不更新——
     *       payload 一次写入契约，多次取 options 幂等）；allowCredentials = 该用户 enabled
     *       credentials。</li>
     *   <li>非 webauthn 类型的 challenge 显式拒绝（无 cryptoChallenge 可读，fail-closed）。</li>
     * </ul>
     */
    @BizQuery
    @Auth(publicAccess = true)
    public WebAuthnRequestOptions webauthnAuthOptions(@Name("challengeToken") String challengeToken,
                                                      IServiceContext context) {
        if (StringHelper.isEmpty(challengeToken) || mfaChallengeStore == null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        MfaChallenge c = mfaChallengeStore.peek(challengeToken);
        if (c == null || !MFA_TYPE_WEBAUTHN.equals(c.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }

        // 非 login 场景：需登录态 + 同会话（login 场景公开——登录期无会话）
        if (!MfaChallenge.SCENE_LOGIN.equals(c.getScene()) && c.getScene() != null) {
            IUserContext userContext = context.getUserContext();
            String payloadSessionId = MfaChallengeHelper.sessionIdOf(c);
            if (userContext == null || StringHelper.isEmpty(userContext.getUserId())
                    || StringHelper.isEmpty(payloadSessionId)
                    || !payloadSessionId.equals(userContext.getSessionId())) {
                throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
            }
        }

        // cryptoChallenge 只读复用（一次写入契约——无后置更新原语）
        String cryptoChallenge = MfaChallengeHelper.cryptoChallengeOf(c);
        if (StringHelper.isEmpty(cryptoChallenge) || webAuthnAuthenticator == null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        return webAuthnAuthenticator.buildRequestOptions(cryptoChallenge, enabledCredentialIds(c.getUserId()));
    }

    /** 该用户 enabled credentials 的 credentialId 列表（allowCredentials）。 */
    private List<String> enabledCredentialIds(String userId) {
        IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential example = dao.newEntity();
        example.setUserId(userId);
        example.setStatus(MFA_STATUS_ENABLED);
        List<NopAuthMfaCredential> found = dao.findAllByExample(example);
        List<String> ids = new ArrayList<>(found.size());
        for (NopAuthMfaCredential credential : found) {
            ids.add(credential.getCredentialId());
        }
        return ids;
    }

    /**
     * 操作级 MFA 第二因子验证（设计 §3.3 验证端点，W12-impl）。
     * <p>
     * <b>需登录态</b>（本 BizModel 首个非 publicAccess action——省略 @Auth 即默认 permission，
     * 登录会话内可达）：操作级 challenge 产生自登录会话内，验证必须同会话（防跨会话重放）。
     * <p>
     * 流程：peek + scene==operation + 同会话校验 + 已验证票拒绝重复验证（票不续命）+
     * setting 复核（runWithTenant 内 status==enabled 且 mfaType 一致）+ 因子校验（共享
     * {@link MfaFactorVerifier}，失败计数超限作废）+ {@code markVerified} 一次性迁移
     * （失败按过期处理=并发已验证）。
     * <p>
     * <b>成功不签发任何凭证</b>（无 accessToken/无 completeLogin/无会话变更）——与登录级
     * {@code mfaVerify}（成功 = completeLogin）的本质区别；客户端凭同一 challengeToken
     * 携 {@code X-Nop-Op-Mfa-Token} 头重试原操作。不接受恢复码（{@link MfaVerifyOperationRequest}
     * 无 recoveryCode 字段，恢复码是登录恢复通道）。
     */
    @BizMutation
    public void mfaVerifyOperation(@RequestBean MfaVerifyOperationRequest request, IServiceContext context) {
        String challengeToken = request.getChallengeToken();
        if (StringHelper.isEmpty(challengeToken))
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);

        IUserContext userContext = context.getUserContext();
        if (userContext == null || StringHelper.isEmpty(userContext.getUserId()))
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);

        // 1. peek + scene 校验（不消费、不刷新 TTL）
        MfaChallenge c = mfaChallengeStore == null ? null : mfaChallengeStore.peek(challengeToken);
        if (c == null || !MfaChallenge.SCENE_OPERATION.equals(c.getScene()))
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);

        // 2. 同会话校验（票与验证都绑定发起会话，防跨会话重放）
        String payloadSessionId = payloadSessionId(c);
        if (payloadSessionId == null || !payloadSessionId.equals(userContext.getSessionId()))
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);

        // 3. 已是票：拒绝重复验证（票不续命）
        if (c.getVerifiedAt() != null)
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);

        // 4. setting 复核（runWithTenant 镜像登录级复核语义：换绑必经 disabled 态，
        //    状态复核即作废换绑前签发的 challenge；mfaType 硬校验防状态机演进破坏隐式依赖）
        NopAuthMfaSetting setting = ContextProvider.runWithTenant(c.getTenantId(), () ->
                daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(c.getUserId()));
        if (setting == null || !NopAuthConstants.MFA_STATUS_ENABLED.equals(setting.getStatus())) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }
        // mfaType 硬校验（防未来状态机演进破坏"状态复核即作废旧票"的隐式依赖）
        if (!StringHelper.isEmpty(c.getMfaType()) && !c.getMfaType().equals(setting.getMfaType())) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }

        // 5. 因子校验（共享组件：TOTP 窗口统一推进；失败计数超限作废 challenge）。
        //    W14-impl 统一载体五参重载：webauthn 用户凭 assertion 验证（cryptoChallenge 取自
        //    challenge payload——拦截器创建处增量），code 参数忽略；其余类型 assertion 忽略
        boolean ok;
        try {
            ok = mfaFactorVerifier.verify(setting, c.getMfaType(), request.getCode(), request.getAssertion(), c);
        } catch (NopException e) {
            auditOperationVerify(operationOf(c), userContext, false);
            throw e;
        }
        if (!ok) {
            int failCount = mfaChallengeStore.incrFailCount(challengeToken);
            if (failCount >= CFG_AUTH_MFA_MAX_ATTEMPTS.get()) {
                mfaChallengeStore.consume(challengeToken);
            }
            auditOperationVerify(operationOf(c), userContext, false);
            throw new NopException(ERR_AUTH_MFA_FAIL).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }

        // 6. 一次性状态迁移（失败=并发已验证，按过期处理；票不续命）
        if (!mfaChallengeStore.markVerified(challengeToken)) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, challengeToken);
        }

        auditOperationVerify(operationOf(c), userContext, true);
        // 成功出口不签发任何凭证（无 completeLogin/无 token/无会话变更）
    }

    private static String payloadSessionId(MfaChallenge c) {
        if (StringHelper.isEmpty(c.getPayload()))
            return null;
        Map<String, Object> payload = JsonTool.parseMap(c.getPayload());
        Object sessionId = payload == null ? null : payload.get(OperationMfaCheckerImpl.PAYLOAD_SESSION_ID);
        return sessionId == null ? null : sessionId.toString();
    }

    // ===================== 登记通道验证（W13-impl，设计 §4.3 防 enrollment attack） =====================

    /**
     * 登记通道验证端点（受限会话引导流，<b>需登录态</b>——受限会话内 bindMfa 的前置门槛）。
     * <p>
     * 校验 {@code proof:{userId}} 短信码（服务端解析用户登记 phone，W13 仅 phone、不接受
     * 客户端指定）→ 成功即创建 scene=channel-proof <b>已验证票</b>（短 TTL——复用
     * {@code op-ticket-expire-seconds} 票窗口语义）并返回票 token；客户端携该 token 调
     * bindMfa（proof 参数），bindMfa 校验后一次性消费。通道为空（无登记 phone）抛
     * {@code ERR_AUTH_MFA_NO_RECOVERY_CHANNEL}（无法自助脱困，管理员介入）。
     * <p>
     * 威胁模型（设计 §4.3）：无此防御时仅持有密码的攻击者进入受限会话后可 bindMfa(totp)
     * 拿到 provisioning URI 绑定自己的验证器 → 重新登录 → 完全接管（经典 enrollment
     * attack）；登记通道 OTP 把门槛提升到"密码 + 登记通道"。
     */
    @BizMutation
    public String verifyChannelProof(@Name("code") String code, IServiceContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null || StringHelper.isEmpty(userContext.getUserId()))
            throw new NopException(ERR_AUTH_USER_NOT_LOGIN);
        String userId = userContext.getUserId();
        if (StringHelper.isEmpty(code))
            throw new NopException(ERR_AUTH_MFA_FAIL);

        // 服务端解析登记通道（W13 仅 phone；不接受客户端指定）
        NopAuthUser user = ContextProvider.runWithTenant(userContext.getTenantId(),
                () -> daoProvider.daoFor(NopAuthUser.class).getEntityById(userId));
        String phone = user == null ? null : user.getPhone();
        if (StringHelper.isEmpty(phone)) {
            throw new NopException(NopAuthErrors.ERR_AUTH_MFA_NO_RECOVERY_CHANNEL).param("userId", userId);
        }

        if (smsCodeStore == null || mfaChallengeStore == null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                    .param(ARG_CHALLENGE_TOKEN, "proof")
                    .param("msg", "MFA stores are not configured; channel proof is unavailable");
        }

        // 校验 proof 码（key=proof:{userId}，一次性原子消费 + 内部失败计数）
        io.nop.auth.core.mfa.store.CodeVerifyResult r = smsCodeStore.verify(
                NopAuthConstants.SMS_KEY_PROOF + userId, code);
        if (r == io.nop.auth.core.mfa.store.CodeVerifyResult.EXPIRED) {
            throw new NopException(NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED);
        }
        if (r != io.nop.auth.core.mfa.store.CodeVerifyResult.VALID) {
            throw new NopException(ERR_AUTH_MFA_FAIL);
        }

        // 创建 scene=channel-proof 已验证票（markVerified 即转票；票窗口 = op-ticket-expire-seconds）
        String token = mfaChallengeStore.create(MfaChallenge.SCENE_CHANNEL_PROOF, userId, null,
                io.nop.auth.service.mfa.OperationMfaCheckerImpl.LOGIN_TYPE_OPERATION,
                userContext.getTenantId(), phone, null);
        if (!mfaChallengeStore.markVerified(token)) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, token);
        }

        auditChannelProof("mfa:channel-proof-verified", userContext, true);
        return token;
    }

    /** 登记通道验证审计事件（验证成功/失败），落 NopAuthOpLog。 */
    private void auditChannelProof(String description, IUserContext userContext, boolean success) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation("LoginApi__verifyChannelProof");
        audit.setDescription(description);
        audit.setResultStatus(success ? 200 : 400);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userContext.getUserId());
        audit.setUserName(userContext.getUserName());
        audit.setSessionId(userContext.getSessionId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", success ? "channel-proof-verified" : "channel-proof-fail");
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    private static String operationOf(MfaChallenge c) {
        if (StringHelper.isEmpty(c.getPayload()))
            return null;
        Map<String, Object> payload = JsonTool.parseMap(c.getPayload());
        Object operation = payload == null ? null : payload.get(OperationMfaCheckerImpl.PAYLOAD_OPERATION);
        return operation == null ? null : operation.toString();
    }

    /** 审计事件（操作级验证成功/失败），记录 operation 与 sessionId。 */
    private void auditOperationVerify(String operation, IUserContext userContext, boolean success) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation(operation);
        audit.setDescription(success ? "operation-mfa:verify-ok" : "operation-mfa:verify-fail");
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userContext.getUserId());
        audit.setUserName(userContext.getUserName());
        audit.setSessionId(userContext.getSessionId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", success ? "op-mfa-verify-success" : "op-mfa-verify-fail");
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    /**
     * 从请求头提取客户端 IP（用于短信发送 IP 维度限流）。
     * 优先 X-Forwarded-For / X-Real-IP，取不到返回 null（IP 限流为次要防线）。
     */
    protected String extractClientIp(IServiceContext context) {
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

    protected LoginResult buildLoginResult(IUserContext userContext) {
        if (userContext == null)
            throw new NopException(NopAuthErrors.ERR_AUTH_SESSION_EXPIRED);

        LoginResult result = new LoginResult();
        String accessToken = userContext.getAccessToken();
        result.setAccessToken(accessToken);

        // VarCollector 是可选的自测支持设施，AutoTestCase 结束后会将其置空，生产代码必须容忍其缺失
        VarCollector varCollector = VarCollector.instance();
        if (varCollector != null) {
            varCollector.collectVar("accessToken", accessToken);
        }

        String refreshToken = userContext.getRefreshToken();
        result.setRefreshToken(refreshToken);

        AuthToken authToken = loginService.parseAuthToken(accessToken);
        // 返回时间为秒
        result.setExpiresIn(authToken.getExpireSeconds());

        result.setUserInfo(loginService.getUserInfo(userContext));

        // W13-impl：受限会话标志回填（受限签发的会话经 login/getLoginResult/refresh 均可见，
        // 前端据此渲染受限引导页；正常登录缺省不出现）
        if (userContext.isMfaRestricted()) {
            result.setMfaRestricted(Boolean.TRUE);
        }

        if (varCollector != null) {
            varCollector.collectVar("refreshToken", refreshToken);
        }

        return result;
    }
}