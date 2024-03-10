/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.utils;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 将Http调用封装为Runnable接口
 *
 * @param <T>
 */
public class HttpFetchTask<T> implements Runnable {
    private final IHttpClient httpClient;
    private final Supplier<HttpRequest> requestBuilder;
    private final Function<IHttpResponse, T> responseConsumer;
    private final ICancelToken cancelToken;

    private final CompletableFuture<T> promise = new CompletableFuture<>();

    public HttpFetchTask(IHttpClient httpClient, Supplier<HttpRequest> requestBuilder,
                         ICancelToken cancelToken,
                         Function<IHttpResponse, T> responseConsumer) {
        this.httpClient = Guard.notNull(httpClient, "httpClient");
        this.cancelToken = cancelToken;
        this.requestBuilder = Guard.notNull(requestBuilder, "requestBuilder");
        this.responseConsumer = Guard.notNull(responseConsumer, "responseConsumer");
    }

    public CompletableFuture<T> getPromise() {
        return promise;
    }

    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    public CompletableFuture<T> invokeAsync() {
        run();
        return getPromise();
    }

    @Override
    public void run() {
        try {
            FutureHelper.bindResult(httpClient.fetchAsync(requestBuilder.get(), cancelToken)
                    .thenApply(this.responseConsumer), promise);
        } catch (Exception e) {
            promise.completeExceptionally(e);
        }
    }
}