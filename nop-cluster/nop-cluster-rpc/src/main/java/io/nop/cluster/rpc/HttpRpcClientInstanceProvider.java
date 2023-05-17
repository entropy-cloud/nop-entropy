package io.nop.cluster.rpc;

import io.nop.api.core.rpc.IRpcService;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.rpc.DefaultRpcUrlBuilder;
import io.nop.http.api.client.rpc.HttpRpcService;

import javax.inject.Inject;

public class HttpRpcClientInstanceProvider implements IRpcClientInstanceProvider {
    private IHttpClient httpClient;
    private boolean useHttps;

    public void setUseHttps(boolean useHttps) {
        this.useHttps = useHttps;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public IRpcService getRpcClientInstance(ServiceInstance instance) {
        String baseUrl = useHttps ? "https://" : "http://";
        baseUrl += instance.getAddr() + ":" + instance.getPort();
        return new HttpRpcService(httpClient, new DefaultRpcUrlBuilder(baseUrl));
    }
}