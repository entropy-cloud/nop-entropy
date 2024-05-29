package io.nop.demo.biz;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.auth.api.messages.LoginRequest;

@DataBean
public class LoginRequestEx extends LoginRequest {
    private String extInfo;

    public String getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo;
    }
}
