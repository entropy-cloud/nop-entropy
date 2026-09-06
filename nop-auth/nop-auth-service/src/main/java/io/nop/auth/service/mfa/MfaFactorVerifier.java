/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.api.messages.WebAuthnAssertion;
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.EmailCodeStore;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaCredential;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_WINDOW_SKEW;
import static io.nop.auth.service.NopAuthConstants.EMAIL_KEY_MFA;
import static io.nop.auth.service.NopAuthConstants.MFA_STATUS_ENABLED;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_EMAIL;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_TOTP;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_WEBAUTHN;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_EMAIL_CODE_EXPIRED;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED;

/**
 * 共享因子校验组件（设计 §3.1 结论 5 / §5.3.0 #5/#6 收敛对象）：登录级
 * （{@code verifySecondFactorAndComplete}）、绑定级（confirmMfa/unbindMfa）、操作级
 * （{@code mfaVerifyOperation}）三处因子校验收敛于此。
 * <ul>
 *   <li><b>组件契约裁定</b>：返回校验结果布尔语义；失败计数与 MFA_FAIL/CHALLENGE_EXPIRED
 *       错误码留在调用方（与既有两调用点语义等价）。SMS 码已失效（EXPIRED）在两处既有
 *       调用点行为完全一致（抛 {@code ERR_AUTH_SMS_CODE_EXPIRED}、不计数），按等价重构
 *       收敛进组件；TOTP authenticator 缺失/secret 为空按绑定级口径返回 false（登录级原
 *       路径同样落到调用方 MFA_FAIL + 失败计数，错误码等价）。</li>
 *   <li><b>TOTP 防重放窗口统一推进内聚</b>（调用方不可选）：任何场景验证成功都更新
 *       {@code lastVerifiedWindow}/{@code lastVerifiedAt}——防同一 30s 窗口码跨场景重放
 *       （如先过操作级再过登录级）。全部调用点收敛后本组件是唯一推进路径。</li>
 *   <li><b>webauthn 分支（W14-impl，设计 §5.3.2）</b>：统一载体五参重载
 *       {@link #verify(NopAuthMfaSetting, String, String, WebAuthnAssertion, MfaChallenge)}——
 *       登录级/操作级/解绑级断言验证共用（cryptoChallenge 取自服务端 challenge payload，
 *       防客户端自造挑战；按 assertion.credentialId 查本人 enabled credential 行）。
 *       <b>signCount 单调递增写内聚组件</b>（对齐 TOTP 窗口推进先例）：条件 UPDATE
 *       {@code WHERE SIGN_COUNT < :new}——跨 challenge 并发断言同一 credential 的竞态方
 *       affected-row=0 按验证失败处理（用户重试）；count=0 认证器跳过单调校验仅记审计。</li>
 *   <li><b>未知 mfaType fail-closed</b>：返回 false（不扩散为可验证因子；登录级调用方
 *       另有作废 challenge 语义）。</li>
 *   <li>恢复码分支不入组件（登录级专用，留在 LoginServiceImpl）。</li>
 * </ul>
 */
public class MfaFactorVerifier {

    @Inject
    @Nullable
    protected TOTPAuthenticator totpAuthenticator;

    @Inject
    @Nullable
    protected SmsCodeStore smsCodeStore;

    /**
     * 邮件验证码 store（W15-impl，设计 §5.3.3）：email 分支 {@code verify(mfa-email:{userId})}。
     * 与 smsCodeStore 平行装配（nopActiveEmailCodeStore 工厂 bean）。
     */
    @Inject
    @Nullable
    protected EmailCodeStore emailCodeStore;

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    @Nullable
    protected IOrmTemplate ormTemplate;

    /**
     * WebAuthn 验证器组件（W14）：{@code nopWebAuthnAuthenticator} bean；未装配（无 webauthn
     * 部署的测试 wiring）时 webauthn 分支返回 false fail-closed。
     */
    @Inject
    @Nullable
    protected WebAuthnAuthenticator webAuthnAuthenticator;

