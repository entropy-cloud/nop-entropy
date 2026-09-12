/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.auth.service.NopAuthErrors;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.PublicKeyCredentialType;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.messages.WebAuthnAssertion;
import io.nop.auth.api.messages.WebAuthnAttestation;
import io.nop.auth.api.messages.WebAuthnCreationOptions;
import io.nop.auth.api.messages.WebAuthnRequestOptions;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_WEBAUTHN_ORIGINS;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_WEBAUTHN_RP_ID;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_WEBAUTHN_RP_NAME;
import static io.nop.auth.service.NopAuthErrors.ARG_MFA_TYPE;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;

/**
 * WebAuthn/FIDO2 验证器组件（W14-impl，设计 §5.3.2）：封装 Yubico {@code webauthn-server-core}
 * 库——库类型不外溢到方法签名（出入参一律平台消息类/base64url 字符串，设计 §5.3.2 边界）。
 * <ul>
 *   <li><b>attestation 验证</b>（注册 ceremony）：clientData.challenge 匹配服务端
 *       cryptoChallenge / origin 精确匹配（allowOriginPort=false，fail-closed 防钓鱼域）/
 *       rpId 哈希校验 / fmt=none 直接信任（完整信任链设计 §七.1 deferred）。</li>
 *   <li><b>断言验证</b>（认证/操作级/解绑共用）：COSE 公钥验签 + challenge 匹配 + origin/rpId
 *       校验 + signCount 单调递增判定（count=0 认证器跳过单调校验——协议允许，返回审计标记）。</li>
 *   <li><b>options 构造</b>：creationOptions（excludeCredentials 防重复注册）/
 *       requestOptions（allowCredentials 收敛该用户 enabled credentials）。</li>
 *   <li><b>配置 fail-closed</b>：rp-id/rp-name/origins 任一缺失即显式报错（不静默放行验证）。</li>
 *   <li><b>验证失败契约</b>：返回 null（布尔语义，对齐 {@link MfaFactorVerifier}——失败计数与
 *       MFA_FAIL 错误码留调用方）；库异常不外溢（LOG.debug 记录细节）。</li>
 * </ul>
 * RelyingParty 实例按次构造（纯配置持有对象，构造廉价）：断言验证经一次性
 * {@link SingleCredentialRepository} 适配当前 credential 行（publicKey + prevSignCount），
 * 避免组件持有可变全局状态。
 */
public class WebAuthnAuthenticator {

    static final Logger LOG = LoggerFactory.getLogger(WebAuthnAuthenticator.class);

    /** 密码学挑战字节数（设计 §5.3.2：32 字节随机）。 */
    private static final int CHALLENGE_BYTES = 32;

    /** options 下发默认超时（毫秒，仅提示客户端，不承担服务端约束）。 */
    private static final long OPTIONS_TIMEOUT_MILLIS = 120_000L;

