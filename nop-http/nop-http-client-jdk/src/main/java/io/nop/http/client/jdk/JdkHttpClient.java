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
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.HttpStatus;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.contenttype.ContentType;
import io.nop.http.api.support.CompositeX509TrustManager;
import io.nop.http.api.support.DefaultHttpResponse;
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
import java.util.concurrent.Flow;

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
                builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_FORM_MULTIPART);
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
            LOG.debug("Header: {} = {}", key, String.join(", ", values));
        });

        LOG.debug("Request body: {}", body);
    }

    Object normalizeBody(String dataType, Object body) {
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
            LOG.debug("http.response:status={},body={}", response.statusCode(), ret.getBodyAsText());

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

        File file = targetFile.toFile();
        FileHelper.assureParent(file);

        CompletableFuture<HttpResponse<Path>> future = client.sendAsync(toJdkHttpRequest(request), HttpResponse.BodyHandlers.ofFile(file.toPath()));
        FutureHelper.bindCancelToken(cancelToken, future);

        return future.thenApply(res -> toHttpResponse(res, true));
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                                      ICancelToken cancelToken) {
        return null;
    }
}