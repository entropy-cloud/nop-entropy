/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.jdk;

import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.HttpApiErrors;
import io.nop.http.api.HttpStatus;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadMode;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.contenttype.ContentType;
import io.nop.http.api.support.CompositeX509TrustManager;
import io.nop.http.api.support.DefaultHttpResponse;
import io.nop.http.api.utils.FileDownloadSink;
import io.nop.http.api.utils.FileTransferHelper;
import io.nop.http.api.utils.HttpHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static io.nop.http.api.HttpApiConfigs.CFG_HTTP_LOG_PRINT_ALL_HEADERS;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_CONNECT_FAIL;

public class JdkHttpClient implements IHttpClient {
    static final Logger LOG = LoggerFactory.getLogger(JdkHttpClient.class);

    private final HttpClientConfig config;

    /**
     * HttpClient 内部会自动管理连接池，复用连接以减少开销。
     */
    private HttpClient client;
    private IThreadPoolExecutor executor;

    public JdkHttpClient(HttpClientConfig config) {
        this.config = config;
    }

    public void refreshConfig() {

    }

    @PostConstruct
    public void start() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(config.isHttp2() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
                .followRedirects(config.isFollowRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);

        if (config.getConnectTimeout() != null) {
            builder.connectTimeout(config.getConnectTimeout());
        }

        if (config.isUseSsl()) {
            builder.sslContext(newSSLContext());
        }

        if (config.getExecutor() != null) {
            builder.executor(config.getExecutor());
        } else if (config.getThreadPoolSize() > 0) {
            executor = DefaultThreadPoolExecutor.newExecutor(config.getThreadName(), config.getThreadPoolSize(), config.getThreadQueueSize());
            builder.executor(executor);
        }

        client = builder.build();
    }

    protected SSLContext newSSLContext() {
        try {
            List<TrustManager> trustManagerList = new ArrayList<>();
            X509TrustManager[] trustManagers = config.getX509TrustManagers();

            if (null != trustManagers) {
                trustManagerList.addAll(Arrays.asList(trustManagers));
            }

            // get trustManager using default certification from jdk
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            trustManagerList.addAll(Arrays.asList(tmf.getTrustManagers()));

            final List<X509TrustManager> finalTrustManagerList = new ArrayList<>();
            for (TrustManager tm : trustManagerList) {
                if (tm instanceof X509TrustManager) {
                    finalTrustManagerList.add((X509TrustManager) tm);
                }
            }
            CompositeX509TrustManager compositeX509TrustManager = new CompositeX509TrustManager(finalTrustManagerList);
            compositeX509TrustManager.setIgnoreSSLCert(config.isIgnoreSslCerts());
            KeyManager[] keyManagers = null;
            if (config.getKeyManagers() != null) {
                keyManagers = config.getKeyManagers();
            }

            SSLContext sslContext = SSLContext.getInstance(config.getSslVersion());
            sslContext.init(keyManagers, new TrustManager[]{compositeX509TrustManager}, config.getSecureRandom());

            return sslContext;
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @PreDestroy
    public void stop() {
        if (executor != null) {
            executor.destroy();
            executor = null;
        }
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens) {
        java.net.http.HttpRequest req = toJdkHttpRequest(request);

        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(req,
                HttpResponse.BodyHandlers.ofByteArray()).exceptionally(this::wrapError);
        if (cancelTokens != null) {
            cancelTokens.appendOnCancel(reason -> {
                future.cancel(false);
            });
        }
        return future.thenApply(this::toHttpResponse);
    }

    protected java.net.http.HttpRequest toJdkHttpRequest(HttpRequest request) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder();
        String method = request.getMethod();
        if (method == null) {
            method = HttpApiConstants.METHOD_GET;
        }
        Object body = normalizeBody(request.getDataType(), request.getBody());

        builder.method(method, toBodyPublisher(body));
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                String name = entry.getKey();
                if (HttpApiConstants.DISALLOWED_HEADERS.contains(name))
                    continue;

                Object value = entry.getValue();
                if (value instanceof Collection) {
                    Collection<?> c = (Collection<?>) value;
                    for (Object v : c) {
                        builder.header(entry.getKey(), String.valueOf(v));
                    }
                } else {
                    builder.setHeader(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }

        if (request.getHeader(HttpApiConstants.HEADER_CONTENT_TYPE) == null) {
            if (HttpApiConstants.DATA_TYPE_FORM.equals(request.getDataType())) {
                builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_FORM_URLENCODED);
            } else if (HttpApiConstants.DATA_TYPE_MULTIPART.equals(request.getDataType())) {
                // multipart 的 Content-Type 必须携带 boundary，否则服务端无法解析分界
                String boundary = body instanceof MultipartBodyPublisher
                        ? ((MultipartBodyPublisher) body).getBoundary() : null;
                String contentType = HttpApiConstants.CONTENT_TYPE_FORM_MULTIPART
                        + (boundary != null ? "; boundary=" + boundary : "");
                builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, contentType);
            } else {
                builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_JSON);
            }
        }

