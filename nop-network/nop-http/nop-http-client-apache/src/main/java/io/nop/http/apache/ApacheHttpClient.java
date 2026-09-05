package io.nop.http.apache;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
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
import io.nop.http.api.support.DefaultHttpResponse;
import io.nop.http.api.utils.FileTransferHelper;
import io.nop.http.api.utils.HttpHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.http.apache.ApacheHttpClientHelper.fromSimpleResponse;
import static io.nop.http.api.HttpApiConfigs.CFG_HTTP_LOG_PRINT_ALL_HEADERS;

public class ApacheHttpClient implements IHttpClient, IConfigRefreshable {
    static final Logger LOG = LoggerFactory.getLogger(ApacheHttpClient.class);

    private final HttpClientConfig clientConfig;

    private CloseableHttpAsyncClient client;

    private RequestConfig defaultRequestConfig;

    public ApacheHttpClient(HttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void refreshConfig() {

    }

    @PostConstruct
    public void start() {

        IOReactorConfig ioReactorConfig = ApacheHttpClientHelper.createReactorConfig(clientConfig);

        this.client = clientConfig.isHttp2() ? ApacheHttpClientHelper.createHttp2(clientConfig, ioReactorConfig) :
                ApacheHttpClientHelper.createHttp(clientConfig, ioReactorConfig, initConnectionManager());

        this.defaultRequestConfig = ApacheHttpClientHelper.createRequestConfig(clientConfig);

        this.client.start();
    }

    @PreDestroy
    public void stop() {
        if (this.client != null)
            this.client.close(CloseMode.GRACEFUL);
    }


    private PoolingAsyncClientConnectionManager initConnectionManager() {
        return ApacheHttpClientHelper.createConnectionManager(clientConfig);
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        SimpleHttpRequest req = toSimpleRequest(request);
        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();

        Future<?> future = client.execute(req, newHttpClientContext(request), new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                try {
                    promise.complete(fromSimpleResponse(result));
                } catch (Exception e) {
                    failed(e);
                }
            }

            @Override
            public void failed(Exception ex) {
                promise.completeExceptionally(ApacheHttpClientHelper.wrapException(ex));
            }

            @Override
            public void cancelled() {
                promise.cancel(false);
            }
        });

        if (cancelToken != null) {
            FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        }

