/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.auth.api.messages.MfaVerifyRequest;
import io.nop.auth.core.login.IAuthTokenProvider;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.auth.core.password.IPasswordEncoder;
import io.nop.auth.dao.entity.NopAuthMfaRecoveryCode;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.annotation.Nullable;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.auth.api.AuthApiConstants.LOGIN_TYPE_PHONE_SMS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_ENABLED;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_MAX_ATTEMPTS;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_DISABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_EMAIL;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_TOTP;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_WEBAUTHN;
import static io.nop.auth.service.NopAuthErrors.ARG_CHALLENGE_TOKEN;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_CHALLENGE_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_MFA_FAIL;

/**
 * 登录级 MFA 流程组件（design nop-auth §3.4，plan 2274 Phase 3 自 LoginServiceImpl 平移，
 * 行为等价重构）：
 * <ul>
 *   <li>MFA 门禁三层判定（{@link #checkMfaRequired}：全局开关 → store 装配 → setting 检查 →
 *       因子等同 → 可信设备豁免 → challenge 创建；W13 第三态受限决策）。</li>
 *   <li>第二因子验证（mfaVerify：TOTP/SMS/EMAIL/WebAuthn 分派 + 恢复码分支 + 失败计数）。</li>
 *   <li>会话出口经 {@link CompleteLoginPort} 回调端口回到宿主签发点（组件不持有
 *       LoginService 引用——接缝裁定，design §3.4）。</li>
 * </ul>
 * 依赖为可选注入 + 宿主 wired 实例传递（{@code LoginServiceImpl} accessor 以自身字段构造，
 * 手工 wiring 测试路径行为不变）。
 */
