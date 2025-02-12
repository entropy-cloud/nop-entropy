package io.nop.http.api.support;

import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.util.Guard;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpClientEnhancer;
import jakarta.annotation.PostConstruct;

import java.util.List;

public class DefaultHttpClientFactory implements IHttpClientFactory {
    private IHttpClient httpClient;
    private List<IHttpClientEnhancer> httpClientEnhancers;

    private IHttpClient enhancedHttpClient;

    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setHttpClientEnhancers(List<IHttpClientEnhancer> httpClientEnhancers) {
        this.httpClientEnhancers = httpClientEnhancers;
    }

    @PostConstruct
    public void init() {
        Guard.notNull(httpClient, "httpClient");

        IHttpClient client = this.httpClient;
        if (this.httpClientEnhancers != null) {
            for (IHttpClientEnhancer enhancer : this.httpClientEnhancers) {
                client = enhancer.enhance(client);
            }
        }
        this.enhancedHttpClient = client;
    }

    @BeanMethod
    @Override
    public IHttpClient getHttpClient() {
        return enhancedHttpClient;
    }
}
