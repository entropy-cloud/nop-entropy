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
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.messages.MfaVerifyOperationRequest;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.mfa.OperationMfaCheckerImpl;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.GraphQLEngine;
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

import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ARG_OPERATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W12-impl Phase 3 E2E 全链（真实容器组件，Anti-Hollow Rule #22/#23）：
 * GraphQL mutation 入口（敏感操作）→ executor 检查点 → {@link OperationMfaCheckerImpl}
 * （容器 bean → GraphQLEngine 注入）→ MfaChallengeStore（DB 实现）→ mfaVerifyOperation
 * （非 admin 登录态默认 permission 路径可达）→ 携票重试 → 成功。
 * <p>
 * 无静默跳过（Rule #24）：同会话不符/票过期/因子失败/超限/重复验证/scene 不符均显式抛错。
 * 判定矩阵：enabled 开关 / 未启用 MFA 用户 / 票四条件 / 一次性 / 绑定 operation+session。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestOperationMfaE2E extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";
    private static final String OP_RESET = "NopAuthUser__resetUserMfa";
    private static final String OP_CHANGE_PWD = "NopAuthUser__changeSelfPassword";

    private static Boolean originalOperationMfaEnabled;

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
    LoginApiBizModel loginApiBizModel;

    private String base32Secret;

    @BeforeAll
    static void enableOperationMfa() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalOperationMfaEnabled = provider.getConfigValue("nop.auth.operation-mfa.enabled", Boolean.FALSE);
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
    }

    @AfterAll
    static void restoreOperationMfa() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.operation-mfa.enabled",
                originalOperationMfaEnabled != null ? originalOperationMfaEnabled : Boolean.FALSE);
    }

    @AfterEach
    void clearUserContext() {
        IUserContext.set(null);
    }

    // ===================== 元数据传播（容器内 BizObjectBuildHelper 链路） + bean 接线 =====================

    @Test
    public void testEngineMetadataOnAnnotatedActions() {
        // 路由项 1 落地（A2 终局裁定）：removeWebauthnCredential 入正例（5→6）；
        // renameWebauthnCredential 入负例（裁定"展示元数据变更不标注"——钉定为预期而非遗漏）
        String[] ops = {OP_RESET, OP_CHANGE_PWD, "NopAuthUser__unbindMfa",
                "NopAuthUser__generateRecoveryCodes", "NopAuthUser__resetUserPassword",
                "NopAuthUser__removeWebauthnCredential"};
        for (String op : ops) {
            assertNotNull(graphQLEngine.getSchemaLoader().getOperationDefinition(GraphQLOperationType.mutation, op),
                    op + " must be a registered mutation");
            assertNotNull(graphQLEngine.getSchemaLoader().getOperationDefinition(GraphQLOperationType.mutation, op)
                            .getMfaRequiredMeta(),
                    op + " must carry mfaRequiredMeta through container wiring (BizObjectBuildHelper path)");
        }
        assertNull(graphQLEngine.getSchemaLoader()
                        .getOperationDefinition(GraphQLOperationType.mutation, "NopAuthUser__renameWebauthnCredential")
                        .getMfaRequiredMeta(),
                "rename is adjudicated non-sensitive (display-only metadata): no mfaRequiredMeta is expected");
        assertNull(graphQLEngine.getSchemaLoader()
                .getOperationDefinition(GraphQLOperationType.query, "NopAuthUser__getMfaStatus").getMfaRequiredMeta(),
                "non-sensitive action must have no mfaRequiredMeta");
    }

    @Test
    public void testRemoveWebauthnCredentialAnnotationInterception() {
        // 路由项 1 行为面：removeWebauthnCredential 标注后进入操作级判定（enabled=true 拦截 /
        // enabled=false 零介入）；rename 未标注 → enabled=true 亦零介入（负向钉定）
        String userId = "op-mfa-remove-cred-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-rm-cred");

        // enabled=true（类缺省）：MFA 用户调 remove → 拦截（新 challenge）
        NopException ex = assertThrows(NopException.class,
                () -> checker().check("NopAuthUser__removeWebauthnCredential", ctx, null));
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), ex.getErrorCode(),
                "annotated removeWebauthnCredential must be intercepted for MFA-enabled user");

        // rename 未标注：enabled=true 亦零介入——经引擎路径（executor 检查点按元数据路由，
        // 无 mfaRequiredMeta 即不进 checker）；业务侧报"credential not found"证明已穿过检查点
        ApiResponse<?> renameResp = rpcMutation("NopAuthUser__renameWebauthnCredential",
                Map.of("sid", "nonexistent", "name", "x"), ctx, null);
        assertFalse(renameResp.isOk(), "rename should fail on business validation (sid not found)");
        assertNotEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), renameResp.getCode(),
                "unannotated rename must never be MFA-intercepted (executor routes by metadata)");

        // enabled=false：remove 零介入（标注存在但开关关闭——一期零回归语义）
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", false);
        try {
            assertDoesNotThrow(() -> checker().check("NopAuthUser__removeWebauthnCredential", ctx, null),
                    "enabled=false must zero-intervene even for annotated actions");
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
        }
    }

    @Test
    public void testCheckerBeanInjectedIntoEngine() {
        // 接线验证（Rule #23）：nopOperationMfaChecker bean 经容器解析并注入 GraphQLEngine
        assertSame(GraphQLEngine.class, graphQLEngine.getClass());
        GraphQLEngine engine = (GraphQLEngine) graphQLEngine;
        assertNotNull(engine.getOperationMfaChecker(), "container must inject IOperationMfaChecker into engine");
        assertSame(OperationMfaCheckerImpl.class, engine.getOperationMfaChecker().getClass());
    }

    // ===================== 判定矩阵：开关 / 未启用用户 =====================

    @Test
    public void testEnabledFalseZeroIntervention() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.operation-mfa.enabled", false);
        try {
            String userId = "op-mfa-off-user";
            saveUserWithTotp(userId);
            ApiResponse<?> resp = rpcMutation(OP_RESET, Map.of("userId", userId),
                    adminContext(userId, "sess-off"), null);
            assertTrue(resp.isOk(), "enabled=false → sensitive op executes without interception (一期零回归)");
        } finally {
            provider.assignConfigValue("nop.auth.operation-mfa.enabled", true);
        }
    }

    @Test
    public void testNonMfaUserNotIntercepted() {
        String userId = "op-mfa-plain-user";
        saveUser(userId);
        ApiResponse<?> resp = rpcMutation(OP_RESET, Map.of("userId", userId),
                adminContext(userId, "sess-plain"), null);
        assertTrue(resp.isOk(), "user without MFA enabled must not be intercepted");
    }

    // ===================== 全链：拦截 → 验证 → 携票重试 → 成功 + 票一次性 =====================

    @Test
    public void testFullChainInterceptVerifyTicketRetry() {
        String userId = "op-mfa-full-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-full");

        // 1a. 引擎路径拦截（GraphQL 文档路径 → executor 检查点 → checker；逐 field error 语义）
        GraphQLResponseBean intercepted = gqlMutation(OP_RESET, argQuote(userId), ctx, null);
        assertTrue(intercepted.hasError(), "sensitive op must be intercepted for MFA-enabled user");
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), intercepted.getErrorCode());

        // 1b. errorParams 三元组（checker 直调取 NopException——响应层只暴露 errorCode）
        NopException ex = assertThrows(NopException.class,
                () -> checker().check(OP_RESET, ctx, null));
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertNotNull(ex.getParam(ARG_CHALLENGE_TOKEN), "errorParams.challengeToken");
        assertEquals(NopAuthConstants.MFA_TYPE_TOTP, ex.getParam(ARG_MFA_TYPE), "errorParams.mfaType");
        assertEquals(OP_RESET, ex.getParam(ARG_OPERATION), "errorParams.operation（bizObjName__action）");
        String challengeToken = String.valueOf(ex.getParam(ARG_CHALLENGE_TOKEN));

        // 2. challenge 语义钉定（scene/payload 可见）
        MfaChallenge c = mfaChallengeStore.peek(challengeToken);
        assertNotNull(c);
        assertEquals(MfaChallenge.SCENE_OPERATION, c.getScene());
        Map<String, Object> payload = JsonTool.parseMap(c.getPayload());
        assertEquals(OP_RESET, payload.get("operation"));
        assertEquals("sess-full", payload.get("sessionId"));

        // 3. 错码一次 → MFA_FAIL（失败计数）
        NopException fail = assertThrows(NopException.class,
                () -> mfaVerifyOperation(challengeToken, "000000", ctx));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());

        // 4. 正确 TOTP 码 → 验证成功（成功不签发任何凭证——void 返回，无 token 语义）
        mfaVerifyOperation(challengeToken, computeTotpCode(base32Secret), ctx);

        // 5. 已验证票拒绝重复验证（票不续命）
        NopException dup = assertThrows(NopException.class,
                () -> mfaVerifyOperation(challengeToken, "000001", ctx));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), dup.getErrorCode());

        // 6. 携票重试原操作 → 成功（reset 实际执行；RPC 单操作路径同样经 executor 检查点）
        Map<String, Object> headers = new HashMap<>();
        headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, challengeToken);
        ApiResponse<?> ok = rpcMutation(OP_RESET, Map.of("userId", userId), ctx, headers);
        assertTrue(ok.isOk(), "retry with valid ticket must execute the sensitive op");
        assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, getSetting(userId).getStatus(),
                "reset must have executed");

        // 7. 票一次性：同票二次使用 → 重新拦截（新 challenge）
        reenableTotp(userId);
        GraphQLResponseBean again = gqlMutation(OP_RESET, argQuote(userId), ctx, headers);
        assertTrue(again.hasError(), "ticket is one-time: second use must be re-intercepted");
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), again.getErrorCode());
    }

    // ===================== 票绑定：operation / session =====================

    @Test
    public void testTicketBoundToOperation() {
        String userId = "op-mfa-bind-op-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-bind-op");

        String challengeToken = requireChallenge(OP_RESET, userId, ctx);
        mfaVerifyOperation(challengeToken, computeTotpCode(base32Secret), ctx);

        Map<String, Object> headers = new HashMap<>();
        headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, challengeToken);

        // 换操作使用同一张票 → 重新拦截（票绑定 operation；票未被消费）
        GraphQLResponseBean other = gqlMutation(OP_CHANGE_PWD,
                "oldPassword:\"123\", newPassword:\"new-pass-1\"", ctx, headers);
        assertTrue(other.hasError(), "ticket bound to operation must not authorize a different operation");
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), other.getErrorCode());

        // 原操作仍可用该票
        ApiResponse<?> origin = rpcMutation(OP_RESET, Map.of("userId", userId), ctx, headers);
        assertTrue(origin.isOk(), "ticket must still authorize its bound operation");
    }

    @Test
    public void testTicketBoundToSession() {
        String userId = "op-mfa-bind-sess-user";
        saveUserWithTotp(userId);
        UserContextImpl ctxA = adminContext(userId, "sess-A");
        UserContextImpl ctxB = adminContext(userId, "sess-B");

        String challengeToken = requireChallenge(OP_RESET, userId, ctxA);
        mfaVerifyOperation(challengeToken, computeTotpCode(base32Secret), ctxA);

        Map<String, Object> headers = new HashMap<>();
        headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, challengeToken);

        // 换会话使用同一张票 → 重新拦截（票绑定 sessionId）
        GraphQLResponseBean other = gqlMutation(OP_RESET, argQuote(userId), ctxB, headers);
        assertTrue(other.hasError(), "ticket bound to session must not authorize another session");
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), other.getErrorCode());

        // mfaVerifyOperation 同会话校验：ctxB 验证 ctxA 的 challenge → CHALLENGE_EXPIRED
        NopException cross = assertThrows(NopException.class,
                () -> mfaVerifyOperation(challengeToken, "000002", ctxB));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), cross.getErrorCode(),
                "mfaVerifyOperation must reject challenges from another session");
    }

    // ===================== 失败计数超限作废 =====================

    @Test
    public void testWrongCodeFailCountExceededInvalidatesChallenge() {
        String userId = "op-mfa-fail-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-fail");

        String challengeToken = requireChallenge(OP_RESET, userId, ctx);

        int maxAttempts = AppConfig.getConfigProvider().getConfigValue("nop.auth.mfa.max-attempts", 5);
        for (int i = 0; i < maxAttempts; i++) {
            NopException fail = assertThrows(NopException.class,
                    () -> mfaVerifyOperation(challengeToken, "000000", ctx));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());
        }
        // 超限后 challenge 作废：正确码也报 CHALLENGE_EXPIRED（不再计数爆破）
        NopException expired = assertThrows(NopException.class,
                () -> mfaVerifyOperation(challengeToken, computeTotpCode(base32Secret), ctx));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), expired.getErrorCode());
        assertNull(mfaChallengeStore.peek(challengeToken), "invalidated challenge must be consumed");
    }

    // ===================== 恢复码在操作级被拒绝 =====================

    @Test
    public void testRecoveryCodeRejectedAtOperationLevel() {
        String userId = "op-mfa-recovery-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-recovery");

        // 请求契约：MfaVerifyOperationRequest 无 recoveryCode 字段（无恢复码通道）
        assertNull(fieldOrNull(new MfaVerifyOperationRequest(), "recoveryCode"),
                "operation verify request must not expose recoveryCode channel");

        // 恢复码形态的 10 位码作为 code 提交 → 因子校验失败（TOTP 只接受 6 位）
        String challengeToken = requireChallenge(OP_RESET, userId, ctx);
        NopException fail = assertThrows(NopException.class,
                () -> mfaVerifyOperation(challengeToken, "0123456789", ctx));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode(),
                "recovery-code-shaped input must fail factor verification at operation level");
    }

    // ===================== scene 纪律：登录级 mfaVerify 拒绝他场景 token（D2-F2） =====================

    @Test
    public void testOperationEndpointRejectsLoginSceneToken() {
        String userId = "op-mfa-scene-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-scene");

        String loginToken = mfaChallengeStore.create(userId, NopAuthConstants.MFA_TYPE_TOTP, 1, TENANT_ID, null);
        NopException login = assertThrows(NopException.class,
                () -> mfaVerifyOperation(loginToken, computeTotpCode(base32Secret), ctx));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), login.getErrorCode(),
                "mfaVerifyOperation must reject scene!=operation tokens (including login scene)");
    }

    @Test
    public void testLoginLevelRejectsOperationSceneToken() {
        // D2-F2（successor-B 落地）：operation scene 的 token 送登录级 mfaVerify 被显式拒绝——
        // 拒绝其"challenge token + 因子码 = 免第一因子签发全新会话"的挪用面（原"安全等价"论证
        // 只覆盖第二因子、忽略第一因子降级，A2 审计提级异议成立，§3.5 再裁定翻案）。
        String userId = "op-mfa-cross-op-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-cross-op");

        String challengeToken = mfaChallengeStore.create(MfaChallenge.SCENE_OPERATION, userId,
                NopAuthConstants.MFA_TYPE_TOTP, 0, TENANT_ID, null,
                "{\"operation\":\"" + OP_RESET + "\",\"sessionId\":\"sess-cross-op\"}");

        NopException ex = assertThrows(NopException.class, () -> {
            MfaVerifyRequest loginRequest = new MfaVerifyRequest();
            loginRequest.setChallengeToken(challengeToken);
            loginRequest.setCode(computeTotpCode(base32Secret));
            ormTemplate.runInSession(s -> {
                // 显式中间类型：嵌套泛型推断（runInSession + syncGet 双泛型方法）的中间变量锚定
                java.util.concurrent.CompletionStage<io.nop.auth.api.messages.LoginResult> stage =
                        loginApiBizModel.mfaVerifyAsync(loginRequest, new ServiceContextImpl());
                return FutureHelper.syncGet(stage);
            });
        });
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), ex.getErrorCode(),
                "login-level mfaVerify must reject operation-scene token even with a valid factor code");

        // 拒绝语义：不消费——该 token 在其自身场景与 TTL 内仍合法可用（mfaVerifyOperation 验证成功）
        mfaVerifyOperation(challengeToken, computeTotpCode(base32Secret), ctx);
    }

    @Test
    public void testLoginLevelRejectsCrossSceneTokensMatrix() {
        // D2-F2 矩阵：webauthn-register / webauthn-unbind / channel-proof 场景 token 全部被拒；
        // 已转票（verifiedAt 非空）的 operation token 被拒（票只授权其绑定操作，不授权登录）
        String userId = "op-mfa-matrix-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-matrix");

        String[] scenes = {MfaChallenge.SCENE_WEBAUTHN_REGISTER, MfaChallenge.SCENE_WEBAUTHN_UNBIND,
                MfaChallenge.SCENE_CHANNEL_PROOF};
        for (String scene : scenes) {
            String token = mfaChallengeStore.create(scene, userId, NopAuthConstants.MFA_TYPE_TOTP, 0,
                    TENANT_ID, null, "{\"sessionId\":\"sess-matrix\"}");
            NopException ex = assertThrows(NopException.class, () -> mfaVerifyLogin(userId, token,
                    computeTotpCode(base32Secret)), "scene=" + scene + " token must be rejected");
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), ex.getErrorCode(),
                    "scene=" + scene + " token must be rejected by login-level mfaVerify");
        }

        // 已转票的 operation token：markVerified 后 peek 可见（票窗口内），登录级拒绝
        String ticketToken = requireChallenge(OP_RESET, userId, ctx);
        mfaVerifyOperation(ticketToken, computeTotpCode(base32Secret), ctx);
        assertNotNull(mfaChallengeStore.peek(ticketToken), "ticket stays visible within its window");
        assertNotNull(mfaChallengeStore.peek(ticketToken).getVerifiedAt(), "verified ticket must carry verifiedAt");
        NopException ticket = assertThrows(NopException.class, () -> mfaVerifyLogin(userId, ticketToken,
                computeTotpCode(base32Secret)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED.getErrorCode(), ticket.getErrorCode(),
                "already-verified operation ticket must not authorize login-level session issuance");
    }

    @Test
    public void testLoginLevelAcceptsLoginAndLegacyNullScene() {
        // 一期兼容钉定：scene=login 与 scene=null（一期存量数据）的正常两阶段登录零回归。
        // 两场景用不同用户：同用户同窗口二次验证会触发 TOTP 防重放（MfaFactorVerifier 统一
        // 窗口推进）——与 scene 兼容语义无关，属组件既有纪律。
        String userId = "op-mfa-compat-user";
        saveUserWithTotp(userId);
        String loginToken = mfaChallengeStore.create(MfaChallenge.SCENE_LOGIN, userId,
                NopAuthConstants.MFA_TYPE_TOTP, 1, TENANT_ID, null, null);
        io.nop.auth.api.messages.LoginResult ctx1 = mfaVerifyLogin(userId, loginToken, computeTotpCode(base32Secret));
        assertNotNull(ctx1.getAccessToken(), "scene=login two-phase login must complete (session issued)");

        // scene=null（一期存量兼容口径——老进程写的无 scene 行）
        String legacyUserId = "op-mfa-compat-legacy-user";
        saveUserWithTotp(legacyUserId);
        String nullToken = mfaChallengeStore.create(null, legacyUserId, NopAuthConstants.MFA_TYPE_TOTP, 1,
                TENANT_ID, null, null);
        io.nop.auth.api.messages.LoginResult ctx2 = mfaVerifyLogin(legacyUserId, nullToken,
                computeTotpCode(base32Secret));
        assertNotNull(ctx2.getAccessToken(), "scene=null legacy challenge must still complete login");
    }

    /** 登录级 mfaVerify 直调（GraphQL 入口 LoginApiBizModel.mfaVerifyAsync 的服务层路径；异常自然上抛）。 */
    private io.nop.auth.api.messages.LoginResult mfaVerifyLogin(String userId, String challengeToken, String code) {
        IUserContext.set(adminContext(userId, "sess-login-" + (challengeToken == null ? 0
                : challengeToken.hashCode() & 0xFFFF)));
        try {
            return ormTemplate.runInSession(s -> {
                MfaVerifyRequest loginRequest = new MfaVerifyRequest();
                loginRequest.setChallengeToken(challengeToken);
                loginRequest.setCode(code);
                // 显式中间类型：嵌套泛型推断（runInSession + syncGet 双泛型方法）的中间变量锚定
                java.util.concurrent.CompletionStage<io.nop.auth.api.messages.LoginResult> stage =
                        loginApiBizModel.mfaVerifyAsync(loginRequest, new ServiceContextImpl());
                return FutureHelper.syncGet(stage);
            });
        } finally {
            IUserContext.set(null);
        }
    }

    // ===================== 跨场景 TOTP 窗口重放拒绝（MfaFactorVerifier 统一推进） =====================

    @Test
    public void testCrossSceneTotpReplayRejected() {
        String userId = "op-mfa-replay-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-replay");

        // 操作级验证成功（窗口 W 推进）
        String challengeToken = requireChallenge(OP_RESET, userId, ctx);
        String code = computeTotpCode(base32Secret);
        mfaVerifyOperation(challengeToken, code, ctx);

        // 同窗口码不得再过登录级：构造 login challenge 后用同一 code 验证 → MFA_FAIL
        String loginToken = mfaChallengeStore.create(userId, NopAuthConstants.MFA_TYPE_TOTP, 1, TENANT_ID, null);
        NopException replay = assertThrows(NopException.class, () -> {
            MfaVerifyRequest loginRequest = new MfaVerifyRequest();
            loginRequest.setChallengeToken(loginToken);
            loginRequest.setCode(code); // 同窗口码重放
            FutureHelper.syncGet(loginApiBizModel.mfaVerifyAsync(loginRequest, new ServiceContextImpl()));
        });
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), replay.getErrorCode(),
                "same-window TOTP code must not pass the login level after operation-level verification "
                        + "(unified window advancement in MfaFactorVerifier)");
    }

    // ===================== SMS 因子分支闭环 =====================

    @Test
    public void testSmsFactorBranchClosedLoop() {
        String userId = "op-mfa-sms-user";
        saveUserWithTotp(userId);
        switchSettingToSms(userId);
        UserContextImpl ctx = adminContext(userId, "sess-sms");

        String challengeToken = requireChallenge(OP_RESET, userId, ctx);
        assertEquals(NopAuthConstants.MFA_TYPE_SMS, mfaChallengeStore.peek(challengeToken).getMfaType());

        // 未发码（store 无条目）→ SMS_CODE_EXPIRED（沿用既有码，不计失败爆破通道）
        NopException noCode = assertThrows(NopException.class,
                () -> mfaVerifyOperation(challengeToken, "000000", ctx));
        assertEquals(NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED.getErrorCode(), noCode.getErrorCode());

        // 发码（key=mfa:userId，组件消费口径）→ 验证 → 票 → 重试成功
        String smsCode = smsCodeStore.send("mfa:" + userId);
        mfaVerifyOperation(challengeToken, smsCode, ctx);
        Map<String, Object> headers = new HashMap<>();
        headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, challengeToken);
        ApiResponse<?> ok = rpcMutation(OP_RESET, Map.of("userId", userId), ctx, headers);
        assertTrue(ok.isOk(), "sms factor full loop: verify → ticket → retry must succeed");
    }

    // ===================== 审计四事件落 NopAuthOpLog =====================

    @Test
    public void testAuditEventsWrittenToOpLog() {
        String userId = "op-mfa-audit-user";
        saveUserWithTotp(userId);
        UserContextImpl ctx = adminContext(userId, "sess-audit");

        // 发起（拦截产生 challenge）→ 验证失败（错码）→ 验证成功（正确码）→ 票消费（携票重试）
        String challengeToken = requireChallenge(OP_RESET, userId, ctx);
        assertThrows(NopException.class, () -> mfaVerifyOperation(challengeToken, "000000", ctx));
        mfaVerifyOperation(challengeToken, computeTotpCode(base32Secret), ctx);
        Map<String, Object> headers = new HashMap<>();
        headers.put(OperationMfaCheckerImpl.HEADER_OP_MFA_TOKEN, challengeToken);
        assertTrue(rpcMutation(OP_RESET, Map.of("userId", userId), ctx, headers).isOk());

        io.nop.api.core.audit.IAuditService auditService =
                io.nop.api.core.ioc.BeanContainer.getBeanByType(io.nop.api.core.audit.IAuditService.class);
        assertTrue(FutureHelper.waitUntil(auditService::isAllProcessed, 10000), "audit batch must drain");

        // 审计批处理线程分批落库：轮询直到四类事件全部可见（最多 ~10s）
        List<NopAuthOpLog> events = pollAuditEvents(OP_RESET,
                List.of("op-mfa-challenge-issued", "verify-fail", "verify-ok", "op-mfa-ticket-consumed"));
        Set<String> descriptions = new HashSet<>();
        for (NopAuthOpLog log : events) {
            descriptions.add(String.valueOf(log.getDescription()));
        }
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("op-mfa-challenge-issued")),
                "challenge issued event must be audited: " + descriptions);
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("verify-fail")),
                "verify fail event must be audited: " + descriptions);
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("verify-ok")),
                "verify success event must be audited: " + descriptions);
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("op-mfa-ticket-consumed")),
                "ticket consumed event must be audited: " + descriptions);
        assertTrue(events.stream().anyMatch(l -> "sess-audit".equals(l.getSessionId())),
                "audit rows must record sessionId");
    }

    // ===================== Helpers =====================

    private UserContextImpl adminContext(String userId, String sessionId) {
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId(sessionId);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        return uc;
    }

    /** 经引擎 RPC 执行 mutation（executor 单操作检查点在途）。 */
    private ApiResponse<?> rpcMutation(String operation, Map<String, Object> data,
                                       UserContextImpl user, Map<String, Object> headers) {
        IUserContext.set(user);
        try {
            ApiRequest<Map<String, Object>> request = new ApiRequest<>();
            request.setData(data);
            if (headers != null)
                request.setHeaders(headers);
            IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                    operation, request);
            return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    /** GraphQL 文档路径执行 mutation（executor 文档检查点在途；错误经逐 field error 携带 errorParams）。 */
    private GraphQLResponseBean gqlMutation(String operation, String argsLiteral, UserContextImpl user,
                                            Map<String, Object> headers) {
        IUserContext.set(user);
        try {
            GraphQLRequestBean request = new GraphQLRequestBean();
            request.setQuery("mutation { " + operation + "(" + argsLiteral + ") }");
            IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(request);
            if (headers != null)
                ctx.setRequestHeaders(headers);
            return FutureHelper.syncGet(graphQLEngine.executeGraphQLAsync(ctx));
        } finally {
            IUserContext.set(null);
        }
    }

    /** 轮询取指定 operation 的审计行，直到全部期望事件可见（批处理分批落库容忍，最多 ~10s）。 */
    private List<NopAuthOpLog> pollAuditEvents(String operation, List<String> expectedEvents) {
        long deadline = System.currentTimeMillis() + 10_000L;
        List<NopAuthOpLog> events = List.of();
        while (System.currentTimeMillis() < deadline) {
            final List<NopAuthOpLog> found = ContextProvider.runWithTenant(TENANT_ID, () -> {
                NopAuthOpLog example = new NopAuthOpLog();
                example.setOperation(operation);
                return daoProvider.daoFor(NopAuthOpLog.class).findAllByExample(example);
            });
            boolean allPresent = expectedEvents.stream().allMatch(expected ->
                    found.stream().anyMatch(l -> String.valueOf(l.getDescription()).contains(expected)));
            events = found;
            if (allPresent)
                return events;
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return events;
    }

    private static String argQuote(String value) {
        return "userId:\"" + value + "\"";
    }

    /** 调用 mfaVerifyOperation（非 admin 登录态即默认 permission 路径可达）。 */
    private void mfaVerifyOperation(String challengeToken, String code, UserContextImpl user) {
        // 有意使用非 admin 上下文：端点不要求 admin，仅需登录态
        UserContextImpl plain = new UserContextImpl();
        plain.setUserId(user.getUserId());
        plain.setUserName(user.getUserId());
        plain.setTenantId(TENANT_ID);
        plain.setSessionId(user.getSessionId());
        IUserContext.set(plain);
        try {
            IServiceContext ctx = new ServiceContextImpl();
            MfaVerifyOperationRequest request = new MfaVerifyOperationRequest();
            request.setChallengeToken(challengeToken);
            request.setCode(code);
            loginApiBizModel.mfaVerifyOperation(request, ctx);
        } finally {
            IUserContext.set(null);
        }
    }

    /** 容器装配的真实 checker（引擎注入的同一实例）。 */
    private io.nop.auth.api.mfa.IOperationMfaChecker checker() {
        return ((GraphQLEngine) graphQLEngine).getOperationMfaChecker();
    }

    /** 触发敏感操作拦截（checker 直调取回带 errorParams 的 NopException），返回新 challengeToken。 */
    private String requireChallenge(String operation, String userId, UserContextImpl user) {
        NopException ex = assertThrows(NopException.class, () -> checker().check(operation, user, null));
        assertEquals(NopAuthErrors.ERR_AUTH_OPERATION_MFA_REQUIRED.getErrorCode(), ex.getErrorCode());
        return String.valueOf(ex.getParam(ARG_CHALLENGE_TOKEN));
    }

    private void saveUser(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            upsertUser(userId);
            return null;
        });
    }

    private void saveUserWithTotp(String userId) {
        base32Secret = totpAuthenticator.generateSecret();
        String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret);
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            upsertUser(userId);
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.getEntityById(userId);
            if (setting == null) {
                setting = dao.newEntity();
                setting.setUserId(userId);
                setting.setTenantId(TENANT_ID);
            }
            setting.setMfaType(NopAuthConstants.MFA_TYPE_TOTP);
            setting.setSecret(encrypted);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            dao.saveEntity(setting);
            return null;
        });
    }

    private void reenableTotp(String userId) {
        String encrypted = totpAuthenticator.getCipher().encrypt(base32Secret);
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.getEntityById(userId);
            setting.setMfaType(NopAuthConstants.MFA_TYPE_TOTP);
            setting.setSecret(encrypted);
            setting.setStatus(NopAuthConstants.MFA_STATUS_ENABLED);
            setting.setPhone(null);
            dao.updateEntityDirectly(setting);
            return null;
        });
    }

    private void switchSettingToSms(String userId) {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthMfaSetting> dao = daoProvider.daoFor(NopAuthMfaSetting.class);
            NopAuthMfaSetting setting = dao.getEntityById(userId);
            setting.setMfaType(NopAuthConstants.MFA_TYPE_SMS);
            setting.setSecret(null);
            setting.setPhone("13800138000");
            dao.updateEntityDirectly(setting);
            return null;
        });
    }

    private NopAuthMfaSetting getSetting(String userId) {
        return ContextProvider.runWithTenant(TENANT_ID,
                () -> daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId));
    }

    private void upsertUser(String userId) {
        IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
        NopAuthUser user = dao.getEntityById(userId);
        if (user != null)
            return;
        user = dao.newEntity();
        user.setUserId(userId);
        user.setUserName(userId);
        user.setNickName(userId);
        user.setPassword("x");
        user.setSalt("x");
        user.setOpenId(userId);
        user.setUserType(1);
        user.setStatus(1);
        user.setGender(1);
        user.setTenantId(TENANT_ID);
        dao.saveEntity(user);
    }

    private String computeTotpCode(String secret) {
        return computeTotpAt(secret, System.currentTimeMillis());
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

    private static Object fieldOrNull(Object target, String name) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }
}