        return promise;
    }

    protected HttpClientContext newHttpClientContext(HttpRequest request) {
        return HttpClientContext.create();
    }

    @Override
    public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
        return new ServerEventPublisher(client, toSimpleRequest(request), newHttpClientContext(request), cancelToken);
    }

    private Method toMethod(String method) {
        if (ApiStringHelper.isEmpty(method))
            return Method.GET;
        return Method.normalizedValueOf(method);
    }

    private SimpleHttpRequest toSimpleRequest(HttpRequest request) {
        SimpleRequestBuilder builder = SimpleRequestBuilder.create(toMethod(request.getMethod()));
        builder.setUri(request.getUrl());
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                String name = entry.getKey();
                if (HttpApiConstants.DISALLOWED_HEADERS.contains(name))
                    continue;

                Object value = entry.getValue();
                if (value == null)
                    continue;

                if (value instanceof Collection<?>) {
                    for (Object item : (Collection<?>) value) {
                        if (item == null)
                            continue;
                        builder.addHeader(entry.getKey(), item.toString());
                    }
                } else {
                    String str = value.toString();
                    builder.addHeader(entry.getKey(), str);
                }
            }
        }

        if (request.getParams() != null) {
            request.getParams().forEach((name, value) -> {
                if (value == null)
                    return;

                if (value instanceof Collection) {
                    ((Collection<?>) value).forEach(item -> builder.addParameter(name, StringHelper.toString(item, "")));
                } else {
                    builder.addParameter(name, value.toString());
                }
            });
        }

        if (request.getTimeout() > 0) {
            RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                    .setResponseTimeout(request.getTimeout(), TimeUnit.MILLISECONDS).build();

            builder.setRequestConfig(requestConfig);
        }

        addBody(builder, request.getBody(), getContentType(request));
        SimpleHttpRequest req = builder.build();
        logRequest(req, request.getBody());
        return req;
    }

    private void addBody(SimpleRequestBuilder builder, Object body, ContentType contentType) {
        if (body == null)
            return;
        if (body instanceof String) {
            builder.setBody(body.toString(), contentType);
        } else if (body instanceof ApiRequest) {
            setApiRequestBody(builder, (ApiRequest) body, contentType);
        } else {
            builder.setBody(JsonTool.stringify(body), contentType);
        }
    }

    private void setApiRequestBody(SimpleRequestBuilder builder, ApiRequest request, ContentType contentType) {
        if (request.hasHeaders()) {
            request.getHeaders().forEach((name, value) -> {
                if (value == null)
                    return;
                builder.setHeader(name, value.toString());
            });
        }
        if (request.getData() != null)
            builder.setBody(JsonTool.stringify(request.getData()), contentType);
    }

    private ContentType getContentType(HttpRequest request) {
        if (HttpApiConstants.DATA_TYPE_FORM.equals(request.getDataType()))
            return ContentType.APPLICATION_FORM_URLENCODED;
        if (HttpApiConstants.DATA_TYPE_MULTIPART.equals(request.getDataType()))
            return ContentType.MULTIPART_FORM_DATA;
        // 显式设置的 content-type 头优先于默认 JSON，返回 null 保留头原样传递
        if (request.getHeader(HttpApiConstants.HEADER_CONTENT_TYPE) != null)
            return null;
        return ContentType.APPLICATION_JSON;
    }


    @Override
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                        DownloadOptions options, ICancelToken cancelToken) {
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
        File partFile = target != null && options.isResume() ? FileTransferHelper.partFileOf(target) : null;
        long offset = partFile != null && partFile.exists() ? partFile.length() : options.getInitialOffset();
        if (retriedFromScratch)
            offset = 0;

        if (offset > 0)
            request.header("range", "bytes=" + offset + "-");
        else
            request.removeHeader("range");

        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();

        FutureCallback<DownloadTransferResult> callback = new FutureCallback<>() {
            @Override
            public void completed(DownloadTransferResult result) {
                try {
                    handleDownloadResult(request, targetFile, options, cancelToken, result, retriedFromScratch, promise);
                } catch (Exception e) {
                    failed(e);
                }
            }

            @Override
            public void failed(Exception ex) {
                promise.completeExceptionally(ApacheHttpClientHelper.wrapException(ex));
            }

            @Override
            public void cancelled() {
                promise.cancel(false);
            }
        };

        Future<?> future = client.execute(SimpleRequestProducer.create(toSimpleRequest(request)),
                new DownloadResponseConsumer(targetFile, offset, options, target != null ? target.getName() : "download"),
                newHttpClientContext(request), callback);

        if (cancelToken != null) {
            FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        }
        return promise;
    }

    /**
     * 传输完成后的统一收尾：416 整体重试、sidecar 探测、摘要校验、.part 原子改名
     */
    private void handleDownloadResult(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                      ICancelToken cancelToken, DownloadTransferResult result,
                                      boolean retriedFromScratch,
                                      CompletableFuture<IHttpResponse> promise) throws IOException {
        int status = result.getStatus();

        // 416：本地 .part 比远端资源新，删除后续传状态整体重传一次
        if (status == 416 && !retriedFromScratch) {
            if (result.getPartFile() != null)
                java.nio.file.Files.deleteIfExists(result.getPartFile().toPath());
            doDownloadAsync(copyRequest(request), targetFile, options, cancelToken, true)
                    .whenComplete((r, e) -> {
                        if (e != null)
                            promise.completeExceptionally(e);
                        else
                            promise.complete(r);
                    });
            return;
        }

        DefaultHttpResponse response = new DefaultHttpResponse();
        response.setHttpStatus(status);
        response.setHeaders(result.getHeaders());

        if (!HttpHelper.isOk(status)) {
            promise.complete(response);
            return;
        }

        // 期望摘要来源：options 显式 > 响应头 > sidecar
        String sidecarText = null;
        if (options.isFetchSidecarChecksum() && !hasHeaderOrExplicitChecksum(options, result)) {
            sidecarText = fetchSidecarChecksum(request.getUrl(), cancelToken);
        }
        FileTransferHelper.ExpectedChecksum expected = FileTransferHelper
                .resolveExpectedChecksum(options, result.getHeaders(), sidecarText);
        if (expected == null && options.isRequireChecksum()) {
            throw new NopException(HttpApiErrors.ERR_HTTP_DOWNLOAD_NO_CHECKSUM);
        }

        // 校验 + .part 原子改名；校验失败时删除 .part（损坏数据不能作为续传基底）
        try {
            FileTransferHelper.verifyChecksum(expected,
                    FileTransferHelper.SHA1.equals(expected != null ? expected.getAlgorithm() : null)
                            ? result.getSha1() : result.getSha256());
        } catch (Exception e) {
            if (result.getPartFile() != null)
                java.nio.file.Files.deleteIfExists(result.getPartFile().toPath());
            throw e;
        }
        if (result.getPartFile() != null) {
            java.nio.file.Files.move(result.getPartFile().toPath(), targetFile.toFile().toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        promise.complete(response);
    }

    private boolean hasHeaderOrExplicitChecksum(DownloadOptions options, DownloadTransferResult result) {
        if (options.getExpectedSha256() != null || options.getExpectedSha1() != null)
            return true;
        Map<String, String> headers = result.getHeaders();
        return headers != null && (FileTransferHelper.getHeaderIgnoreCase(headers,
                HttpApiConstants.HEADER_X_CONTENT_SHA256) != null
                || FileTransferHelper.getHeaderIgnoreCase(headers,
                HttpApiConstants.HEADER_X_AMZ_CHECKSUM_SHA256) != null);
    }

    /**
     * 探测同目录 sidecar 校验文件：{url}.sha256 → {url}.sha1，取到则返回其文本内容
     */
    private String fetchSidecarChecksum(String url, ICancelToken cancelToken) {
        for (String algorithm : new String[]{FileTransferHelper.SHA256, FileTransferHelper.SHA1}) {
            try {
                IHttpResponse res = fetchAsync(HttpRequest.get(FileTransferHelper.sidecarUrl(url, algorithm)), cancelToken)
                        .toCompletableFuture().get(30, TimeUnit.SECONDS);
                if (res.getHttpStatus() == 200 && res.getBodyAsString() != null)
                    return res.getBodyAsString();
            } catch (Exception e) {
                // sidecar 探测失败不阻断下载
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

    private void logRequest(SimpleHttpRequest req, Object body) {
        if (!LOG.isDebugEnabled())
            return;

        LOG.debug("http.request:method={},url={}", req.getMethod(), req.getRequestUri());
        Header[] headers = req.getHeaders();
        for (Header header : headers) {
            String key = header.getName();
            String value = header.getValue();
            if (!CFG_HTTP_LOG_PRINT_ALL_HEADERS.get() && isSecretHeader(key)) {
                LOG.debug("Header:{} = {}", key, "******");
            } else {
                LOG.debug("Header: {} = {}", key, value);
            }
        }

        LOG.debug("Request body: {}", body);
    }

    protected boolean isSecretHeader(String headerName) {
        return HttpHelper.isSecretHeader(headerName);
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                                      ICancelToken cancelToken) {
        if (options == null)
            options = new UploadOptions();
        if (inputFile == null)
            throw new IllegalArgumentException("inputFile is null");
        try {
            if (options.getMode() == UploadMode.BASE64_FORM)
                return uploadByBase64Form(request, inputFile, options, cancelToken);
            return uploadByBinary(request, inputFile, options, cancelToken);
        } catch (IOException e) {
            throw new NopException(HttpApiErrors.ERR_HTTP_UPLOAD_INPUT_FILE, e);
        }
    }

    /**
     * BINARY 模式：PUT/POST + application/octet-stream，元数据经 x-file-* 头，文件流式发送
     */
    private CompletionStage<IHttpResponse> uploadByBinary(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) throws IOException {
        File file = inputFile.toFile();
        if (file == null)
            throw new IllegalArgumentException("BINARY upload mode requires file-backed IHttpInputFile");

        String method = ApiStringHelper.isEmpty(options.getHttpMethod()) ? "PUT" : options.getHttpMethod();
        BasicHttpRequest req = new BasicHttpRequest(Method.normalizedValueOf(method),
                request.getUrlWithParams());

        String fileName = inputFile.getName() != null ? inputFile.getName() : file.getName();
        req.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_OCTET_STREAM);
        req.setHeader(HttpApiConstants.HEADER_X_FILE_NAME, ApiStringHelper.encodeURL(fileName));
        req.setHeader(HttpApiConstants.HEADER_X_FILE_LENGTH, String.valueOf(inputFile.getLength()));
        req.setHeader(HttpApiConstants.HEADER_X_FILE_MODE, "binary");
        if (options.isComputeSha256())
            req.setHeader(HttpApiConstants.HEADER_X_FILE_SHA256,
                    FileTransferHelper.digestFile(file, FileTransferHelper.SHA256));

        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                if (entry.getValue() == null || HttpApiConstants.DISALLOWED_HEADERS.contains(entry.getKey()))
                    continue;
                req.setHeader(entry.getKey(), entry.getValue().toString());
            }
        }

        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();
        ProgressFileEntityProducer producer = new ProgressFileEntityProducer(file,
                ContentType.APPLICATION_OCTET_STREAM, options.getProgressListener(), fileName);

        Future<SimpleHttpResponse> future = client.execute(new BasicRequestProducer(req, producer),
                org.apache.hc.client5.http.async.methods.SimpleResponseConsumer.create(),
                newHttpClientContext(request), newUploadCallback(promise));

        if (cancelToken != null)
            FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        return promise;
    }

    /**
     * BASE64_FORM 模式：文件内容 base64 编码后作为普通 multipart 文本字段提交
     */
    private CompletionStage<IHttpResponse> uploadByBase64Form(HttpRequest request, IHttpInputFile inputFile,
                                                              UploadOptions options, ICancelToken cancelToken) throws IOException {
        String fileName = inputFile.getName();
        byte[] content = IoHelper.readBytes(inputFile.getInputStream());
        String base64 = java.util.Base64.getEncoder().encodeToString(content);

        org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder builder =
                org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder.create();
        if (options.getFileNameField() != null)
            builder.addTextBody(options.getFileNameField(), fileName,
                    ContentType.create("text/plain", StandardCharsets.UTF_8));
        builder.addTextBody(options.getFieldName(), base64,
                ContentType.create("text/plain", StandardCharsets.UTF_8));

        org.apache.hc.core5.http.HttpEntity entity = builder.build();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try {
            entity.writeTo(bos);
        } catch (IOException e) {
            throw new NopException(HttpApiErrors.ERR_HTTP_UPLOAD_INPUT_FILE, e);
        }
        byte[] body = bos.toByteArray();

        SimpleHttpRequest req = toSimpleRequest(request);
        req.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, entity.getContentType());
        req.setBody(body, null);

        if (options.getProgressListener() != null)
            options.getProgressListener().onProgress(fileName, inputFile.getLength(), inputFile.getLength());

        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();
        Future<SimpleHttpResponse> future = client.execute(SimpleRequestProducer.create(req),
                org.apache.hc.client5.http.async.methods.SimpleResponseConsumer.create(),
                newHttpClientContext(request), newUploadCallback(promise));
        if (cancelToken != null)
            FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        return promise;
    }

    private FutureCallback<SimpleHttpResponse> newUploadCallback(CompletableFuture<IHttpResponse> promise) {
        return new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                try {
                    promise.complete(fromSimpleResponse(result));
                } catch (Exception e) {
                    failed(e);
                }
            }

            @Override
            public void failed(Exception ex) {
                promise.completeExceptionally(ApacheHttpClientHelper.wrapException(ex));
            }

            @Override
            public void cancelled() {
                promise.cancel(false);
            }
        };
    }
}
