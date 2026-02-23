package io.nop.gateway.model;

import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.ModelBasedGatewayInterceptor;
import io.nop.gateway.model._gen._GatewayInterceptorModel;

public class GatewayInterceptorModel extends _GatewayInterceptorModel{
    private IGatewayInterceptor interceptor;

    public GatewayInterceptorModel(){

    }

    public IGatewayInterceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(IGatewayInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    public IGatewayInterceptor getOrCreateInterceptor(IGatewayContext svcCtx){
        if(interceptor == null){
            String bean = getBean();
            if(bean != null){
                interceptor = (IGatewayInterceptor) svcCtx.getEvalScope().getBeanProvider().getBean(bean);
            }else{
                interceptor = new ModelBasedGatewayInterceptor(this);
            }
        }
        return interceptor;
    }
}
