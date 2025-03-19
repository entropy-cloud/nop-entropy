package io.nop.http.apache;

import io.nop.api.core.exceptions.NopConnectException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.support.CompositeX509TrustManager;
import io.nop.http.api.support.DefaultHttpResponse;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.nop.http.api.HttpApiErrors.ERR_HTTP_CONNECT_FAIL;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_INIT_SSL_FAIL;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_TIMEOUT;

public class ApacheHttpClientHelper {

    public static CloseableHttpAsyncClient createHttp2(HttpClientConfig clientConfig, IOReactorConfig ioReactorConfig) {
        H2AsyncClientBuilder builder = HttpAsyncClients.customHttp2();
        builder.setIOReactorConfig(ioReactorConfig)
                .evictIdleConnections(TimeValue.ofMilliseconds(clientConfig.getMaxIdleTime().toMillis()))
                .setUserAgent(clientConfig.getUserAgent()).build();
        return builder.build();
    }

    public static CloseableHttpAsyncClient createHttp(HttpClientConfig clientConfig, IOReactorConfig ioReactorConfig,
                                                      PoolingAsyncClientConnectionManager connManager) {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();
        builder.setIOReactorConfig(ioReactorConfig).setConnectionManager(connManager)
                .evictIdleConnections(TimeValue.ofMilliseconds(clientConfig.getMaxIdleTime().toMillis()))
                .setUserAgent(clientConfig.getUserAgent());
        return builder.build();
    }

    public static RequestConfig createRequestConfig(HttpClientConfig clientConfig) {
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
        return builder.build();
    }

    public static IOReactorConfig createReactorConfig(HttpClientConfig clientConfig) {
        final IOReactorConfig.Builder builder = IOReactorConfig.custom();
        if (clientConfig.getWriteTimeout() != null) {
            builder.setSoTimeout((int) clientConfig.getWriteTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (clientConfig.getIoThreadCount() > 0)
            builder.setIoThreadCount(clientConfig.getIoThreadCount());
        return builder.build();
    }

    public static PoolingAsyncClientConnectionManager createConnectionManager(HttpClientConfig clientConfig) {
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

        TlsStrategy tlsStrategy = createTlsStrategy(clientConfig);
        builder.setTlsStrategy(tlsStrategy);

        return builder.build();
    }

    public static TlsStrategy createTlsStrategy(HttpClientConfig clientConfig) {
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

            SSLContext sslContext = SSLContext.getInstance(clientConfig.getSslVersion());
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

    public static DefaultHttpResponse fromSimpleResponse(SimpleHttpResponse httpResponse) {
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
        Map<String, String> headers = getHeaders(httpResponse.getHeaders());
        result.setHeaders(headers);

        return result;
    }

    public static Map<String, String> getHeaders(Header[] headers) {
        Map<String, String> ret = new HashMap<>();
        for (Header header : headers) {
            ret.putIfAbsent(header.getName(), header.getValue());
        }
        return ret;
    }

    public static RuntimeException wrapException(Throwable e) {
        if (e instanceof UnknownHostException)
            return new NopConnectException(ERR_HTTP_CONNECT_FAIL, e);
        if (e instanceof HttpHostConnectException)
            return new NopConnectException(ERR_HTTP_CONNECT_FAIL, e);
        if (e instanceof SocketTimeoutException)
            return new NopTimeoutException(ERR_HTTP_TIMEOUT).cause(e);
        if (e instanceof H2StreamResetException)
            return new NopTimeoutException(ERR_HTTP_TIMEOUT).cause(e);
        return NopException.adapt(e);
    }
}
