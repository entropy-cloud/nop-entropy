package io.nop.gateway.support;

import io.nop.http.api.client.IHttpClient;

import java.lang.reflect.Proxy;

/**
 * 为单元测试提供 IHttpClient 占位 bean。
 * gateway-defaults.beans.xml 中 AiFailoverGatewayInterceptor 通过按类型自动装配依赖宿主应用提供的
 * IHttpClient；裸测试 classpath 中没有该 bean，因此用空实现满足装配（本测试不触发任何 HTTP 调用）。
 */
public class NoopHttpClientFactory {

    public static IHttpClient noopClient() {
        return (IHttpClient) Proxy.newProxyInstance(IHttpClient.class.getClassLoader(),
                new Class<?>[]{IHttpClient.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException("IHttpClient stub: no HTTP call expected in tests");
                });
    }
}
