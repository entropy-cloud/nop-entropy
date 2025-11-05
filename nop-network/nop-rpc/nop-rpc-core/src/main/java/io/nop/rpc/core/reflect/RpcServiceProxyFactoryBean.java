/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.core.reflect;

import io.nop.api.core.util.Guard;
import io.nop.core.reflect.ReflectionManager;
import io.nop.rpc.api.IAopDynamicProxy;
import io.nop.rpc.api.IRpcService;
import io.nop.rpc.api.IRpcServiceInterceptor;
import io.nop.rpc.api.IRpcProxyFactory;
import jakarta.annotation.PostConstruct;

import java.util.Collections;
import java.util.List;

public class RpcServiceProxyFactoryBean implements IRpcProxyFactory {
    private String serviceName;
    private Class<?> serviceClass;
    private List<IRpcServiceInterceptor> interceptors;
    private IRpcService rpcService;
    private IRpcMessageTransformer messageTransformer = HttpRpcMessageTransformer.INSTANCE;

    private Object serviceBean;

    private int retryCount;

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

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

    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
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
        Class[] inf = new Class[]{serviceClass, IAopDynamicProxy.class};
        this.serviceBean = ReflectionManager.instance().newProxyInstance(inf, handler);
    }

    @Override
    public Object getObject() {
        return serviceBean;
    }
}