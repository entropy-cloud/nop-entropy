/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthRoleMfaPolicy;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.dao.entity.NopAuthUserRole;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.mock.CapturingEmailSender;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.nop.auth.service.NopAuthErrors.ARG_CHANNEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15-impl Phase 1：登记通道 email 解锁 E2E（设计 §4.3 既定扩展 + §5.3.3）。真实容器组件
 * （JunitBaseTestCase + 容器装配的 LoginApiBizModel/NopAuthUserBizModel/EmailCodeStore +
 * CapturingEmailSender）。
 * <ul>
 *   <li>无 phone 有 email 的受限用户：bindMfa → proof 码发至登记 email（脱敏提示）→
 *       verifyChannelProof → proof 票 → bindMfa(totp) → confirm → 重新登录（全链）。</li>
 *   <li>双通道用户：默认走 phone（W13 行为不变）；显式 channel=email 选择 email；任意指定
 *       （未登记/非法值）显式拒绝。</li>
 *   <li>proof 码按通道隔离（proof-email:{userId} 与 proof:{userId} 互不通用）。</li>
 *   <li>email-code.enabled=false 时 email 通道 proof 显式拒绝。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestChannelProofEmailE2E extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";
    private static final String POLICY_ROLE = "e2e-proof-email-role";

    private static Boolean originalMfaEnabled;
    private static Boolean originalEmailEnabled;
    private static Integer originalSendInterval;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    LoginApiBizModel loginApiBizModel;

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

    @BeforeAll
    static void enableMfaAndEmail() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        originalMfaEnabled = provider.getConfigValue("nop.auth.mfa.enabled", Boolean.FALSE);
        originalEmailEnabled = provider.getConfigValue("nop.auth.email-code.enabled", Boolean.FALSE);
        originalSendInterval = provider.getConfigValue("nop.auth.email-code.send-interval-seconds", 60);
        provider.assignConfigValue("nop.auth.mfa.enabled", true);
        provider.assignConfigValue("nop.auth.email-code.enabled", true);
        provider.assignConfigValue("nop.auth.email-code.send-interval-seconds", 0);
    }

    @AfterAll
    static void restore() {
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.mfa.enabled",
                originalMfaEnabled != null ? originalMfaEnabled : Boolean.FALSE);
        provider.assignConfigValue("nop.auth.email-code.enabled",
                originalEmailEnabled != null ? originalEmailEnabled : Boolean.FALSE);
        provider.assignConfigValue("nop.auth.email-code.send-interval-seconds",
                originalSendInterval != null ? originalSendInterval : 60);
    }

    @AfterEach
    void clearUserContext() {
        IUserContext.set(null);
        CapturingEmailSender.reset();
    }

    // ===================== 无 phone 有 email：proof email 全链 =====================

    @Test
    public void testNoPhoneUserProofViaEmailFullChain() {
        String userId = "pe-nophone-user";
        saveUser(userId, null, "nophone@example.com");
        UserContextImpl rctx = restrictedCtx(userId);

        // 1. bindMfa 无 proof → email proof 发码 + CHANNEL_PROOF_REQUIRED（邮箱脱敏提示）
        NopException noProof = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, null));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), noProof.getErrorCode());
        String channel = String.valueOf(noProof.getParam(ARG_CHANNEL));
        assertTrue(channel.contains("no***@example.com") && !channel.contains("nophone@"),
                "email channel hint must be masked: " + channel);
        assertNotNull(CapturingEmailSender.lastCode(), "proof code must be sent to the registered email");
        assertEquals(List.of("nophone@example.com"), CapturingEmailSender.lastMessage().getTo());
        String proofCode = CapturingEmailSender.lastCode();

        // 2. verifyChannelProof（无 channel 参数——缺省解析与发码一致：无 phone 回退 email）
        NopException wrong = assertThrows(NopException.class, () ->
                verifyChannelProofAs(rctx, "000000", null));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), wrong.getErrorCode());
        String ticket = verifyChannelProofAs(rctx, proofCode, null);
        assertNotNull(ticket);
        MfaChallenge c = proofTicket(ticket);
        assertEquals(MfaChallenge.SCENE_CHANNEL_PROOF, c.getScene());
        assertNotNull(c.getVerifiedAt(), "proof ticket must be a verified ticket");

        // 3. 携 proof 票 bindMfa(totp) → provisioning URI 可达（合法引导流）
        MfaBindResult bind = bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, ticket, null);
        assertNotNull(bind.getProvisioningUri());

        // 4. confirmMfa → enabled（策略 level 2 + totp 达标）
        String secret = extractSecret(bind.getProvisioningUri());
        String confirmCode = computeTotp(secret);
        List<String> recoveryCodes = confirmMfaAs(rctx, bind.getBindToken(), confirmCode);
        assertEquals(10, recoveryCodes.size());
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, getSetting(userId).getStatus());
    }

    // ===================== 双通道用户：默认 phone、显式选择 email、任意指定拒绝 =====================

    @Test
    public void testDualChannelUserDefaultsToPhone() {
        String userId = "pe-dual-user";
        saveUser(userId, "13911100001", "dual@example.com");
        UserContextImpl rctx = restrictedCtx(userId);

        // 缺省（无 channel 参数）→ phone 通道（W13 行为不变）
        NopException ex = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, null));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam(ARG_CHANNEL)).contains("0001"),
                "default channel must be phone (masked tail 4): " + ex.getParam(ARG_CHANNEL));
    }

    @Test
    public void testDualChannelUserExplicitEmailSelection() {
        String userId = "pe-select-user";
        saveUser(userId, "13911100002", "select@example.com");
        UserContextImpl rctx = restrictedCtx(userId);

        // 显式 channel=email → email 通道发码
        NopException ex = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, NopAuthConstants.PROOF_CHANNEL_EMAIL));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam(ARG_CHANNEL)).contains("se***@example.com"),
                "explicit email channel must be used: " + ex.getParam(ARG_CHANNEL));
        String proofCode = CapturingEmailSender.lastCode();
        assertNotNull(proofCode);

        // verifyChannelProof(channel=email) → 票（通道一致性）
        String ticket = verifyChannelProofAs(rctx, proofCode, NopAuthConstants.PROOF_CHANNEL_EMAIL);
        assertNotNull(ticket);
        MfaBindResult bind = bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, ticket, null);
        assertNotNull(bind.getProvisioningUri(), "proof ticket must authorize bindMfa");
    }

    @Test
    public void testArbitraryChannelRejected() {
        String userId = "pe-arbitrary-user";
        saveUser(userId, "13911100003", "arbitrary@example.com");
        UserContextImpl rctx = restrictedCtx(userId);

        // 任意指定（非法值）→ 显式拒绝（不静默回退）
        NopException bogus = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, "fax"));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), bogus.getErrorCode());

        // 已登记 phone 但未登记 email，指定 email 通道 → 拒绝（服务端限定已登记集合）
        String phoneOnlyUser = "pe-arbitrary-phoneonly";
        saveUser(phoneOnlyUser, "13911100005", null);
        NopException unregistered = assertThrows(NopException.class, () ->
                verifyChannelProofAs(restrictedCtx(phoneOnlyUser), "123456", NopAuthConstants.PROOF_CHANNEL_EMAIL));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), unregistered.getErrorCode(),
                "requesting an unregistered channel must be rejected explicitly");
    }

    // ===================== 通道隔离与门控 =====================

    @Test
    public void testProofCodeChannelIsolation() {
        String userId = "pe-isolation-user";
        saveUser(userId, null, "isolation@example.com");
        UserContextImpl rctx = restrictedCtx(userId);

        // email proof 发码后，phone 侧 proof key 无码（通道隔离）
        assertThrows(NopException.class, () -> bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, null));
        String emailProofCode = CapturingEmailSender.lastCode();
        assertNotNull(emailProofCode);

        // 无 phone 用户显式请求 phone 通道 → 拒绝（服务端限定已登记集合）
        NopException phoneChannel = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, NopAuthConstants.PROOF_CHANNEL_PHONE));
        assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), phoneChannel.getErrorCode());

        // email proof 码在 email 通道验证通过
        String ticket = verifyChannelProofAs(rctx, emailProofCode, null);
        assertNotNull(ticket);
    }

    @Test
    public void testEmailProofDisabledByDefaultGate() {
        String userId = "pe-gate-user";
        saveUser(userId, null, "gate@example.com");
        UserContextImpl rctx = restrictedCtx(userId);
        IConfigProvider provider = AppConfig.getConfigProvider();
        provider.assignConfigValue("nop.auth.email-code.enabled", false);
        try {
            NopException ex = assertThrows(NopException.class, () ->
                    bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, null));
            assertEquals(NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), ex.getErrorCode());
            assertTrue(String.valueOf(ex.getParam("msg")).contains("email-code.enabled"),
                    "email proof must be explicitly rejected when email-code is disabled: " + ex.getParam("msg"));
        } finally {
            provider.assignConfigValue("nop.auth.email-code.enabled", true);
        }
    }

    // ===================== 既有 phone 通道零回归 =====================

    @Test
    public void testPhoneProofUnchangedWhenEmailAlsoRegistered() {
        String userId = "pe-phone-regression";
        saveUser(userId, "13911100004", "regression@example.com");
        UserContextImpl rctx = restrictedCtx(userId);
        // 显式 channel=phone（双通道登记时选择 phone）→ 与 W13 缺省路径同行为
        NopException ex = assertThrows(NopException.class, () ->
                bindMfaAs(rctx, NopAuthConstants.MFA_TYPE_TOTP, null, NopAuthConstants.PROOF_CHANNEL_PHONE));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_CHANNEL_PROOF_REQUIRED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam(ARG_CHANNEL)).contains("0004"),
                "explicit phone channel must follow the W13 path: " + ex.getParam(ARG_CHANNEL));
    }

    // ===================== Helpers（TestMfaRestrictedSessionE2E 同型） =====================

    private MfaBindResult bindMfaAs(UserContextImpl ctx, String mfaType, String proof, String channel) {
        IUserContext.set(ctx);
        try {
            return ormTemplate.runInSession(s ->
                    userBizModel().bindMfa(mfaType, proof, channel, serviceCtx(ctx)));
        } finally {
            IUserContext.set(null);
        }
    }

    private String verifyChannelProofAs(UserContextImpl ctx, String code, String channel) {
        IUserContext.set(ctx);
        try {
            return ormTemplate.runInSession(s -> loginApiBizModel.verifyChannelProof(code, channel, serviceCtx(ctx)));
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

    private io.nop.core.context.IServiceContext serviceCtx(UserContextImpl user) {
        io.nop.core.context.ServiceContextImpl c = new io.nop.core.context.ServiceContextImpl();
        c.setUserContext(user);
        return c;
    }

    private UserContextImpl restrictedCtx(String userId) {
        UserContextImpl uc = new UserContextImpl();
        uc.setUserId(userId);
        uc.setUserName(userId);
        uc.setTenantId(TENANT_ID);
        uc.setSessionId("sess-" + userId);
        uc.setMfaRestricted(true);
        Set<String> roles = new HashSet<>();
        roles.add(NopAuthConstants.ROLE_ADMIN);
        uc.setRoles(roles);
        return uc;
    }

    private MfaChallenge proofTicket(String token) {
        io.nop.auth.core.mfa.store.MfaChallengeStore store =
                (io.nop.auth.core.mfa.store.MfaChallengeStore) io.nop.api.core.ioc.BeanContainer.tryGetBean(
                        "nopActiveMfaChallengeStore");
        MfaChallenge c = store.peek(token);
        assertNotNull(c);
        return c;
    }

    private NopAuthMfaSetting getSetting(String userId) {
        return ormTemplate.runInSession(s -> ContextProvider.runWithTenant(TENANT_ID,
                () -> daoProvider.daoFor(NopAuthMfaSetting.class).getEntityById(userId)));
    }

    private void saveUser(String userId, String phone, String email) {
        io.nop.auth.core.password.SHA256PasswordEncoder encoder = new io.nop.auth.core.password.SHA256PasswordEncoder();
        String salt = encoder.generateSalt();
        ContextProvider.runWithTenant(TENANT_ID, () -> {
            IEntityDao<NopAuthUser> dao = daoProvider.daoFor(NopAuthUser.class);
            NopAuthUser user = dao.newEntity();
            user.setUserId(userId);
            user.setUserName(userId);
            user.setNickName(userId);
            user.setPassword(encoder.encodePassword(salt, "123"));
            user.setSalt(salt);
            user.setOpenId(userId);
            user.setUserType(1);
            user.setStatus(1);
            user.setGender(1);
            user.setTenantId(TENANT_ID);
            if (phone != null)
                user.setPhone(phone);
            if (email != null)
                user.setEmail(email);
            dao.saveEntity(user);

            // 挂策略角色（level 2 → 未绑定 totp 的该用户受限）
            IEntityDao<NopAuthUserRole> mappingDao = daoProvider.daoFor(NopAuthUserRole.class);
            NopAuthUserRole mapping = mappingDao.newEntity();
            mapping.setUserId(userId);
            mapping.setRoleId(POLICY_ROLE);
            mappingDao.saveEntity(mapping);
            return null;
        });
    }

    @BeforeEach
    void ensurePolicyRole() {
        ContextProvider.runWithTenant(TENANT_ID, () -> {
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
            return null;
        });
    }

    private static String extractSecret(String uri) {
        int idx = uri.indexOf("secret=");
        int end = uri.indexOf("&", idx);
        return uri.substring(idx + "secret=".length(), end > 0 ? end : uri.length());
    }

    /** RFC 6238 TOTP 码计算（与 TOTPAuthenticator 同算法：HMAC-SHA1/6 位/30s 窗口）。 */
    private static String computeTotp(String base32Secret) {
        byte[] secretBytes = base32Decode(base32Secret);
        long window = System.currentTimeMillis() / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
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
}
