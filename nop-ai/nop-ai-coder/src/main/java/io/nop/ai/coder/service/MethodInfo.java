package io.nop.ai.coder.service;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class MethodInfo {
    private final String serviceName;
    private final String methodName;

    public MethodInfo(@Name("serviceName") String serviceName, @Name("methodName") String methodName) {
        this.serviceName = serviceName;
        this.methodName = methodName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMethodName() {
        return methodName;
    }
}