    /** 支持的公钥算法（COSE alg：ES256=-7 / RS256=-257——与 PublicKeyCredentialParameters 对齐）。 */
    private static final List<String> SUPPORTED_ALGS = List.of("ES256", "RS256");

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成 32 字节随机密码学挑战（base64url 无填充）。 */
    public static String randomCryptoChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        RANDOM.nextBytes(bytes);
        return B64URL.encodeToString(bytes);
    }

    // ===================== 配置 =====================

    /** rp-id/rp-name/origins 三项齐备才可用（缺配置显式报错 fail-closed）。 */
    public boolean isConfigured() {
        return !StringHelper.isEmpty(CFG_AUTH_MFA_WEBAUTHN_RP_ID.get())
                && !StringHelper.isEmpty(CFG_AUTH_MFA_WEBAUTHN_RP_NAME.get())
                && !StringHelper.isEmpty(CFG_AUTH_MFA_WEBAUTHN_ORIGINS.get());
    }

    private RelyingPartyIdentity requireIdentity() {
        requireConfigured();
        return RelyingPartyIdentity.builder()
                .id(CFG_AUTH_MFA_WEBAUTHN_RP_ID.get())
                .name(CFG_AUTH_MFA_WEBAUTHN_RP_NAME.get())
                .build();
    }

    private Set<String> requireOrigins() {
        requireConfigured();
        Set<String> origins = new LinkedHashSet<>();
        for (String part : CFG_AUTH_MFA_WEBAUTHN_ORIGINS.get().split(",")) {
            String o = part.trim();
            if (!o.isEmpty())
                origins.add(URI.create(o).toString());
        }
        if (origins.isEmpty())
            throw notConfigured();
        return origins;
    }

    private void requireConfigured() {
        if (!isConfigured())
            throw notConfigured();
    }

    private static NopException notConfigured() {
        return new NopException(ERR_AUTH_INVALID_LOGIN_REQUEST)
                .param(ARG_MFA_TYPE, "webauthn")
                .param("msg", "WebAuthn RP config is incomplete; configure nop.auth.mfa.webauthn.rp-id/rp-name/origins to enable webauthn MFA");
    }

    // ===================== options 构造 =====================

    /**
     * 注册 creationOptions（excludeCredentials = 该用户既有 credential，防同一钥匙重复注册）。
     * challenge 由调用方生成并写入 challenge payload（一次写入契约在 store 层）。
     */
    public WebAuthnCreationOptions buildCreationOptions(String cryptoChallenge, String userId, String userName,
                                                         List<String> existingCredentialIds) {
        RelyingPartyIdentity identity = requireIdentity();
        WebAuthnCreationOptions options = new WebAuthnCreationOptions();
        options.setChallenge(cryptoChallenge);
        options.setRpId(identity.getId());
        options.setRpName(identity.getName());
        options.setUserId(B64URL.encodeToString(userId.getBytes(StringHelper.CHARSET_UTF8)));
        options.setUserName(userName);
        options.setUserDisplayName(userName);
        options.setPubKeyCredParams(SUPPORTED_ALGS);
        options.setExcludeCredentials(existingCredentialIds == null
                ? Collections.emptyList() : new ArrayList<>(existingCredentialIds));
        options.setTimeout(OPTIONS_TIMEOUT_MILLIS);
        return options;
    }

    /**
     * 认证 requestOptions（allowCredentials = 该用户 enabled credentials；challenge 为
     * challenge payload 中 cryptoChallenge 的只读复用）。
     */
    public WebAuthnRequestOptions buildRequestOptions(String cryptoChallenge, List<String> enabledCredentialIds) {
        WebAuthnRequestOptions options = new WebAuthnRequestOptions();
        options.setChallenge(cryptoChallenge);
        options.setRpId(requireIdentity().getId());
        options.setAllowCredentials(enabledCredentialIds == null
                ? Collections.emptyList() : new ArrayList<>(enabledCredentialIds));
        options.setUserVerification("preferred");
        options.setTimeout(OPTIONS_TIMEOUT_MILLIS);
        return options;
    }

    // ===================== attestation 验证 =====================

    /**
     * 注册证明验证结果：credentialId（base64url）/ COSE 公钥（base64url）/ 注册时 signCount /
     * 传输方式。验证失败返回 null（调用方负责失败计数与 MFA_FAIL）。
     */
    public static final class RegistrationCheck {
        private final String credentialId;
        private final String publicKeyCose;
        private final long signCount;

        public RegistrationCheck(String credentialId, String publicKeyCose, long signCount) {
            this.credentialId = credentialId;
            this.publicKeyCose = publicKeyCose;
            this.signCount = signCount;
        }

        public String getCredentialId() {
            return credentialId;
        }

        public String getPublicKeyCose() {
            return publicKeyCose;
        }

        public long getSignCount() {
            return signCount;
        }
    }

    /**
     * 验证注册证明（设计 §5.3.2 注册 ceremony）：clientData.type=webauthn.create、challenge
     * 匹配服务端 cryptoChallenge（防客户端自造挑战）、origin 匹配、rpId 哈希、UP/AT 标志、
     * COSE 公钥与 alg 一致性；fmt=none 直接信任。
     *
     * @param cryptoChallenge 服务端 challenge payload 中的密码学挑战（base64url）
     * @param userId          用户 ID（userHandle 绑定）
     * @param userName        用户名（UserIdentity.name）
     * @param attestation     客户端回传证明
     * @return 验证结果；失败返回 null（不抛库异常）
     */
    public RegistrationCheck verifyRegistration(String cryptoChallenge, String userId, String userName,
                                                WebAuthnAttestation attestation) {
        if (attestation == null || StringHelper.isEmpty(attestation.getClientDataJSON())
                || StringHelper.isEmpty(attestation.getAttestationObject()))
            return null;
        try {
            RelyingPartyIdentity identity = requireIdentity();
            com.yubico.webauthn.data.PublicKeyCredentialCreationOptions options =
                    com.yubico.webauthn.data.PublicKeyCredentialCreationOptions.builder()
                            .rp(identity)
                            .user(com.yubico.webauthn.data.UserIdentity.builder()
                                    .name(userName)
                                    .displayName(userName)
                                    .id(new ByteArray(userId.getBytes(StringHelper.CHARSET_UTF8)))
                                    .build())
                            .challenge(ByteArray.fromBase64Url(cryptoChallenge))
                            .pubKeyCredParams(pubKeyCredParams())
                            .timeout(OPTIONS_TIMEOUT_MILLIS)
                            .build();

            com.yubico.webauthn.data.AuthenticatorAttestationResponse attResp =
                    com.yubico.webauthn.data.AuthenticatorAttestationResponse.builder()
                            .attestationObject(ByteArray.fromBase64Url(attestation.getAttestationObject()))
                            .clientDataJSON(ByteArray.fromBase64Url(attestation.getClientDataJSON()))
                            .build();
            // credentialId 取自 attestedCredentialData（与证明材料同源，避免客户端 id 字段伪造面）
            ByteArray credentialId = attResp.getAttestation().getAuthenticatorData().getAttestedCredentialData()
                    .orElseThrow(() -> new IllegalArgumentException("attestation has no attestedCredentialData"))
                    .getCredentialId();
            PublicKeyCredential<com.yubico.webauthn.data.AuthenticatorAttestationResponse,
                    com.yubico.webauthn.data.ClientRegistrationExtensionOutputs> pkc =
                    PublicKeyCredential.<com.yubico.webauthn.data.AuthenticatorAttestationResponse,
                            com.yubico.webauthn.data.ClientRegistrationExtensionOutputs>builder()
                            .id(credentialId)
                            .response(attResp)
                            .clientExtensionResults(
                                    com.yubico.webauthn.data.ClientRegistrationExtensionOutputs.builder().build())
                            .build();

            com.yubico.webauthn.FinishRegistrationOptions finish = com.yubico.webauthn.FinishRegistrationOptions
                    .builder()
                    .request(options)
                    .response(pkc)
                    .build();

            RelyingParty rp = RelyingParty.builder()
                    .identity(identity)
                    .credentialRepository(EMPTY_REPOSITORY)
                    .origins(requireOrigins())
                    .build();
            com.yubico.webauthn.RegistrationResult result = rp.finishRegistration(finish);
            return new RegistrationCheck(
                    result.getKeyId().getId().getBase64Url(),
                    result.getPublicKeyCose().getBase64Url(),
                    result.getSignatureCount());
        } catch (RegistrationFailedException e) {
            LOG.debug("nop.auth.webauthn-registration-failed:userId={}", userId, e);
            return null;
        } catch (Exception e) {
            // 输入格式错误（base64url/CBOR 解析失败）等价验证失败：fail-closed，不外溢异常
            LOG.debug("nop.auth.webauthn-registration-malformed:userId={}", userId, e);
            return null;
        }
    }

    // ===================== 断言验证 =====================

    /**
     * 断言验证结果：新 signCount + 零计数标记（count=0 认证器跳过单调校验，调用方记审计）。
     */
    public static final class AssertionCheck {
        private final long signatureCount;
        private final boolean zeroCounter;

        public AssertionCheck(long signatureCount, boolean zeroCounter) {
            this.signatureCount = signatureCount;
            this.zeroCounter = zeroCounter;
        }

        public long getSignatureCount() {
            return signatureCount;
        }

        public boolean isZeroCounter() {
            return zeroCounter;
        }
    }

    /**
     * 验证断言（设计 §5.3.2 认证 ceremony——登录级/操作级/解绑共用语义）：clientData.
     * type=webauthn.get、challenge 匹配 cryptoChallenge、origin 匹配、rpId 哈希、COSE 公钥验签
     * （authenticatorData || SHA256(clientDataJSON)）、signCount 单调递增判定（库内建：新计数
     * 与既存计数任一非零即要求严格递增；双零=认证器不维护计数，跳过单调校验）。
     * <p>
     * userHandle 绑定：request 侧以服务端已知的 {@code userId} 字节为权威句柄（注册时
     * creationOptions.userId 即该值）；assertion 携带句柄时库强校验两者一致（Step6），
     * credential 定位（本人 userId 维度查找）由调用方承担。
     *
     * @param publicKeyCoseB64 credential 行存储的 COSE 公钥（base64url，注册时落库）
     * @param cryptoChallenge  服务端 challenge payload 中的密码学挑战（base64url）
     * @param assertion        客户端断言
     * @param prevSignCount    credential 行当前 signCount（单调递增基线）
     * @param userId           credential 归属用户 ID（userHandle 权威值）
     * @return 验证结果（含新计数与零计数标记）；失败返回 null
     */
    public AssertionCheck verifyAssertion(String publicKeyCoseB64, String cryptoChallenge,
                                          WebAuthnAssertion assertion, long prevSignCount, String userId) {
        if (assertion == null || StringHelper.isEmpty(assertion.getCredentialId())
                || StringHelper.isEmpty(assertion.getClientDataJSON())
                || StringHelper.isEmpty(assertion.getAuthenticatorData())
                || StringHelper.isEmpty(assertion.getSignature()))
            return null;
        try {
            RelyingPartyIdentity identity = requireIdentity();
            ByteArray credentialId = ByteArray.fromBase64Url(assertion.getCredentialId());
            ByteArray userHandle = userHandleOf(assertion);

            com.yubico.webauthn.data.AuthenticatorAssertionResponse resp =
                    com.yubico.webauthn.data.AuthenticatorAssertionResponse.builder()
                            .authenticatorData(ByteArray.fromBase64Url(assertion.getAuthenticatorData()))
                            .clientDataJSON(ByteArray.fromBase64Url(assertion.getClientDataJSON()))
                            .signature(ByteArray.fromBase64Url(assertion.getSignature()))
                            .userHandle(userHandle == null ? Optional.empty() : Optional.of(userHandle))
                            .build();
            PublicKeyCredential<com.yubico.webauthn.data.AuthenticatorAssertionResponse,
                    com.yubico.webauthn.data.ClientAssertionExtensionOutputs> pkc =
                    PublicKeyCredential.<com.yubico.webauthn.data.AuthenticatorAssertionResponse,
                            com.yubico.webauthn.data.ClientAssertionExtensionOutputs>builder()
                            .id(credentialId)
                            .response(resp)
                            .clientExtensionResults(com.yubico.webauthn.data.ClientAssertionExtensionOutputs.builder().build())
                            .build();

            com.yubico.webauthn.data.PublicKeyCredentialRequestOptions requestOptions =
                    com.yubico.webauthn.data.PublicKeyCredentialRequestOptions.builder()
                            .challenge(ByteArray.fromBase64Url(cryptoChallenge))
                            .rpId(identity.getId())
                            .allowCredentials(Collections.singletonList(
                                    PublicKeyCredentialDescriptor.builder()
                                            .id(credentialId)
                                            .type(PublicKeyCredentialType.PUBLIC_KEY)
                                            .build()))
                            .build();
            com.yubico.webauthn.AssertionRequest request = com.yubico.webauthn.AssertionRequest.builder()
                    .publicKeyCredentialRequestOptions(requestOptions)
                    // userHandle 权威值 = 服务端 userId 字节（库要求 username/userHandle 至少其一；
                    // assertion 携带句柄时被强校验一致——防句柄漂移）
                    .userHandle(new ByteArray(userId.getBytes(StringHelper.CHARSET_UTF8)))
                    .build();

            // 单 credential 仓储适配：publicKey + prevSignCount + userId 句柄即本行数据
            //（RelyingParty 按次构造）
            RegisteredCredential registered = RegisteredCredential.builder()
                    .credentialId(credentialId)
                    .userHandle(new ByteArray(userId.getBytes(StringHelper.CHARSET_UTF8)))
                    .publicKeyCose(ByteArray.fromBase64Url(publicKeyCoseB64))
                    .signatureCount(prevSignCount)
                    .build();
            RelyingParty rp = RelyingParty.builder()
                    .identity(identity)
                    .credentialRepository(new SingleCredentialRepository(registered))
                    .origins(requireOrigins())
                    .build();

            com.yubico.webauthn.AssertionResult result = rp.finishAssertion(
                    com.yubico.webauthn.FinishAssertionOptions.builder()
                            .request(request)
                            .response(pkc)
                            .build());
            if (!result.isSuccess())
                return null;
            long newCount = result.getSignatureCount();
            return new AssertionCheck(newCount, newCount == 0);
        } catch (AssertionFailedException e) {
            LOG.debug("nop.auth.webauthn-assertion-failed:credentialId={}",
                    assertion.getCredentialId(), e);
            return null;
        } catch (Exception e) {
            LOG.debug("nop.auth.webauthn-assertion-malformed:credentialId={}",
                    assertion.getCredentialId(), e);
            return null;
        }
    }

    private static ByteArray userHandleOf(WebAuthnAssertion assertion) {
        if (StringHelper.isEmpty(assertion.getUserHandle()))
            return null;
        try {
            return ByteArray.fromBase64Url(assertion.getUserHandle());
        } catch (com.yubico.webauthn.data.exception.Base64UrlException e) {
            // 非法 base64url 等价输入格式错误（调用方 catch-all 收敛为验证失败）
            throw new NopException(NopAuthErrors.ERR_AUTH_MALFORMED_USER_HANDLE, e);
        }
    }

    private static List<com.yubico.webauthn.data.PublicKeyCredentialParameters> pubKeyCredParams() {
        List<com.yubico.webauthn.data.PublicKeyCredentialParameters> params = new ArrayList<>(2);
        params.add(com.yubico.webauthn.data.PublicKeyCredentialParameters.ES256);
        params.add(com.yubico.webauthn.data.PublicKeyCredentialParameters.RS256);
        return params;
    }

    /** 空仓储（注册验证不需要 credential 查找）。 */
    private static final CredentialRepository EMPTY_REPOSITORY = new CredentialRepository() {
        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return Collections.emptySet();
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            return Optional.empty();
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return Optional.empty();
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            return Optional.empty();
        }

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return Collections.emptySet();
        }
    };

    /**
     * 单 credential 仓储适配：断言验证时把目标行（publicKey + prevSignCount + userId 句柄）
     * 包装为库要求的 {@link CredentialRepository}——按次构造、无全局可变状态。
     * userHandle = 服务端 userId 字节（与 AssertionRequest 侧一致；assertion 携带句柄时被
     * 库强校验等于该值）。
     */
    private static final class SingleCredentialRepository implements CredentialRepository {
        private final RegisteredCredential credential;

        SingleCredentialRepository(RegisteredCredential credential) {
            this.credential = credential;
        }

        private boolean idMatches(ByteArray credentialId) {
            return credentialId != null && credential.getCredentialId().equals(credentialId);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return idMatches(credentialId) ? Collections.singleton(credential) : Collections.emptySet();
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            if (!idMatches(credentialId))
                return Optional.empty();
            if (userHandle != null && !credential.getUserHandle().equals(userHandle))
                return Optional.empty();
            return Optional.of(credential);
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return Optional.of(credential.getUserHandle());
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            return credential.getUserHandle().equals(userHandle) ? Optional.of("user") : Optional.empty();
        }

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return Collections.singleton(PublicKeyCredentialDescriptor.builder()
                    .id(credential.getCredentialId())
                    .type(PublicKeyCredentialType.PUBLIC_KEY)
                    .build());
        }
    }
}
