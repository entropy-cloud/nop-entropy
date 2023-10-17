package io.nop.http.apache;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.CompositeX509TrustManager;
import io.nop.http.api.support.DefaultHttpResponse;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.http.api.HttpApiErrors.ERR_HTTP_INIT_SSL_FAIL;

public class ApacheHttpClient implements IHttpClient, IConfigRefreshable {

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
        final IOReactorConfig.Builder ioReactorConfigBuilder = IOReactorConfig.custom();
        if (clientConfig.getWriteTimeout() != null) {
            ioReactorConfigBuilder.setSoTimeout((int) clientConfig.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        IOReactorConfig ioReactorConfig = ioReactorConfigBuilder.build();

        AsyncClientConnectionManager connManager = initConnectionManager();

        this.client = HttpAsyncClients.custom().setIOReactorConfig(ioReactorConfig).setConnectionManager(connManager)
                .evictExpiredConnections().evictIdleConnections(TimeValue.ofMilliseconds(clientConfig.getMaxIdleTime().toMillis()))
                .setUserAgent(clientConfig.getUserAgent()).build();

        RequestConfig.Builder builder = RequestConfig.custom();
        if (clientConfig.getKeepAliveDuration() != null) {
            builder.setDefaultKeepAlive(clientConfig.getKeepAliveDuration().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (clientConfig.getConnectTimeout() != null) {
            builder.setConnectTimeout(clientConfig.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS);
            builder.setConnectionRequestTimeout(clientConfig.getConnectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (clientConfig.getReadTimeout() != null) {
            builder.setResponseTimeout(clientConfig.getReadTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }

        this.defaultRequestConfig = builder.build();

        this.client.start();
    }

    @PreDestroy
    public void stop() {
        if (this.client != null)
            this.client.close(CloseMode.GRACEFUL);
    }

    private TlsStrategy createTlsStrategy() {
        ClientTlsStrategyBuilder builder = ClientTlsStrategyBuilder.create();
        try {

            List<TrustManager> trustManagerList = new ArrayList<>();
            X509TrustManager[] trustManagers = clientConfig.getX509TrustManagers();

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
            compositeX509TrustManager.setIgnoreSSLCert(clientConfig.isIgnoreSslCerts());
            KeyManager[] keyManagers = null;
            if (clientConfig.getKeyManagers() != null) {
                keyManagers = clientConfig.getKeyManagers();
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{compositeX509TrustManager},
                    clientConfig.getSecureRandom());

            HostnameVerifier hostnameVerifier = null;
            if (clientConfig.isIgnoreSslCerts()) {
                hostnameVerifier = new NoopHostnameVerifier();
            } else if (clientConfig.getHostnameVerifier() != null) {
                hostnameVerifier = clientConfig.getHostnameVerifier();
            } else {
                hostnameVerifier = new DefaultHostnameVerifier();
            }

            builder.setHostnameVerifier(hostnameVerifier);
            builder.setSslContext(sslContext);

            return builder.build();
        } catch (Exception e) {
            throw new NopException(ERR_HTTP_INIT_SSL_FAIL, e);
        }

    }

    private PoolingAsyncClientConnectionManager initConnectionManager() {
        PoolingAsyncClientConnectionManagerBuilder builder = PoolingAsyncClientConnectionManagerBuilder.create();
        if (clientConfig.getDnsResolver() != null) {
            builder.setDnsResolver(new DnsResolver() {
                @Override
                public InetAddress[] resolve(String host) throws UnknownHostException {
                    return clientConfig.getDnsResolver().resolve(host);
                }

                @Override
                public String resolveCanonicalHostname(String host) throws UnknownHostException {
                    return clientConfig.getDnsResolver().resolveCanonicalHostname(host);
                }
            });
        }
        builder.setMaxConnTotal(clientConfig.getMaxConnTotal());
        builder.setMaxConnPerRoute(clientConfig.getMaxRequestsPerHost());

        TlsStrategy tlsStrategy = createTlsStrategy();
        builder.setTlsStrategy(tlsStrategy);

        return builder.build();
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        SimpleHttpRequest req = toSimpleRequest(request);
        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();

        Future<?> future = client.execute(req, new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                promise.complete(fromSimpleResponse(result));
            }

            @Override
            public void failed(Exception ex) {
                promise.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
            }
        });

        if (cancelToken != null) {
            cancelToken.appendOnCancelTask(() -> future.cancel(false));
        }
        return promise;
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
                Object value = entry.getValue();
                if (value == null)
                    continue;

                if (value instanceof List<?>) {
                    List<?> list = (List<?>) value;
                    for (Object item : list) {
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

        if (request.getTimeout() > 0) {
            RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                    .setResponseTimeout(request.getTimeout(), TimeUnit.MILLISECONDS).build();

            builder.setRequestConfig(requestConfig);
        }
        return builder.build();
    }

    private DefaultHttpResponse fromSimpleResponse(SimpleHttpResponse httpResponse) {
        DefaultHttpResponse result = new DefaultHttpResponse();

        // status code
        result.setHttpStatus(httpResponse.getCode());
        httpResponse.getBodyBytes();
        SimpleBody body = httpResponse.getBody();
        if (body.isText()) {
            result.setBodyAsText(body.getBodyText());
        } else if (body.isBytes()) {
            result.setBodyAsBytes(body.getBodyBytes());
        }
        ContentType contentType = body.getContentType();
        if (contentType != null) {
            Charset charset = contentType.getCharset();
            if (charset != null) {
                result.setCharset(charset.name());
            }
            result.setContentType(contentType.getMimeType());
        }

        // headers
        Map<String, String> headers = new HashMap<>();
        for (Header header : httpResponse.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        result.setHeaders(headers);

        return result;
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
