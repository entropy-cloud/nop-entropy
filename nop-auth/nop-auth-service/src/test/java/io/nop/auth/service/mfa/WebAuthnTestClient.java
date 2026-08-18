/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import com.upokecenter.cbor.CBORObject;
import io.nop.auth.api.messages.WebAuthnAssertion;
import io.nop.auth.api.messages.WebAuthnAttestation;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

/**
 * 测试侧 WebAuthn 认证器模拟（W14-impl Phase 2 Proof (e)：测试侧可构造合法
 * attestation/assertion 正例——Phase 1 POC 结论 (e) 的实证）。
 * <p>
 * 按 W3C WebAuthn Level 2 协议格式组装：
 * <ul>
 *   <li>注册：clientDataJSON（type=webauthn.create）+ attestationObject（CBOR：
 *       fmt=none/attStmt={}/authData=rpIdHash+flags(UP|AT)+signCount+
 *       attestedCredentialData{aaguid+credentialId+COSE ES256 公钥}）。</li>
 *   <li>断言：clientDataJSON（type=webauthn.get）+ authenticatorData（rpIdHash+flags(UP)+
 *       signCount）+ 签名（ECDSA-SHA256 DER，对 authData||SHA256(clientDataJSON)）。</li>
 * </ul>
 * COSE/CBOR 编码经库传递依赖 com.upokecenter:cbor（POC 结论：runtime scope → test
 * classpath 可用）；EC 密钥用 JDK KeyPairGenerator（P-256/ES256）。
 */
public class WebAuthnTestClient {

    public static final String RP_ID = "rp.example.com";
    public static final String RP_NAME = "Test RP";
    public static final String ORIGIN = "https://rp.example.com";

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keyPair;
    private final byte[] credentialId;
    private final byte[] cosePublicKey;

    public WebAuthnTestClient() {
        KeyMaterial material = generateKeyMaterial();
        this.keyPair = material.keyPair;
        this.credentialId = material.credentialId;
        this.cosePublicKey = material.cosePublicKey;
    }

    private static final class KeyMaterial {
        final KeyPair keyPair;
        final byte[] credentialId;
        final byte[] cosePublicKey;

        KeyMaterial(KeyPair keyPair, byte[] credentialId, byte[] cosePublicKey) {
            this.keyPair = keyPair;
            this.credentialId = credentialId;
            this.cosePublicKey = cosePublicKey;
        }
    }

    /** EC 初始化偶发失败（JVM crypto provider 竞态）——短暂退避重试。 */
    private static KeyMaterial generateKeyMaterial() {
        IllegalStateException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
                gen.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair keyPair = gen.generateKeyPair();
                byte[] credentialId = new byte[32];
                new java.security.SecureRandom().nextBytes(credentialId);
                return new KeyMaterial(keyPair, credentialId, buildCoseKeyOf(keyPair));
            } catch (Exception e) {
                failure = new IllegalStateException(
                        "cannot init webauthn test client (attempt " + (attempt + 1) + ")", e);
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw failure;
    }

    /** 模拟认证器注册（返回 attestation；成功验证后 credentialId/publicKey 与服务端落库一致）。 */
    public WebAuthnAttestation attest(String cryptoChallenge, long signCount) {
        return attest(cryptoChallenge, signCount, ORIGIN, RP_ID);
    }

    public WebAuthnAttestation attest(String cryptoChallenge, long signCount, String origin, String rpId) {
        byte[] authData = registrationAuthData(rpId, signCount);

        CBORObject attObj = CBORObject.NewMap();
        attObj.set("fmt", CBORObject.FromObject("none"));
        attObj.set("attStmt", CBORObject.NewMap());
        attObj.set("authData", CBORObject.FromObject(authData));

        WebAuthnAttestation attestation = new WebAuthnAttestation();
        attestation.setClientDataJSON(B64URL.encodeToString(
                clientDataJSON("webauthn.create", cryptoChallenge, origin)));
        attestation.setAttestationObject(B64URL.encodeToString(attObj.EncodeToBytes()));
        attestation.setTransports(List.of("usb"));
        return attestation;
    }

    /** 模拟认证器断言（signCount 为本次断言计数；签名覆盖 authData||SHA256(clientDataJSON)）。 */
    public WebAuthnAssertion assert_(String cryptoChallenge, long signCount) {
        return assert_(cryptoChallenge, signCount, ORIGIN, RP_ID, null);
    }

    public WebAuthnAssertion assert_(String cryptoChallenge, long signCount, String userHandle) {
        return assert_(cryptoChallenge, signCount, ORIGIN, RP_ID, userHandle);
    }

