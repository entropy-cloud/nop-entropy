/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.okhttp;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.HttpApiErrors;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadMode;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import io.nop.http.api.utils.FileDownloadSink;
import io.nop.http.api.utils.FileTransferHelper;
import io.nop.http.api.utils.HttpHelper;
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
        requestBuilder.url(request.getUrlWithParams());

        String method = request.getMethod();
        if (method == null)
            method = HttpApiConstants.METHOD_GET;

        // GET/HEAD 不允许携带请求体（OkHttp 会抛 IllegalArgumentException）
        boolean permitsBody = !HttpApiConstants.METHOD_GET.equalsIgnoreCase(method)
                && !HttpApiConstants.METHOD_HEAD.equalsIgnoreCase(method);
        String json = request.getBody() == null ? "" : JSON.serialize(request.getBody(), config.isPrettyJson());
        final RequestBody body = permitsBody ? RequestBody.create(MEDIA_TYPE_JSON, json) : null;
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
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                                        ICancelToken cancelToken) {
        if (options == null)
            options = new DownloadOptions();
        if (targetFile == null)
            throw new IllegalArgumentException("targetFile is null");
        return doDownloadAsync(copyRequest(request), targetFile, options, cancelToken, false);
    }

    private CompletionStage<IHttpResponse> doDownloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                           DownloadOptions options, ICancelToken cancelToken,
                                                           boolean retriedFromScratch) {
        java.io.File target = targetFile.toFile();
        if (target != null && target.getParentFile() != null)
            target.getParentFile().mkdirs();
        FileDownloadSink sink = new FileDownloadSink(targetFile, options,
                target != null ? target.getName() : "download");

        long offset = retriedFromScratch ? 0 : sink.resolveOffset();
        if (offset > 0)
            request.header("range", "bytes=" + offset + "-");

        Call call = newCall(request);
        if (cancelToken != null)
            cancelToken.appendOnCancelTask(call::cancel);

        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                promise.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response resp = response) {
                    int status = resp.code();

                    // 416：本地 .part 比远端资源新，删除后续传状态整体重传一次
                    if (status == 416 && offset > 0 && !retriedFromScratch) {
                        java.io.File part = sink.getPartFile();
                        if (part == null && target != null)
                            part = FileTransferHelper.partFileOf(target);
                        if (part != null)
                            java.nio.file.Files.deleteIfExists(part.toPath());
                        doDownloadAsync(copyRequest(request), targetFile, options, cancelToken, true)
                                .whenComplete((r, e) -> {
                                    if (e != null)
                                        promise.completeExceptionally(e);
                                    else
                                        promise.complete(r);
                                });
                        return;
                    }

                    DefaultHttpResponse ret = new DefaultHttpResponse();
                    ret.setHttpStatus(status);
                    Map<String, String> headers = new LinkedHashMap<>();
                    for (String name : resp.headers().names()) {
                        headers.put(name, resp.header(name));
                    }
                    ret.setHeaders(headers);

                    if (!HttpHelper.isOk(status)) {
                        promise.complete(ret);
                        return;
                    }

                    sink.begin(status, resp.header("content-range"),
                            resp.header("content-length") != null ? Long.parseLong(resp.header("content-length")) : -1L);

                    okio.BufferedSource source = resp.body() != null ? resp.body().source() : null;
                    byte[] buf = new byte[8192];
                    while (source != null && !source.exhausted()) {
                        int n = source.read(buf);
                        if (n < 0)
                            break;
                        sink.write(buf, 0, n);
                    }
                    sink.finish();

                    String sidecarText = null;
                    if (options.isFetchSidecarChecksum() && !hasHeaderOrExplicitChecksum(options, headers)) {
                        sidecarText = fetchSidecarChecksum(request.getUrl(), cancelToken);
                    }
                    FileTransferHelper.ExpectedChecksum expected = FileTransferHelper
                            .resolveExpectedChecksum(options, headers, sidecarText);
                    if (expected == null && options.isRequireChecksum())
                        throw new NopException(HttpApiErrors.ERR_HTTP_DOWNLOAD_NO_CHECKSUM);

                    sink.verifyAndRename(expected);
                    promise.complete(ret);
                } catch (Exception e) {
                    sink.close();
                    promise.completeExceptionally(e);
                }
            }
        });
        return promise;
    }

    private boolean hasHeaderOrExplicitChecksum(DownloadOptions options, Map<String, String> headers) {
        if (options.getExpectedSha256() != null || options.getExpectedSha1() != null)
            return true;
        return headers != null && (headers.containsKey(HttpApiConstants.HEADER_X_CONTENT_SHA256)
                || headers.containsKey(HttpApiConstants.HEADER_X_AMZ_CHECKSUM_SHA256));
    }

    private String fetchSidecarChecksum(String url, ICancelToken cancelToken) {
        for (String algorithm : new String[]{FileTransferHelper.SHA256, FileTransferHelper.SHA1}) {
            try {
                IHttpResponse res = fetchAsync(
                        HttpRequest.get(FileTransferHelper.sidecarUrl(url, algorithm)), cancelToken)
                        .toCompletableFuture().get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (res.getHttpStatus() == 200 && res.getBodyAsString() != null)
                    return res.getBodyAsString();
            } catch (Exception e) {
                // sidecar 探测失败不阻断下载
            }
        }
        return null;
    }

    private static HttpRequest copyRequest(HttpRequest request) {
        HttpRequest ret = new HttpRequest();
        ret.setUrl(request.getUrl());
        ret.setMethod(request.getMethod());
        ret.setBody(request.getBody());
        ret.setDataType(request.getDataType());
        ret.setTimeout(request.getTimeout());
        if (request.getHeaders() != null)
            ret.setHeaders(new LinkedHashMap<>(request.getHeaders()));
        if (request.getParams() != null)
            ret.setParams(new LinkedHashMap<>(request.getParams()));
        return ret;
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                                      ICancelToken cancelToken) {
        if (options == null)
            options = new UploadOptions();
        if (inputFile == null)
            throw new IllegalArgumentException("inputFile is null");

        Request.Builder requestBuilder = new Request.Builder().url(request.getUrlWithParams());
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                if (entry.getValue() != null)
                    requestBuilder.header(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        String fileName = inputFile.getName();
        RequestBody body;
        if (options.getMode() == UploadMode.BASE64_FORM) {
            String base64;
            try {
                base64 = java.util.Base64.getEncoder()
                        .encodeToString(IoHelper.readBytes(inputFile.getInputStream()));
            } catch (IOException e) {
                throw new NopException(HttpApiErrors.ERR_HTTP_UPLOAD_INPUT_FILE, e);
            }
            okhttp3.MultipartBody.Builder multipart = new okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM);
            if (options.getFileNameField() != null)
                multipart.addFormDataPart(options.getFileNameField(), fileName != null ? fileName : "");
            multipart.addFormDataPart(options.getFieldName(), base64);
            body = multipart.build();
            if (options.getProgressListener() != null)
                options.getProgressListener().onProgress(fileName, inputFile.getLength(), inputFile.getLength());
        } else {
            java.io.File file = inputFile.toFile();
            if (file == null)
                throw new IllegalArgumentException("BINARY upload mode requires file-backed IHttpInputFile");

            requestBuilder.header(HttpApiConstants.HEADER_X_FILE_NAME,
                    io.nop.api.core.util.ApiStringHelper.encodeURL(fileName));
            requestBuilder.header(HttpApiConstants.HEADER_X_FILE_LENGTH, String.valueOf(inputFile.getLength()));
            requestBuilder.header(HttpApiConstants.HEADER_X_FILE_MODE, "binary");
            if (options.isComputeSha256())
                requestBuilder.header(HttpApiConstants.HEADER_X_FILE_SHA256,
                        FileTransferHelper.digestFile(file, FileTransferHelper.SHA256));

            RequestBody fileBody = RequestBody.create(file,
                    MediaType.parse(HttpApiConstants.CONTENT_TYPE_OCTET_STREAM));
            body = new ProgressRequestBody(fileBody, options.getProgressListener(), fileName);
        }

        String method = options.getMode() == UploadMode.BASE64_FORM
                ? (request.getMethod() == null ? "POST" : request.getMethod())
                : (options.getHttpMethod() == null ? "PUT" : options.getHttpMethod());
        requestBuilder.method(method, body);

        Call call = client.newCall(requestBuilder.build());
        if (cancelToken != null)
            cancelToken.appendOnCancelTask(call::cancel);

        CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response resp = response) {
                    DefaultHttpResponse res = new DefaultHttpResponse();
                    res.setHttpStatus(resp.code());
                    res.setBodyAsText(resp.body() == null ? "" : resp.body().string());
                    Map<String, String> headers = new LinkedHashMap<>();
                    for (String name : resp.headers().names()) {
                        headers.put(name, resp.header(name));
                    }
                    res.setHeaders(headers);
                    future.complete(res);
                }
            }
        });
        return future;
    }

    /**
     * 按写入 socket 的字节数回调进度
     */
    static class ProgressRequestBody extends RequestBody {
        private final RequestBody delegate;
        private final IProgressListener progressListener;
        private final Object message;

        ProgressRequestBody(RequestBody delegate, IProgressListener progressListener, Object message) {
            this.delegate = delegate;
            this.progressListener = progressListener;
            this.message = message;
        }

        @Override
        public MediaType contentType() {
            return delegate.contentType();
        }

        @Override
        public long contentLength() throws IOException {
            return delegate.contentLength();
        }

        @Override
        public void writeTo(okio.BufferedSink sink) throws IOException {
            long total = contentLength();
            okio.ForwardingSink counting = new okio.ForwardingSink(sink) {
                long sent;

                @Override
                public void write(okio.Buffer source, long byteCount) throws IOException {
                    super.write(source, byteCount);
                    sent += byteCount;
                    if (progressListener != null)
                        progressListener.onProgress(message, sent, total);
                }
            };
            okio.BufferedSink buffered = okio.Okio.buffer(counting);
            delegate.writeTo(buffered);
            buffered.flush();
        }
    }
}
