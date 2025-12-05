/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.cluster.http;

import io.nop.cluster.discovery.ServiceInstance;
import io.nop.http.api.client.IHttpClient;
import io.nop.rpc.http.DefaultRpcUrlBuilder;
import io.nop.rpc.http.HttpRpcService;
import io.nop.api.core.rpc.IRpcService;
import io.nop.rpc.cluster.IRpcClientInstanceProvider;

import jakarta.inject.Inject;

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