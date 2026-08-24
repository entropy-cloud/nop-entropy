package io.nop.gateway.model;

import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.ModelBasedGatewayInterceptor;
import io.nop.gateway.model._gen._GatewayInterceptorModel;

public class GatewayInterceptorModel extends _GatewayInterceptorModel{
    // GatewayModel 经 ResourceCacheEntry 跨请求共享，多线程并发首调会读写该字段：
    // volatile 保证安全发布（重复创建是幂等且无害的竞态）
    private volatile IGatewayInterceptor interceptor;

    public GatewayInterceptorModel(){

    }

    public IGatewayInterceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(IGatewayInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public IGatewayInterceptor getOrCreateInterceptor(IGatewayContext svcCtx){
        IGatewayInterceptor cached = interceptor;
        if (cached == null) {
            String bean = getBean();
            if (bean != null) {
                cached = (IGatewayInterceptor) svcCtx.getEvalScope().getBeanProvider().getBean(bean);
            } else {
                cached = new ModelBasedGatewayInterceptor(this);
            }
            interceptor = cached;
        }
        return cached;
    }
}
