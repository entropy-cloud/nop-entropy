/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.api.messages;

import io.nop.api.core.annotations.data.DataBean;

/**
 * WebAuthn 断言（W14-impl，设计 §5.3.2 / §3.1 结论 5 统一验证载体的 assertion 侧）。
 * <p>
 * 字段与浏览器 {@code PublicKeyCredential<AuthenticatorAssertionResponse>} 平铺对应，全部为
 * base64url 编码字符串（不经 CBOR——authenticatorData/signature 为原始字节，clientDataJSON
 * 为 JSON 文本）。经 {@code MfaVerifyRequest.assertion}（登录级）/ {@code
 * MfaVerifyOperationRequest.assertion}（操作级）/ {@code unbindMfa} 扩展参数（解绑级）提交，
 * 由共享因子校验组件的 webauthn 分支收敛验证（防客户端自造挑战：cryptoChallenge 取自
 * 服务端 challenge payload，不在此载体上）。
 */
@DataBean
public class WebAuthnAssertion {
    /** credential ID（base64url）——按此定位用户的 enabled credential 行。 */
    private String credentialId;
    /** CollectedClientData（base64url 编码的 JSON 文本，含 type/challenge/origin）。 */
    private String clientDataJSON;
    /** authenticatorData 原始字节（base64url：rpIdHash + flags + signCount + [... extensions]）。 */
    private String authenticatorData;
    /** 断言签名（base64url，对 authenticatorData || SHA256(clientDataJSON) 的签名）。 */
    private String signature;
    /** 用户句柄（base64url，可空——部分认证器不回传；回传时须与注册时一致）。 */
    private String userHandle;

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    public String getClientDataJSON() {
        return clientDataJSON;
    }

    public void setClientDataJSON(String clientDataJSON) {
        this.clientDataJSON = clientDataJSON;
    }

    public String getAuthenticatorData() {
        return authenticatorData;
    }

    public void setAuthenticatorData(String authenticatorData) {
        this.authenticatorData = authenticatorData;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getUserHandle() {
        return userHandle;
    }

    public void setUserHandle(String userHandle) {
        this.userHandle = userHandle;
    }
}
