/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import java.io.Serializable;

/**
 * MFA challenge 状态对象（存储在 {@link MfaChallengeStore} 中）。
 * <p>
 * 必须是可 JSON 序列化的 POJO：Redis 实现通过 nop-nosql 的 {@code PrefixTextCodec}
 * （{@code PrefixEncodeHelper} → {@code $d:className\n + json}）编解码，因此要求
 * 默认构造器 + getter/setter，且字段为简单类型（设计 §3.3）。
 * <p>
 * challenge 自身只负责一次性与失败计数（{@link MfaChallengeStore#incrFailCount}），
 * 不承担 TOTP 窗口防重放（防重放是 per-user 的，见 TOTP 组件 + 实体 lastVerifiedWindow）。
 */
public class MfaChallenge implements Serializable {

    private static final long serialVersionUID = 1L;

    private String challengeToken;
    private String userId;
    private String mfaType;
    private int loginType;
    private String tenantId;
    private String phone;
    private long createdAt;
    private long expireAt;

    public MfaChallenge() {
    }

    public MfaChallenge(String challengeToken, String userId, String mfaType, int loginType,
                        String tenantId, String phone, long createdAt, long expireAt) {
        this.challengeToken = challengeToken;
        this.userId = userId;
        this.mfaType = mfaType;
        this.loginType = loginType;
        this.tenantId = tenantId;
        this.phone = phone;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
    }

    public String getChallengeToken() {
        return challengeToken;
    }

    public void setChallengeToken(String challengeToken) {
        this.challengeToken = challengeToken;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMfaType() {
        return mfaType;
    }

    public void setMfaType(String mfaType) {
        this.mfaType = mfaType;
    }

    public int getLoginType() {
        return loginType;
    }

    public void setLoginType(int loginType) {
        this.loginType = loginType;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }
}
