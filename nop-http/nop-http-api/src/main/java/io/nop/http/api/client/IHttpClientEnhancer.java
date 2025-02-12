package io.nop.http.api.client;

public interface IHttpClientEnhancer {
    IHttpClient enhance(IHttpClient client);
}