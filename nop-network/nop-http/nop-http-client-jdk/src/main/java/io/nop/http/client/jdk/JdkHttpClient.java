/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.jdk;

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

        CompletableFuture<HttpResponse<byte[]>> raw = client.sendAsync(req,
                HttpResponse.BodyHandlers.ofByteArray());
        CompletableFuture<HttpResponse<byte[]>> future = raw.exceptionally(this::wrapError);

        CompletableFuture<IHttpResponse> promise = future.thenApply(this::toHttpResponse)
                .toCompletableFuture();
        // C6-006 (plan 2523)：promise 终态→底层取消桥接——promise 是 thenApply 派生 stage，
        // 其 cancel 永不向上游传播；对裸 sendAsync stage 取消才触达传输层
        promise.whenComplete((r, e) -> {
            if (promise.isCancelled()) {
                raw.cancel(true);
            }
        });
        // 4.3-C3-012：取消监听器经平台单点 FutureHelper.bindCancelToken 收敛——注册与 remove
        // 按 promise 终态配对，长生命周期 cancelToken 的监听器不再随请求次数无界累积
        FutureHelper.bindCancelToken(cancelTokens, reason -> future.cancel(false), promise);
        return promise;
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
        // P1-check-netrpc-24（plan 2391）：移除「503 → 连接失败」特判。该 workaround
        //（旧 MAC/JDK 连接失败表现为 503）使所有真实 503 响应被误判为连接失败且丢弃
        // body——与 Apache/OkHttp（503 按正常响应返回）行为分歧。连接失败在现代 JDK
        // 上正确抛 ConnectException；503 属服务端响应语义，按正常响应送达调用方。
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

        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();
        // 4.3-C3-012：取消监听器经平台单点收敛（注册与 remove 按 promise 终态配对）
        // 4.3-C3-002（plan 2505）：绑定动作补挂 pendingBody 即时关闭——headers 后 future.cancel
        // 为 no-op 的窗口内，取消经 bodyRef 命中已登记的响应体流即刻解除阻塞读
        java.util.concurrent.atomic.AtomicReference<InputStream> bodyRef = new java.util.concurrent.atomic.AtomicReference<>();
        FutureHelper.bindCancelToken(cancelToken, reason -> {
            future.cancel(false);
            IoHelper.safeClose(bodyRef.get());
        }, promise);
        future.whenComplete((response, err) -> {
            if (err != null) {
                promise.completeExceptionally(wrapError0(err));
                return;
            }
            try {
                // 416：本地 .part 比远端资源新，删除后续传状态整体重传一次
                if (response.statusCode() == 416 && offset > 0 && !retriedFromScratch) {
                    // 4.3-C6-002①（plan 2505）：早退重试分支必须先关闭遗弃的响应体流
                    // （连接/socket 归还），不得静默 return 遗弃
                    IoHelper.safeClose(response.body());
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
                    InputStream in = response.body();
                    bodyRef.set(in);
                    // 4.3-C3-002/CL-026（plan 2505）：body 阶段 idle 读看门狗——静默流上的
                    // read 不再无限阻塞（预算独立于 headers 阶段 request timeout）
                    BodyIdleWatchdog watchdog = BodyIdleWatchdog.start(in, cancelToken,
                            JdkHttpConfigs.CFG_HTTP_BODY_IDLE_TIMEOUT.get());
                    try (InputStream closeable = in) {
                        promise.complete(processDownloadResponse(request, response, closeable, targetFile,
                                options, cancelToken, sink, watchdog));
                    } catch (Exception e) {
                        sink.close();
                        if (cancelToken != null && cancelToken.isCancelled()) {
                            // R21 纪律：取消路径以显式取消终态交付，不伪装成传输错误
                            promise.completeExceptionally(new java.util.concurrent.CancellationException(
                                    "download cancelled"));
                        } else {
                            promise.completeExceptionally(wrapError0(e));
                        }
                    } finally {
                        if (watchdog != null)
                            watchdog.stop();
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
                                                  ICancelToken cancelToken, FileDownloadSink sink,
                                                  BodyIdleWatchdog watchdog) throws Exception {
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
            // 4.3-C3-002（plan 2505）：读循环取消响应点——取消后立即停止写入 sink，
            // 幽灵下载（带宽/磁盘持续消耗）在单次 read 返回内有界终止
            if (cancelToken != null && cancelToken.isCancelled())
                throw new java.util.concurrent.CancellationException("download cancelled");
            sink.write(buf, 0, n);
            if (watchdog != null)
                watchdog.progress();
        }
        if (cancelToken != null && cancelToken.isCancelled())
            throw new java.util.concurrent.CancellationException("download cancelled");
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
        // CRA WI 4.3 C6-001（P0）：成功路径必须关闭 sink 持有的文件句柄——否则 resume=true（缺省）下
        // 每次成功下载泄漏一个 fd；Windows 上 Files.move 因句柄占用确定性失败（审计复核四子项核验）
        sink.close();
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
            java.util.concurrent.CompletableFuture<IHttpResponse> future = fetchAsync(HttpRequest.get(FileTransferHelper.sidecarUrl(url, algorithm)), cancelToken)
                    .toCompletableFuture();
            try {
                IHttpResponse res = future.get(30, TimeUnit.SECONDS);
                if (res.getHttpStatus() == 200 && res.getBodyAsString() != null)
                    return res.getBodyAsString();
            } catch (Exception e) {
                // C6-006（plan 2523）：get 超时取消在途请求——经 promise→raw stage 桥触达底层
                future.cancel(false);
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
        CompletionStage<IHttpResponse> promise = future.thenApply(res -> toHttpResponse(res, true));
        // 4.3-C3-012：取消监听器经平台单点收敛（注册与 remove 按 promise 终态配对）
        FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        return promise;
    }

    /**
     * BASE64_FORM 模式：文件内容 base64 编码后作为普通 multipart 文本字段提交
     */
    private CompletionStage<IHttpResponse> uploadByBase64Form(HttpRequest request, IHttpInputFile inputFile,
                                                              UploadOptions options, ICancelToken cancelToken) {
        String fileName = inputFile.getName();
        String base64;
        // C6-005 (plan 2522): the input stream must be closed — IoHelper.readBytes never closes
        try (java.io.InputStream in = inputFile.getInputStream()) {
            base64 = java.util.Base64.getEncoder().encodeToString(IoHelper.readBytes(in));
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
        CompletionStage<IHttpResponse> promise = future.thenApply(res -> toHttpResponse(res, true));
        // 4.3-C3-012：取消监听器经平台单点收敛（注册与 remove 按 promise 终态配对）
        FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        return promise;
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