    /** 审计服务（W14：webauthn 断言成功/失败 + count=0 认证器审计标记）。 */
    @Inject
    @Nullable
    protected IAuditService auditService;

    /**
     * 校验第二因子（code 载体：totp/sms）。
     *
     * @param setting  用户 MFA setting（TOTP 分支读 secret + lastVerifiedWindow；SMS 分支读 userId）
     * @param mfaType  因子类型（以 challenge/调用上下文的 mfaType 为准，与 setting.mfaType
     *                 的一致性校验属调用方复核语义）
     * @param code     用户输入的验证码
     * @return 校验是否通过；TOTP 成功时窗口推进副作用内聚于此
     */
    public boolean verify(NopAuthMfaSetting setting, String mfaType, String code) {
        return verify(setting, mfaType, code, null, null);
    }

    /**
     * 统一验证载体（设计 §3.1 结论 5 / §5.3.2，W14-impl）：{@code code}（totp/sms/email）与
     * {@code assertion}（webauthn 断言）可选共存——webauthn 分支要求 assertion + challenge
     * （payload 含服务端 cryptoChallenge），code 参数忽略；其余分支 assertion 参数忽略。
     * <p>
     * webauthn 分支输入依赖：challenge.payload 的 cryptoChallenge（防客户端自造挑战——省略即
     * 弱化实现）+ 按 assertion.credentialId 查 credential 行 + signCount 更新副作用内聚组件。
     * 登录级（mfaVerify）/操作级（mfaVerifyOperation）/解绑级（unbindMfa）三调用点共用本重载。
     *
     * @param assertion webauthn 断言（mfaType=webauthn 时必填）
     * @param challenge 当前验证上下文的 challenge（mfaType=webauthn 时必填——cryptoChallenge 来源）
     * @return 校验是否通过；webauthn 成功时 signCount 单调递增写内聚于此
     */
    public boolean verify(NopAuthMfaSetting setting, String mfaType, String code,
                          WebAuthnAssertion assertion, MfaChallenge challenge) {
        if (setting == null)
            return false;

        if (MFA_TYPE_TOTP.equals(mfaType)) {
            if (totpAuthenticator == null || StringHelper.isEmpty(setting.getSecret()))
                return false;
            // skew 与配置对齐（一期 verifyTotp 口径）。check2 P3 修复：仅配置漂移时写入——
            // 每请求无条件 setSkew 变异容器单例共享可变状态（skew 非 volatile），是并发隐患
            // 模板；beans 初始化时已按同一配置 setSkew，常态零写入，配置热更时恰一次纠偏
            int skew = CFG_AUTH_MFA_TOTP_WINDOW_SKEW.get();
            if (totpAuthenticator.getSkew() != skew) {
                totpAuthenticator.setSkew(skew);
            }
            long lastWindow = setting.getLastVerifiedWindow() == null ? -1L : setting.getLastVerifiedWindow();
            long window = totpAuthenticator.verify(setting.getSecret(), code, lastWindow);
            if (window < 0)
                return false;
            // 防重放：更新 lastVerifiedWindow（当前窗口严格大于历史才通过，已由 verify 保证）。
            // 组件内聚推进（调用方不可选）——唯一推进路径，防跨场景窗口码重放
            setting.setLastVerifiedWindow(window);
            setting.setLastVerifiedAt(new Timestamp(CoreMetrics.currentTimeMillis()));
            daoForSetting().updateEntityDirectly(setting);
            return true;
        }

        if (MFA_TYPE_SMS.equals(mfaType)) {
            if (smsCodeStore == null)
                return false;
            CodeVerifyResult r = smsCodeStore.verify(SMS_KEY_MFA + setting.getUserId(), code);
            if (r == CodeVerifyResult.EXPIRED) {
                // 两处既有调用点行为一致：EXPIRED 抛错（不进失败计数），等价重构收敛进组件
                throw new NopException(ERR_AUTH_SMS_CODE_EXPIRED);
            }
            return r == CodeVerifyResult.VALID;
        }

        if (MFA_TYPE_EMAIL.equals(mfaType)) {
            // W15-impl（设计 §5.3.3）：email 同 sms 形态——key 通道隔离（mfa-email:{userId}），
            // EXPIRED 抛 email 专属错误码（错误码定稿见 NopAuthErrors），MISMATCH 落调用方 MFA_FAIL
            if (emailCodeStore == null)
                return false;
            CodeVerifyResult r = emailCodeStore.verify(EMAIL_KEY_MFA + setting.getUserId(), code);
            if (r == CodeVerifyResult.EXPIRED) {
                throw new NopException(ERR_AUTH_EMAIL_CODE_EXPIRED);
            }
            return r == CodeVerifyResult.VALID;
        }

        if (MFA_TYPE_WEBAUTHN.equals(mfaType)) {
            return verifyWebauthn(setting, assertion, challenge);
        }

        // 未知 mfaType：fail-closed（白名单外值不扩散为可验证因子）
        return false;
    }