public class LoginMfaFlow {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LoginMfaFlow.class);

    /** IUserContext attr key：信道类 mfaVerify 成功出口的 accessCode（密码类不设此 attr）。 */
    public static final String ATTR_MFA_ACCESS_CODE = "mfaAccessCode";

    /**
     * IUserContext attr key：mfaVerify(rememberDevice) 的可信设备登记结果（W15-impl，仅密码类
     * 路径携带 Boolean true/false；LoginApiBizModel 回填 {@code LoginResult.trustedDeviceRegistered}）。
     */
    public static final String ATTR_TRUSTED_DEVICE_REGISTERED = "trustedDeviceRegistered";

    /**
     * 会话签发回调端口（design §3.4 接缝）：组件不持有 LoginService 引用，验证成功后经此
     * 回到宿主 completeLogin（resetFailCount/notifyHook/restricted 语义与既有裁决逐项对应，
     * 宿主仍是唯一签发点）。
     */
    @FunctionalInterface
    public interface CompleteLoginPort {
        CompletionStage<IUserContext> complete(NopAuthUser user, LoginRequest request,
                                               Map<String, Object> headers,
                                               boolean resetFailCount, boolean notifyHook, boolean restricted);
    }

    private final MfaChallengeStore mfaChallengeStore;
    private final MfaFactorVerifier mfaFactorVerifier;
    @Nullable
    private final RoleMfaPolicyEvaluator roleMfaPolicyEvaluator;
    @Nullable
    private final MfaTrustedDeviceManager trustedDeviceManager;
    @Nullable
    private final IOrmTemplate ormTemplate;
    private final IPasswordEncoder passwordEncoder;
    private final IAuthTokenProvider authTokenProvider;
    private final IDaoProvider daoProvider;

    public LoginMfaFlow(MfaChallengeStore mfaChallengeStore,
                        MfaFactorVerifier mfaFactorVerifier,
                        @Nullable RoleMfaPolicyEvaluator roleMfaPolicyEvaluator,
                        @Nullable MfaTrustedDeviceManager trustedDeviceManager,
                        @Nullable IOrmTemplate ormTemplate,
                        IPasswordEncoder passwordEncoder,
                        IAuthTokenProvider authTokenProvider,
                        IDaoProvider daoProvider) {
        this.mfaChallengeStore = mfaChallengeStore;
        this.mfaFactorVerifier = mfaFactorVerifier;
        this.roleMfaPolicyEvaluator = roleMfaPolicyEvaluator;
        this.trustedDeviceManager = trustedDeviceManager;
        this.ormTemplate = ormTemplate;
        this.passwordEncoder = passwordEncoder;
        this.authTokenProvider = authTokenProvider;
        this.daoProvider = daoProvider;
    }

    // ===================== MFA 第二因子验证（设计 §3.2 / §3.6） =====================

    public CompletionStage<IUserContext> mfaVerifyAsync(MfaVerifyRequest request, Map<String, Object> headers,
                                                        CompleteLoginPort completeLogin) {
        io.nop.api.core.util.Guard.notEmpty(request.getChallengeToken(), "challengeToken");

        // 1. peek challenge（不消费、不刷新 TTL）
        MfaChallenge challenge = mfaChallengeStore == null ? null : mfaChallengeStore.peek(request.getChallengeToken());
        if (challenge == null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED).param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
        }

        // 1b. scene/verifiedAt 纪律（A2-audit D2-F2，successor-B 落地；设计 §3.5 再裁定）：登录级
        //     验证端点仅接受 scene ∈ {login, null}（null = 一期存量兼容口径）且未转票
        //     （verifiedAt==null）的 challenge。operation/webauthn-register/webauthn-unbind/
        //     channel-proof 等他场景 token 携真实因子码可在此免第一因子签发全新会话（challenge
        //     token 替代密码的账户接管面）——显式拒绝。已转票（verifiedAt 非空）的 operation
        //     token 同样拒绝（票只授权其绑定操作，不授权登录）。拒绝语义对齐 mfaVerifyOperation
        //     对 login token 的既有行为：抛 CHALLENGE_EXPIRED 且不消费（错误场景的 token 在其
        //     自身场景与 TTL 内仍合法可用）。
        if ((challenge.getScene() != null && !MfaChallenge.SCENE_LOGIN.equals(challenge.getScene()))
                || challenge.getVerifiedAt() != null) {
            throw new NopException(ERR_AUTH_MFA_CHALLENGE_EXPIRED)
                    .param(ARG_CHALLENGE_TOKEN, request.getChallengeToken());
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
                return verifyRecoveryCodeAndComplete(request, challenge, user, setting, completeLogin);
            }
            return verifySecondFactorAndComplete(request, challenge, user, setting, headers, completeLogin);
        });
    }

    /**
     * TOTP / SMS / EMAIL / WebAuthn 第二因子验证 + completeLogin（设计 §3.2 mfaVerify 流程）。
     * 因子校验收敛至 {@link MfaFactorVerifier}（W12-impl 等价重构）；W14 webauthn 分支
     * {@code code} 载体换 {@code assertion}；W15 可信设备登记（completeLogin 之前纯 DB 写）。
     */
    protected CompletionStage<IUserContext> verifySecondFactorAndComplete(MfaVerifyRequest request,
                                                                          MfaChallenge challenge,
                                                                          NopAuthUser user,
                                                                          NopAuthMfaSetting setting,
                                                                          Map<String, Object> headers,
                                                                          CompleteLoginPort completeLogin) {
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

        return completeMfaLogin(user, challenge.getLoginType(), completeLogin).thenApply(ctx -> {
            if (registered != null) {
                // 结果回填（显式 true/false——满员/无 device-id 也是 false 提示非静默；仅密码类路径携带）
                ctx.setAttr(ATTR_TRUSTED_DEVICE_REGISTERED, registered);
            }
            return ctx;
        });
    }

    /**
     * 恢复码验证 + completeLogin（设计 §3.2 / §3.4 恢复码分支）：
     * 已用恢复码 → 统一 MFA_FAIL（不计数、不消费）；无效码 → incrFailCount + MFA_FAIL；
     * 有效恢复码 → consume challenge + setting.status=disabled（强制重绑）+ 审计 + completeLogin。
     */
    protected CompletionStage<IUserContext> verifyRecoveryCodeAndComplete(MfaVerifyRequest request,
                                                                          MfaChallenge challenge,
                                                                          NopAuthUser user,
                                                                          NopAuthMfaSetting setting,
                                                                          CompleteLoginPort completeLogin) {
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
        // A2-audit D3-F1（P1 修复）：恢复码使用 = 因子失效边界 → 物理删除 webauthn credential 行
        MfaWebauthnSupport.deleteWebauthnCredentials(daoProvider, challenge.getUserId());
        LOG.info("nop.auth.mfa-recovery-used:userId={},userName={}", user.getUserId(), user.getUserName());
        return completeMfaLogin(user, challenge.getLoginType(), completeLogin);
    }

    /** 恢复码验证三态。 */
    public enum RecoveryVerifyResult {
        VALID, USED, INVALID
    }

    /**
     * 验证恢复码：遍历用户的所有恢复码，BCrypt 比对。codeHash 格式为 {@code salt:hash}（无独立
     * salt 列）。A2-followup-1 D3-F2：used 置位为条件写（affected-row 判定）——并发双 verify
     * 同码恰一次成功。
     */
    public RecoveryVerifyResult verifyRecoveryCode(String userId, String inputCode) {
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
                // 条件写置 used：仅 USED=0 行受影响（并发/regenerate 竞态方 affected=0 → USED 路径）
                if (!markRecoveryCodeUsed(code.getSid())) {
                    return RecoveryVerifyResult.USED;
                }
                return RecoveryVerifyResult.VALID;
            }
        }
        return RecoveryVerifyResult.INVALID;
    }

    /**
     * 恢复码 used 条件置位（D3-F2）。ormTemplate 可用时走原子条件 EQL UPDATE（affected-row
     * 判定）；手工 wiring 退化路径经实体写（直调路径无并发竞争，语义等价）。
     */
    private boolean markRecoveryCodeUsed(String sid) {
        if (ormTemplate != null) {
            SQL upd = SQL.begin().name("mfaRecoveryCodeMarkUsed")
                    .sql("update NopAuthMfaRecoveryCode o set o.used = 1, o.usedAt = ? "
                            + "where o.sid = ? and o.used = 0",
                            new Timestamp(CoreMetrics.currentTimeMillis()), sid)
                    .end();
            return ormTemplate.executeUpdate(upd) > 0;
        }
        IEntityDao<NopAuthMfaRecoveryCode> dao = daoProvider.daoFor(NopAuthMfaRecoveryCode.class);
        NopAuthMfaRecoveryCode code = dao.getEntityById(sid);
        if (code == null || (code.getUsed() != null && code.getUsed() != 0)) {
            return false;
        }
        code.setUsed((byte) 1);
        code.setUsedAt(new Timestamp(CoreMetrics.currentTimeMillis()));
        dao.updateEntityDirectly(code);
        return true;
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
     * resetFailCount=false, notifyHook=false；信道类 loginType 生成 accessCode 经
     * {@code ATTR_MFA_ACCESS_CODE} attr 携带；密码类 completeLogin 后已含 accessToken。
     */
    protected CompletionStage<IUserContext> completeMfaLogin(NopAuthUser user, int loginType,
                                                             CompleteLoginPort completeLogin) {
        LoginRequest request = MfaRequests.synthetic(loginType);
        return completeLogin.complete(user, request, new java.util.HashMap<>(), false, false, false).thenApply(ctx -> {
            if (isChannelLoginType(loginType)) {
                String accessCode = authTokenProvider.generateAccessCode(ctx, CFG_AUTH_MFA_ACCESS_CODE_EXPIRE_SECONDS.get());
                ctx.setAttr(ATTR_MFA_ACCESS_CODE, accessCode);
            }
            return ctx;
        });
    }

    /** 信道类 loginType 判定（20-23=飞书/钉钉/企微/Webhook，4=SSO 也按信道处理以签发 accessCode）。 */
    public static boolean isChannelLoginType(int loginType) {
        return loginType >= 20 || loginType == AuthApiConstants.LOGIN_TYPE_SSO;
    }

    /**
     * 可信设备豁免/登记适用的登录类型判定（设计 §6.1 结论 3）：密码类 loginType 1/2/3/5；
     * 信道类（SSO 4 / 20-23）与 OAuth 不适用不登记。
     */
    public static boolean isPasswordLoginType(int loginType) {
        return loginType == AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD
                || loginType == AuthApiConstants.LOGIN_TYPE_EMAIL_PASSWORD
                || loginType == AuthApiConstants.LOGIN_TYPE_PHONE_PASSWORD
                || loginType == AuthApiConstants.LOGIN_TYPE_PHONE_SMS;
    }

    public NopAuthMfaSetting loadMfaSetting(String userId) {
        if (StringHelper.isEmpty(userId))
            return null;
        return daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId);
    }

    protected NopAuthUser getUserByUserId(String userId) {
        return daoProvider.daoFor(NopAuthUser.class).getEntityById(userId);
    }

    /**
     * MFA 门禁判定（设计 §3.2 / §4.3 三层判定矩阵）。返回非 null 表示需要处理：
     * restricted 决策（W13 第三态）/ challenge 决策 / null 放行。分支原位原序保留。
     * <p>
     * W15-impl：{@code requestHeaders} 可信设备豁免判定输入（信道路径传 null 结构性跳过）。
     * <b>同构副本裁定（W15）</b>：{@code MfaLoginPolicyServiceImpl.checkMfaForUserName}（OAuth
     * 入口）不加豁免分支——信道路径无 headers 结构性不可达。
     */
    public MfaChallengeDecision checkMfaRequired(NopAuthUser user, int loginType,
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
     * MFA 门禁结果：challenge 决策（challengeToken + mfaType，仅当需要第二因子时非 null）
     * 或受限决策（{@link #restricted()}，W13 第三态——角色策略不达标，不建 challenge）。
     */
    public static final class MfaChallengeDecision {
        final String challengeToken;
        final String mfaType;
        final boolean restricted;

        public MfaChallengeDecision(String challengeToken, String mfaType) {
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

        public boolean isRestricted() {
            return restricted;
        }

        public String getChallengeToken() {
            return challengeToken;
        }

        public String getMfaType() {
            return mfaType;
        }
    }
}
