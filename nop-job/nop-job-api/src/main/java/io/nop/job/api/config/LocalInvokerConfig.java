package io.nop.job.api.config;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class LocalInvokerConfig {
    private String bean;
    private String method;

    public String getBean() {
        return bean;
    }

    public void setBean(String bean) {
        this.bean = bean;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
