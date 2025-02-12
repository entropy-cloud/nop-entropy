package io.nop.http.api.support;

import io.nop.http.api.client.IHttpClient;

public interface IHttpClientFactory {
    IHttpClient getHttpClient();
}
