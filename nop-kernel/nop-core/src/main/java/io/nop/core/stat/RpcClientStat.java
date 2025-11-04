package io.nop.core.stat;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class RpcClientStat extends AbstractExecuteStat {
    private String serviceName;
    private String serviceAction;

    public RpcClientStat(String serviceName, String serviceAction) {
        this.serviceName = serviceName;
        this.serviceAction = serviceAction;
    }

    public RpcClientStat() {
    }

    public static String buildFullServiceName(String serviceName, String serviceAction) {
        return serviceName + ':' + serviceAction;
    }

    public String getFullServiceName() {
        return buildFullServiceName(serviceName, serviceAction);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceAction() {
        return serviceAction;
    }

    public void setServiceAction(String serviceAction) {
        this.serviceAction = serviceAction;
    }
}