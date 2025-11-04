package io.nop.api.core.beans.oauth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;
import io.nop.api.core.time.CoreMetrics;

@DataBean
public class Oauth2TokenResponseBean extends ExtensibleBean {
    private String accessToken;
    private String refreshToken;
    private String tokenType;

    private long expiresIn;

    private String scope;

    private String state;

    private long createTime = CoreMetrics.currentTimeMillis();

    /**
     * 判断token是否已经超时。为了防止网络延迟等原因，提前gap毫秒过期
     *
     * @param gap 提前过期的毫秒数
     * @return 是否已经过期
     */
    public boolean isExpired(long gap) {
        return CoreMetrics.currentTimeMillis() - createTime > expiresIn * 1000 - gap;
    }

    @JsonProperty("access_token")
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @JsonProperty("refresh_token")
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @JsonProperty("token_type")
    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    @JsonProperty("expires_in")
    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @JsonIgnore
    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