        if (request.getTimeout() > 0) {
            builder.timeout(Duration.of(request.getTimeout(), ChronoUnit.MILLIS));
        } else if (config.getReadTimeout() != null) {
            builder.timeout(config.getReadTimeout());
        }

        builder.uri(toURI(request.getUrlWithParams()));

        java.net.http.HttpRequest req = builder.build();
        logRequest(req, body);
        return req;
    }

    public HttpResponse<byte[]> wrapError(Throwable e) {
        RuntimeException exp = JdkHttpClientHelper.wrapException(e);

        LOG.info("nop.err.http.error", exp);
        throw exp;
    }

    private void logRequest(java.net.http.HttpRequest req, Object body) {
        if (!LOG.isDebugEnabled())
            return;

        LOG.debug("http.request:method={},url={}", req.method(), req.uri());
        HttpHeaders headers = req.headers();
        headers.map().forEach((key, values) -> {
            if (!CFG_HTTP_LOG_PRINT_ALL_HEADERS.get() && isSecretHeader(key)) {
                LOG.debug("Header:{} = {}", key, "******");
            } else {
                LOG.debug("Header: {} = {}", key, String.join(", ", values));
            }
        });

        LOG.debug("Request body: {}", body);
    }

    protected boolean isSecretHeader(String headerName) {
        return HttpHelper.isSecretHeader(headerName);
    }

    protected Object normalizeBody(String dataType, Object body) {
        if (body == null)
            return BodyPublishers.noBody();

        if (body instanceof String)
            return body;

        if (body instanceof byte[])
            return BodyPublishers.ofByteArray((byte[]) body);

        if (HttpApiConstants.DATA_TYPE_FORM.equals(dataType)) {
            return StringHelper.encodeQuery((Map<String, Object>) body, StringHelper.ENCODING_UTF8);
        } else if (HttpApiConstants.DATA_TYPE_MULTIPART.equals(dataType)) {
            return toMultipart((Map<String, Object>) body);
        }
        return JSON.stringify(body);
    }

    URI toURI(String url) {
        return URI.create(url);
    }

    java.net.http.HttpRequest.BodyPublisher toBodyPublisher(Object body) {
        if (body instanceof java.net.http.HttpRequest.BodyPublisher)
            return (java.net.http.HttpRequest.BodyPublisher) body;

        if (body instanceof String)
            return BodyPublishers.ofString(body.toString());

        return BodyPublishers.ofString(JSON.stringify(body));
    }

    java.net.http.HttpRequest.BodyPublisher toMultipart(Map<String, Object> map) {
        MultipartBodyPublisher multipartBodyPublisher = new MultipartBodyPublisher();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                multipartBodyPublisher.addPart(name, (String) value);
            } else if (value instanceof Path) {
                try {
                    multipartBodyPublisher.addPart(name, HttpApiConstants.CONTENT_TYPE_OCTET, (Path) value);
                } catch (IOException e) {
                    throw NopException.adapt(e);
                }
            } else {
                // 静默丢弃表单字段比报错更难排查，这里显式失败
                throw new IllegalArgumentException("Unsupported multipart part type: field=" + name
                        + ", type=" + (value != null ? value.getClass().getName() : "null"));
            }
        }
        return multipartBodyPublisher;
    }

    IHttpResponse toHttpResponse(HttpResponse<byte[]> response) {
        return toHttpResponse(response, false);
    }

    IHttpResponse toHttpResponse(HttpResponse<?> response, boolean ignoreBody) {
        // 某些MAC系统或者JDK版本中连接不上也不会抛出connect异常，而是会返回503错误码
        if (response.statusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE)
            throw new NopConnectException(ERR_HTTP_CONNECT_FAIL);

        DefaultHttpResponse ret = new DefaultHttpResponse();
        ret.setHttpStatus(response.statusCode());
        if (!ignoreBody)
            ret.setBodyAsBytes((byte[]) response.body());

        ret.setHeaders(toMap(response.headers()));

        Optional<String> contentType = response.headers().firstValue(HttpApiConstants.HEADER_CONTENT_TYPE);
        if (contentType.isPresent()) {
            ContentType parsed = ContentType.parse(contentType.get());
            if (parsed.getCharset() != null) {
                ret.setCharset(parsed.getCharset().name());
            } else {
                ret.setCharset(ApiStringHelper.ENCODING_UTF8);
            }
            ret.setContentType(parsed.getMimeType());
        }

        if (LOG.isDebugEnabled())
            LOG.debug("http.response:status={},body={}", response.statusCode(), ret.getBodyAsString());

        return ret;
    }

    Map<String, String> toMap(HttpHeaders headers) {
        return JdkHttpClientHelper.getHeaders(headers);
    }

    @Override
    public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
        return new ServerEventPublisher(client, toJdkHttpRequest(request), cancelToken);
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
        File target = targetFile.toFile();
        if (target != null)
            FileHelper.assureParent(target);
        FileDownloadSink sink = new FileDownloadSink(targetFile, options,
                target != null ? target.getName() : "download");

        long offset = retriedFromScratch ? 0 : sink.resolveOffset();
        if (offset > 0)
            request.header("range", "bytes=" + offset + "-");

        CompletableFuture<HttpResponse<InputStream>> future =
                client.sendAsync(toJdkHttpRequest(request), HttpResponse.BodyHandlers.ofInputStream());
        if (cancelToken != null)
            cancelToken.appendOnCancelTask(() -> future.cancel(false));

        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();
        future.whenComplete((response, err) -> {
            if (err != null) {
                promise.completeExceptionally(wrapError0(err));
                return;
            }
            try {
                // 416：本地 .part 比远端资源新，删除后续传状态整体重传一次
                if (response.statusCode() == 416 && offset > 0 && !retriedFromScratch) {
                    File part = sink.getPartFile();
                    if (part == null)
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

                executor().execute(() -> {
                    try (InputStream in = response.body()) {
                        promise.complete(processDownloadResponse(request, response, in, targetFile, options,
                                cancelToken, sink));
                    } catch (Exception e) {
                        sink.close();
                        promise.completeExceptionally(wrapError0(e));
                    }
                });
            } catch (Exception e) {
                promise.completeExceptionally(wrapError0(e));
            }
        });
        return promise;
    }

    private Executor executor() {
        return client.executor().orElseGet(GlobalExecutors::globalWorker);
    }

    private RuntimeException wrapError0(Throwable e) {
        RuntimeException exp = JdkHttpClientHelper.wrapException(e);
        LOG.info("nop.err.http.error", exp);
        return exp;
    }

    private IHttpResponse processDownloadResponse(HttpRequest request, HttpResponse<InputStream> response,
                                                  InputStream in, IHttpOutputFile targetFile, DownloadOptions options,
                                                  ICancelToken cancelToken, FileDownloadSink sink) throws Exception {
        int status = response.statusCode();
        DefaultHttpResponse ret = new DefaultHttpResponse();
        ret.setHttpStatus(status);
        ret.setHeaders(toMap(response.headers()));

        if (!HttpHelper.isOk(status))
            return ret;

        sink.begin(status, response.headers().firstValue("content-range").orElse(null),
                response.headers().firstValue("content-length").map(Long::parseLong).orElse(-1L));

        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            sink.write(buf, 0, n);
        }
        sink.finish();

        // 期望摘要来源：options 显式 > 响应头 > sidecar
        String sidecarText = null;
        if (options.isFetchSidecarChecksum() && !hasHeaderOrExplicitChecksum(options, response)) {
            sidecarText = fetchSidecarChecksum(request.getUrl(), cancelToken);
        }
        FileTransferHelper.ExpectedChecksum expected = FileTransferHelper.resolveExpectedChecksum(
                options, toMap(response.headers()), sidecarText);
        if (expected == null && options.isRequireChecksum())
            throw new NopException(HttpApiErrors.ERR_HTTP_DOWNLOAD_NO_CHECKSUM);

        sink.verifyAndRename(expected);
        return ret;
    }

    private boolean hasHeaderOrExplicitChecksum(DownloadOptions options, HttpResponse<?> response) {
        if (options.getExpectedSha256() != null || options.getExpectedSha1() != null)
            return true;
        return response.headers().firstValue(HttpApiConstants.HEADER_X_CONTENT_SHA256).isPresent()
                || response.headers().firstValue(HttpApiConstants.HEADER_X_AMZ_CHECKSUM_SHA256).isPresent();
    }

    private String fetchSidecarChecksum(String url, ICancelToken cancelToken) {
        for (String algorithm : new String[]{FileTransferHelper.SHA256, FileTransferHelper.SHA1}) {
            try {
                IHttpResponse res = fetchAsync(HttpRequest.get(FileTransferHelper.sidecarUrl(url, algorithm)), cancelToken)
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                if (res.getHttpStatus() == 200 && res.getBodyAsString() != null)
                    return res.getBodyAsString();
            } catch (Exception e) {
                LOG.debug("nop.http.download.sidecar-check-fail:url={}", url, e);
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
            ret.setHeaders(new java.util.LinkedHashMap<>(request.getHeaders()));
        if (request.getParams() != null)
            ret.setParams(new java.util.LinkedHashMap<>(request.getParams()));
        return ret;
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                                      ICancelToken cancelToken) {
        if (options == null)
            options = new UploadOptions();
        if (inputFile == null)
            throw new IllegalArgumentException("inputFile is null");

        File file = inputFile.toFile();
        if (options.getMode() == UploadMode.BASE64_FORM)
            return uploadByBase64Form(request, inputFile, options, cancelToken);
        if (file == null)
            throw new IllegalArgumentException("BINARY upload mode requires file-backed IHttpInputFile");
        return uploadByBinary(request, inputFile, options, cancelToken, file);
    }

    /**
     * BINARY 模式：PUT/POST + application/octet-stream，元数据经 x-file-* 头，BodyPublishers.ofFile 流式发送
     */
    private CompletionStage<IHttpResponse> uploadByBinary(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken, File file) {
        String method = ApiStringHelper.isEmpty(options.getHttpMethod()) ? "PUT" : options.getHttpMethod();
        String fileName = inputFile.getName();

        java.net.http.HttpRequest.Builder builder;
        try {
            builder = java.net.http.HttpRequest.newBuilder()
                    .uri(toURI(request.getUrlWithParams()))
                    .method(method, new ProgressBodyPublisher(BodyPublishers.ofFile(file.toPath()),
                            options.getProgressListener(), fileName));
        } catch (java.io.FileNotFoundException e) {
            throw new NopException(HttpApiErrors.ERR_HTTP_UPLOAD_INPUT_FILE, e);
        }

        applyUploadHeaders(builder, request, fileName, inputFile.getLength(), options, file);

        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(builder.build(),
                HttpResponse.BodyHandlers.ofByteArray()).exceptionally(this::wrapError);
        if (cancelToken != null)
            cancelToken.appendOnCancelTask(() -> future.cancel(false));
        return future.thenApply(res -> toHttpResponse(res, true));
    }

    /**
     * BASE64_FORM 模式：文件内容 base64 编码后作为普通 multipart 文本字段提交
     */
    private CompletionStage<IHttpResponse> uploadByBase64Form(HttpRequest request, IHttpInputFile inputFile,
                                                              UploadOptions options, ICancelToken cancelToken) {
        String fileName = inputFile.getName();
        String base64;
        try {
            base64 = java.util.Base64.getEncoder().encodeToString(IoHelper.readBytes(inputFile.getInputStream()));
        } catch (IOException e) {
            throw new NopException(HttpApiErrors.ERR_HTTP_UPLOAD_INPUT_FILE, e);
        }

        MultipartBodyPublisher multipart = new MultipartBodyPublisher();
        if (options.getFileNameField() != null)
            multipart.addPart(options.getFileNameField(), fileName != null ? fileName : "");
        multipart.addPart(options.getFieldName(), base64);

        String method = request.getMethod() == null ? HttpApiConstants.METHOD_POST : request.getMethod();
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(toURI(request.getUrlWithParams()))
                .method(method, multipart);

        applyUploadHeaders(builder, request, fileName, inputFile.getLength(), options, null);
        builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE,
                HttpApiConstants.CONTENT_TYPE_FORM_MULTIPART + "; boundary=" + multipart.getBoundary());

        if (options.getProgressListener() != null)
            options.getProgressListener().onProgress(fileName, inputFile.getLength(), inputFile.getLength());

        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(builder.build(),
                HttpResponse.BodyHandlers.ofByteArray()).exceptionally(this::wrapError);
        if (cancelToken != null)
            cancelToken.appendOnCancelTask(() -> future.cancel(false));
        return future.thenApply(res -> toHttpResponse(res, true));
    }

    private void applyUploadHeaders(java.net.http.HttpRequest.Builder builder, HttpRequest request,
                                    String fileName, long fileLength, UploadOptions options, File file) {
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                if (entry.getValue() == null || HttpApiConstants.DISALLOWED_HEADERS.contains(entry.getKey()))
                    continue;
                builder.setHeader(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        if (options.getMode() == UploadMode.BINARY) {
            builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_OCTET_STREAM);
            builder.setHeader(HttpApiConstants.HEADER_X_FILE_NAME, ApiStringHelper.encodeURL(fileName));
            builder.setHeader(HttpApiConstants.HEADER_X_FILE_LENGTH, String.valueOf(fileLength));
            builder.setHeader(HttpApiConstants.HEADER_X_FILE_MODE, "binary");
            if (options.isComputeSha256() && file != null) {
                builder.setHeader(HttpApiConstants.HEADER_X_FILE_SHA256,
                        FileTransferHelper.digestFile(file, FileTransferHelper.SHA256));
            }
        }
    }
}