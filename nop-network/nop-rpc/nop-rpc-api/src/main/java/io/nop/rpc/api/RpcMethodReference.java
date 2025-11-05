/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.util.Guard;

import java.io.Serializable;

/**
 * RpcMethodReference相当于是RpcService的序列化形式。知道RpcMethodReference后，即可利用
 * IRpcServiceLocator来动态调用服务方法。
 *
 * <pre>{@code
 *     rpcServiceLocator.getService(ref.getServiceName()).callAsync(ref.getServiceMethod(),request);
 * }</pre>
 */
public class RpcMethodReference implements Serializable {
    private final String serviceName;
    private final String serviceMethod;

    public RpcMethodReference(String serviceName, String serviceMethod) {
        this.serviceName = Guard.notEmpty(serviceName, "serviceName is empty");
        this.serviceMethod = Guard.notEmpty(serviceMethod, "serviceMethod is empty");
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceMethod() {
        return serviceMethod;
    }

    public String toString() {
        return serviceName + "/" + serviceMethod;
    }

    public int hashCode() {
        return serviceName.hashCode() * 31 + serviceMethod.hashCode();
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof RpcMethodReference))
            return false;

        RpcMethodReference other = (RpcMethodReference) o;
        return serviceName.equals(other.getServiceName())
                || serviceMethod.equals(other.getServiceMethod());
    }
}
