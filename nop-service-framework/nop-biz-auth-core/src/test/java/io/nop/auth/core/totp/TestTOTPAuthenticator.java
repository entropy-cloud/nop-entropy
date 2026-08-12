/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.totp;

import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W4 Phase 2 regression tests for {@link TOTPAuthenticator}.
 * <p>
 * Covers: RFC 6238 Appendix B test vectors, window skew (±1), anti-replay (window ≤
 * lastVerifiedWindow rejected, no window returned), provisioning URI format, and
 * AESTextCipher wiring (decrypt path observable).
 */
public class TestTOTPAuthenticator {

    // RFC 6238 Appendix B seed for SHA-1 (ASCII), 20 bytes.
    private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.UTF_8);

    // RFC 6238 Appendix B expected 6-digit codes at specific unix times (SHA-1).
    // (seconds, expected 6-digit code)
    private static final long[][] RFC_VECTORS = {
            {59L, 287082L},
            {1111111109L, 81804L},
            {1111111111L, 50471L},
            {1234567890L, 5924L},
            {2000000000L, 279037L},
            {20000000000L, 353130L}
    };

    private final TOTPAuthenticator totp = new TOTPAuthenticator();

    @Test
    public void testRfc6238Vectors() {
        for (long[] vector : RFC_VECTORS) {
            long seconds = vector[0];
            long expectedCode = vector[1];
            long timeMillis = seconds * 1000L;
            String code = String.format("%06d", expectedCode);

            // verifyRaw should match at the exact window with skew=0
            long window = totp.verifyRaw(RFC_SEED, code, timeMillis, 0, 0L);
            assertTrue(window >= 0, "RFC vector must verify: t=" + seconds + " code=" + code);
            assertEquals(seconds / 30L, window, "matched window should be the exact window at skew=0");
        }
    }

    @Test
    public void testWindowSkewAccepted() {
        // Use a clean window boundary so offset math is unambiguous.
        long codeWindow = 56_666_666L;
        long baseSeconds = codeWindow * 30L;
        String code = String.format("%06d", TOTPAuthenticator.hotp(RFC_SEED, codeWindow));

        // +29s later still within codeWindow (same window) -> accept with skew 0
        assertTrue(totp.verifyRaw(RFC_SEED, code, (baseSeconds + 29) * 1000L, 0, 0L) >= 0,
                "same window must be accepted at skew 0");

        // exactly next window boundary (+30s): cur=codeWindow+1, range [codeWindow, codeWindow+2] with skew=1
        assertTrue(totp.verifyRaw(RFC_SEED, code, (baseSeconds + 30) * 1000L, 1, 0L) >= 0,
                "previous window must be accepted within +1 skew");

        // +2 windows away (cur=codeWindow+2, range [codeWindow+1, codeWindow+3]) -> codeWindow rejected
        assertEquals(-1L, totp.verifyRaw(RFC_SEED, code, (baseSeconds + 60) * 1000L, 1, 0L),
                "code 2 windows old must be rejected");
    }

    @Test
    public void testSkewBeyondRejected() {
        long seconds = 1_700_000_000L;
        long window = seconds / 30L;
        String code = String.format("%06d", TOTPAuthenticator.hotp(RFC_SEED, window));

        // future code from window+3 must be rejected with skew=1 (cur=window, range [window-1, window+1])
        String futureCode = String.format("%06d", TOTPAuthenticator.hotp(RFC_SEED, window + 3));
        assertEquals(-1L, totp.verifyRaw(RFC_SEED, futureCode, seconds * 1000L, 1, 0L),
                "code 3 windows in the future must be rejected");
    }

    @Test
    public void testAntiReplaySameWindowRejected() {
        long seconds = 1_700_000_000L;
        long window = seconds / 30L;
        String code = String.format("%06d", TOTPAuthenticator.hotp(RFC_SEED, window));

        // lastVerifiedWindow == current window -> must reject and return -1 (no window update)
        long result = totp.verifyRaw(RFC_SEED, code, seconds * 1000L, 1, window);
        assertEquals(-1L, result, "replay of current window (<=lastVerifiedWindow) must be rejected");

        // lastVerifiedWindow in the future -> all candidate windows rejected
        assertEquals(-1L, totp.verifyRaw(RFC_SEED, code, seconds * 1000L, 1, window + 10),
                "lastVerifiedWindow beyond all candidates must reject");

        // a fresh verification (lastVerifiedWindow < current window) returns the matched window
        long okWindow = totp.verifyRaw(RFC_SEED, code, seconds * 1000L, 1, window - 1);
        assertEquals(window, okWindow, "fresh verification returns the success window");
    }

    @Test
    public void testReplayNoWindowReturnedSoCallerDoesNotUpdate() {
        long seconds = 1_700_000_000L;
        long window = seconds / 30L;
        String code = String.format("%06d", TOTPAuthenticator.hotp(RFC_SEED, window));

        // First success at this window with no history
        long first = totp.verifyRaw(RFC_SEED, code, seconds * 1000L, 0, 0L);
        assertEquals(window, first);

        // Replay: caller now passes lastVerifiedWindow=window. Must return -1 (caller keeps old value).
        long replay = totp.verifyRaw(RFC_SEED, code, seconds * 1000L, 0, first);
        assertEquals(-1L, replay, "replay must return -1 so caller does NOT advance lastVerifiedWindow");
    }

    @Test
    public void testMalformedCodeRejected() {
        long seconds = 1_700_000_000L;
        assertEquals(-1L, totp.verifyRaw(RFC_SEED, "abc", seconds * 1000L, 1, 0L), "non-numeric rejected");
        assertEquals(-1L, totp.verifyRaw(RFC_SEED, "12345", seconds * 1000L, 1, 0L), "too short rejected");
        assertEquals(-1L, totp.verifyRaw(RFC_SEED, "1234567", seconds * 1000L, 1, 0L), "too long rejected");
        assertEquals(-1L, totp.verifyRaw(new byte[0], "123456", seconds * 1000L, 1, 0L), "empty secret rejected");
        assertEquals(-1L, totp.verifyRaw(null, "123456", seconds * 1000L, 1, 0L), "null secret rejected");
    }

    @Test
    public void testProvisioningUriFormat() {
        String secret = totp.generateSecret();
        String uri = totp.generateProvisioningUri("NopCorp", "alice@example.com", secret);

        assertTrue(uri.startsWith("otpauth://totp/"), "must be otpauth://totp scheme");
        assertTrue(uri.contains("issuer=NopCorp"), "must carry issuer param");
        assertTrue(uri.contains("secret=" + secret), "must carry the secret");
        assertTrue(uri.contains("algorithm=SHA1"), "must declare SHA1");
        assertTrue(uri.contains("digits=6"), "must declare 6 digits");
        assertTrue(uri.contains("period=30"), "must declare 30s period");
        // label is "issuer:account" url-encoded
        assertTrue(uri.contains("NopCorp%3Aalice%40example.com")
                        || uri.contains("NopCorp:alice%40example.com"),
                "label must be issuer:account, got: " + uri);
    }

    @Test
    public void testSecretIs32BytesBase32() {
        String secret = totp.generateSecret();
        assertNotNull(secret);
        // 32 bytes -> ceil(32*8/5)=52 base32 chars (+ padding to 56)
        assertTrue(secret.length() == 52 || secret.length() == 56,
                "32-byte secret base32 length around 52-56, got " + secret.length());
        // two generated secrets must differ (randomness sanity)
        assertNotEquals(secret, totp.generateSecret());
    }

    /**
     * Wiring verification (Plan Phase 2 Exit Criteria): the {@link AESTextCipher} decrypt
     * path is observable — verifying an ENCRYPTED secret must succeed, proving decrypt ran.
     */
    @Test
    public void testAesCipherWiringObservable() {
        // Use a cipher that records whether decrypt was invoked.
        RecordingCipher recorder = new RecordingCipher(new AESTextCipher());
        TOTPAuthenticator t = new TOTPAuthenticator();
        t.setCipher(recorder);

        String base32Secret = t.generateSecret();
        String encrypted = recorder.delegate.encrypt(base32Secret);

        // Build a code for the current window from the RAW secret bytes.
        byte[] raw = Base32.decode(base32Secret);
        long now = System.currentTimeMillis();
        long window = now / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
        String code = String.format("%06d", TOTPAuthenticator.hotp(raw, window));

        // Verifying the ENCRYPTED secret must succeed and exercise decrypt.
        long result = t.verify(encrypted, code, 0L);
        assertTrue(result >= 0, "verify with encrypted secret must succeed");
        assertTrue(recorder.decryptCalled, "AESTextCipher.decrypt must be invoked on the verify path");

        // Sanity: the ciphertext is not itself valid base32, so it cannot decode to the
        // raw secret — proving the success above truly came from decrypting, not from
        // matching the ciphertext bytes directly.
        assertFalse(base32Decodes(encrypted), "ciphertext must not be valid base32");
    }

    private static boolean base32Decodes(String s) {
        try {
            Base32.decode(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Test
    public void testVerifyTamperedCiphertextFails() {
        TOTPAuthenticator t = new TOTPAuthenticator();
        String base32Secret = t.generateSecret();
        String encrypted = t.getCipher().encrypt(base32Secret);
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "XX";

        byte[] raw = Base32.decode(base32Secret);
        long now = System.currentTimeMillis();
        long window = now / 1000L / TOTPAuthenticator.PERIOD_SECONDS;
        String code = String.format("%06d", TOTPAuthenticator.hotp(raw, window));

        // tampered ciphertext must fail-closed (return -1), not throw
        assertEquals(-1L, t.verify(tampered, code, 0L));
    }

    /** A delegating cipher that records decrypt invocation for wiring observability. */
    static class RecordingCipher implements ITextCipher {
        final ITextCipher delegate;
        boolean decryptCalled;

        RecordingCipher(ITextCipher delegate) {
            this.delegate = delegate;
        }

        @Override
        public ITextCipher encKey(String encKey) {
            return delegate.encKey(encKey);
        }

        @Override
        public String encrypt(String text) {
            return delegate.encrypt(text);
        }

        @Override
        public String decrypt(String text) {
            decryptCalled = true;
            return delegate.decrypt(text);
        }
    }
}
