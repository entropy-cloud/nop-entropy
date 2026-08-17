/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.auth.core.mfa.store.MfaChallenge;
import io.nop.auth.core.mfa.store.MfaChallengeStore;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_WEBAUTHN;

/**
 * MFA challenge payload 契约辅助（W14-impl，设计 §5.3.2 cryptoChallenge 承载裁定）：
 * webauthn 类型的 challenge 在 <b>create 时一次写入</b> payload.cryptoChallenge（32 字节随机
 * base64url），不提供后置更新原语（多次取 options 幂等——只读复用同一 payload；challenge
 * 自身的一次性即防重放）。
 * <p>
 * <b>challenge 创建侧三触点同步</b>（W13 登记的同构副本不变式，W14 履行）：
 * ①{@code LoginServiceImpl.checkMfaRequired}；②{@code OperationMfaCheckerImpl}（操作级，
 * 与 operation/sessionId 同 payload）；③{@code MfaLoginPolicyServiceImpl.checkMfaForUserName}
 * （OAuth/SSO 入口同构副本）——①③经 {@link #createLoginChallenge} 收敛共用，②按同 key 增量。
 */
public final class MfaChallengeHelper {

    /** payload 契约键：密码学挑战（webauthn）。 */
    public static final String PAYLOAD_CRYPTO_CHALLENGE = "cryptoChallenge";

    /** payload 契约键：会话 ID（operation/register/unbind 场景绑定当前会话）。 */
    public static final String PAYLOAD_SESSION_ID = OperationMfaCheckerImpl.PAYLOAD_SESSION_ID;

    private MfaChallengeHelper() {
    }

    /** 生成 32 字节随机密码学挑战（base64url 无填充）。 */
    public static String randomCryptoChallenge() {
        return WebAuthnAuthenticator.randomCryptoChallenge();
    }

    /**
     * 登录级 challenge 创建（webauthn 类型增量 cryptoChallenge payload；其余类型 payload=null
     * 走一期五参语义——一期调用点行为逐字节等价）。
     */
    public static String createLoginChallenge(MfaChallengeStore store, String userId, String mfaType,
                                              int loginType, String tenantId, String phone) {
        if (!MFA_TYPE_WEBAUTHN.equals(mfaType)) {
            return store.create(userId, mfaType, loginType, tenantId, phone);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(PAYLOAD_CRYPTO_CHALLENGE, randomCryptoChallenge());
        return store.create(MfaChallenge.SCENE_LOGIN, userId, mfaType, loginType, tenantId, phone,
                JsonTool.stringify(payload));
    }

    /** 构造带会话绑定的 webauthn 场景 payload（register/unbind：{sessionId, cryptoChallenge}）。 */
    public static String webauthnScenePayload(String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(PAYLOAD_SESSION_ID, sessionId);
        payload.put(PAYLOAD_CRYPTO_CHALLENGE, randomCryptoChallenge());
        return JsonTool.stringify(payload);
    }

    /**
     * 读取 challenge payload 中的密码学挑战（webauthn 断言/证明验证的防自造挑战比对源）。
     * 缺失（payload 空 / 非 JSON / 无该键）返回 null——调用方按 fail-closed 处理。
     */
    public static String cryptoChallengeOf(MfaChallenge challenge) {
        if (challenge == null || StringHelper.isEmpty(challenge.getPayload()))
            return null;
        Map<String, Object> payload = JsonTool.parseMap(challenge.getPayload());
        if (payload == null)
            return null;
        Object crypto = payload.get(PAYLOAD_CRYPTO_CHALLENGE);
        return crypto == null ? null : crypto.toString();
    }

    /** 读取 challenge payload 中的会话 ID（scene 非 login 的同会话校验源）。缺失返回 null。 */
    public static String sessionIdOf(MfaChallenge challenge) {
        if (challenge == null || StringHelper.isEmpty(challenge.getPayload()))
            return null;
        Map<String, Object> payload = JsonTool.parseMap(challenge.getPayload());
        if (payload == null)
            return null;
        Object sessionId = payload.get(PAYLOAD_SESSION_ID);
        return sessionId == null ? null : sessionId.toString();
    }
}
