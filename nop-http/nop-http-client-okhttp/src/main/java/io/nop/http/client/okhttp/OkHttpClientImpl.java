/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.client.okhttp;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OkHttpClientImpl implements IHttpClient {
    static final MediaType MEDIA_TYPE_JSON = MediaType.parse(HttpApiConstants.CONTENT_TYPE_JSON);

    private final OkHttpClient client;
    private final HttpClientConfig config;

    public OkHttpClientImpl(OkHttpClient client, HttpClientConfig config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        Call call = newCall(request);

        CompletableFuture<IHttpResponse> future = new CompletableFuture<>();

        if (cancelToken != null)
            cancelToken.appendOnCancelTask(call::cancel);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int status = response.code();
                String text = response.body() == null ? "" : response.body().string();
                DefaultHttpResponse res = new DefaultHttpResponse();
                res.setHttpStatus(status);
                res.setBodyAsText(text);

                Map<String, String> headers = new LinkedHashMap<>();
                for (String header : response.headers().names()) {
                    String value = response.header(header);
                    headers.put(header, value);
                }
                res.setHeaders(headers);
                future.complete(res);
            }
        });
        return future;
    }

    public void stop() {
        //client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        Cache cache = client.cache();
        if (cache != null) {
            IoHelper.safeCloseObject(cache);
        }
    }

    protected Call newCall(HttpRequest request) {
        final Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(request.getUrl());

        String method = request.getMethod();
        String json = request.getBody() == null ? "" : JSON.serialize(request.getBody(), config.isPrettyJson());

        final RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, json);
        requestBuilder.method(method, body);

        if (!StringHelper.isEmpty(config.getUserAgent())) {
            requestBuilder.header(HttpApiConstants.HEADER_USER_AGENT, config.getUserAgent());
        }

        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        // create a new call
        return client.newCall(requestBuilder.build());
    }

    @Override
    public CompletionStage<Void> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                               ICancelToken cancelToken) {
        client.newCall(null).enqueue(null);
        return null;
    }

    @Override
    public CompletionStage<Void> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                             ICancelToken cancelToken) {
        return null;
    }
}
