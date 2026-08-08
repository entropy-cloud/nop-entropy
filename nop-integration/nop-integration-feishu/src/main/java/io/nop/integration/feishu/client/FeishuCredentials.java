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
}
