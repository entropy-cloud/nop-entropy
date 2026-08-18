/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.LoginResult;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.mock.CapturingSmsSender;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.auth.service.NopAuthErrors.ARG_CHANNEL;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_LEVEL;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ARG_OPERATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W13-impl Phase 3 E2E 全链（真实容器组件，Anti-Hollow）：受限会话拦截矩阵 +
 * 登记通道 proof 全链（防 enrollment attack）+ confirmMfa 防降级 + 重新登录完整两阶段。
 * <p>
 * 拦截矩阵（设计 §4.3/§3.3 受限分支）：query 放行 / 白名单 mutation 放行（含 @MfaRequired
 * 白名单短路——不再叠加操作级 challenge）/ publicAccess mutation 放行 / 其余 mutation 抛
 * {@code ERR_AUTH_MFA_RESTRICTED_SESSION}（未标注 @MfaRequired 的 mutation 同样被拦截——
 * executor/checker 双触点接线专项断言）/ {@code operation-mfa.enabled=false} 时受限拦截仍
 * 生效（伪代码序钉定：受限分支前置于 enabled 门）。
 * <p>
 * 无静默跳过：受限拒绝/proof 缺失/通道为空/因子过弱各分支显式抛错（专项测试）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMfaRestrictedSessionE2E extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";
    private static final String POLICY_ROLE = "e2e-restricted-role";

    private static Boolean originalMfaEnabled;

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    MfaChallengeStore mfaChallengeStore;

    @Inject
    SmsCodeStore smsCodeStore;

    @Inject
    TOTPAuthenticator totpAuthenticator;

    @Inject
    IPasswordEncoder passwordEncoder;

    @Inject
    LoginApiBizModel loginApiBizModel;

    /** 容器装配的 NopAuthUserBizModel（按 bean id 解析——按类型注入存在多 bean 歧义）。 */
    private NopAuthUserBizModel userBizModel;

    private NopAuthUserBizModel userBizModel() {
        if (userBizModel == null) {
            Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean(
                    "io.nop.auth.service.entity.NopAuthUserBizModel");
            if (bean == null)
                throw new IllegalStateException("NopAuthUserBizModel bean not found in container");
            userBizModel = (NopAuthUserBizModel) bean;
        }
        return userBizModel;
    }

    /** 容器装配的 checker bean（接线验证与 errorParams 直调断言——RPC 路径错误经 ApiResponse 包装）。 */
    private io.nop.auth.api.mfa.IOperationMfaChecker checker() {
        Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean("nopOperationMfaChecker");
        if (bean == null)
            throw new IllegalStateException("nopOperationMfaChecker bean not found in container");
        return (io.nop.auth.api.mfa.IOperationMfaChecker) bean;
    }

    private String base32Secret;

    @BeforeAll
    static void enableMfa() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
        // 操作级开关保持缺省 false——受限拦截不受其门控（专项断言依赖此缺省）
    }

    @AfterAll
    static void restoreMfa() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
    }

    @AfterEach
    void clearUserContext() {
        IUserContext.set(null);
        CapturingSmsSender.reset();
    }

    // ===================== 拦截矩阵 =====================

    @Test
    public void testRestrictedQueryAllowed() {
        String userId = "rs-query-user";
        saveUser(userId, "13900000001");

        // query 放行（getMfaStatus 本身也在白名单——双保险；query 类经 executor 侧放行）
        ApiResponse<?> resp = rpcQuery("NopAuthUser__getMfaStatus", Map.of(), restrictedCtx(userId, "sess-query"));
        assertTrue(resp.isOk(), "restricted session must be allowed to run queries: " + resp.getErrors());
    }

    @Test
    public void testRestrictedNonWhitelistMutationRejectedIncludingUnannotated() {
        String userId = "rs-reject-user";
        saveUser(userId, "13900000002");

        // 未标注 @MfaRequired 的 mutation（enableUser）同样被拦截——双触点接线专项断言
        GraphQLResponseBean doc = gqlMutation("NopAuthUser__enableUser", "userId:\"" + userId + "\"",
                restrictedCtx(userId, "sess-reject"));
        assertTrue(doc.hasError());
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), doc.getErrorCode());

        // RPC 单操作路径同样拒绝（错误经 ApiResponse 包装——code = errorCode）
        ApiResponse<?> rejected = rpcMutation("NopAuthUser__resetUserMfa", Map.of("userId", userId),
                restrictedCtx(userId, "sess-reject2"), null);
        assertFalse(rejected.isOk(), "RPC single-operation path must reject non-whitelist mutation");
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), rejected.getCode(),
                "unexpected error: " + rejected.getCode());
        // errorParams.operation（bizObjName__action）经 checker 直调钉定（W12 先例——响应层只暴露 errorCode）
        NopException ex = assertThrows(NopException.class, () ->
                checker().check("NopAuthUser__resetUserMfa", restrictedCtx(userId, "sess-reject3"), null));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), ex.getErrorCode());
        assertEquals("NopAuthUser__resetUserMfa", ex.getParam(ARG_OPERATION),
                "errorParams.operation（bizObjName__action）");

        // 受限拒绝审计落 NopAuthOpLog
        assertTrue(pollAuditDescriptions("NopAuthUser__resetUserMfa")
                        .stream().anyMatch(d -> d.contains("mfa-restricted-rejected")),
                "restricted rejection must be audited");
    }

    @Test
    public void testRestrictedWhitelistMutationShortCircuitsOperationMfa() {
        String userId = "rs-whitelist-user";
        saveUser(userId, "13900000003");
        UserContextImpl ctx = restrictedCtx(userId, "sess-whitelist");

        // unbindMfa 自身标注 @MfaRequired 且在白名单：受限分支命中白名单即短路——
        // 不再触发操作级 challenge，动作本体执行（未启用 MFA → NOT_ENABLED，非 RESTRICTED/
        // OPERATION_MFA——操作级与受限分支共存路径专项钉定）。开 operation-mfa 开关使
        // @MfaRequired 判定可达（缺省 false 时短路不可分辨）
        IConfigProvider provider = AppConfig.getConfigProvider();
        Boolean original = provider.getConfigValue("nop.auth.operation-mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
        try {
            // checker 直调：白名单命中即静默返回（短路，无操作级 challenge 创建）
            assertDoesNotThrow(() -> checker().check("NopAuthUser__unbindMfa", ctx, null),
                    "whitelisted @MfaRequired action must short-circuit (no operation-level challenge)");

            // RPC 全链：动作本体执行（NOT_ENABLED 来自动作体），非两个拦截错误码
            ApiResponse<?> resp = rpcMutation("NopAuthUser__unbindMfa", Map.of("code", "000000"), ctx, null);
            assertFalse(resp.isOk());
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_NOT_ENABLED.getErrorCode(), resp.getCode(),
                    "whitelisted @MfaRequired action must short-circuit (action body error, not interception)");
            assertNotEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), resp.getCode());
            assertNotEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), resp.getCode());
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled",
                    original != null ? original : Boolean.FALSE);
        }
    }

    @Test
    public void testRestrictedPublicAccessMutationAllowed() {
        String userId = "rs-public-user";
        saveUser(userId, "13900000004");

        // publicAccess mutation（token 刷新——会话基建类，设计 §4.3 白名单语义）放行：
        // 受限签发的 refreshToken 在受限 ctx 下可刷新（防受限会话中途 token 过期死锁）。
        // 操作全名 = LoginApi__refreshToken（builder 剥除方法名 Async 尾缀）
        LoginResult first = doPasswordLogin(userId);
        assertEquals(Boolean.TRUE, first.getMfaRestricted());
        assertNotNull(first.getRefreshToken(), "restricted login must issue refreshToken");
        ApiResponse<?> resp = rpcMutation("LoginApi__refreshToken",
                Map.of("refreshToken", first.getRefreshToken()),
                restrictedCtx(userId, "sess-public"), null);
        assertTrue(resp.isOk(), "publicAccess mutation must pass the restricted gate: " + resp.getCode());
    }

    @Test
    public void testRestrictedInterceptionNotGatedByOperationMfaSwitch() {
        String userId = "rs-switch-user";
        saveUser(userId, "13900000005");
        IConfigProvider provider = AppConfig.getConfigProvider();
        Boolean original = provider.getConfigValue("nop.auth.operation-mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", false);
        try {
            GraphQLResponseBean doc = gqlMutation("NopAuthUser__enableUser", "userId:\"" + userId + "\"",
                    restrictedCtx(userId, "sess-switch"));
            assertTrue(doc.hasError());
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), doc.getErrorCode(),
                    "restricted interception must NOT be gated by nop.auth.operation-mfa.enabled（伪代码序钉定）");
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled",
                    original != null ? original : Boolean.FALSE);
        }
    }

    @Test
    public void testUnrestrictedSessionNotAffected() {
        // 零回归：非受限会话 + operation-mfa.enabled=false（缺省）→ 未标注 mutation 正常执行
        String userId = "rs-normal-user";
        saveUser(userId, "13900000006");
        ApiResponse<?> resp = rpcMutation("NopAuthUser__enableUser", Map.of("userId", userId),
                adminCtx(userId, "sess-normal"), null);
        assertTrue(resp.isOk(), "normal session must not be affected by the restricted branch: " + resp.getErrors());
    }

    // ===================== 登记通道 proof 全链 =====================

    /**
     * 全链（plan Phase 3 Proof 主链）：受限登录 → bindMfa 被阻发码（脱敏提示）→ 60s 限流断言 →
     * verifyChannelProof（错码/正确码）→ 携 proof 票 bindMfa(totp) 过（enrollment attack 对抗：
     * 无票不可达 provisioning URI）→ 票一次性（重放拒绝）→ confirmMfa 过 → 登出 → 重新登录
     * 完整两阶段（challenge → mfaVerify → 完整会话，非受限）。
     */
    @Test
    public void testChannelProofFullChainBindStrongFactorAndReLogin() {
        String userId = "rs-proof-user";
        saveUser(userId, "13911100001");
        withSendInterval(0, () -> {
            // 1. 真实登录 → 受限签发
            LoginResult first = doPasswordLogin(userId);
            assertEquals(Boolean.TRUE, first.getMfaRestricted(), "policy level 2 + not enabled => restricted");

            UserContextImpl rctx = restrictedCtx(userId, "sess-proof");

            // 2. bindMfa 无 proof → 发码 + CHANNEL_PROOF_REQUIRED（通道脱敏提示：尾 4 位，不含完整号码）
            NopException noProof = assertThrows(NopException.class, () ->
                    bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), noProof.getErrorCode());
            String channel = String.valueOf(noProof.getParam(ARG_CHANNEL));
            assertTrue(channel.contains("0001") && !channel.contains("139111"),
                    "channel hint must be masked (tail 4 digits only): " + channel);
            assertNotNull(CapturingSmsSender.lastCode(), "proof code must be sent to the registered phone");
            String proofCode = CapturingSmsSender.lastCode();

            // 3. verifyChannelProof 错码 → MFA_FAIL；正确码 → proof 票
            NopException wrong = assertThrows(NopException.class, () ->
                    verifyChannelProofAs(rctx, "000000"));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), wrong.getErrorCode());
            String ticket = verifyChannelProofAs(rctx, proofCode);
            assertNotNull(ticket);
            MfaChallenge c = mfaChallengeStore.peek(ticket);
            assertNotNull(c);
            assertEquals(MfaChallenge.SCENE_CHANNEL_PROOF, c.getScene());
            assertNotNull(c.getVerifiedAt(), "proof ticket must be a verified ticket（已验证票）");

            // 4. 携 proof 票 bindMfa(totp) 通过（provisioning URI 可达——合法引导流）
            MfaBindResult bind = bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, ticket);
            assertNotNull(bind.getProvisioningUri(), "with a valid proof ticket bindMfa must proceed");
            base32Secret = extractSecretFromUri(bind.getProvisioningUri());

            // 5. 票一次性：重放同一张票 → 重新发码 + PROOF_REQUIRED（不静默放行）
            NopException replay = assertThrows(NopException.class, () ->
                    bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, ticket));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), replay.getErrorCode(),
                    "consumed proof ticket must not authorize a second bind (一次性)");
            assertNotNull(CapturingSmsSender.lastCode());

            // 6. confirmMfa（强因子 totp，level 2 ≥ 策略 2 → 通过）
            String confirmCode = computeTotpCode(base32Secret);
            List<String> recoveryCodes = confirmMfaAs(rctx, bind.getBindToken(), confirmCode);
            assertEquals(10, recoveryCodes.size());
            assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, getSetting(userId).getStatus());

            // 7. 受限会话不原位升级（设计 §4.1 结论 7）：受限 ctx 的非白名单 mutation 仍被拒
            ApiResponse<?> still = rpcMutation("NopAuthUser__enableUser", Map.of("userId", userId), rctx, null);
            assertFalse(still.isOk());
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_RESTRICTED_SESSION.getErrorCode(), still.getCode(),
                    "restricted session must NOT be upgraded in place after confirmMfa");

            // 8. 登出 → 重新登录完整两阶段：challenge 拦截 → mfaVerify(totp) → 完整会话（非受限）
            NopException challenge = assertThrows(NopException.class, () -> doPasswordLogin(userId));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), challenge.getErrorCode());
            String challengeToken = String.valueOf(challenge.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN));

            // 下一个 TOTP 窗口码（confirm 已消费当前窗口——防重放）
            NopAuthMfaSetting enabled = getSetting(userId);
            long nextWindow = (enabled.getLastVerifiedWindow() == null ? 0 : enabled.getLastVerifiedWindow()) + 1;
            String verifyCode = computeTotpAt(base32Secret, nextWindow * TOTPAuthenticator.PERIOD_SECONDS * 1000L);
            LoginResult full = doMfaVerify(challengeToken, verifyCode);
            assertNotNull(full.getAccessToken(), "full two-phase login must yield accessToken");
            assertNull(full.getMfaRestricted(), "post-upgrade relogin must be a full (unrestricted) session");

            // proof 发码与验证审计落 NopAuthOpLog
            assertTrue(pollAuditDescriptions("NopAuthUser__bindMfa")
                            .stream().anyMatch(d -> d.contains("channel-proof-sent")),
                    "channel proof send must be audited");
            assertTrue(pollAuditDescriptions("LoginApi__verifyChannelProof")
                            .stream().anyMatch(d -> d.contains("channel-proof-verified")),
                    "channel proof verify must be audited");
        });
    }

    /** 60s 发码间隔限流断言（防受限会话内 proof 码轰炸受害者登记手机）。 */
    @Test
    public void testProofRateLimitInterval() {
        String userId = "rs-ratelimit-user";
        saveUser(userId, "13911100002");
        UserContextImpl rctx = restrictedCtx(userId, "sess-ratelimit");

        // 第一次：发码 + PROOF_REQUIRED
        assertThrows(NopException.class, () -> bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null));
        // 60s 内第二次（缺省 send-interval-seconds=60）→ RATE_LIMITED
        NopException limited = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null));
        assertEquals(NopAuthErrors.ERR_AUTH_SMS_RATE_LIMITED.getErrorCode(), limited.getErrorCode(),
                "proof send must respect the 60s interval (sendMfaCode 限流先例)");
    }

    /** 通道为空（无登记 phone）→ NO_RECOVERY_CHANNEL（无法自助脱困，管理员介入）。 */
    @Test
    public void testNoRecoveryChannelWhenNoPhone() {
        String userId = "rs-nophone-user";
        saveUser(userId, null);
        UserContextImpl rctx = restrictedCtx(userId, "sess-nophone");

        NopException ex = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_NO_RECOVERY_CHANNEL.getErrorCode(), ex.getErrorCode());
    }

    /** confirmMfa 防降级：绑定 sms（level 1 < 策略 2）→ confirm 被拒（FACTOR_TOO_WEAK），setting 不生效。 */
    @Test
    public void testConfirmMfaFactorTooWeakRejected() {
        String userId = "rs-tooweak-user";
        saveUser(userId, "13911100003");
        withSendInterval(0, () -> {
            UserContextImpl rctx = restrictedCtx(userId, "sess-tooweak");

            // proof → bind sms（弱因子）
            assertThrows(NopException.class, () -> bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_SMS, null));
            String ticket = verifyChannelProofAs(rctx, CapturingSmsSender.lastCode());
            MfaBindResult bind = bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_SMS, ticket);
            assertNotNull(bind.getBindToken());

            // bindSms 已向登记手机发绑定码（key=mfa:userId）；用该码 confirm → FACTOR_TOO_WEAK
            String smsCode = CapturingSmsSender.lastCode();
            NopException ex = assertThrows(NopException.class, () ->
                    confirmMfaAs(rctx, bind.getBindToken(), smsCode));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_POLICY_FACTOR_TOO_WEAK.getErrorCode(), ex.getErrorCode(),
                    "confirming a factor weaker than role policy must be rejected（防因子降级）");
            assertEquals(NopAuthConstants.MFA_TYPE_SMS, ex.getParam(ARG_MFA_TYPE));
            assertEquals(2, ex.getParam(ARG_MFA_LEVEL), "errorParams.mfaLevel（策略强度下限）");

            // setting 未生效（仍 pending）
            assertEquals(NopAuthConstants.MFA_STATUS_PENDING, getSetting(userId).getStatus());
        });
    }

    /**
     * 已启用弱因子用户的升级路径：受限登录 → unbindMfa（验证当前弱因子——攻击者无因子不可解绑，
     * 白名单放行）→ proof → bind 强因子 → confirm → 重新登录完整两阶段。
     */
    @Test
    public void testWeakFactorUserUnbindThenUpgrade() {
        String userId = "rs-upgrade-user";
        saveUser(userId, "13911100004");
        // sms 已启用（策略生效前注册的弱因子）
        enableSmsDirectly(userId);
        UserContextImpl rctx = restrictedCtx(userId, "sess-upgrade");

        // 1. 受限登录（弱因子 level 1 < 策略 2）
        LoginResult first = doPasswordLogin(userId);
        assertEquals(Boolean.TRUE, first.getMfaRestricted(), "weak factor => restricted");

        // 2. unbindMfa：验证当前弱因子（发码 key=mfa:userId → 验证）→ 解绑成功
        assertThrows(NopException.class, () -> userBizModel().unbindMfa("000000", serviceCtx(rctx)));
        String smsCode = smsCodeStore.send("mfa:" + userId);
        unbindMfaAs(rctx, smsCode);
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, getSetting(userId).getStatus(),
                "unbind with the current factor must succeed（解绑不受策略限制）");

        // 3. proof → bind totp → confirm → 重新登录完整两阶段
        withSendInterval(0, () -> {
            assertThrows(NopException.class, () -> bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null));
            String ticket = verifyChannelProofAs(rctx, CapturingSmsSender.lastCode());
            MfaBindResult bind = bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, ticket);
            base32Secret = extractSecretFromUri(bind.getProvisioningUri());
            confirmMfaAs(rctx, bind.getBindToken(), computeTotpCode(base32Secret));

            NopException challenge = assertThrows(NopException.class, () -> doPasswordLogin(userId));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_REQUIRED.getErrorCode(), challenge.getErrorCode());
            String challengeToken = String.valueOf(challenge.getParam(NopAuthErrors.ARG_CHALLENGE_TOKEN));
            NopAuthMfaSetting enabled = getSetting(userId);
            long nextWindow = (enabled.getLastVerifiedWindow() == null ? 0 : enabled.getLastVerifiedWindow()) + 1;
            LoginResult full = doMfaVerify(challengeToken,
                    computeTotpAt(base32Secret, nextWindow * TOTPAuthenticator.PERIOD_SECONDS * 1000L));
            assertNull(full.getMfaRestricted(), "upgraded user relogin must be a full session");
        });
    }

    // ===================== Helpers: biz 调用（受限 ctx） =====================

    // 直调 biz 动作统一包在 ormTemplate.runInSession 内（对齐 TestMfaRestrictedDaoCache 先例）：
    // buildUserContext/RoleMfaPolicyEvaluator 惰性装载角色集合必须发生在同一 ORM 会话内，
    // 否则脱离会话的 lazy collection 触发 nop.err.orm.session-closed

    private MfaBindResult bindMfaAs(UserContextImpl ctx, String mfaType, String proof) {
        IUserContext.set(ctx);
        try {
            return ormTemplate.runInSession(s -> userBizModel().bindMfa(mfaType, proof, serviceCtx(ctx)));
        } finally {
            IUserContext.set(null);
        }
    }

    private String verifyChannelProofAs(UserContextImpl ctx, String code) {
        IUserContext.set(ctx);
        try {
            return ormTemplate.runInSession(s -> loginApiBizModel.verifyChannelProof(code, serviceCtx(ctx)));
        } finally {
            IUserContext.set(null);
        }
    }

    private List<String> confirmMfaAs(UserContextImpl ctx, String bindToken, String code) {
        IUserContext.set(ctx);
        try {
            return ormTemplate.runInSession(s -> userBizModel().confirmMfa(bindToken, code, serviceCtx(ctx)));
        } finally {
            IUserContext.set(null);
        }
    }

    private void unbindMfaAs(UserContextImpl ctx, String code) {
        IUserContext.set(ctx);
        try {
            ormTemplate.runInSession(() -> userBizModel().unbindMfa(code, serviceCtx(ctx)));
        } finally {
            IUserContext.set(null);
        }
    }

    private IServiceContext serviceCtx(UserContextImpl user) {
        ServiceContextImpl c = new ServiceContextImpl();
        c.setUserContext(user);
        return c;
    }

    private void withSendInterval(int seconds, Runnable body) {
        IConfigProvider provider = AppConfig.getConfigProvider();
        Integer original = provider.getConfigValue("nop.auth.sms-code.send-interval-seconds", 60);
        provider.assignConfigValue("nop.auth.sms-code.send-interval-seconds", seconds);
        try {
            body.run();
        } finally {
            provider.assignConfigValue("nop.auth.sms-code.send-interval-seconds",
                    original != null ? original : 60);
        }
    }

    // ===================== Helpers: RPC / GraphQL =====================

    private ApiResponse<?> rpcMutation(String operation, Map<String, Object> data,
                                       UserContextImpl user, Map<String, Object> headers) {
        return rpc(GraphQLOperationType.mutation, operation, data, user, headers);
    }

    private ApiResponse<?> rpcQuery(String operation, Map<String, Object> data, UserContextImpl user) {
        return rpc(GraphQLOperationType.query, operation, data, user, null);
    }

    private ApiResponse<?> rpc(GraphQLOperationType type, String operation, Map<String, Object> data,
                               UserContextImpl user, Map<String, Object> headers) {
        IUserContext.set(user);
        try {
            ApiRequest<Map<String, Object>> request = new ApiRequest<>();
            request.setData(data);
            if (headers != null)
                request.setHeaders(headers);
            IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(type, operation, request);
            return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    private GraphQLResponseBean gqlMutation(String operation, String argsLiteral, UserContextImpl user) {
        IUserContext.set(user);
        try {
            GraphQLRequestBean request = new GraphQLRequestBean();
            request.setQuery("mutation { " + operation + "(" + argsLiteral + ") }");
            IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
            return FutureHelper.syncGet(graphQLEngine.executeGraphQLAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    private UserContextImpl restrictedCtx(String userId, String sessionId) {
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId(sessionId);
        uc.setMfaRestricted(true);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        return uc;
    }

    private UserContextImpl adminCtx(String userId, String sessionId) {
        UserContextImpl uc = restrictedCtx(userId, sessionId);
        uc.setMfaRestricted(false);
        return uc;
    }

    // ===================== Helpers: 数据准备 =====================

    private LoginResult doPasswordLogin(String userName) {
        LoginRequest req = new LoginRequest();
        req.setLoginType(1);
        req.setPrincipalId(userName);
        req.setPrincipalSecret("123");
        IServiceContext c = new ServiceContextImpl();
        return ormTemplate.runInSession(s -> FutureHelper.syncGet(loginApiBizModel.loginAsync(req, c)));
    }

    private LoginResult doMfaVerify(String challengeToken, String code) {
        MfaVerifyRequest req = new MfaVerifyRequest();
        req.setChallengeToken(challengeToken);
        req.setCode(code);
        IServiceContext c = new ServiceContextImpl();
        return ormTemplate.runInSession(s -> FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(req, c)));
    }

    private void saveUser(String userId, String phone) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            if (dao.getEntityById(userId) == null) {
                NopAuthUser user = dao.newEntity();
                user.setUserId(userId);
                user.setUserName(userId);
                user.setNickName(userId);
                String salt = passwordEncoder.generateSalt();
                user.setPassword(passwordEncoder.encodePassword(salt, "123"));
                user.setSalt(salt);
                user.setOpenId(userId);
                user.setUserType(1);
                user.setStatus(1);
                user.setGender(1);
                user.setTenantId(TENANT_ID);
                if (phone != null) {
                    user.setPhone(phone);
                }
                dao.saveEntity(user);
            }
            savePolicyRoleIfAbsent();
            IEntityDao<NopAuthUserRole> mappingDao = daoProvider.daoFor(NopAuthUserRole.class);
            NopAuthUserRole mapping = mappingDao.newEntity();
            mapping.setUserId(userId);
            mapping.setRoleId(POLICY_ROLE);
            mappingDao.saveEntity(mapping);
            return null;
        });
    }

    private void savePolicyRoleIfAbsent() {
        IEntityDao<NopAuthRole> roleDao = daoProvider.daoFor(NopAuthRole.class);
        if (roleDao.getEntityById(POLICY_ROLE) == null) {
            NopAuthRole role = roleDao.newEntity();
            role.setRoleId(POLICY_ROLE);
            role.setRoleName(POLICY_ROLE);
            roleDao.saveEntity(role);
        }
        IEntityDao<NopAuthRoleMfaPolicy> policyDao = daoProvider.daoFor(NopAuthRoleMfaPolicy.class);
        if (policyDao.getEntityById(POLICY_ROLE) == null) {
            NopAuthRoleMfaPolicy policy = policyDao.newEntity();
            policy.setRoleId(POLICY_ROLE);
            policy.setMinMfaLevel(2);
            policy.setAllowTrustedDevice((byte) 1);
            policyDao.saveEntity(policy);
        }
    }

    private void enableSmsDirectly(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.getEntityById(userId);
            if (setting == null) {
                setting = dao.newEntity();
                setting.setUserId(userId);
                setting.setTenantId(TENANT_ID);
            }
            setting.setMfaType(NopAuthConstants.MFA_TYPE_SMS);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            dao.saveEntity(setting);
            return null;
        });
    }

    private NopAuthMfaSetting getSetting(String userId) {
        return ContextProvider.runWithTenant(TENANT_ID,
                () -> daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId));
    }

    /** 轮询取指定 operation 的审计行 description（批处理分批落库容忍，最多 ~10s）。 */
    private List<String> pollAuditDescriptions(String operation) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            List<NopAuthOpLog> found = ContextProvider.runWithTenant(TENANT_ID, () -> {
                NopAuthOpLog example = new NopAuthOpLog();
                example.setOperation(operation);
                return daoProvider.daoFor(NopAuthOpLog.class).findAllByExample(example);
            });
            if (!found.isEmpty()) {
                return found.stream().map(l -> String.valueOf(l.getDescription())).collect(Collectors.toList());
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return List.of();
    }

    // ===================== TOTP code computation =====================

    private String computeTotpCode(String base32Secret) {
        return computeTotpAt(base32Secret, System.currentTimeMillis());
    }

    private static String computeTotpAt(String base32Secret, long timeMillis) {
        byte[] secretBytes = base32Decode(base32Secret);
        long window = timeMillis / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
        byte[] counterBytes = new byte[8];
        long t = window;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (t & 0xFF);
            t >>>= 8;
        }
        byte[] hash = io.nop.commons.crypto.HashHelper.hmac(TOTPAuthenticator.HMAC_ALGORITHM, counterBytes,
                secretBytes);
        int offset = hash[hash.length - 1] & 0x0F;
        int truncated = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return String.format("%06d", truncated % TOTPAuthenticator.MODULUS);
    }

    private static String extractSecretFromUri(String uri) {
        int idx = uri.indexOf("secret=");
        int end = uri.indexOf("&", idx);
        return uri.substring(idx + "secret=".length(), end > 0 ? end : uri.length());
    }

    private static byte[] base32Decode(String encoded) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        encoded = encoded.toUpperCase().replaceAll("[=]", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bitsLeft = 0;
        for (char ch : encoded.toCharArray()) {
            int val = alphabet.indexOf(ch);
            if (val < 0)
                continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
