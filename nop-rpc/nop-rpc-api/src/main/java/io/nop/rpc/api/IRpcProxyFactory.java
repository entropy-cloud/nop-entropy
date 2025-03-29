package io.nop.rpc.api;

public interface IRpcProxyFactory {
    String getServiceName();

    Class<?> getServiceClass();

    Object getObject();
}