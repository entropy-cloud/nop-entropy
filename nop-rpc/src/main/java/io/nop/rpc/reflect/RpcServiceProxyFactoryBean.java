package io.nop.rpc.reflect;

import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.util.Guard;
import io.nop.core.reflect.ReflectionManager;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

public class RpcServiceProxyFactoryBean {
    private String serviceName;
    private Class<?> serviceClass;
    private List<IRpcServiceInterceptor> interceptors;
    private IRpcService rpcService;
    private IRpcMessageTransformer messageTransformer = HttpRpcMessageTransformer.INSTANCE;

    private Object serviceBean;

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setServiceClass(Class<?> serviceClass) {
        this.serviceClass = serviceClass;
    }

    public void setInterceptors(List<IRpcServiceInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void setRpcService(IRpcService rpcService) {
        this.rpcService = rpcService;
    }

    public void setMessageTransformer(IRpcMessageTransformer messageTransformer) {
        this.messageTransformer = messageTransformer;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Class<?> getServiceClass() {
        return serviceClass;
    }

    public List<IRpcServiceInterceptor> getInterceptors() {
        return interceptors;
    }

    public IRpcService getRpcService() {
        return rpcService;
    }

    public IRpcMessageTransformer getMessageTransformer() {
        return messageTransformer;
    }

    public Object getServiceBean() {
        return serviceBean;
    }

    @PostConstruct
    public void init() {
        Guard.notEmpty(getServiceName(), "serviceName");
        Guard.notNull(serviceClass, "serviceClass");

        if (interceptors == null)
            interceptors = Collections.emptyList();

        RpcInvocationHandler handler = new RpcInvocationHandler(getServiceName(), rpcService,
                interceptors, messageTransformer);
        Class[] inf = new Class[]{serviceClass};
        this.serviceBean = ReflectionManager.instance().newProxyInstance(inf, handler);
    }

    public Object getObject() {
        return serviceBean;
    }
}