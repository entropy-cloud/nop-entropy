package io.nop.auth.service.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.sql.Timestamp;

/**
 * 可信设备展示项（W15-impl，设计 §六 listTrustedDevices）——全部行含过期标记（支持自助清理）。
 * 不含 deviceHash（不可逆哈希非秘密，但展示无益——最小暴露面）。
 */
@DataBean
public class TrustedDeviceInfo {

    private String sid;
    private String deviceName;
    private Timestamp expireAt;
    private Timestamp lastUsedAt;
    private Timestamp createTime;
    /** 是否已过期（到期行保留审计，可自助清理；豁免判定惰性跳过）。 */
    private boolean expired;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Timestamp getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Timestamp expireAt) {
        this.expireAt = expireAt;
    }

    public Timestamp getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Timestamp lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }
}
