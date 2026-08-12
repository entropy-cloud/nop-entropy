package io.nop.auth.service.biz.dto;

import io.nop.api.core.annotations.data.DataBean;

/**
 * getMfaStatus 的返回结果（设计 §3.6）。返回 mfaType/status/phone（脱敏），
 * <b>永不返回 secret</b>（明文边界约束）。
 */
@DataBean
public class MfaStatusResult {

    private String mfaType;
    private String status;
    /** 手机号脱敏（仅显示后 4 位）。 */
    private String phone;

    public String getMfaType() {
        return mfaType;
    }

    public void setMfaType(String mfaType) {
        this.mfaType = mfaType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
