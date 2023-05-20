/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.client.jdk;

import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
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
import io.nop.http.api.contenttype.ContentType;
import io.nop.http.api.support.CompositeX509TrustManager;
import io.nop.http.api.support.DefaultHttpResponse;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static io.nop.http.api.HttpApiErrors.ERR_HTTP_CONNECT_FAIL;

public class JdkHttpClient implements IHttpClient {
    private final HttpClientConfig config;

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
                .version(config.isHttp2() ? HttpClient.Version.HTTP_1_1 : HttpClient.Version.HTTP_2)
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
            executor = DefaultThreadPoolExecutor.newExecutor(config.getThreadName(), config.getThreadPoolSize(), 10);
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

            SSLContext sslContext = SSLContext.getInstance("TLS");
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
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder();
        String method = request.getMethod();
        if (method == null) {
            method = HttpApiConstants.METHOD_GET;
        }
        builder.method(method, toBody(request.getDataType(), request.getBody()));
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
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
            } else {
                builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_JSON);
            }
        }

        if (request.getTimeout() > 0) {
            builder.timeout(Duration.of(request.getTimeout(), ChronoUnit.MILLIS));
        } else if (config.getReadTimeout() != null) {
            builder.timeout(config.getReadTimeout());
        }

        builder.uri(toURI(request.getUrl()));

        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(builder.build(),
                HttpResponse.BodyHandlers.ofByteArray()).exceptionally(this::wrapError);
        if (cancelTokens != null) {
            cancelTokens.appendOnCancel(reason -> {
                future.cancel(false);
            });
        }
        return future.thenApply(this::toHttpResponse);
    }

    public HttpResponse<byte[]> wrapError(Throwable e) {
        if (e instanceof CompletionException) {
            e = e.getCause();
        }
        if (e instanceof ConnectException)
            throw new NopConnectException(ERR_HTTP_CONNECT_FAIL);
        throw NopException.adapt(e);
    }

    URI toURI(String url) {
        return URI.create(url);
    }

    java.net.http.HttpRequest.BodyPublisher toBody(String dataType, Object body) {
        if (body == null)
            return BodyPublishers.noBody();
        if (body instanceof String)
            return BodyPublishers.ofString(body.toString());

        if (body instanceof byte[]) {
            return BodyPublishers.ofByteArray((byte[]) body);
        }
        if (HttpApiConstants.DATA_TYPE_FORM.equals(dataType)) {
            return BodyPublishers.ofString(StringHelper.encodeQuery((Map<String, Object>) body, StringHelper.ENCODING_UTF8));
        }
        return BodyPublishers.ofString(JSON.stringify(body));
    }

    IHttpResponse toHttpResponse(HttpResponse<byte[]> response) {
        DefaultHttpResponse ret = new DefaultHttpResponse();
        ret.setHttpStatus(response.statusCode());
        ret.setBodyAsBytes(response.body());
        ret.setHeaders(toMap(response.headers()));

        Optional<String> contentType = response.headers().firstValue(HttpApiConstants.HEADER_CONTENT_TYPE);
        if (contentType.isPresent()) {
            ContentType parsed = ContentType.parse(contentType.get());
            if (parsed.getCharset() != null) {
                ret.setCharset(parsed.getCharset().name());
            } else {
                ret.setCharset("UTF-8");
            }
            ret.setContentType(parsed.getMimeType());
        }
        return ret;
    }

    Map<String, String> toMap(HttpHeaders headers) {
        Map<String, String> ret = new HashMap<>();
        for (String name : headers.map().keySet()) {
            ret.put(name, headers.firstValue(name).get());
        }
        return ret;
    }

    @Override
    public CompletionStage<Void> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                               ICancelToken cancelToken) {
        return null;
    }

    @Override
    public CompletionStage<Void> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                             ICancelToken cancelToken) {
        return null;
    }
}