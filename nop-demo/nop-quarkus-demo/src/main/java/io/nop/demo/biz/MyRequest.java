package io.nop.demo.biz;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MyRequest {
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