    /**
     * webauthn 断言验证（W14-impl，设计 §5.3.2）：
     * <ol>
     *   <li>载体完整性：assertion + challenge（payload.cryptoChallenge）缺一 fail-closed。</li>
     *   <li>credential 定位：assertion.credentialId + 本人 userId + status=enabled——
     *       不存在/禁用即失败（错误细节不外泄，统一 false）。</li>
     *   <li>断言验证（{@link WebAuthnAuthenticator#verifyAssertion}）：验签 + challenge 匹配 +
     *       origin/rpId + signCount 单调判定。</li>
     *   <li>signCount 写（单调递增内聚）：newCount&gt;0 时条件 UPDATE
     *       {@code WHERE SID=? AND SIGN_COUNT<?}——affected=0（并发竞态已推进更大计数）按验证
     *       失败处理；newCount=0（认证器不维护计数，协议允许）跳过单调写、仅更新 lastUsedAt
     *       并落审计标记。</li>
     * </ol>
     * 审计：断言成功/失败两事件落 NopAuthOpLog（userName 非空列——W13 执行期缺陷教训）。
     */
    protected boolean verifyWebauthn(NopAuthMfaSetting setting, WebAuthnAssertion assertion,
                                     MfaChallenge challenge) {
        String userId = setting.getUserId();
        if (webAuthnAuthenticator == null || assertion == null || challenge == null)
            return false;
        String cryptoChallenge = MfaChallengeHelper.cryptoChallengeOf(challenge);
        if (StringHelper.isEmpty(cryptoChallenge))
            return false;

        NopAuthMfaCredential credential = findEnabledCredential(userId, assertion.getCredentialId());
        if (credential == null) {
            auditWebauthnAssertion(userId, null, false, "credential-not-found", false);
            return false;
        }

        WebAuthnAuthenticator.AssertionCheck check = webAuthnAuthenticator.verifyAssertion(
                credential.getPublicKey(), cryptoChallenge, assertion,
                credential.getSignCount() == null ? 0L : credential.getSignCount(), userId);
        if (check == null) {
            auditWebauthnAssertion(userId, credential.getCredentialId(), false, "assertion-invalid", false);
            return false;
        }

        if (check.getSignatureCount() > 0) {
            // 单调递增写（并发语义裁定回写设计 §5.3.2）：条件 UPDATE，竞态方按失败处理
            long now = CoreMetrics.currentTimeMillis();
            if (ormTemplate != null) {
                SQL upd = SQL.begin().name("webauthnSignCountAdvance")
                        .sql("update NopAuthMfaCredential o set o.signCount = ?, o.lastUsedAt = ?, "
                                        + "o.updateTime = ?, o.version = o.version + 1 "
                                        + "where o.sid = ? and o.signCount < ?",
                                check.getSignatureCount(), new Timestamp(now), new Timestamp(now),
                                credential.getSid(), check.getSignatureCount())
                        .end();
                long affected = ormTemplate.executeUpdate(upd);
                if (affected == 0) {
                    // 并发断言已推进 ≥ 本计数：按验证失败处理（用户重试）——不覆盖更大计数
                    auditWebauthnAssertion(userId, credential.getCredentialId(), false, "sign-count-race", false);
                    return false;
                }
            } else {
                // 手工装配（@Nullable ormTemplate未注入，测试/无装饰器直调路径）退化实体写，
                // 与LoginServiceImpl.markRecoveryCodeUsed的null退化先例一致；实体乐观锁兜底并发
                long stored = credential.getSignCount() == null ? 0L : credential.getSignCount();
                if (stored >= check.getSignatureCount()) {
                    auditWebauthnAssertion(userId, credential.getCredentialId(), false, "sign-count-race", false);
                    return false;
                }
                credential.setSignCount(check.getSignatureCount());
                credential.setLastUsedAt(new Timestamp(now));
                daoProvider.daoFor(NopAuthMfaCredential.class).updateEntity(credential);
            }
        } else {
            // count=0 认证器（不维护计数，协议允许）：跳过单调校验，仅审计标记（watch-only）
            touchLastUsed(credential);
        }
        auditWebauthnAssertion(userId, credential.getCredentialId(), true,
                check.isZeroCounter() ? "zero-counter-authenticator" : "ok", check.isZeroCounter());
        return true;
    }

