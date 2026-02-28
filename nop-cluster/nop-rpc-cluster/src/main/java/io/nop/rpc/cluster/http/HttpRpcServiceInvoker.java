package io.nop.rpc.cluster.http;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.api.core.rpc.IRpcServiceLocator;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.IHttpClient;
import io.nop.rpc.api.AopRpcService;
import io.nop.rpc.http.DefaultRpcUrlBuilder;
import io.nop.rpc.http.HttpRpcService;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static io.nop.rpc.api.RpcErrors.ARG_SERVICE_NAME;
import static io.nop.rpc.api.RpcErrors.ERR_RPC_NOT_ALLOWED_SERVICE_NAME;

public class HttpRpcServiceInvoker implements IRpcServiceInvoker , IRpcServiceLocator {
    private IHttpClient httpClient;
    private Map<String, String> urlMap;
    private String defaultBaseUrl;
    private List<IRpcServiceInterceptor> interceptors;
    private Set<String> allowedServiceNames;

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setUrlMap(Map<String, String> urlMap) {
        this.urlMap = urlMap;
    }

    public void setDefaultBaseUrl(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    public void setInterceptors(List<IRpcServiceInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    public void setAllowedServiceNames(Set<String> allowedServiceNames) {
        this.allowedServiceNames = allowedServiceNames;
    }

    protected String getBaseUrl(String serviceName) {
        String baseUrl = urlMap == null ? null : urlMap.get(serviceName);
        if (baseUrl == null)
            baseUrl = defaultBaseUrl;
        if (StringHelper.isEmpty(baseUrl))
            throw new IllegalArgumentException("nop.rpc.service-no-base-url:" + serviceName);
        return baseUrl;
    }

    @Override
    public IRpcService getRpcService(String serviceName) {
        String baseUrl = getBaseUrl(serviceName);
        IRpcService rpcService = new HttpRpcService(httpClient, new DefaultRpcUrlBuilder(baseUrl));

        if (interceptors == null || interceptors.isEmpty())
            return rpcService;

        return new AopRpcService(serviceName, rpcService, interceptors, false);
    }

    protected boolean isAllowedService(String serviceName) {
        if (allowedServiceNames == null)
            return false;

        return allowedServiceNames.contains(serviceName) || allowedServiceNames.contains("*");
    }

    @Override
    public CompletionStage<ApiResponse<?>> invokeAsync(String serviceName, String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
        if (!isAllowedService(serviceName))
            return FutureHelper.reject(new NopException(ERR_RPC_NOT_ALLOWED_SERVICE_NAME)
                    .param(ARG_SERVICE_NAME, serviceName));

        ApiHeaders.setSvcName(request, serviceName);
        ApiHeaders.setSvcAction(request, serviceMethod);

        return getRpcService(serviceName).callAsync(serviceMethod, request, cancelToken);
    }
}
