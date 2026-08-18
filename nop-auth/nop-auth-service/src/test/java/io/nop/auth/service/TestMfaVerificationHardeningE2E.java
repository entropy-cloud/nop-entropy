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
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.biz.LoginApiBizModel;
import io.nop.auth.service.biz.dto.MfaBindResult;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2-followup-1 Phase 2 E2E：验证语义加固——
 * <ul>
 *   <li><b>D1-1</b>：confirmMfa/unbindMfa 的 TOTP 分支失败计数上限（缺省 5，配置化；成功清零；
 *       pending 路径达上限作废 bindToken → BIND_EXPIRED；enabled 路径冷却窗口内拒绝、窗口过期
 *       后可重试）；SMS/EMAIL 分支不引入新计数的边界钉定。</li>
 *   <li><b>D1-3</b>：verifyChannelProof 码错误路径产生 {@code mfa:channel-proof-fail} 审计事件
 *       （断言事件名 + maskedTarget），正确路径 {@code verified} 事件不回归。</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestMfaVerificationHardeningE2E extends JunitBaseTestCase {

    private static final String TENANT_ID = "0";

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    SmsCodeStore smsCodeStore;

    @Inject
    TOTPAuthenticator totpAuthenticator;

    @Inject
    io.nop.auth.core.password.IPasswordEncoder passwordEncoder;

    @Inject
    LoginApiBizModel loginApiBizModel;

    private String base32Secret;

    private NopAuthUserBizModel userBizModel() {
        Object bean = io.nop.api.core.ioc.BeanContainer.tryGetBean(
                "io.nop.auth.service.entity.NopAuthUserBizModel");
        if (bean == null)
            throw new IllegalStateException("NopAuthUserBizModel bean not found in container");
        return (NopAuthUserBizModel) bean;
    }

    @AfterEach
    void clearUserContext() {
        IUserContext.set(null);
    }

    // ===================== D1-1：confirmMfa（pending 路径） =====================

    @Test
    public void testConfirmMfaTotpFailLimitInvalidatesBindToken() {
        String userId = "d11-confirm-user";
        saveUser(userId);
        withConfig("nop.auth.mfa.totp-verify-max-fails", 2, () -> {
            MfaBindResult bind = bindTotp(userId);
            String bindToken = bind.getBindToken();

            // 2 次失败（达上限）
            for (int i = 0; i < 2; i++) {
                NopException fail = assertThrows(NopException.class,
                        () -> confirmMfa(userId, bindToken, "000000"));
                assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());
            }
            // 第 3 次即使码正确也报 BIND_EXPIRED（bindToken 已作废）
            NopException locked = assertThrows(NopException.class,
                    () -> confirmMfa(userId, bindToken, computeTotpCode(base32Secret)));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED.getErrorCode(), locked.getErrorCode());

            // 冷却窗口内重新 bindMfa：新 bindToken 的 confirm 仍被冷却门拒绝（BIND_EXPIRED）
            MfaBindResult rebind = bindTotp(userId);
            NopException cooldown = assertThrows(NopException.class,
                    () -> confirmMfa(userId, rebind.getBindToken(), computeTotpCode(base32Secret)));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_BIND_EXPIRED.getErrorCode(), cooldown.getErrorCode());
        });
    }

    @Test
    public void testConfirmMfaSuccessResetsCounter() {
        String userId = "d11-reset-user";
        saveUser(userId);
        withConfig("nop.auth.mfa.totp-verify-max-fails", 3, () -> {
            MfaBindResult bind = bindTotp(userId);
            // 1 次失败后成功：计数清零（后续 confirm 不受残留计数影响——此处以换绑后再次完整流程验证）
            assertThrows(NopException.class, () -> confirmMfa(userId, bind.getBindToken(), "000000"));
            List<String> codes = confirmMfa(userId, bind.getBindToken(), computeTotpCode(base32Secret));
            assertNotNull(codes, "confirmMfa must succeed after limited failures");

            NopAuthMfaSetting setting = getSetting(userId);
            assertNotNull(setting);
            assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, setting.getStatus());
            assertTrue(setting.getTotpFailCount() == null || setting.getTotpFailCount() == 0,
                    "successful confirm must reset the TOTP fail counter");
            assertNull(setting.getTotpFailAt(), "successful confirm must clear the TOTP fail timestamp");
        });
    }

    // ===================== D1-1：unbindMfa（enabled 路径，冷却窗口） =====================

    @Test
    public void testUnbindMfaTotpCooldownWindow() {
        String userId = "d11-unbind-user";
        saveUser(userId);
        withConfigs(Map.of(
                "nop.auth.mfa.totp-verify-max-fails", 2,
                "nop.auth.mfa.totp-cooldown-seconds", 1), () -> {
            enableTotp(userId);

            // 2 次失败（达上限）
            for (int i = 0; i < 2; i++) {
                NopException fail = assertThrows(NopException.class, () -> unbindMfa(userId, "000000"));
                assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());
            }
            // 冷却窗口内：正确码也直接拒绝（COOLDOWN）
            NopException cooldown = assertThrows(NopException.class,
                    () -> unbindMfa(userId, nextWindowTotpCode(base32Secret)));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_COOLDOWN.getErrorCode(), cooldown.getErrorCode());

            // 窗口过期后：正确码可重试成功（解绑完成）。用 W+1 窗口码（confirm 已消费 W 窗口，
            // TOTP 防重放要求严格递增窗口）。等待以 CoreMetrics 逻辑时钟为准（TestClock 单调
            // 前移超前墙钟，固定 sleep 不可靠）
            waitForLogicalMillis(1500);
            unbindMfa(userId, nextWindowTotpCode(base32Secret));
            NopAuthMfaSetting setting = getSetting(userId);
            assertNotNull(setting);
            assertEquals(NopAuthConstants.MFA_STATUS_DISABLED, setting.getStatus(),
                    "unbind must succeed after cooldown window expiry");
        });
    }

    /** 边界钉定（D1-1 裁定）：SMS 分支失败不引入 TOTP 计数（store 内部 max-attempts 已覆盖）。 */
    @Test
    public void testSmsFailuresDoNotTripTotpLockout() {
        String userId = "d11-sms-boundary-user";
        saveUser(userId, "13911112222");
        enableSmsDirectly(userId);
        // smsCodeStore.send 建码（真实码存在于 store，使 verify 走 MISMATCH 而非 EXPIRED）
        ormTemplate.runInSession(s -> smsCodeStore.send(io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA + userId));

        for (int i = 0; i < 2; i++) {
            NopException fail = assertThrows(NopException.class, () -> unbindMfa(userId, "000000"));
            assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode(),
                    "sms mismatch must fail via MFA_FAIL");
        }
        NopAuthMfaSetting setting = getSetting(userId);
        assertNotNull(setting);
        assertTrue(setting.getTotpFailCount() == null || setting.getTotpFailCount() == 0,
                "sms failures must NOT increment the TOTP lockout counter (boundary pin)");
        assertNull(setting.getTotpFailAt());
        // 未进入冷却：继续 sms 失败仍是 MFA_FAIL（非 COOLDOWN）
        NopException still = assertThrows(NopException.class, () -> unbindMfa(userId, "000001"));
        assertNotEquals(NopAuthErrors.ERR_AUTH_MFA_COOLDOWN.getErrorCode(), still.getErrorCode());
    }

    // ===================== D1-3：channel-proof fail 审计 =====================

    @Test
    public void testVerifyChannelProofFailAuditedWithMaskedTarget() {
        String userId = "d13-proof-user";
        saveUser(userId, "13933334444");
        // 发 proof 码（key=proof:{userId}）——send 返回明文码供正确路径复用
        String code = ormTemplate.runInSession(
                s -> smsCodeStore.send(io.nop.auth.service.NopAuthConstants.SMS_KEY_PROOF + userId));

        // 错码 → MFA_FAIL + mfa:channel-proof-fail 审计（含 maskedTarget，不含明文手机号）
        UserContextImpl ctx = adminCtx(userId, "sess-proof");
        IServiceContext svc = serviceCtx(ctx);
        NopException fail = assertThrows(NopException.class,
                () -> ormTemplate.runInSession(s -> loginApiBizModel.verifyChannelProof("000000", svc)));
        assertEquals(NopAuthErrors.ERR_AUTH_MFA_FAIL.getErrorCode(), fail.getErrorCode());

        List<String> failEvents = pollAuditRequests("mfa:channel-proof-fail");
        assertTrue(!failEvents.isEmpty(), "channel-proof fail must be audited");
        String event = failEvents.get(failEvents.size() - 1);
        assertTrue(event.contains("phone"), "fail audit must carry channel=phone: " + event);
        assertTrue(event.contains("*******4444") || event.contains("4444"),
                "fail audit must carry masked target: " + event);
        assertTrue(!event.contains("13933334444"), "fail audit must NOT contain the plaintext phone: " + event);

        // 正确码 → 票 + verified 事件不回归
        String ticket = ormTemplate.runInSession(s -> loginApiBizModel.verifyChannelProof(code, svc));
        assertNotNull(ticket, "correct proof code must issue a ticket");
        assertTrue(!pollAuditRequests("mfa:channel-proof-verified").isEmpty(),
                "verified event must still be audited");
    }

    // ===================== Helpers：MFA 流 =====================

    private MfaBindResult bindTotp(String userId) {
        IServiceContext svc = serviceCtx(adminCtx(userId, "sess-bind-" + System.nanoTime()));
        MfaBindResult result = ormTemplate.runInSession(
                s -> userBizModel().bindMfa(NopAuthConstants.MFA_TYPE_TOTP, null, null, svc));
        base32Secret = extractSecretFromUri(result.getProvisioningUri());
        return result;
    }

    private List<String> confirmMfa(String userId, String bindToken, String code) {
        IServiceContext svc = serviceCtx(adminCtx(userId, "sess-confirm-" + System.nanoTime()));
        return ormTemplate.runInSession(s -> userBizModel().confirmMfa(bindToken, code, svc));
    }

    private void unbindMfa(String userId, String code) {
        IServiceContext svc = serviceCtx(adminCtx(userId, "sess-unbind-" + System.nanoTime()));
        ormTemplate.runInSession(s -> {
            userBizModel().unbindMfa(code, null, null, svc);
            return null;
        });
    }

    /** 直接经 dao 建 enabled TOTP setting（绕过 bind/confirm 仪式，聚焦 unbind 语义）。 */
    private void enableTotp(String userId) {
        MfaBindResult bind = bindTotp(userId);
        confirmMfa(userId, bind.getBindToken(), computeTotpCode(base32Secret));
        NopAuthMfaSetting setting = getSetting(userId);
        assertEquals(NopAuthConstants.MFA_STATUS_ENABLED, setting.getStatus(), "totp must be enabled for unbind test");
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

    // ===================== Helpers：上下文/配置/审计 =====================

    private IServiceContext serviceCtx(UserContextImpl user) {
        ServiceContextImpl c = new ServiceContextImpl();
        c.setUserContext(user);
        return c;
    }

    private UserContextImpl adminCtx(String userId, String sessionId) {
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

    private void withConfig(String key, Object value, Runnable body) {
        withConfigs(Map.of(key, value), body);
    }

    private void withConfigs(Map<String, Object> configs, Runnable body) {
        IConfigProvider provider = AppConfig.getConfigProvider();
        Map<String, Object> originals = new java.util.HashMap<>();
        configs.forEach((k, v) -> originals.put(k, provider.getConfigValue(k, v)));
        configs.forEach((k, v) -> provider.assignConfigValue(k, v));
        try {
            body.run();
        } finally {
            originals.forEach(provider::assignConfigValue);
        }
    }

    /** 轮询取指定 description 的审计行 requestData（批处理分批落库容忍，最多 ~10s）。 */
    private List<String> pollAuditRequests(String description) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            List<NopAuthOpLog> found = ContextProvider.runWithTenant(TENANT_ID, () -> {
                NopAuthOpLog example = new NopAuthOpLog();
                example.setDescription(description);
                return daoProvider.daoFor(NopAuthOpLog.class).findAllByExample(example);
            });
            if (!found.isEmpty()) {
                return found.stream().map(l -> String.valueOf(l.getOpRequest())).collect(Collectors.toList());
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待 CoreMetrics 逻辑时钟推进至少 millis（TestClock 保证单调且向前，但超前墙钟——
     * 固定墙钟 sleep 可能只推进逻辑时钟 ~0ms，冷却窗口过期断言必须按逻辑时钟等待）。
     */
    private static void waitForLogicalMillis(long millis) {
        long start = io.nop.api.core.time.CoreMetrics.currentTimeMillis();
        long deadline = System.currentTimeMillis() + millis + 30_000;
        while (io.nop.api.core.time.CoreMetrics.currentTimeMillis() - start < millis) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("logical clock did not advance within deadline");
            }
            sleep(50);
        }
    }

    // ===================== Helpers：数据准备 / TOTP =====================

    private void saveUser(String userId) {
        saveUser(userId, null);
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
            return null;
        });
    }

    private String computeTotpCode(String secret) {
        return computeTotpCodeAt(secret, System.currentTimeMillis());
    }

    /** W+1 窗口码（confirm 已消费当前窗口时使用——TOTP 防重放要求窗口严格递增，skew=1 允许 +1）。 */
    private static String nextWindowTotpCode(String secret) {
        // 精确取下一窗口内的时间点（当前窗口起点 + 1 周期 + 1s 余量）——
        // 简单 +35s 在窗口已过 25s 时会落到 W+2（超出 floor/ceil 候选，验证必败，曾致偶发失败）
        long periodMs = TOTPAuthenticator.PERIOD_SECONDS * 1000L;
        long nextWindowAt = (System.currentTimeMillis() / periodMs) * periodMs + periodMs + 1000L;
        return computeTotpCodeAt(secret, nextWindowAt);
    }

    private static String computeTotpCodeAt(String secret, long timeMillis) {
        byte[] secretBytes = base32Decode(secret);
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
