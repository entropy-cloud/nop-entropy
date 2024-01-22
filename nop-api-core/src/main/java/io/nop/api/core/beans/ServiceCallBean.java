/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

import java.io.Serializable;

@DataBean
public class ServiceCallBean implements Serializable {
    private static final long serialVersionUID = -7401450479448631186L;

    private String serviceName;
    private String serviceMethod;
    private Object request;

    @PropMeta(propId = 1)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    @PropMeta(propId = 2)
    public String getServiceMethod() {
        return serviceMethod;
    }

    public void setServiceMethod(String serviceMethod) {
        this.serviceMethod = serviceMethod;
    }

    @PropMeta(propId = 3)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Object getRequest() {
        return request;
    }

    public void setRequest(Object request) {
        this.request = request;
    }
}