    /** 按 credentialId 定位本人 enabled credential（唯一约束保证至多一行）。 */
    protected NopAuthMfaCredential findEnabledCredential(String userId, String credentialId) {
        if (StringHelper.isEmpty(credentialId))
            return null;
        IEntityDao<NopAuthMfaCredential> dao = daoProvider.daoFor(NopAuthMfaCredential.class);
        NopAuthMfaCredential example = dao.newEntity();
        example.setCredentialId(credentialId);
        example.setUserId(userId);
        example.setStatus(MFA_STATUS_ENABLED);
        List<NopAuthMfaCredential> found = dao.findAllByExample(example, null);
        return found.isEmpty() ? null : found.get(0);
    }

    /** count=0 认证器的 lastUsedAt 审计更新（不动 signCount）。手工装配无 ormTemplate 时退化实体写。 */
    private void touchLastUsed(NopAuthMfaCredential credential) {
        long now = CoreMetrics.currentTimeMillis();
        if (ormTemplate != null) {
            SQL upd = SQL.begin().name("webauthnTouchLastUsed")
                    .sql("update NopAuthMfaCredential o set o.lastUsedAt = ?, o.updateTime = ?, "
                            + "o.version = o.version + 1 where o.sid = ?",
                            new Timestamp(now), new Timestamp(now), credential.getSid())
                    .end();
            ormTemplate.executeUpdate(upd);
        } else {
            credential.setLastUsedAt(new Timestamp(now));
            daoProvider.daoFor(NopAuthMfaCredential.class).updateEntity(credential);
        }
    }

    /** webauthn 断言审计事件（成功/失败），userName 非空列必须设置（W13 教训）。 */
    private void auditWebauthnAssertion(String userId, String credentialId, boolean success, String reason,
                                        boolean zeroCounter) {
        if (auditService == null)
            return;
        String userName = userNameOf(userId);
        AuditRequest audit = new AuditRequest();
        audit.setOperation("mfa:webauthn-assertion");
        audit.setDescription(success ? "webauthn:assertion-success" : "webauthn:assertion-fail");
        audit.setResultStatus(success ? 200 : 400);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userId);
        audit.setUserName(userName == null ? userId : userName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", success ? "webauthn-assertion-success" : "webauthn-assertion-fail");
        data.put("reason", reason);
        data.put("zeroCounter", zeroCounter);
        audit.setRequestData(JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    private String userNameOf(String userId) {
        NopAuthUser user = daoProvider.daoFor(NopAuthUser.class).getEntityById(userId);
        return user == null ? null : user.getUserName();
    }

    private IEntityDao<NopAuthMfaSetting> daoForSetting() {
        return daoProvider.daoFor(NopAuthMfaSetting.class);
    }
}
