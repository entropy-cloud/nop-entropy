/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.mfa.store;

import io.nop.api.core.annotations.data.DataBean;

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
 * <p>
 * 场景化扩展（设计 §3.1 结论 4 / §3.3）：{@code scene}（缺省 login）/{@code payload}
 * （场景数据 JSON 字符串，一次写入只读）/{@code verifiedAt}（操作级票状态）。三字段均为
 * 可空简单类型 + 无参默认——Redis 滚动升级时老进程写的新 JSON 增量 key 与新进程读的老
 * JSON（缺新 key）互不破坏（unknown-prop 容忍由 JsonTool 反序列化配置保证，测试钉定）。
 * scene==null 语义等同 {@link #SCENE_LOGIN}（一期存量数据兼容口径）。
 */
@DataBean
public class MfaChallenge implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录级 challenge（一期唯一场景，缺省值）。 */
    public static final String SCENE_LOGIN = "login";

    /** 操作级 challenge（会话内敏感操作二次验证，验证后转一次性短 TTL 票）。 */
    public static final String SCENE_OPERATION = "operation";

    /**
     * 登记通道验证票场景（W13-impl，设计 §4.3——防 enrollment attack：受限会话内 bindMfa
     * 的前置门槛）。verifyChannelProof 校验 proof:{userId} 短信码成功后创建该场景已验证票
     * （短 TTL，复用 op-ticket-expire-seconds 票窗口语义），bindMfa 经 token 往返一次性消费
     * （设计伪代码的 peekVerified(scene, userId) 查找原语不落地——见 W13 §4.6 回写）。
     */
    public static final String SCENE_CHANNEL_PROOF = "channel-proof";

    private String challengeToken;
    private String userId;
    private String mfaType;
    private int loginType;
    private String tenantId;
    private String phone;
    private long createdAt;
    private long expireAt;
    private String scene;
    private String payload;
    private Long verifiedAt;

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

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Long getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Long verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
