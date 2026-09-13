/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.totp;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * RFC 6238 TOTP 验证器：HMAC-SHA1 / 6 位 / 30s 窗口 / ±1 skew。
 * <p>
 * 纯逻辑组件，位于 {@code nop-biz-auth-core}（上游纯逻辑层），不做持久化、不读配置。
 * <ul>
 *   <li>{@link #generateSecret()}：生成 32 字节随机 base32 secret。</li>
 *   <li>{@link #generateProvisioningUri(String, String, String)}：issuer 由调用方传入
 *       （TOTP 组件无法读取 {@code nop-auth-service} 的 {@code NopAuthConfigs}；
 *       {@code nop.auth.mfa.totp-issuer} 配置连线是 W5/W6 的工作）。</li>
 *   <li>防重放：{@code verifyRaw} 只接受窗口号严格大于 {@code lastVerifiedWindow} 的码——
 *       当前窗口 ≤ 上次成功窗口判定为拒绝且不返回窗口号（设计 §3.4）。</li>
 *   <li>secret 加解密通过 {@link ITextCipher}（默认 {@link AESTextCipher}）：实体存密文，
 *       {@link #verify(String, String, long)} 在内存解密后校验，明文不外泄。</li>
 * </ul>
 */
public class TOTPAuthenticator {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    public static final String HMAC_ALGORITHM = "HmacSHA1";
    public static final int DEFAULT_SKEW = 1;
    public static final int SECRET_BYTE_LENGTH = 32;
    public static final int MODULUS = 1_000_000; // 10^DIGITS

    private ITextCipher cipher = new AESTextCipher();

    private int skew = DEFAULT_SKEW;

    public void setCipher(ITextCipher cipher) {
        this.cipher = cipher;
    }

    public ITextCipher getCipher() {
        return cipher;
    }

    public void setSkew(int skew) {
        if (skew < 0)
            throw new IllegalArgumentException("skew must be >= 0");
        this.skew = skew;
    }

    public int getSkew() {
        return skew;
    }

    /**
     * 生成 32 字节随机 TOTP secret（base32 编码）。
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        try {
            MathHelper.secureRandom().nextBytes(bytes);
        } catch (Exception e) {
            // fail-closed：secret 生成失败不可静默降级到固定值
            throw NopException.adapt(e);
        }
        return Base32.encode(bytes);
    }

    /**
     * 生成 otpauth://totp provisioning URI（Google Authenticator / 标准 OTP 库格式）。
     * issuer 由调用方传入。
     */
    public String generateProvisioningUri(String issuer, String account, String base32Secret) {
        if (StringHelper.isEmpty(account))
            throw new IllegalArgumentException("account is required for provisioning uri");
        if (StringHelper.isEmpty(base32Secret))
            throw new IllegalArgumentException("base32Secret is required for provisioning uri");
        String safeIssuer = urlEncode(StringHelper.isEmpty(issuer) ? "" : issuer);
        String safeAccount = urlEncode(account);
        String label = safeIssuer.isEmpty() ? safeAccount : safeIssuer + ":" + safeAccount;
        StringBuilder sb = new StringBuilder("otpauth://totp/").append(label);
        sb.append("?secret=").append(base32Secret);
        if (!safeIssuer.isEmpty()) {
            sb.append("&issuer=").append(safeIssuer);
        }
        sb.append("&algorithm=SHA1&digits=").append(DIGITS).append("&period=").append(PERIOD_SECONDS);
        return sb.toString();
    }

    /**
     * 校验加密的 secret（先 {@link ITextCipher} 解密，再 base32 解码，再 HMAC 校验）。
     *
     * @param encryptedSecret    AES 密文（实体持久化形态）
     * @param code               用户输入的 6 位验证码
     * @param lastVerifiedWindow 上次成功验证的窗口号（≤0 表示无历史），当前窗口 ≤ 它则拒绝
     * @return 成功窗口号（&gt; {@code lastVerifiedWindow}），失败返回 -1
     */
    public long verify(String encryptedSecret, String code, long lastVerifiedWindow) {
        if (StringHelper.isEmpty(encryptedSecret) || StringHelper.isEmpty(code))
            return -1L;
        String base32Secret;
        try {
            base32Secret = cipher.decrypt(encryptedSecret);
        } catch (Exception e) {
            // 密文损坏/篡改：fail-closed，不抛出中断登录链，由调用方按失败计数处理
            return -1L;
        }
        if (StringHelper.isEmpty(base32Secret))
            return -1L;
        return verifyRaw(Base32.decode(base32Secret), code, CoreMetrics.currentTimeMillis(), skew, lastVerifiedWindow);
    }

    /**
     * 核心算法：对原始 secret 字节做 RFC 6238 校验（含防重放窗口判定）。
     *
     * @param secretBytes        原始 secret 字节
     * @param code               6 位验证码
     * @param timeMillis         校验时刻
     * @param skew               允许的窗口偏差
     * @param lastVerifiedWindow 上次成功窗口号（≤0 表示无历史）
     * @return 成功窗口号，失败返回 -1
     */
    public long verifyRaw(byte[] secretBytes, String code, long timeMillis, int skew, long lastVerifiedWindow) {
        if (secretBytes == null || secretBytes.length == 0 || StringHelper.isEmpty(code))
            return -1L;
        if (skew < 0)
            skew = 0;

        int inputCode = parseCode(code);
        if (inputCode < 0)
            return -1L;

        long currentWindow = timeMillis / 1000L / PERIOD_SECONDS;
        // 防重放：候选窗口必须严格大于 lastVerifiedWindow
        long floor = Math.max(currentWindow - skew, lastVerifiedWindow + 1);
        long ceil = currentWindow + skew;
        for (long w = floor; w <= ceil; w++) {
            int expected = hotp(secretBytes, w);
            if (expected == inputCode) {
                return w;
            }
        }
        return -1L;
    }

    private static int parseCode(String code) {
        String digits = code.replaceAll("\\s", "");
        if (digits.length() != DIGITS)
            return -1;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * RFC 4226 HOTP：HMAC-SHA1(secret, counter) → 动态截断 → mod 10^6。
     */
    static int hotp(byte[] secret, long counter) {
        byte[] counterBytes = new byte[8];
        long t = counter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (t & 0xFF);
            t >>>= 8;
        }
        byte[] hash = HashHelper.hmac(HMAC_ALGORITHM, counterBytes, secret);
        int offset = hash[hash.length - 1] & 0x0F;
        int truncated = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return truncated % MODULUS;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