    public WebAuthnAssertion assert_(String cryptoChallenge, long signCount, String origin, String rpId,
                                     String userHandle) {
        byte[] authData = assertionAuthData(rpId, signCount);
        byte[] clientData = clientDataJSON("webauthn.get", cryptoChallenge, origin);
        byte[] signature = sign(authData, sha256(clientData));

        WebAuthnAssertion assertion = new WebAuthnAssertion();
        assertion.setCredentialId(B64URL.encodeToString(credentialId));
        assertion.setClientDataJSON(B64URL.encodeToString(clientData));
        assertion.setAuthenticatorData(B64URL.encodeToString(authData));
        assertion.setSignature(B64URL.encodeToString(signature));
        assertion.setUserHandle(userHandle);
        return assertion;
    }

    /** 注册成功后服务端应落库的 credentialId（base64url）。 */
    public String credentialIdB64Url() {
        return B64URL.encodeToString(credentialId);
    }

    /** 注册成功后服务端应落库的 COSE 公钥（base64url）。 */
    public String publicKeyCoseB64Url() {
        return B64URL.encodeToString(cosePublicKey);
    }

    // ===================== 协议格式组装 =====================

    private byte[] clientDataJSON(String type, String challengeB64Url, String origin) {
        return ("{\"type\":\"" + type + "\",\"challenge\":\"" + challengeB64Url
                + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** 注册 authData：rpIdHash + flags(UP=0x01|AT=0x40) + signCount + attestedCredentialData。 */
    private byte[] registrationAuthData(String rpId, long signCount) {
        byte[] authData = new byte[37 + 16 + 2 + credentialId.length + cosePublicKey.length];
        int pos = appendAuthDataHeader(authData, rpId, (byte) 0x41, signCount);
        // aaguid（16 字节，全零——测试无设备型号）
        pos += 16;
        // credentialIdLength（2 字节大端）
        authData[pos++] = (byte) ((credentialId.length >> 8) & 0xFF);
        authData[pos++] = (byte) (credentialId.length & 0xFF);
        System.arraycopy(credentialId, 0, authData, pos, credentialId.length);
        pos += credentialId.length;
        System.arraycopy(cosePublicKey, 0, authData, pos, cosePublicKey.length);
        return authData;
    }

    /** 断言 authData：rpIdHash + flags(UP=0x01) + signCount。 */
    private byte[] assertionAuthData(String rpId, long signCount) {
        byte[] authData = new byte[37];
        appendAuthDataHeader(authData, rpId, (byte) 0x01, signCount);
        return authData;
    }

    /** rpIdHash(32) + flags(1) + signCount(4) 公共头，返回写入后的偏移。 */
    private static int appendAuthDataHeader(byte[] dest, String rpId, byte flags, long signCount) {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
        System.arraycopy(rpIdHash, 0, dest, 0, 32);
        dest[32] = flags;
        dest[33] = (byte) ((signCount >> 24) & 0xFF);
        dest[34] = (byte) ((signCount >> 16) & 0xFF);
        dest[35] = (byte) ((signCount >> 8) & 0xFF);
        dest[36] = (byte) (signCount & 0xFF);
        return 37;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** COSE ES256 公钥（EC2/P-256）：{1:2, 3:-7, -1:1, -2:x, -3:y}。 */
    private static byte[] buildCoseKeyOf(KeyPair keyPair) throws Exception {
        java.security.interfaces.ECPublicKey ecPub = (java.security.interfaces.ECPublicKey) keyPair.getPublic();
        byte[] x = trimLeadingZero(ecPub.getW().getAffineX().toByteArray());
        byte[] y = trimLeadingZero(ecPub.getW().getAffineY().toByteArray());

        CBORObject key = CBORObject.NewMap();
        key.set(CBORObject.FromObject(1), CBORObject.FromObject(2));
        key.set(CBORObject.FromObject(3), CBORObject.FromObject(-7));
        key.set(CBORObject.FromObject(-1), CBORObject.FromObject(1));
        key.set(CBORObject.FromObject(-2), CBORObject.FromObject(x));
        key.set(CBORObject.FromObject(-3), CBORObject.FromObject(y));
        return key.EncodeToBytes();
    }

    /** ECDSA-SHA256 签名（JDK 输出即 DER 编码——ES256 断言签名要求）。 */
    private byte[] sign(byte[] authData, byte[] clientDataHash) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            PrivateKey privateKey = keyPair.getPrivate();
            signature.initSign(privateKey);
            signature.update(authData);
            signature.update(clientDataHash);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("cannot sign assertion", e);
        }
    }

    private static byte[] trimLeadingZero(byte[] bytes) {
        if (bytes.length == 32)
            return bytes;
        byte[] fixed = new byte[32];
        System.arraycopy(bytes, bytes.length - 32, fixed, 0, 32);
        return fixed;
    }
}
