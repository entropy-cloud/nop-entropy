package io.nop.http.api.client;

import io.nop.api.core.util.ICancelToken;

public interface IHttpClientInterceptor {
    default void onBeginFetch(IHttpClient client, HttpRequest request, ICancelToken cancelToken){

    }

    default void onEndFetch(IHttpClient client, HttpRequest request, Throwable ex, IHttpResponse response){

    }
}