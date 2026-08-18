/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.messages;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

/**
 * WebAuthn 注册证明（attestation，W14-impl，设计 §5.3.2 注册 ceremony 的客户端回传）。
 * <p>
 * 字段与浏览器 {@code AuthenticatorAttestationResponse} 平铺对应（base64url）。
 * 经 {@code confirmWebauthnRegistration} 端点提交；attestation=none 部署语义下
 * fmt=none 直接信任 + origin/rpId 强校验（完整信任链验证设计 §七.1 deferred）。
 */
@DataBean
public class WebAuthnAttestation {
    /** CollectedClientData（base64url 编码的 JSON 文本，含 type=webauthn.create/challenge/origin）。 */
    private String clientDataJSON;
    /** attestationObject（base64url 的 CBOR 编码：fmt/attStmt/authData[attestedCredentialData+COSE 公钥]）。 */
    private String attestationObject;
    /** 传输方式（可空：internal/hybrid/usb/nfc/ble——审计与 UI 提示）。 */
    private List<String> transports;

    public String getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public String getAttestationObject() {
        return attestationObject;
    }

    public void setAttestationObject(String attestationObject) {
        this.attestationObject = attestationObject;
    }

    public List<String> getTransports() {
        return transports;
    }

    public void setTransports(List<String> transports) {
        this.transports = transports;
    }
}
