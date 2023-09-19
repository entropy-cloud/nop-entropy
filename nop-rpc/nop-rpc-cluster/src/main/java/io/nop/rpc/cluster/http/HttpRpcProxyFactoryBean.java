package io.nop.rpc.cluster.http;

import io.nop.http.api.client.IHttpClient;
import io.nop.rpc.core.reflect.RpcServiceProxyFactoryBean;
import io.nop.rpc.http.DefaultRpcUrlBuilder;
import io.nop.rpc.http.HttpRpcService;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

public class HttpRpcProxyFactoryBean extends RpcServiceProxyFactoryBean {
    private IHttpClient httpClient;
    private String baseUrl;

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    @PostConstruct
    public void init() {
        HttpRpcService rpcService = new HttpRpcService(httpClient, new DefaultRpcUrlBuilder(baseUrl));
        setRpcService(rpcService);
        super.init();
    }
}
