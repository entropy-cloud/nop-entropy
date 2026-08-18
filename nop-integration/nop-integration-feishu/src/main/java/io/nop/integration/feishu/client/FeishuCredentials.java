package io.nop.integration.feishu.client;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.ioc.InjectValue;

/**
 * Feishu/Lark application credentials consumed by {@link FeishuClient}.
 *
 * <p>Values are injected from Nop configuration via {@link InjectValue}
 * setters (config keys {@code nop.integration.feishu.*}). Secret values
 * (appSecret, encryptKey) may use the Nop {@code @sec:} encrypted-value form:
 * the platform's {@code DefaultConfigValueEnhancer} recognises the
 * {@code @sec:} prefix and decrypts it through {@code AESTextCipher} before
 * injection, so secrets never need to appear in cleartext files.
 *
 * <p>(The design doc originally referred to this protection as
 * "nop-config-encrypt"; that is a design-layer term — the actual mechanism is
 * {@code DefaultConfigValueEnhancer} + {@code @sec:} prefix +
 * {@code AESTextCipher}.)
 */
@DataBean
public class FeishuCredentials {

    private String appId;
    private String appSecret;
    private String verificationToken;
    private String encryptKey;

    /**
     * 可选的凭证库引用（W16-impl-ext，设计 §4.1 结论 7）：非空时 {@code feishu-app} 四字段
     * （appId/appSecret/verificationToken/encryptKey）在 {@code FeishuClient.start} 期整组取自
     * 凭证库（解析副本，同名静态值忽略）；空/空白时四键静态值现状路径（既有部署零回归）。
     * 配置键 {@code nop.integration.feishu.credentialId}（与现有四键并列，空串缺省）。
     */
    private String credentialId;

    public String getAppId() {
        return appId;
    }

    @InjectValue("@cfg:nop.integration.feishu.appId|")
    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    @InjectValue("@cfg:nop.integration.feishu.appSecret|")
    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    @InjectValue("@cfg:nop.integration.feishu.verificationToken|")
    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    @InjectValue("@cfg:nop.integration.feishu.encryptKey|")
    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getCredentialId() {
        return credentialId;
    }

    @InjectValue("@cfg:nop.integration.feishu.credentialId|")
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }
}
