package io.nop.auth.service.biz;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

@DataBean
public class DemoRequest {

    private String userId;

    public String getUserId() {
        return userId;
    }

    @PropMeta(mandatory = true)
    public void setUserId(String userId) {
        this.userId = userId;
    }
}
